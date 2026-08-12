# Builds llama.cpp (KleidiAI ON + OFF) and whisper.cpp for arm64-v8a.
# Host: Windows x86-64.  Target: Armv8.2-A, Cortex-A76/A78, dotprod, NO i8mm.
$ErrorActionPreference = 'Continue'

$NDK    = 'D:/Android/Sdk/ndk/27.3.13750724'
$CMAKE  = 'D:/Android/Sdk/cmake/3.31.6/bin/cmake.exe'
$NINJA  = 'D:/Android/Sdk/cmake/3.31.6/bin/ninja.exe'
$TOOLCH = "$NDK/build/cmake/android.toolchain.cmake"

# NOTE: deliberately NO -march flag. The official docs/android.md example uses
# -march=armv8.7a, which emits instructions Cortex-A76/A78 (Armv8.2-A) do not
# have -> SIGILL at runtime. GGML dispatches on runtime CPU detection instead.
$common = @(
  "-G","Ninja",
  "-DCMAKE_MAKE_PROGRAM=$NINJA",
  "-DCMAKE_TOOLCHAIN_FILE=$TOOLCH",
  "-DANDROID_ABI=arm64-v8a",
  "-DANDROID_PLATFORM=android-28",
  "-DCMAKE_BUILD_TYPE=Release",
  "-DBUILD_SHARED_LIBS=OFF",
  "-DGGML_OPENMP=OFF",
  "-DGGML_LLAMAFILE=OFF"
)

function Build($srcDir, $buildDir, $extra) {
  Write-Output "==================== CONFIGURE $buildDir ===================="
  Push-Location $srcDir
  & $CMAKE -B $buildDir @common @extra
  if ($LASTEXITCODE -ne 0) { Write-Output "!! CONFIGURE FAILED: $buildDir"; Pop-Location; return }
  Write-Output "==================== BUILD $buildDir ===================="
  & $CMAKE --build $buildDir -j
  if ($LASTEXITCODE -ne 0) { Write-Output "!! BUILD FAILED: $buildDir" } else { Write-Output "== OK $buildDir" }
  Pop-Location
}

Build 'D:/omnitalk/third_party/llama.cpp'   'build-android'          @('-DGGML_CPU_KLEIDIAI=ON','-DLLAMA_CURL=OFF')
Build 'D:/omnitalk/third_party/llama.cpp'   'build-android-nokleidi' @('-DGGML_CPU_KLEIDIAI=OFF','-DLLAMA_CURL=OFF')
Build 'D:/omnitalk/third_party/whisper.cpp' 'build-android'          @('-DGGML_CPU_KLEIDIAI=ON')

Write-Output '==================== ARTIFACTS ===================='
foreach ($p in @(
  'D:/omnitalk/third_party/llama.cpp/build-android/bin/llama-cli',
  'D:/omnitalk/third_party/llama.cpp/build-android/bin/llama-bench',
  'D:/omnitalk/third_party/llama.cpp/build-android-nokleidi/bin/llama-bench',
  'D:/omnitalk/third_party/whisper.cpp/build-android/bin/whisper-cli')) {
  if (Test-Path $p) { "{0,-12:N2} KB  {1}" -f ((Get-Item $p).Length/1KB), $p }
  else {
    $alt = Get-ChildItem (Split-Path $p) -Filter "$(Split-Path $p -Leaf)*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($alt) { "{0,-12:N2} KB  {1}" -f ($alt.Length/1KB), $alt.FullName } else { "MISSING     $p" }
  }
}
Write-Output '==================== DONE ===================='
