# Builds ONLY the binaries we need, for arm64-v8a.
# Host: Windows x86-64.  Target: Armv8.2-A, Cortex-A76/A78, dotprod, NO i8mm.
#
# WHY THIS IS SO NARROW  ────────────────────────────────────────────────────────
# A default `cmake --build` on llama.cpp builds 520 targets: every example, tool,
# test and the server. Each is statically linked AND the NDK toolchain adds -g,
# so each binary is 100-200 MB. That produced an 8 GB build tree and filled the
# disk mid-link with "LLVM ERROR: IO failure on output stream: No space left on
# device" — which looks like a toolchain bug and is not.
#
# So: examples/tests/server OFF, explicit --target list, -g0 to stop generating
# debug info, and -s to strip at link. Result is a few MB per binary.
$ErrorActionPreference = 'Continue'

$NDK    = 'D:/Android/Sdk/ndk/27.3.13750724'
$CMAKE  = 'D:/Android/Sdk/cmake/3.31.6/bin/cmake.exe'
$NINJA  = 'D:/Android/Sdk/cmake/3.31.6/bin/ninja.exe'
$TOOLCH = "$NDK/build/cmake/android.toolchain.cmake"

$common = @(
  "-G","Ninja",
  "-DCMAKE_MAKE_PROGRAM=$NINJA",
  "-DCMAKE_TOOLCHAIN_FILE=$TOOLCH",
  "-DANDROID_ABI=arm64-v8a",
  "-DANDROID_PLATFORM=android-28",
  "-DCMAKE_BUILD_TYPE=Release",
  "-DBUILD_SHARED_LIBS=OFF",
  "-DGGML_OPENMP=OFF",
  "-DGGML_LLAMAFILE=OFF",
  # NO -march: docs/android.md uses armv8.7a, which SIGILLs on Armv8.2-A cores.
  # GGML dispatches on runtime CPU feature detection instead.
  "-DCMAKE_C_FLAGS=-g0",
  "-DCMAKE_CXX_FLAGS=-g0",
  "-DCMAKE_EXE_LINKER_FLAGS=-s"
)

function Build($src, $dir, $extra, $targets) {
  Write-Output "==================== CONFIGURE $dir ===================="
  Push-Location $src
  & $CMAKE -B $dir @common @extra
  if ($LASTEXITCODE -ne 0) { Write-Output "!! CONFIGURE FAILED: $dir"; Pop-Location; return }
  Write-Output "==================== BUILD $dir :: $($targets -join ' ') ===================="
  & $CMAKE --build $dir -j 6 --target @targets
  if ($LASTEXITCODE -ne 0) { Write-Output "!! BUILD FAILED: $dir" } else { Write-Output "== OK $dir" }
  Pop-Location
  "  D: free after: {0:N2} GB" -f ((Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='D:'").FreeSpace/1GB) | Write-Output
}

$llamaBase = @(
  "-DLLAMA_BUILD_TESTS=OFF","-DLLAMA_BUILD_EXAMPLES=OFF",
  "-DLLAMA_BUILD_TOOLS=ON","-DLLAMA_CURL=OFF"
)

# GOTCHA: in this llama.cpp revision tools/cli is inside `if (LLAMA_BUILD_SERVER)`,
# so LLAMA_BUILD_SERVER=OFF silently deletes the `llama-cli` target and ninja
# fails with "unknown target 'llama-cli', did you mean 'llama-app'?".
# Turning SERVER on only affects CONFIGURE; ninja still builds nothing but the
# targets we name, so we do not pay for the server or the UI.
Build 'D:/omnitalk/third_party/llama.cpp' 'build-android' `
      ($llamaBase + @("-DLLAMA_BUILD_SERVER=ON","-DGGML_CPU_KLEIDIAI=ON")) `
      @('llama-cli','llama-bench')

# The KleidiAI-OFF control only needs llama-bench, so keep it minimal.
Build 'D:/omnitalk/third_party/llama.cpp' 'build-android-nokleidi' `
      ($llamaBase + @("-DLLAMA_BUILD_SERVER=OFF","-DGGML_CPU_KLEIDIAI=OFF")) `
      @('llama-bench')

Build 'D:/omnitalk/third_party/whisper.cpp' 'build-android' `
      @("-DWHISPER_BUILD_TESTS=OFF","-DWHISPER_BUILD_SERVER=OFF","-DGGML_CPU_KLEIDIAI=ON") `
      @('whisper-cli')

Write-Output '==================== ARTIFACTS ===================='
New-Item -ItemType Directory -Force -Path 'D:\omnitalk\prebuilt' | Out-Null
$map = @{
  'D:/omnitalk/third_party/llama.cpp/build-android/bin/llama-cli'             = 'llama-cli'
  'D:/omnitalk/third_party/llama.cpp/build-android/bin/llama-bench'           = 'llama-bench'
  'D:/omnitalk/third_party/llama.cpp/build-android-nokleidi/bin/llama-bench'  = 'llama-bench-nokleidi'
  'D:/omnitalk/third_party/whisper.cpp/build-android/bin/whisper-cli'         = 'whisper-cli'
}
foreach ($src in $map.Keys) {
  if (Test-Path $src) {
    Copy-Item $src "D:\omnitalk\prebuilt\$($map[$src])" -Force
    "{0,10:N2} MB  {1}" -f ((Get-Item $src).Length/1MB), $map[$src]
  } else { "  MISSING     $($map[$src])   ($src)" }
}
"D: free = {0:N2} GB" -f ((Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='D:'").FreeSpace/1GB)
Write-Output '==================== DONE ===================='
