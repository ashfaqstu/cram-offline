// OmniTalk Edge — single JNI translation unit for llama.cpp + whisper.cpp.
//
// All API names below were verified against the pinned headers on 2026-08-13:
//   llama.cpp   9558fa44c92746a58dd07ad1bf0c889715b938a6
//   whisper.cpp 592feef04a1802b18cbeffd0fd0eb5d02570c2ec
// If you bump either submodule, re-run the drift check in SPEC.md Part 7.3.
//
// THREADING CONTRACT (this is HetPipe, and it is load-bearing):
//   GGML worker threads inherit the affinity mask of the thread that creates
//   them, and the pool is created when the model loads. So Kotlin must call
//   setAffinity() on a dedicated single-thread dispatcher BEFORE llmLoad/asrLoad
//   on that same thread, and every later call for that model must come from the
//   same thread. Pipeline.kt enforces this.
//
// MEMORY CONTRACT: n_ctx is always passed explicitly. Llama 3.2's trained
// context is 131072 tokens; defaulting to it sizes a multi-GB KV cache and the
// Android LMK starts killing apps. See SPEC.md Part 2.

#include <jni.h>
#include <android/log.h>
#include <sched.h>
#include <unistd.h>
#include <ctime>
#include <cstring>
#include <cstdio>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>

#include "llama.h"
#include "whisper.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "otjni", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "otjni", __VA_ARGS__)

static double now_ms() {
    timespec t{};
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1e3 + t.tv_nsec / 1e6;
}

// ─── affinity ────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jint JNICALL
Java_dev_omnitalk_Native_setAffinity(JNIEnv*, jobject, jlong mask) {
    if (mask <= 0) return 0;                 // <=0 means "no pinning"
    cpu_set_t set;
    CPU_ZERO(&set);
    for (int i = 0; i < 64; ++i) if (mask & (1LL << i)) CPU_SET(i, &set);
    int rc = sched_setaffinity(gettid(), sizeof(set), &set);
    LOGI("setAffinity tid=%d mask=0x%llx rc=%d", gettid(), (unsigned long long) mask, rc);
    return rc;
}

// ─── LLM ─────────────────────────────────────────────────────────────────────
struct LlmCtx {
    llama_model*             model = nullptr;
    llama_context*           ctx   = nullptr;
    const llama_vocab*       vocab = nullptr;
    int    n_past            = 0;            // tokens already in the KV cache (O6)
    double last_prefill_ms   = 0;
    double last_decode_ms    = 0;
    int    last_prefill_tok  = 0;
    int    last_decode_tok   = 0;
    std::mutex mu;
};

extern "C" JNIEXPORT jlong JNICALL
Java_dev_omnitalk_Native_llmLoad(JNIEnv* env, jobject, jstring jpath,
                                 jint n_ctx, jint n_threads, jint n_threads_batch) {
    static bool inited = false;
    if (!inited) { llama_backend_init(); inited = true; }

    const char* path = env->GetStringUTFChars(jpath, nullptr);
    auto* L = new LlmCtx();

    llama_model_params mp = llama_model_default_params();
    // API drift: `use_mmap` was replaced by `load_mode` in this revision.
    // mmap keeps RSS at roughly the file size instead of doubling it — on a 6 GB
    // phone that is the difference between running and being LMK-killed.
    mp.load_mode        = LLAMA_LOAD_MODE_MMAP;
    mp.n_gpu_layers     = 0;      // CPU only — that is the whole point
    // Enables GGML's "extra buffer types", i.e. the aarch64 weight-repack path.
    // This — NOT KleidiAI — is what makes Q4_0 ~15% faster at prefill than
    // Q4_K_M on this device. KleidiAI never loads here (see docs/OPTIMIZATION.md O1).
    mp.use_extra_bufts  = true;
    L->model = llama_model_load_from_file(path, mp);
    LOGI("llmLoad path=%s n_ctx=%d t=%d tb=%d -> %p", path, n_ctx, n_threads, n_threads_batch,
         (void*) L->model);
    env->ReleaseStringUTFChars(jpath, path);
    if (!L->model) { delete L; return 0; }

    L->vocab = llama_model_get_vocab(L->model);

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx           = (uint32_t) n_ctx;    // ALWAYS explicit
    cp.n_threads       = n_threads;           // decode  (measured optimum: 6)
    cp.n_threads_batch = n_threads_batch;     // prefill (measured optimum: 8)
    // n_ubatch MUST be <= n_batch. Defaults are n_batch=2048, n_ubatch=512, so
    // lowering only n_batch leaves n_ubatch larger than the batch and the compute
    // buffer overruns -> SIGSEGV inside ggml_vec_dot_*, which reads like a GGML
    // bug and is not. Set them together, always.
    cp.n_batch         = 256;
    cp.n_ubatch        = 256;

    L->ctx = llama_init_from_model(L->model, cp);
    if (!L->ctx) { llama_model_free(L->model); delete L; return 0; }
    return reinterpret_cast<jlong>(L);
}

