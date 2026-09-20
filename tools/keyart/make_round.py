#!/usr/bin/env python3
"""Round radio pushbutton sprites (SVG): out (proud), in (pressed), lit (pressed with an amber ring, for PLAY while playing)."""
import json, os, sys

C = 240                       # canvas is C x C
CX, CY = 120, 112             # body centre (leaves room below for the shadow)
R_BEZEL, R_FACE, R_DISH = 100, 89, 68
TRAVEL = 8

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
  <radialGradient id="faceOut" cx=".4" cy=".3" r=".95">
    <stop offset="0" stop-color="#4c4237"/><stop offset=".5" stop-color="#1e1913"/><stop offset="1" stop-color="#0a0806"/>
  </radialGradient>
  <radialGradient id="faceIn" cx=".5" cy=".62" r=".95">
    <stop offset="0" stop-color="#241d15"/><stop offset=".6" stop-color="#14100c"/><stop offset="1" stop-color="#090705"/>
  </radialGradient>
  <linearGradient id="dishOut" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#0b0907"/><stop offset="1" stop-color="#332b22"/>
  </linearGradient>
  <linearGradient id="dishIn" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#080604"/><stop offset="1" stop-color="#1d1812"/>
  </linearGradient>
  <radialGradient id="bloom" cx=".5" cy=".5" r=".5">
    <stop offset="0" stop-color="#ffb347" stop-opacity=".30"/><stop offset="1" stop-color="#ffb347" stop-opacity="0"/>
  </radialGradient>
  <filter id="b9" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="9"/></filter>
  <filter id="b5" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="5"/></filter>
  <filter id="b16" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="16"/></filter>
  <filter id="brush" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.012 0.8" numOctaves="2" seed="11"/>
    <feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .9 -.3"/>
  </filter>
  <filter id="speck" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="4"/>
    <feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .8 -.32"/>
  </filter>
</defs>
"""

def arc(cx, cy, r, a0, a1):
    import math
    x0, y0 = cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0))
    x1, y1 = cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1))
    return f"M{x0:.1f} {y0:.1f} A{r} {r} 0 0 1 {x1:.1f} {y1:.1f}"

def sprite(state):
    lit, pressed = state == "lit", state in ("in", "lit")
    dy = TRAVEL if pressed else 0
    cy = CY + dy
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{C}" height="{C}" viewBox="0 0 {C} {C}">', DEFS]
    # fixed shadow the button stands on
    o.append(f'<circle cx="{CX}" cy="{CY + 20}" r="{R_BEZEL - 4}" fill="#000" opacity=".55" filter="url(#b9)"/>')
    o.append(f'<circle cx="{CX}" cy="{CY + 9}" r="{R_BEZEL}" fill="#080604"/>')
    if lit:                                                     # light leaking out past the bezel
        o.append(f'<circle cx="{CX}" cy="{cy}" r="{R_BEZEL - 4}" fill="#ffab3d" opacity=".34" filter="url(#b9)"/>')
    if not pressed:                                             # the button's thick side wall
        o.append(f'<circle cx="{CX}" cy="{CY + 13}" r="{R_BEZEL}" fill="url(#wall)" stroke="#0a0806" stroke-width="1.2"/>')
    # chrome bezel
    o.append(f'<circle cx="{CX}" cy="{cy}" r="{R_BEZEL}" fill="url(#chrome)" stroke="#15110c" stroke-width="1.5"/>')
    o.append(f'<path d="{arc(CX, cy, R_BEZEL - 3, 205, 335)}" stroke="#fff" stroke-opacity=".75" stroke-width="2.5" fill="none" stroke-linecap="round"/>')
    # plastic face
    o.append(f'<clipPath id="fc"><circle cx="{CX}" cy="{cy}" r="{R_FACE}"/></clipPath>')
    o.append(f'<circle cx="{CX}" cy="{cy}" r="{R_FACE}" fill="url({"#faceIn" if pressed else "#faceOut"})" stroke="#000" stroke-opacity=".7" stroke-width="1.5"/>')
    o.append(f'<g clip-path="url(#fc)"><rect x="0" y="0" width="{C}" height="{C}" fill="#fff" filter="url(#brush)" opacity=".05"/>'
             f'<rect x="0" y="0" width="{C}" height="{C}" fill="#fff" filter="url(#speck)" opacity=".05"/>')
    if pressed:
        o.append(f'<rect x="{CX - R_FACE}" y="{cy - R_FACE}" width="{2 * R_FACE}" height="40" fill="#000" opacity=".35"/>')
    o.append('</g>')
    # concave dish
    o.append(f'<circle cx="{CX}" cy="{cy}" r="{R_DISH}" fill="url({"#dishIn" if pressed else "#dishOut"})"/>')
    o.append(f'<circle cx="{CX}" cy="{cy}" r="{R_DISH}" fill="none" stroke="#000" stroke-opacity=".65" stroke-width="2"/>')
    o.append(f'<path d="{arc(CX, cy, R_DISH - 1.5, 20, 100)}" stroke="#fff" stroke-opacity="{.16 if not pressed else .09}" stroke-width="2" fill="none" stroke-linecap="round"/>')
    if lit:                                                     # amber ring inside the bezel + glow on the dish
        o.append(f'<circle cx="{CX}" cy="{cy}" r="{R_FACE - 7}" fill="none" stroke="#ffcb74" stroke-width="9" opacity=".8" filter="url(#b5)"/>')
        o.append(f'<circle cx="{CX}" cy="{cy}" r="{R_FACE - 7}" fill="none" stroke="#ffd995" stroke-width="3.2"/>')
        o.append(f'<circle cx="{CX}" cy="{cy}" r="{R_DISH}" fill="url(#bloom)"/>')
    o.append('</svg>')
    return "\n".join(o)

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for s in ("out", "in", "lit"):
        open(os.path.join(out, f"round_{s}.svg"), "w").write(sprite(s))
    json.dump(dict(canvas=[C, C], centre=[CX, CY], bezel=R_BEZEL, face=R_FACE, dish=R_DISH, travel=TRAVEL),
              open(os.path.join(out, "round_regions.json"), "w"), indent=1)
