# otbench — the measurement harness. Every number the submission claims comes
# from here, and a judge can run it on their own Arm phone.
#
#   powershell -ExecutionPolicy Bypass -File D:\omnitalk\bench\otbench.ps1
#
# Memory policy (SPEC.md Part 2): one model at a time, short runs, cooldown
# between cells, nothing left running at the end.
#
# Sweeps
#   A  thread count x CPU affinity        -> O2  the big.LITTLE thread cliff
#   B  Q4_0 vs Q4_K_M, KleidiAI ON vs OFF -> O1  the KleidiAI cliff (null result)
$ErrorActionPreference = 'Continue'
$ADB  = 'D:\Android\Sdk\platform-tools\adb.exe'
$DEV  = '/data/local/tmp/ot'
$OUT  = 'D:\omnitalk\bench\results'
$CSV  = "$OUT\sweep_POCO_M2_Pro.csv"
$REPS = 3
$COOL = 6
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

function Sh($c) { & $ADB shell $c 2>&1 }
"sweep,quant,binary,threads,affinity,test,tps" | Set-Content $CSV -Encoding ascii

# NOTE ON PARSING: llama-bench prints "12.82 ± 0.02". The '±' is UTF-8 and does
# not survive the Windows console codepage intact, so a regex containing it
# silently matches nothing. Match only up to the number and ignore the rest.
function Cell($sweep, $quant, $bin, $t, $aff) {
  $pre = if ($aff -eq 'none') { '' } else { "taskset $aff " }
  $tbl = Sh "cd $DEV && $pre./$bin -m Llama-3.2-1B-Instruct-$quant.gguf -t $t -p 128 -n 32 -r $REPS 2>/dev/null | grep -E 'pp128|tg32'"
  $got = $false
  foreach ($row in $tbl) {
    if ("$row" -match '(pp128|tg32)\s*\|\s*([\d.]+)') {
      "$sweep,$quant,$bin,$t,$aff,$($Matches[1]),$($Matches[2])" | Add-Content $CSV -Encoding ascii
      "    {0,-6} {1,8} t/s" -f $Matches[1], $Matches[2] | Write-Output
      $got = $true
    }
  }
  if (-not $got) { Write-Output "    !! no rows parsed"; Write-Output "    raw: $tbl" }
  Start-Sleep -Seconds $COOL
}

Write-Output "=== SWEEP A: threads x affinity (Q4_0, KleidiAI build) ==="
foreach ($aff in @('none','c0','3f')) {
  foreach ($t in @(2,4,6,8)) {
    if ($aff -eq 'c0' -and $t -gt 2) { continue }   # only 2 big cores exist
    if ($aff -eq '3f' -and $t -gt 6) { continue }   # only 6 LITTLE cores exist
    Write-Output "  A: t=$t aff=$aff"
    Cell 'A' 'Q4_0' 'llama-bench' $t $aff
  }
}

Write-Output "`n=== SWEEP B: quant x KleidiAI (t=6, unpinned) ==="
foreach ($q in @('Q4_0','Q4_K_M')) {
  foreach ($b in @('llama-bench','llama-bench-nokleidi')) {
    Write-Output "  B: $q $b"
    Cell 'B' $q $b 6 'none'
  }
}


Sh "pkill -f llama-" | Out-Null
Write-Output "`n=== done -> $CSV ==="
Get-Content $CSV
