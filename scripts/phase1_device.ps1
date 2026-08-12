# PHASE 1 — one shot. Run with the phone connected.
#
#   powershell -ExecutionPolicy Bypass -File D:\omnitalk\scripts\phase1_device.ps1
#
# MEMORY POLICY — this phone has 6 GB with only ~1.7 GB MemAvailable in normal
# use. Everything below is deliberately gentle:
#   * -c 2048 is passed to EVERY llama invocation. Without it llama.cpp uses the
#     model's trained context (Llama 3.2 = 131072 tokens), and the KV cache alone
#     would be many GB -> the Android Low Memory Killer kills the app, or the
#     phone thrashes and feels broken. This is the single most important flag here.
#   * one model in memory at a time; each run is a separate short-lived process
#   * -j/-r kept low, --no-warmup where supported, and a cooldown between runs
#   * the concurrency test is short and is the ONLY step that loads two models
#
# Every value it prints is evidence for the submission; transcripts go to
# bench/results/.

$ErrorActionPreference = 'Continue'
$ADB   = 'D:\Android\Sdk\platform-tools\adb.exe'
$DEV   = '/data/local/tmp/ot'
$PRE   = 'D:\omnitalk\prebuilt'
$MOD   = 'D:\omnitalk\models'
$OUT   = 'D:\omnitalk\bench\results'
$NCTX  = 2048          # <- the memory guardrail. Do not remove.
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

function Sh($cmd) { & $ADB shell $cmd 2>&1 }
function Mem() { (Sh 'grep MemAvailable /proc/meminfo').Trim() }
function Cool($s) { Write-Output "    (cooldown ${s}s)"; Start-Sleep -Seconds $s }

