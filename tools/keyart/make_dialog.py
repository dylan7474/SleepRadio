#!/usr/bin/env python3
"""9-slice sprites for the radio-style dialogs: the faceplate frame and the push-button (out / in).
Insets (art px) the app stretches between: frame 64 on every side; button L/R 40, top 34, bottom 44."""
import json, os, sys

DEFS = """
<defs>
  <linearGradient id="chrome" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#fbf6ea"/><stop offset=".14" stop-color="#d8cfbd"/>
    <stop offset=".40" stop-color="#8f8676"/><stop offset=".50" stop-color="#565044"/>
    <stop offset=".58" stop-color="#a69d8b"/><stop offset=".84" stop-color="#dad1bf"/>
    <stop offset="1" stop-color="#7b7364"/>
  </linearGradient>
  <linearGradient id="wall" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#6b6356"/><stop offset=".25" stop-color="#3a342b"/><stop offset="1" stop-color="#0d0b08"/>
  </linearGradient>
  <linearGradient id="ts" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#000" stop-opacity=".6"/><stop offset="1" stop-color="#000" stop-opacity="0"/>
  </linearGradient>
  <filter id="b10" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="10"/></filter>
  <filter id="b5" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="5"/></filter>
  <filter id="brush" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.004 0.85" numOctaves="2" seed="11"/>
    <feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .9 -.3"/>
  </filter>
  <filter id="speck" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="4"/>
    <feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .8 -.32"/>
  </filter>
</defs>
"""

def rr(x, y, w, h, r, **a):
    attrs = " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in a.items())
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" {attrs}/>'

def frame():
    W = H = 192
    L, T, B = 20, 14, 28              # margins: left/right, top, bottom (room for the shadow)
    bw, bh, R, t = W - 2 * L, H - T - B, 34, 8
    fx, fy, fw, fh = L + t, T + t, bw - 2 * t, bh - 2 * t
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', DEFS,
         # a face gradient that is flat through the stretchable middle rows (so stretching does not distort it)
         f'<linearGradient id="face" gradientUnits="userSpaceOnUse" x1="0" y1="{fy}" x2="0" y2="{fy + fh}">'
         f'<stop offset="0" stop-color="#3c342a"/><stop offset=".22" stop-color="#241e18"/>'
         f'<stop offset=".75" stop-color="#1c1712"/><stop offset="1" stop-color="#100d0a"/></linearGradient>',
         f'<clipPath id="fc"><rect x="{fx}" y="{fy}" width="{fw}" height="{fh}" rx="{R - t + 2}"/></clipPath>']
    o.append(rr(L + 4, T + 16, bw - 8, bh, R, fill="#000", opacity=".6", filter="url(#b10)"))
    o.append(rr(L, T + 6, bw, bh, R, fill="#080604"))
    o.append(rr(L, T, bw, bh, R, fill="url(#chrome)", stroke="#15110c", stroke_width="1.5"))
    o.append(f'<path d="M{L + R} {T + 2.5} H{L + bw - R}" stroke="#fff" stroke-opacity=".7" stroke-width="2" stroke-linecap="round"/>')
    o.append(rr(fx, fy, fw, fh, R - t + 2, fill="url(#face)"))
    o.append(f'<g clip-path="url(#fc)">'
             f'<rect x="{fx}" y="{fy}" width="{fw}" height="{fh}" fill="#fff" filter="url(#brush)" opacity=".05"/>'
             f'<rect x="{fx}" y="{fy}" width="{fw}" height="{fh}" fill="#fff" filter="url(#speck)" opacity=".05"/>'
             f'<path d="M{fx + 12} {fy + 2} H{fx + fw - 12}" stroke="#fff" stroke-opacity=".16" stroke-width="2" stroke-linecap="round"/></g>')
    o.append(rr(fx + .75, fy + .75, fw - 1.5, fh - 1.5, R - t + 1.5, fill="none", stroke="#000", stroke_opacity=".7", stroke_width="1.5"))
    # a fine engraved panel line inside the face
    o.append(rr(fx + 9, fy + 9, fw - 18, fh - 18, R - t - 6, fill="none", stroke="#000", stroke_opacity=".55", stroke_width="1.2"))
    o.append(rr(fx + 10, fy + 10, fw - 18, fh - 18, R - t - 6, fill="none", stroke="#fff", stroke_opacity=".06", stroke_width="1"))
    o.append('</svg>')
    return "\n".join(o)

def button(pressed):
    W, H = 128, 96
    L, T = 8, 6
    bw, bh, R, t = W - 2 * L, 76, 24, 7
    dy = 5 if pressed else 0
    fx, fy, fw, fh = L + t, T + t + dy, bw - 2 * t, bh - 2 * t
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', DEFS,
         f'<linearGradient id="face" x1="0" y1="0" x2="0" y2="1">' +
         ('<stop offset="0" stop-color="#0b0907"/><stop offset=".4" stop-color="#15110d"/><stop offset="1" stop-color="#211a13"/>' if pressed else
          '<stop offset="0" stop-color="#4a4136"/><stop offset=".12" stop-color="#2f2921"/><stop offset=".6" stop-color="#181410"/><stop offset="1" stop-color="#0b0907"/>') +
         '</linearGradient>',
         f'<clipPath id="fc"><rect x="{fx}" y="{fy}" width="{fw}" height="{fh}" rx="{R - t + 2}"/></clipPath>']
    o.append(rr(L + 3, T + 12, bw - 6, bh, R, fill="#000", opacity=".55", filter="url(#b5)"))
    o.append(rr(L, T + 6, bw, bh, R, fill="#080604"))
    if not pressed:
        o.append(rr(L, T + 10, bw, bh, R, fill="url(#wall)", stroke="#0a0806", stroke_width="1.2"))
    o.append(rr(L, T + dy, bw, bh, R, fill="url(#chrome)", stroke="#15110c", stroke_width="1.5"))
    o.append(f'<path d="M{L + R} {T + dy + 2.5} H{L + bw - R}" stroke="#fff" stroke-opacity=".7" stroke-width="2" stroke-linecap="round"/>')
    o.append(rr(fx, fy, fw, fh, R - t + 2, fill="url(#face)"))
    o.append(f'<g clip-path="url(#fc)">'
             f'<rect x="{fx}" y="{fy}" width="{fw}" height="{fh}" fill="#fff" filter="url(#brush)" opacity=".05"/>'
             f'<rect x="{fx}" y="{fy}" width="{fw}" height="{fh}" fill="#fff" filter="url(#speck)" opacity=".05"/>')
    if pressed:
        o.append(f'<rect x="{fx}" y="{fy}" width="{fw}" height="24" fill="url(#ts)"/>')
    else:
        o.append(f'<path d="M{fx + 10} {fy + 2} H{fx + fw - 10}" stroke="#fff" stroke-opacity=".2" stroke-width="2" stroke-linecap="round"/>')
    o.append('</g>')
    o.append(rr(fx + .75, fy + .75, fw - 1.5, fh - 1.5, R - t + 1.5, fill="none", stroke="#000", stroke_opacity=".7", stroke_width="1.5"))
    o.append('</svg>')
    return "\n".join(o)

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    open(os.path.join(out, "dialog_frame.svg"), "w").write(frame())
    open(os.path.join(out, "dialog_button_out.svg"), "w").write(button(False))
    open(os.path.join(out, "dialog_button_in.svg"), "w").write(button(True))
    json.dump(dict(frame=dict(size=[192, 192], inset=64), button=dict(size=[128, 96], insetX=40, insetTop=34, insetBottom=44)),
              open(os.path.join(out, "dialog_regions.json"), "w"), indent=1)