// O3/O5 — retune threads per pipeline phase without reloading the model.
extern "C" JNIEXPORT void JNICALL
Java_dev_omnitalk_Native_llmSetThreads(JNIEnv*, jobject, jlong h, jint t, jint tb) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    if (!L || !L->ctx) return;
    std::lock_guard<std::mutex> lk(L->mu);
    llama_set_n_threads(L->ctx, t, tb);
}

static std::vector<llama_token> tokenize(LlmCtx* L, const std::string& s, bool add_special) {
    int n = -llama_tokenize(L->vocab, s.c_str(), (int32_t) s.size(),
                            nullptr, 0, add_special, true);
    if (n <= 0) return {};
    std::vector<llama_token> out(n);
    int got = llama_tokenize(L->vocab, s.c_str(), (int32_t) s.size(),
                             out.data(), n, add_special, true);
    if (got < 0) return {};
    out.resize(got);
    return out;
}

// O6 — prefill ONLY the delta. Caller passes just the new text since last call.
// Returns tokens prefilled, or -1 on failure.
extern "C" JNIEXPORT jint JNICALL
Java_dev_omnitalk_Native_llmPrefill(JNIEnv* env, jobject, jlong h, jstring jtext) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    if (!L || !L->ctx) return -1;
    std::lock_guard<std::mutex> lk(L->mu);

    const char* t = env->GetStringUTFChars(jtext, nullptr);
    std::string text(t ? t : "");
    env->ReleaseStringUTFChars(jtext, t);
    if (text.empty()) return 0;

    auto toks = tokenize(L, text, L->n_past == 0);
    if (toks.empty()) return 0;

    const int n_ctx = (int) llama_n_ctx(L->ctx);
    if (L->n_past + (int) toks.size() >= n_ctx) {
        LOGE("prefill would overflow n_ctx (%d + %zu >= %d)", L->n_past, toks.size(), n_ctx);
        return -1;
    }

    double t0 = now_ms();
    const int CHUNK = 256;
    for (size_t i = 0; i < toks.size(); i += CHUNK) {
        int n = (int) std::min<size_t>(CHUNK, toks.size() - i);
        llama_batch b = llama_batch_get_one(toks.data() + i, n);
        if (llama_decode(L->ctx, b) != 0) { LOGE("llama_decode failed in prefill"); return -1; }
        L->n_past += n;
    }
    L->last_prefill_ms  = now_ms() - t0;
    L->last_prefill_tok = (int) toks.size();
    return (jint) toks.size();
}