# ── 0. device present? ────────────────────────────────────────────────────────
$devs = (& $ADB devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
if (-not $devs) {
  Write-Output "!! No authorised device."
  Write-Output "   Try: adb kill-server; adb start-server; replug cable; accept RSA prompt."
  & $ADB devices -l
  exit 1
}
Write-Output "Device: $devs"
Write-Output "Memory before we start: $(Mem)"

# ── 1. silicon facts ──────────────────────────────────────────────────────────
$model = (Sh 'getprop ro.product.model').Trim() -replace '[^\w]','_'
$info  = "$OUT\device_info_$model.txt"
Write-Output "`n=== silicon facts -> $info ==="
$lines = @("# Captured $(Get-Date -Format o)")
$lines += "model     : $(Sh 'getprop ro.product.model')"
$lines += "board     : $(Sh 'getprop ro.board.platform')"
$lines += "android   : $(Sh 'getprop ro.build.version.release')"
$lines += "abi       : $(Sh 'getprop ro.product.cpu.abi')"
$lines += "`n## /proc/cpuinfo Features (expect asimddp; NO i8mm/sve/sme)"
$lines += (Sh 'grep -m1 Features /proc/cpuinfo')
$lines += "`n## per-core max frequency (kHz)"
$freqRaw = Sh 'for i in 0 1 2 3 4 5 6 7; do f=$(cat /sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq 2>/dev/null); echo "cpu$i $f"; done'
$lines += $freqRaw
$lines += "`n## memory"; $lines += (Sh 'head -3 /proc/meminfo')
$lines += "`n## storage"; $lines += (Sh 'df -h /data')
$lines | Set-Content -Path $info -Encoding utf8
Write-Output "  saved."

# ── 2. derive core masks ──────────────────────────────────────────────────────
$freqs = @{}
foreach ($l in ($freqRaw -split "`n")) { if ($l -match '^cpu(\d+)\s+(\d+)') { $freqs[[int]$Matches[1]] = [int]$Matches[2] } }
if ($freqs.Count -eq 0) { $BIG=0xC0; $LIT=0x3F }
else {
  $maxF = ($freqs.Values | Measure-Object -Maximum).Maximum
  $BIG = 0; $LIT = 0
  foreach ($k in $freqs.Keys) { if ($freqs[$k] -ge $maxF*0.85) { $BIG = $BIG -bor (1 -shl $k) } else { $LIT = $LIT -bor (1 -shl $k) } }
}
$bigHex = '{0:x}' -f $BIG; $litHex = '{0:x}' -f $LIT
$nBig = ([Convert]::ToString($BIG,2).ToCharArray() | ? {$_ -eq '1'}).Count
$nLit = ([Convert]::ToString($LIT,2).ToCharArray() | ? {$_ -eq '1'}).Count
Write-Output ">>> BIG=0x$bigHex ($nBig)  LITTLE=0x$litHex ($nLit)"
Add-Content $info "`n## derived masks`nBIG_MASK=0x$bigHex n=$nBig`nLITTLE_MASK=0x$litHex n=$nLit"

# ── 3. push (skips anything already present and correctly sized) ──────────────
Write-Output "`n=== pushing ==="
Sh "mkdir -p $DEV" | Out-Null
foreach ($f in @('llama-cli','llama-bench','llama-bench-nokleidi','whisper-cli')) {
  if (Test-Path "$PRE\$f") { & $ADB push "$PRE\$f" "$DEV/$f" 2>&1 | Select-Object -Last 1 | Write-Output }
}
& $ADB push "D:\omnitalk\third_party\whisper.cpp\samples\jfk.wav" "$DEV/jfk.wav" 2>&1 | Select-Object -Last 1 | Write-Output
foreach ($m in @('Llama-3.2-1B-Instruct-Q4_0.gguf','Llama-3.2-1B-Instruct-Q4_K_M.gguf','ggml-base-q5_1.bin','ggml-tiny-q5_1.bin')) {
  $want = (Get-Item "$MOD\$m").Length
  $have = (Sh "stat -c %s $DEV/$m 2>/dev/null || echo 0").Trim()
  if ($have -eq "$want") { Write-Output "  ok (size matches): $m" }
  else {
    if ($have -ne '0') { Write-Output "  re-pushing truncated $m ($have != $want)"; Sh "rm -f $DEV/$m" | Out-Null }
    & $ADB push "$MOD\$m" "$DEV/$m" 2>&1 | Select-Object -Last 1 | Write-Output
  }
}
Sh "chmod +x $DEV/llama-cli $DEV/llama-bench $DEV/llama-bench-nokleidi $DEV/whisper-cli" | Out-Null

# ── 4. GATE 1 — first token.  -c $NCTX is the memory guardrail. ───────────────
Write-Output "`n============ GATE 1: first token ============"
Write-Output "  mem before: $(Mem)"
$g1 = Sh "cd $DEV && taskset $bigHex ./llama-cli -m Llama-3.2-1B-Instruct-Q4_0.gguf -c $NCTX -t $nBig -n 40 --no-cnv -no-cnv -p 'Translate to Spanish: Where is the bus station?' 2>&1 | tail -40"
$g1 | Write-Output
$g1 | Set-Content "$OUT\gate1_first_token.txt" -Encoding utf8
Write-Output "  mem after: $(Mem)"
Cool 5

# ── 5. O1 smoking gun — KleidiAI kernel selection ─────────────────────────────
Write-Output "`n============ O1: KleidiAI kernel selection ============"
Remove-Item "$OUT\o1_kleidiai_selection.txt" -ErrorAction SilentlyContinue
foreach ($q in @('Q4_0','Q4_K_M')) {
  Write-Output "--- $q ---"
  $o = Sh "cd $DEV && ./llama-cli -m Llama-3.2-1B-Instruct-$q.gguf -c 512 -n 1 -t $nBig --no-cnv -p hi 2>&1 | grep -i -e kleidi -e repack -e 'buffer type'"
  if ($o) { $o | Write-Output } else { Write-Output "  (no kleidi/repack line)" }
  Add-Content "$OUT\o1_kleidiai_selection.txt" "=== $q ===`n$o`n"
  Cool 5
}

# ── 6. GATE 2 — Whisper RTF. jfk.wav = 11.0 s, 16 kHz mono. ───────────────────
Write-Output "`n============ GATE 2: Whisper RTF (LITTLE cluster) ============"
Remove-Item "$OUT\gate2_whisper_rtf.txt" -ErrorAction SilentlyContinue
foreach ($m in @('ggml-tiny-q5_1.bin','ggml-base-q5_1.bin')) {
  Write-Output "--- $m (t=$nLit on 0x$litHex) ---"
  $r = Sh "cd $DEV && taskset $litHex ./whisper-cli -m $m -f jfk.wav -t $nLit -l en -np 2>&1 | grep -E 'total time|encode time|decode time'"
  $r | Write-Output
  Add-Content "$OUT\gate2_whisper_rtf.txt" "=== $m ===`n$r`n"
  Cool 5
}
Write-Output "RTF = total_time / 11.0 s.  Need < 0.7 to use 'base'."

# ── 7. GATE 3 — cluster concurrency. Only step with 2 models loaded. ──────────
Write-Output "`n============ GATE 3: concurrency ============"
Write-Output "  mem before: $(Mem)"
Write-Output "--- solo: LLM on big ---"
$solo = Sh "cd $DEV && taskset $bigHex ./llama-bench -m Llama-3.2-1B-Instruct-Q4_0.gguf -t $nBig -p 128 -n 32 -r 2 2>&1 | tail -6"
$solo | Write-Output
Cool 10
Write-Output "--- concurrent: LLM on big + Whisper(tiny) on LITTLE ---"
$conc = Sh "cd $DEV && (taskset $litHex ./whisper-cli -m ggml-tiny-q5_1.bin -f jfk.wav -t $nLit -l en -np >/dev/null 2>&1 &) ; sleep 1 ; taskset $bigHex ./llama-bench -m Llama-3.2-1B-Instruct-Q4_0.gguf -t $nBig -p 128 -n 32 -r 2 2>&1 | tail -6"
$conc | Write-Output
Set-Content "$OUT\gate3_concurrency.txt" "=== solo ===`n$solo`n`n=== concurrent ===`n$conc" -Encoding utf8
Write-Output "  mem after: $(Mem)"
Write-Output "GATE 3 passes if concurrent tok/s >= 80% of solo."

# ── 8. leave the phone clean ──────────────────────────────────────────────────
Sh "pkill -f llama- ; pkill -f whisper-" | Out-Null
Write-Output "`n=== done. results in $OUT ==="
Write-Output "Final memory: $(Mem)"
