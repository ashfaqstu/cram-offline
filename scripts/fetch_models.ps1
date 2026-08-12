# Downloads model weights to D:\omnitalk\models. Weights are NEVER committed.
# Q8_0 is deliberately omitted: 1.32 GB for a secondary O1 comparison arm.
# Add it later only if the phone has >= 6 GB free.
$ErrorActionPreference = 'Continue'
$dir = 'D:\omnitalk\models'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$LB = 'https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main'
$WB = 'https://huggingface.co/ggerganov/whisper.cpp/resolve/main'

$files = [ordered]@{
  'Llama-3.2-1B-Instruct-Q4_0.gguf'   = "$LB/Llama-3.2-1B-Instruct-Q4_0.gguf"
  'Llama-3.2-1B-Instruct-Q4_K_M.gguf' = "$LB/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
  'ggml-base-q5_1.bin'                = "$WB/ggml-base-q5_1.bin"
  'ggml-tiny-q5_1.bin'                = "$WB/ggml-tiny-q5_1.bin"
}

foreach ($name in $files.Keys) {
  $out = Join-Path $dir $name
  if (Test-Path $out) { Write-Output "SKIP (exists) $name"; continue }
  Write-Output "=== downloading $name ==="
  & curl.exe -L --fail --retry 3 --retry-delay 5 -o $out $files[$name]
  if ($LASTEXITCODE -ne 0) { Write-Output "!! FAILED $name (exit $LASTEXITCODE)"; continue }
  Write-Output ("    {0:N1} MB" -f ((Get-Item $out).Length/1MB))
}

Write-Output '=== SHA256 (record these in scripts/fetch_models.sh) ==='
Get-ChildItem $dir -File | ForEach-Object {
  "{0}  {1}" -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name
}
$free = (Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='D:'").FreeSpace/1GB
Write-Output ("=== DONE. D: free = {0:N1} GB ===" -f $free)
