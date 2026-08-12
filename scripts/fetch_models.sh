#!/usr/bin/env bash
# Downloads and verifies the model weights OmniTalk Edge needs.
#
# Weights are NOT committed to this repository. Llama 3.2 is distributed under
# the Llama 3.2 Community License, not Apache-2.0 — see NOTICE.
#
# Usage:  ./scripts/fetch_models.sh [--with-q8]
#
# Q8_0 (1.32 GB) is optional. It is only a third comparison arm for O1; the
# finding itself is Q4_0 vs Q4_K_M. Skip it unless your device has >= 6 GB free.

set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p models
cd models

LB="https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main"
WB="https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

fetch() {  # fetch <url> <filename> <sha256>
  local url="$1" name="$2" want="$3"
  if [ -f "$name" ]; then
    echo "  exists: $name"
  else
    echo "==> $name"
    curl -L --fail --retry 3 --retry-delay 5 -o "$name" "$url"
  fi
  local got
  got="$(sha256sum "$name" | cut -d' ' -f1)"
  if [ "$got" != "$want" ]; then
    echo "!! SHA256 MISMATCH for $name" >&2
    echo "   expected $want" >&2
    echo "   got      $got"  >&2
    exit 1
  fi
  echo "    sha256 ok"
}

fetch "$LB/Llama-3.2-1B-Instruct-Q4_0.gguf"   Llama-3.2-1B-Instruct-Q4_0.gguf \
      fa0390e7c043f89ae1847bd6682d748041a99d4ef3de0e0b27d33b6af97a8be8
fetch "$LB/Llama-3.2-1B-Instruct-Q4_K_M.gguf" Llama-3.2-1B-Instruct-Q4_K_M.gguf \
      6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83
fetch "$WB/ggml-base-q5_1.bin" ggml-base-q5_1.bin \
      422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898
fetch "$WB/ggml-tiny-q5_1.bin" ggml-tiny-q5_1.bin \
      818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7

if [ "${1:-}" = "--with-q8" ]; then
  echo "==> Q8_0 (1.32 GB, optional)"
  curl -L --fail -o Llama-3.2-1B-Instruct-Q8_0.gguf "$LB/Llama-3.2-1B-Instruct-Q8_0.gguf"
fi

echo
echo "Models ready in $(pwd)"
du -sh . 2>/dev/null || true
