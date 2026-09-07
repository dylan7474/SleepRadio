#!/usr/bin/env bash
#
# Build the "stock" Broadcast-mode voice pack that VoicePackInstaller.STOCK_URL
# points at. No voice/eSpeak data is committed to this repo or shipped in the
# APK (see NOTICE) — this bundle is published as a GitHub Release asset and
# downloaded (or side-loaded) on first use.
#
# It takes the sherpa-onnx pre-packaged Piper voice and trims espeak-ng-data
# down to English only (19 MB -> ~1.8 MB): the core phoneme tables + en_dict +
# the (tiny) lang/ and voices/ trees, dropping the ~112 other *_dict files.
#
# Output:  tools/build/sleepradio-voice-en_GB-v1.zip  (+ its SHA-256)
# Publish: gh release create voice-stock-v1 tools/build/sleepradio-voice-en_GB-v1.zip
# Then paste the printed SHA-256 into VoicePackInstaller.STOCK_SHA256.
#
set -euo pipefail

VOICE="vits-piper-en_GB-southern_english_female-low"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${VOICE}.tar.bz2"
OUT_ZIP="sleepradio-voice-en_GB-v1.zip"

here="$(cd "$(dirname "$0")" && pwd)"
build="$here/build"
rm -rf "$build" && mkdir -p "$build"
cd "$build"

echo "==> Fetching $VOICE"
curl -fsSL -o voice.tar.bz2 "$URL"
tar xjf voice.tar.bz2

echo "==> Trimming espeak-ng-data to English only"
src="$VOICE/espeak-ng-data"
mkdir pkg
cp "$VOICE/${VOICE#vits-piper-}.onnx"      pkg/
cp "$VOICE/${VOICE#vits-piper-}.onnx.json" pkg/
cp "$VOICE/tokens.txt"                     pkg/
mkdir pkg/espeak-ng-data
cp "$src"/phondata "$src"/phondata-manifest "$src"/phonindex \
   "$src"/phontab "$src"/intonations "$src"/en_dict          pkg/espeak-ng-data/
cp -r "$src"/lang   pkg/espeak-ng-data/lang
cp -r "$src"/voices pkg/espeak-ng-data/voices

echo "==> Packaging $OUT_ZIP (deterministic)"
# Fixed mtimes + sorted entry order + no extra fields => reproducible SHA-256.
find pkg -exec touch -t 202601010000.00 {} +
rm -f "$build/$OUT_ZIP"
( cd pkg && find . -type f | LC_ALL=C sort | zip -qX "$build/$OUT_ZIP" -@ )

full=$(du -sh "$src" | cut -f1)
trim=$(du -sh pkg/espeak-ng-data | cut -f1)
echo
echo "espeak-ng-data: $full -> $trim"
echo "zip:  $build/$OUT_ZIP  ($(du -h "$build/$OUT_ZIP" | cut -f1))"
echo "sha256: $(sha256sum "$build/$OUT_ZIP" | cut -d' ' -f1)"
