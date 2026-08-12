# Live monitor. Pulls each turn's telemetry off the phone as it happens and
# prints a verdict, so a test on the device becomes data here instead of a
# description.
#
#   powershell -ExecutionPolicy Bypass -File D:\omnitalk\scripts\watch_turns.ps1
#
# Per turn the app writes turn_NNN.wav (exact audio Whisper saw) and
# turn_NNN.json (trace, transcript, raw output, timings, slots).
$ErrorActionPreference = 'Continue'
$ADB  = 'D:\Android\Sdk\platform-tools\adb.exe'
$DEV  = '/sdcard/Android/data/dev.omnitalk/files'
$OUT  = 'D:\omnitalk\bench\results\turns'
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

$seen = @{}
Get-ChildItem $OUT -Filter 'turn_*.json' -ErrorAction SilentlyContinue | ForEach-Object { $seen[$_.Name] = $true }

Write-Output "watching $DEV  (Ctrl-C to stop)"
Write-Output ("-" * 78)

while ($true) {
    $names = & $ADB shell "ls $DEV 2>/dev/null | grep -E '^turn_[0-9]+\.json$'" 2>$null
    foreach ($n in $names) {
        $n = "$n".Trim()
        if (-not $n -or $seen[$n]) { continue }
        $seen[$n] = $true
        $base = $n -replace '\.json$',''

        & $ADB pull "$DEV/$n"       "$OUT\$n"        2>&1 | Out-Null
        & $ADB pull "$DEV/$base.wav" "$OUT\$base.wav" 2>&1 | Out-Null

        $j = Get-Content "$OUT\$n" -Raw | ConvertFrom-Json
        $fa = $null
        if ($j.trace -and $j.trace.first_audio_ms) { $fa = [double]$j.trace.first_audio_ms }

        Write-Output ""
        Write-Output ("=== {0}  [{1}]  lang={2} ===" -f $base, $j.mode, $j.lang)
        Write-Output ("  audio     {0:N1} s   peak {1:N3}  {2}" -f `
            $j.audio_seconds, $j.audio_peak,
            $(if ($j.audio_peak -lt 0.01) { '<<< SILENT - mic captured nothing' }
              elseif ($j.audio_peak -lt 0.05) { '<< very quiet' } else { 'ok' }))
        Write-Output ("  heard     '{0}'" -f $j.transcript)
        Write-Output ("  asks      '{0}'" -f $j.question)
        Write-Output ("  gloss     '{0}'" -f $j.gloss)
        if ($j.slots) {
            $filled = @()
            foreach ($p in $j.slots.PSObject.Properties) { if ($p.Value) { $filled += "$($p.Name)=$($p.Value)" } }
            Write-Output ("  slots     {0}" -f $(if ($filled.Count) { $filled -join ' | ' } else { '(none filled)' }))
        }
        if ($j.timings) {
            Write-Output ("  perf      prefill {0} tok @ {1} tok/s   decode {2} tok @ {3} tok/s" -f `
                $j.timings.prefill_tok, $j.timings.prefill_tps, $j.timings.decode_tok, $j.timings.decode_tps)
        }
        if ($fa) { Write-Output ("  FIRST AUDIO  {0:N2} s" -f ($fa / 1000.0)) }

        # verdicts — the point of this script
        $bad = @()
        if ($j.audio_peak -lt 0.01)                { $bad += 'mic silent' }
        if (-not $j.transcript)                    { $bad += 'ASR produced nothing' }
        if (-not $j.question)                      { $bad += 'no question generated' }
        if ($j.raw_output -and -not ($j.raw_output -match '^\s*\{')) { $bad += 'output is not JSON' }
        if ($bad.Count) { Write-Output ("  >>> PROBLEM: {0}" -f ($bad -join ', ')) }
        else            { Write-Output  "  >>> OK" }
    }
    Start-Sleep -Seconds 3
}
