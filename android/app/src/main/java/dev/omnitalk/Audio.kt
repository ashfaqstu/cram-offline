package dev.omnitalk

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
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
    }.flowOn(Dispatchers.IO)
}
