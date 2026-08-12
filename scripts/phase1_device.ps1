# PHASE 1 — one shot. Run this the moment the phone is connected.
#
#   powershell -ExecutionPolicy Bypass -File D:\omnitalk\scripts\phase1_device.ps1
#
# Captures the silicon facts, detects the big/LITTLE core masks, pushes the
# binaries and models, then runs GATE 1 (first token), the O1 smoking gun,
# GATE 2 (Whisper RTF) and GATE 3 (cluster concurrency).
# Everything it prints is evidence for the submission — the transcript is saved.

$ErrorActionPreference = 'Continue'
$ADB   = 'D:\Android\Sdk\platform-tools\adb.exe'
$DEV   = '/data/local/tmp/ot'
$PRE   = 'D:\omnitalk\prebuilt'
$MOD   = 'D:\omnitalk\models'
$OUT   = 'D:\omnitalk\bench\results'
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

function Sh($cmd) { & $ADB shell $cmd 2>&1 }

# ── 0. device present? ────────────────────────────────────────────────────────
$devs = (& $ADB devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
if (-not $devs) {
  Write-Output "!! No authorised device. Check: USB debugging ON, RSA prompt accepted on the phone."
  & $ADB devices -l
  exit 1
}
Write-Output "Device: $devs"

# ── 1. silicon facts ──────────────────────────────────────────────────────────
$model = (Sh 'getprop ro.product.model').Trim() -replace '[^\w]','_'
$info  = "$OUT\device_info_$model.txt"
Write-Output "`n=== capturing silicon facts -> $info ==="

$lines = @()
$lines += "# Captured $(Get-Date -Format o)"
$lines += "model            : $(Sh 'getprop ro.product.model')"
$lines += "board            : $(Sh 'getprop ro.board.platform')"
$lines += "soc_model        : $(Sh 'getprop ro.soc.model')"
$lines += "android          : $(Sh 'getprop ro.build.version.release')"
$lines += "abi              : $(Sh 'getprop ro.product.cpu.abi')"
$lines += ""
$lines += "## /proc/cpuinfo Features (THE thesis line: expect asimddp, NO i8mm/sve/sme)"
$lines += (Sh 'grep -m1 Features /proc/cpuinfo')
$lines += ""
$lines += "## per-core max frequency (kHz)"
$freqRaw = Sh 'for i in 0 1 2 3 4 5 6 7; do f=$(cat /sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq 2>/dev/null); echo "cpu$i $f"; done'
$lines += $freqRaw
$lines += ""
$lines += "## memory"
$lines += (Sh 'head -3 /proc/meminfo')
$lines += ""
$lines += "## storage"
$lines += (Sh 'df -h /data')
$lines | Set-Content -Path $info -Encoding utf8
Get-Content $info | Write-Output

# ── 2. derive core masks ──────────────────────────────────────────────────────
$freqs = @{}
foreach ($l in ($freqRaw -split "`n")) {
  if ($l -match '^cpu(\d+)\s+(\d+)') { $freqs[[int]$Matches[1]] = [int]$Matches[2] }
}
if ($freqs.Count -eq 0) { Write-Output "!! could not read cpufreq; defaulting to BIG=0xC0 LITTLE=0x3F"; $BIG=0xC0; $LIT=0x3F }
else {
  $maxF = ($freqs.Values | Measure-Object -Maximum).Maximum
  $BIG = 0; $LIT = 0
  foreach ($k in $freqs.Keys) {
    if ($freqs[$k] -ge $maxF * 0.85) { $BIG = $BIG -bor (1 -shl $k) } else { $LIT = $LIT -bor (1 -shl $k) }
  }
}
$bigHex = '{0:x}' -f $BIG; $litHex = '{0:x}' -f $LIT
$nBig = ([Convert]::ToString($BIG,2).ToCharArray() | Where-Object {$_ -eq '1'}).Count
$nLit = ([Convert]::ToString($LIT,2).ToCharArray() | Where-Object {$_ -eq '1'}).Count
Write-Output "`n>>> BIG_MASK=0x$bigHex ($nBig cores)   LITTLE_MASK=0x$litHex ($nLit cores)"
Write-Output ">>> RECORD THESE IN PROGRESS.md. If they are not 0xc0 / 0x3f, update SPEC.md."
Add-Content $info "`n## derived masks`nBIG_MASK=0x$bigHex n=$nBig`nLITTLE_MASK=0x$litHex n=$nLit"

# ── 3. push ───────────────────────────────────────────────────────────────────
Write-Output "`n=== pushing binaries + models (first run moves ~1.6 GB, be patient) ==="
Sh "mkdir -p $DEV" | Out-Null
foreach ($f in @('llama-cli','llama-bench','llama-bench-nokleidi','whisper-cli')) {
  if (Test-Path "$PRE\$f") { & $ADB push "$PRE\$f" "$DEV/$f" | Select-Object -Last 1 }
  else { Write-Output "  !! missing prebuilt/$f" }
}
& $ADB push "D:\omnitalk\third_party\whisper.cpp\samples\jfk.wav" "$DEV/jfk.wav" | Select-Object -Last 1
foreach ($m in @('Llama-3.2-1B-Instruct-Q4_0.gguf','Llama-3.2-1B-Instruct-Q4_K_M.gguf','ggml-base-q5_1.bin','ggml-tiny-q5_1.bin')) {
  $exists = Sh "[ -f $DEV/$m ] && echo yes || echo no"
  if ($exists -match 'yes') { Write-Output "  already on device: $m" }
  else { & $ADB push "$MOD\$m" "$DEV/$m" | Select-Object -Last 1 }
}
Sh "chmod +x $DEV/llama-cli $DEV/llama-bench $DEV/llama-bench-nokleidi $DEV/whisper-cli" | Out-Null

# ── 4. GATE 1 — first token ───────────────────────────────────────────────────
Write-Output "`n============ GATE 1: first token on the phone ============"
$g1 = Sh "cd $DEV && taskset $bigHex ./llama-cli -m Llama-3.2-1B-Instruct-Q4_0.gguf -p 'Translate to Spanish: Where is the bus station?' -n 48 -t $nBig --no-cnv 2>&1"
$g1 | Write-Output
$g1 | Set-Content "$OUT\gate1_first_token.txt" -Encoding utf8

# ── 5. O1 smoking gun — KleidiAI fallback warning ─────────────────────────────
Write-Output "`n============ O1: KleidiAI kernel selection ============"
foreach ($q in @('Q4_0','Q4_K_M')) {
  Write-Output "--- $q ---"
  $o = Sh "cd $DEV && ./llama-cli -m Llama-3.2-1B-Instruct-$q.gguf -p hi -n 1 --no-cnv 2>&1 | grep -i -e kleidi -e repack"
  if ($o) { $o | Write-Output } else { Write-Output "  (no kleidiai/repack line)" }
  Add-Content "$OUT\o1_kleidiai_selection.txt" "=== $q ===`n$o`n"
}

# ── 6. GATE 2 — Whisper RTF (jfk.wav is 11.0 s of 16 kHz mono) ────────────────
Write-Output "`n============ GATE 2: Whisper RTF ============"
foreach ($m in @('ggml-tiny-q5_1.bin','ggml-base-q5_1.bin')) {
  $r = Sh "cd $DEV && taskset $litHex ./whisper-cli -m $m -f jfk.wav -t $nLit -l en 2>&1 | grep -E 'total time|encode time|decode time'"
  Write-Output "$m (on LITTLE, t=$nLit):"; $r | Write-Output
  Add-Content "$OUT\gate2_whisper_rtf.txt" "=== $m ===`n$r`n"
}
Write-Output "RTF = total_time / 11.0 s.  Need < 0.7 to proceed with 'base'."

# ── 7. GATE 3 — cluster concurrency ───────────────────────────────────────────
Write-Output "`n============ GATE 3: concurrency (LLM on big vs LLM on big + ASR on LITTLE) ============"
Write-Output "--- solo ---"
$solo = Sh "cd $DEV && taskset $bigHex ./llama-bench -m Llama-3.2-1B-Instruct-Q4_0.gguf -t $nBig -p 128 -n 64 -r 2 2>&1 | tail -5"
$solo | Write-Output
Write-Output "--- concurrent (whisper hammering the LITTLE cluster) ---"
$conc = Sh "cd $DEV && (taskset $litHex ./whisper-cli -m ggml-base-q5_1.bin -f jfk.wav -t $nLit -l en >/dev/null 2>&1 &) ; sleep 1 ; taskset $bigHex ./llama-bench -m Llama-3.2-1B-Instruct-Q4_0.gguf -t $nBig -p 128 -n 64 -r 2 2>&1 | tail -5"
$conc | Write-Output
Set-Content "$OUT\gate3_concurrency.txt" "=== solo ===`n$solo`n`n=== concurrent ===`n$conc" -Encoding utf8
Write-Output "`nGATE 3 passes if concurrent tok/s >= 80% of solo."

Write-Output "`n=== results written to $OUT ==="
Get-ChildItem $OUT | Select-Object Name, Length | Format-Table -AutoSize
