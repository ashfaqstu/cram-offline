package dev.omnitalk

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import android.util.Log
import kotlin.concurrent.thread

private const val TAG = "otaudio"

/**
 * Microphone capture.
 *
 * whisper.cpp accepts ONLY 16 kHz mono float32 in [-1,1]. Feed it anything else
 * and it does not error — it returns confident nonsense, which is far harder to
 * debug. Everything here exists to guarantee that format.
 */
object Audio {
    const val SAMPLE_RATE = 16_000

    /**
     * Read a 16 kHz mono 16-bit PCM WAV into the float format whisper.cpp wants.
     *
     * This exists so the pipeline can be driven from a file instead of the
     * microphone. Android gives no way to inject audio into the mic, so without
     * it every regression test needs a human to speak — which is both slow and
     * unrepeatable. With it, the same utterance can be replayed after every
     * change and the results compared exactly.
     */
    fun readWav(f: java.io.File): FloatArray {
        val b = f.readBytes()
        if (b.size < 44) return FloatArray(0)
        // walk the chunks rather than assuming a 44-byte header
        var pos = 12
        var dataOff = -1
        var dataLen = 0
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val sz = (b[pos + 4].toInt() and 0xff) or ((b[pos + 5].toInt() and 0xff) shl 8) or
                    ((b[pos + 6].toInt() and 0xff) shl 16) or ((b[pos + 7].toInt() and 0xff) shl 24)
            if (id == "data") { dataOff = pos + 8; dataLen = sz; break }
            pos += 8 + sz + (sz and 1)
        }
        if (dataOff < 0) return FloatArray(0)
        val n = minOf(dataLen, b.size - dataOff) / 2
        val out = FloatArray(n)
        for (i in 0 until n) {
            val lo = b[dataOff + i * 2].toInt() and 0xff
            val hi = b[dataOff + i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort() / 32768f
        }
        return out
    }

    /**
     * Emits a chunk roughly every [chunkSec] seconds while recording, plus the
     * remainder when [stopping] flips true. Chunking is what lets ASR run on the
     * LITTLE cluster *during* speech instead of after it (HetPipe overlap 1).
     */
    @SuppressLint("MissingPermission")
    fun record(chunkSec: Double, stopping: () -> Boolean): Flow<FloatArray> = callbackFlow {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 4
        )

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            close(IllegalStateException("AudioRecord failed to initialise"))
            return@callbackFlow
        }

        val chunkSamples = (SAMPLE_RATE * chunkSec).toInt()
        val acc = FloatArray(chunkSamples)
        var filled = 0
        val raw = ShortArray(minBuf)

        val t = thread(name = "ot-mic", start = true) {
            rec.startRecording()
            Log.i(TAG, "recording started, state=${rec.recordingState} chunk=$chunkSamples")
            var chunks = 0
            var reads = 0
            var peak = 0f
            try {
                while (!stopping()) {
                    val n = rec.read(raw, 0, raw.size)
                    if (n <= 0) { Log.w(TAG, "read returned $n"); continue }
                    reads++
                    for (i in 0 until n) {
                        val s = raw[i] / 32768f
                        if (kotlin.math.abs(s) > peak) peak = kotlin.math.abs(s)
                        acc[filled++] = s
                        if (filled == chunkSamples) {
                            val ok = trySend(acc.copyOf()).isSuccess
                            chunks++
                            if (!ok) Log.w(TAG, "chunk $chunks DROPPED (consumer too slow)")
                            filled = 0
                        }
                    }
                }
                // flush the tail so the last words are not lost
                if (filled > 0) { trySend(acc.copyOf(filled)); chunks++ }
                Log.i(TAG, "recording stopped: reads=$reads chunks=$chunks peak=%.3f".format(peak))
                if (peak < 0.01f) Log.w(TAG, "MIC SILENT — peak amplitude $peak, check mic permission/hardware")
            } catch (e: Throwable) {
                Log.e(TAG, "record loop failed", e)
            } finally {
                runCatching { rec.stop() }
                runCatching { rec.release() }
                close()
            }
        }

        awaitClose { runCatching { t.interrupt() } }
    }
        // UNLIMITED is not optional here. Whisper needs ~1 s to transcribe a 2 s
        // chunk, so the consumer runs behind the microphone from the first chunk.
        // With callbackFlow's default bounded buffer, trySend starts failing and
        // audio is silently discarded: holding for 4.5 s yielded only 1.3 s of
        // captured audio, and the user hears the agent answer a third of what
        // they said. Buffering costs ~32 KB/s of heap; dropping costs the turn.
        .buffer(Channel.UNLIMITED)
        .flowOn(Dispatchers.IO)
}
