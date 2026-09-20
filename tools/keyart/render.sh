#!/usr/bin/env sh
# Regenerates the channel-button sprites used by ChannelKey.kt:
#   make_keys.py (parametric SVG) -> headless Chrome (transparent PNG) -> lossless WebP in res/drawable-nodpi.
# If you change the geometry in make_keys.py, update ChannelKeyArt in core/design/ChannelKey.kt to match
# regions.json (a unit test checks the regions are consistent).
set -e
cd "$(dirname "$0")"
python3 make_keys.py .
for s in out dim lit; do
  google-chrome-stable --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=1 --default-background-color=00000000 --window-size=560,236 \
    --screenshot="key_$s.png" "file://$PWD/key_$s.svg"
  python3 -c "from PIL import Image; Image.open('key_$s.png').convert('RGBA').save('../../app/src/main/res/drawable-nodpi/channel_key_$s.webp','WEBP',lossless=True,quality=100,method=6)"
done

# ---- round pushbuttons (PLAY, previous, next) ----
python3 make_round.py .
for s in out in lit; do
  google-chrome-stable --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=1 --default-background-color=00000000 --window-size=240,240 \
    --screenshot="round_$s.png" "file://$PWD/round_$s.svg"
  python3 -c "from PIL import Image; Image.open('round_$s.png').convert('RGBA').save('../../app/src/main/res/drawable-nodpi/round_key_$s.webp','WEBP',lossless=True,quality=100,method=6)"
done
