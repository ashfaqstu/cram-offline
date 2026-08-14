# Reproducing the measurements

Every number in the README came from `bench/otbench.ps1` running `llama-bench` on a physical phone over adb. This document is enough to re-run it on yours and disagree with us.

Reported device: **Poco M2 Pro** (Snapdragon 720G, 2× Cortex-A76 @ 2.3 GHz + 6× Cortex-A55, Armv8.2-A, 6 GB RAM), Android 12 / MIUI. Raw output is in [`bench/results/`](../bench/results/); the device's own `/proc/cpuinfo` and feature flags are in `device_info_POCO_M2_Pro.txt`.

---

## What you need

- An arm64 Android phone with USB debugging on
- `adb` on PATH
- The Android NDK (r26+) to build the native binaries
- ~3 GB free on the phone's `/data/local/tmp`

## 1. Build two llama-bench binaries

The KleidiAI claim rests on comparing an identical build with the library on and off, so build both from the same source tree:

```bash
git submodule update --init --recursive

# WITH KleidiAI
cmake -B build-kleidi third_party/llama.cpp \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DGGML_CPU_KLEIDIAI=ON -DLLAMA_CURL=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build build-kleidi --target llama-bench -j 6

# WITHOUT KleidiAI
cmake -B build-nokleidi third_party/llama.cpp \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DGGML_CPU_KLEIDIAI=OFF -DLLAMA_CURL=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build build-nokleidi --target llama-bench -j 6
```

Two build notes that cost us an afternoon each:

- `LLVM ERROR: IO failure on output stream: No space left on device` reads like an NDK bug and is exactly what it says — the linker needs several GB of scratch. Building with `-g0 -s` cut our binaries from 196 MB to 4.7 MB.
- `ninja: error: unknown target 'llama-cli'` — the CLI tools sit inside llama.cpp's `if (LLAMA_BUILD_SERVER)` block, so configuring with `-DLLAMA_BUILD_SERVER=OFF` removes them. `llama-bench` is unaffected, but add `-DLLAMA_BUILD_SERVER=ON` if you want the CLI too.

Our own build script, with the exact flags used for the published numbers, is [`scripts/build_native.ps1`](../scripts/build_native.ps1).

## 2. Push everything to the phone

```bash
./scripts/fetch_models.sh          # Q4_0 and Q4_K_M weights, sha256-verified
adb shell mkdir -p /data/local/tmp/ot
adb push build-kleidi/bin/llama-bench   /data/local/tmp/ot/llama-bench
adb push build-nokleidi/bin/llama-bench /data/local/tmp/ot/llama-bench-nokleidi
adb push models/Llama-3.2-1B-Instruct-Q4_0.gguf   /data/local/tmp/ot/
adb push models/Llama-3.2-1B-Instruct-Q4_K_M.gguf /data/local/tmp/ot/
adb shell chmod +x /data/local/tmp/ot/llama-bench /data/local/tmp/ot/llama-bench-nokleidi
```

## 3. Run the sweep

```powershell
pwsh bench/otbench.ps1        # writes bench/results/sweep_<device>.csv
python bench/analyze.py       # writes TABLES.md and the charts
```

Each cell is `llama-bench -p 128 -n 32 -r 3` — 128-token prefill, 32-token decode, **3 repetitions**, and llama-bench reports its own mean ± stddev. `pp128` is prefill throughput, `tg32` is decode.

**Keep the phone cool and plugged in.** Thermal throttling on a passively-cooled midrange SoC moves these numbers by more than the effects being measured. We saw prefill vary between 14 and 19 tok/s on the same build depending on how warm the phone was, which is why the KleidiAI comparison is run back-to-back rather than hours apart.

---

## Reading the results

### The KleidiAI comparison

```bash
grep "^B," bench/results/sweep_*.csv
```

You are looking for whether `llama-bench` and `llama-bench-nokleidi` differ **consistently and in one direction**. Ours did not: Q4_0 prefill was 7% faster with KleidiAI on, Q4_0 decode was 4% faster with it *off*, and Q4_K_M prefill was 5% faster with it *off*. Effects that change sign between measurements are noise.

Confirm the mechanism rather than inferring it from timings — the library announces its own refusal:

```bash
adb shell "cd /data/local/tmp/ot && ./llama-bench -m Llama-3.2-1B-Instruct-Q4_0.gguf -p 8 -n 8 2>&1 | grep -i kleidi"
```

On Armv8.2-A this prints `no compatible q4 kernels found for CPU features mask 1`. On a phone with i8mm (Cortex-A710 / X2 and later, roughly 2021 flagships onward) it will select a kernel instead — **and there the guidance is correct.** If you have such a device, we would genuinely like your CSV.

Check what your CPU actually has:

```bash
adb shell grep -m1 Features /proc/cpuinfo
```

`asimddp` is dotprod, which Armv8.2-A has. `i8mm` and `sve`/`sme` are the ones that matter for KleidiAI's int4 path.

### The thread cliff

```bash
grep "^A,.*none" bench/results/sweep_*.csv
```

The result to look for is decode collapsing at `-t 8` while prefill peaks there. On our 2+6 chip decode fell 58% (10.95 → 4.57 tok/s). The mechanism is a per-layer barrier: the two fast cores finish and wait on the six slow ones, so the eighth thread adds stalls rather than work.

Phones with a different cluster split will put the cliff somewhere else — that is the point. The app measures its own thread counts at startup rather than shipping ours.

### Affinity

`affinity=none` is unpinned, `c0` is the two big cores, `3f` is the six LITTLE cores (`taskset` masks). The LITTLE cluster alone manages 3.41 tok/s decode against the big pair's 8.64 — but 6 *unpinned* threads beat both at 10.95, because the little cores do contribute real work at 1B.

---

## Measuring the app itself, not the benchmark

`llama-bench` measures the engine. Cram's user-visible latency also includes PDF extraction, BM25 retrieval and prompt assembly, so the app instruments and displays those itself:

- **Every answer** shows `evidence N ms` (retrieval) and `first word N.Ns` (end-to-end).
- **Settings → Measured on first run** shows the prefill throughput the app timed on your phone at startup, and the prompt budget it derived from it.

Those are live numbers from your own device, not ours. If the app feels slow on your phone, that screen tells you which half is responsible.

---

## If your numbers disagree with ours

Please open an issue with `bench/results/sweep_<device>.csv` and the output of `adb shell grep -m1 Features /proc/cpuinfo`. Disagreement across devices is the useful outcome here — the entire claim is that one hard-coded configuration cannot be right for all Arm phones.