// Streams each piece back through cb.onToken(String) so Kotlin can start TTS at
// the first sentence boundary (O4 overlap 3 / O8). grammar may be null.
extern "C" JNIEXPORT jstring JNICALL
Java_dev_omnitalk_Native_llmGenerate(JNIEnv* env, jobject, jlong h, jint max_tokens,
                                     jstring jgrammar, jobject cb) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    if (!L || !L->ctx) return env->NewStringUTF("");
    std::lock_guard<std::mutex> lk(L->mu);

    auto sp = llama_sampler_chain_default_params();
    llama_sampler* chain = llama_sampler_chain_init(sp);

    if (jgrammar) {                                            // O7
        const char* g = env->GetStringUTFChars(jgrammar, nullptr);
        llama_sampler* gs = llama_sampler_init_grammar(L->vocab, g, "root");
        env->ReleaseStringUTFChars(jgrammar, g);
        if (gs) llama_sampler_chain_add(chain, gs);
        else    LOGE("grammar failed to parse — falling back to unconstrained");
    }
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(0.4f));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jmethodID onTok = nullptr;
    if (cb) {
        jclass c = env->GetObjectClass(cb);
        onTok = env->GetMethodID(c, "onToken", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(c);
    }

    const int n_ctx = (int) llama_n_ctx(L->ctx);
    std::string out;
    double t0 = now_ms();
    int n = 0;

    for (; n < max_tokens; ++n) {
        if (L->n_past >= n_ctx - 1) { LOGE("hit n_ctx during generate"); break; }

        llama_token tok = llama_sampler_sample(chain, L->ctx, -1);
        if (llama_vocab_is_eog(L->vocab, tok)) break;

        char buf[512];
        int len = llama_token_to_piece(L->vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) {
            std::string piece(buf, len);
            out += piece;
            if (onTok) {
                jstring js = env->NewStringUTF(piece.c_str());
                env->CallVoidMethod(cb, onTok, js);
                env->DeleteLocalRef(js);
                if (env->ExceptionCheck()) { env->ExceptionClear(); onTok = nullptr; }
            }
        }

        llama_sampler_accept(chain, tok);
        llama_batch b = llama_batch_get_one(&tok, 1);
        if (llama_decode(L->ctx, b) != 0) { LOGE("llama_decode failed in generate"); break; }
        L->n_past++;
    }

    L->last_decode_ms  = now_ms() - t0;
    L->last_decode_tok = n;
    llama_sampler_free(chain);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_omnitalk_Native_llmResetKv(JNIEnv*, jobject, jlong h) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    if (!L || !L->ctx) return;
    std::lock_guard<std::mutex> lk(L->mu);
    llama_memory_clear(llama_get_memory(L->ctx), true);
    L->n_past = 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_omnitalk_Native_llmTimings(JNIEnv* env, jobject, jlong h) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    if (!L) return env->NewStringUTF("{}");
    char b[512];
    double ptps = L->last_prefill_ms > 0 ? L->last_prefill_tok * 1000.0 / L->last_prefill_ms : 0.0;
    double dtps = L->last_decode_ms  > 0 ? L->last_decode_tok  * 1000.0 / L->last_decode_ms  : 0.0;
    snprintf(b, sizeof(b),
             "{\"prefill_ms\":%.1f,\"prefill_tok\":%d,\"prefill_tps\":%.2f,"
             "\"decode_ms\":%.1f,\"decode_tok\":%d,\"decode_tps\":%.2f,\"n_past\":%d}",
             L->last_prefill_ms, L->last_prefill_tok, ptps,
             L->last_decode_ms,  L->last_decode_tok,  dtps, L->n_past);
    return env->NewStringUTF(b);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_omnitalk_Native_llmFree(JNIEnv*, jobject, jlong h) {
    auto* L = reinterpret_cast<LlmCtx*>(h);
    if (!L) return;
    if (L->ctx)   llama_free(L->ctx);
    if (L->model) llama_model_free(L->model);
    delete L;
}

// ─── ASR ─────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jlong JNICALL
Java_dev_omnitalk_Native_asrLoad(JNIEnv* env, jobject, jstring jpath) {
    const char* p = env->GetStringUTFChars(jpath, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;                        // CPU only
    whisper_context* c = whisper_init_from_file_with_params(p, cp);
    LOGI("asrLoad path=%s -> %p", p, (void*) c);
    env->ReleaseStringUTFChars(jpath, p);
    return reinterpret_cast<jlong>(c);
}

// pcm must be 16 kHz mono float32 in [-1,1]. Anything else yields confident
// nonsense rather than an error — see SPEC.md Part 8.5.
extern "C" JNIEXPORT jstring JNICALL
Java_dev_omnitalk_Native_asrTranscribe(JNIEnv* env, jobject, jlong h,
                                       jfloatArray jpcm, jstring jlang, jint n_threads) {
    auto* c = reinterpret_cast<whisper_context*>(h);
    if (!c) return env->NewStringUTF("");

    jsize n = env->GetArrayLength(jpcm);
    if (n <= 0) return env->NewStringUTF("");
    std::vector<float> pcm((size_t) n);
    env->GetFloatArrayRegion(jpcm, 0, n, pcm.data());

    const char* lang = env->GetStringUTFChars(jlang, nullptr);
    std::string langStr(lang ? lang : "en");
    env->ReleaseStringUTFChars(jlang, lang);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads       = n_threads;
    p.language        = langStr.c_str();
    p.translate       = false;
    p.print_progress  = false;
    p.print_realtime  = false;
    p.print_timestamps= false;
    p.no_timestamps   = true;
    p.no_context      = true;     // chunked mode: each chunk stands alone
    p.single_segment  = false;
    p.suppress_blank  = true;

    std::string out;
    if (whisper_full(c, p, pcm.data(), (int) pcm.size()) == 0) {
        const int ns = whisper_full_n_segments(c);
        for (int i = 0; i < ns; ++i) {
            const char* s = whisper_full_get_segment_text(c, i);
            if (s) out += s;
        }
    } else {
        LOGE("whisper_full failed");
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_omnitalk_Native_asrFree(JNIEnv*, jobject, jlong h) {
    auto* c = reinterpret_cast<whisper_context*>(h);
    if (c) whisper_free(c);
}

// ─── topology ────────────────────────────────────────────────────────────────
// Detected natively so the same APK adapts to any big.LITTLE Arm phone —
// this is what makes docs/REPRODUCE.md honest for a judge's own device.
extern "C" JNIEXPORT jstring JNICALL
Java_dev_omnitalk_Native_cpuInfo(JNIEnv* env, jobject) {
    int n = (int) sysconf(_SC_NPROCESSORS_CONF);
    if (n <= 0 || n > 64) n = 8;

    std::vector<long> freq((size_t) n, 0);
    long maxf = 0;
    for (int i = 0; i < n; ++i) {
        char path[128];
        snprintf(path, sizeof(path),
                 "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i);
        if (FILE* f = fopen(path, "r")) {
            long v = 0;
            if (fscanf(f, "%ld", &v) == 1) freq[(size_t) i] = v;
            fclose(f);
        }
        maxf = std::max(maxf, freq[(size_t) i]);
    }

    long bigMask = 0, litMask = 0;
    int nBig = 0, nLit = 0;
    for (int i = 0; i < n; ++i) {
        if (maxf > 0 && freq[(size_t) i] >= (long) (maxf * 0.85)) { bigMask |= (1L << i); nBig++; }
        else                                                      { litMask |= (1L << i); nLit++; }
    }
    if (nBig == 0 || nBig == n) { bigMask = -1; litMask = -1; }   // uniform SoC: no pinning

    // report the ISA features that decide whether KleidiAI can engage at all
    bool dotprod = false, i8mm = false, sve = false, sme = false;
    if (FILE* f = fopen("/proc/cpuinfo", "r")) {
        char line[1024];
        while (fgets(line, sizeof(line), f)) {
            if (strncmp(line, "Features", 8) == 0) {
                dotprod = strstr(line, "asimddp") != nullptr;
                i8mm    = strstr(line, "i8mm")    != nullptr;
                sve     = strstr(line, "sve")     != nullptr;
                sme     = strstr(line, "sme")     != nullptr;
                break;
            }
        }
        fclose(f);
    }

    char buf[512];
    snprintf(buf, sizeof(buf),
             "{\"cores\":%d,\"big_mask\":%ld,\"little_mask\":%ld,\"n_big\":%d,\"n_little\":%d,"
             "\"max_khz\":%ld,\"dotprod\":%s,\"i8mm\":%s,\"sve\":%s,\"sme\":%s}",
             n, bigMask, litMask, nBig, nLit, maxf,
             dotprod ? "true" : "false", i8mm ? "true" : "false",
             sve ? "true" : "false", sme ? "true" : "false");
    return env->NewStringUTF(buf);
}
