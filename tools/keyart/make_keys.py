#!/usr/bin/env python3
"""Generates the wide channel-button sprites (SVG) for the three states: out (idle), dim (paused), lit (playing)."""
import json, os, sys

W, H = 560, 236            # sprite canvas (3x: 186.7 x 78.7 dp)
PAD_L, PAD_T = 16, 6
BW, BH = 528, 198          # button body
R, T = 34, 10              # corner radius, bezel thickness
X0, Y0 = PAD_L, PAD_T
FX, FY, FW, FH = X0 + T, Y0 + T, BW - 2 * T, BH - 2 * T

# regions (in canvas px, before the state's downward travel)
PLATE = dict(x=FX + 14, y=FY + 24, w=128, h=FH - 48)
LAMP_D = 62
LAMP = dict(cx=FX + FW - 14 - LAMP_D // 2, cy=FY + FH // 2, d=LAMP_D)
WIN_X = PLATE['x'] + PLATE['w'] + 14
WIN = dict(x=WIN_X, y=FY + 30, w=(LAMP['cx'] - LAMP_D // 2 - 14) - WIN_X, h=FH - 60)
TRAVEL = 10                # how far a latched button sits lower

DEFS = """
<defs>
  <linearGradient id="chrome" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#fbf6ea"/><stop offset=".14" stop-color="#d8cfbd"/>
    <stop offset=".40" stop-color="#8f8676"/><stop offset=".50" stop-color="#565044"/>
    <stop offset=".58" stop-color="#a69d8b"/><stop offset=".84" stop-color="#dad1bf"/>
    <stop offset="1" stop-color="#7b7364"/>
  </linearGradient>
  <linearGradient id="chromeH" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#ffffff" stop-opacity=".0"/><stop offset=".08" stop-color="#ffffff" stop-opacity=".35"/>
    <stop offset=".2" stop-color="#ffffff" stop-opacity="0"/><stop offset=".82" stop-color="#000000" stop-opacity="0"/>
    <stop offset="1" stop-color="#000000" stop-opacity=".28"/>
  </linearGradient>
  <linearGradient id="wall" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#6b6356"/><stop offset=".25" stop-color="#3a342b"/><stop offset="1" stop-color="#0d0b08"/>
  </linearGradient>
  <linearGradient id="faceOut" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#4a4136"/><stop offset=".10" stop-color="#2f2921"/>
    <stop offset=".55" stop-color="#181410"/><stop offset="1" stop-color="#0b0907"/>
  </linearGradient>
  <linearGradient id="faceIn" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#0a0806"/><stop offset=".4" stop-color="#15110d"/><stop offset="1" stop-color="#241d15"/>
  </linearGradient>
  <linearGradient id="topShade" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#000" stop-opacity=".75"/><stop offset="1" stop-color="#000" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="sheen" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#fff" stop-opacity=".10"/><stop offset=".45" stop-color="#fff" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="glassOff" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#2a1d0e"/><stop offset="1" stop-color="#0d0905"/>
  </linearGradient>
  <linearGradient id="glassDim" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#8a5c1f"/><stop offset="1" stop-color="#3f2810"/>
  </linearGradient>
  <radialGradient id="glassLit" cx=".5" cy=".38" r=".75">
    <stop offset="0" stop-color="#ffd995"/><stop offset=".45" stop-color="#e8ad52"/><stop offset="1" stop-color="#a86f1f"/>
  </radialGradient>
  <radialGradient id="lampOff" cx=".42" cy=".34" r=".8">
    <stop offset="0" stop-color="#4a3418"/><stop offset="1" stop-color="#150d05"/>
  </radialGradient>
  <radialGradient id="lampDim" cx=".45" cy=".35" r=".8">
    <stop offset="0" stop-color="#cf913a"/><stop offset=".6" stop-color="#86581a"/><stop offset="1" stop-color="#4b300c"/>
  </radialGradient>
  <radialGradient id="lampLit" cx=".5" cy=".4" r=".7">
    <stop offset="0" stop-color="#fff0c8"/><stop offset=".25" stop-color="#ffcb74"/><stop offset=".65" stop-color="#e0a64c"/><stop offset="1" stop-color="#9a6420"/>
  </radialGradient>
  <linearGradient id="winFloorLit" x1="0" y1="1" x2="0" y2="0">
    <stop offset="0" stop-color="#ffab3d" stop-opacity=".42"/><stop offset=".7" stop-color="#ffab3d" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="winFloorDim" x1="0" y1="1" x2="0" y2="0">
    <stop offset="0" stop-color="#e09632" stop-opacity=".16"/><stop offset=".7" stop-color="#e09632" stop-opacity="0"/>
  </linearGradient>
  <filter id="b4" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="4"/></filter>
  <filter id="b9" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="9"/></filter>
  <filter id="b18" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="18"/></filter>
  <filter id="brush" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.004 0.85" numOctaves="2" seed="11"/>
    <feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .9 -.3"/>
  </filter>
  <filter id="speck" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="4"/>
    <feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .8 -.32"/>
  </filter>
  <clipPath id="faceClip"><rect x="{FX}" y="{FY}" width="{FW}" height="{FH}" rx="{RF}"/></clipPath>
</defs>
""".replace("{FX}", str(FX)).replace("{FY}", str(FY)).replace("{FW}", str(FW)).replace("{FH}", str(FH)).replace("{RF}", str(R - T + 3))


def rr(x, y, w, h, r, **a):
    attrs = " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in a.items())
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" {attrs}/>'


def sprite(state):
    lit, dim = state == "lit", state == "dim"
    latched = lit or dim
    dy = TRAVEL if latched else 0
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', DEFS]

    # ---- the fixed shadow the key stands on (same in every state; a latched key covers it) ----
    o.append(rr(X0 + 4, Y0 + 20, BW - 8, BH, R, fill="#000", opacity=".55", filter="url(#b9)"))
    o.append(rr(X0, Y0 + 9, BW, BH, R, fill="#080604"))

    o.append(f'<g transform="translate(0 {dy})">')
    if not latched:                               # the key's side wall: it stands proud of the plate
        o.append(rr(X0, Y0 + 13, BW, BH, R, fill="url(#wall)", stroke="#0a0806", stroke_width="1.2"))
    # ---- chrome bezel ----
    o.append(rr(X0, Y0, BW, BH, R, fill="url(#chrome)", stroke="#15110c", stroke_width="1.5"))
    o.append(rr(X0, Y0, BW, BH, R, fill="url(#chromeH)"))
    o.append(f'<path d="M{X0 + R} {Y0 + 2.5} H{X0 + BW - R}" stroke="#fff" stroke-opacity=".7" stroke-width="2" stroke-linecap="round"/>')

    # ---- plastic face ----
    o.append(rr(FX, FY, FW, FH, R - T + 3, fill="url(#faceIn)" if latched else "url(#faceOut)"))
    o.append('<g clip-path="url(#faceClip)">')
    o.append(rr(FX, FY, FW, FH, 0, fill="#fff", filter="url(#brush)", opacity=".055"))
    o.append(rr(FX, FY, FW, FH, 0, fill="#fff", filter="url(#speck)", opacity=".05"))
    o.append(rr(FX, FY, FW, FH, 0, fill="url(#sheen)"))
    if latched:                                   # recessed: shadow falls from the top edge
        o.append(rr(FX, FY, FW, 38, 0, fill="url(#topShade)"))
    else:                                         # proud: bright top bevel, dark lower edge
        o.append(f'<path d="M{FX + 14} {FY + 2} H{FX + FW - 14}" stroke="#fff" stroke-opacity=".22" stroke-width="2.5" stroke-linecap="round"/>')
        o.append(f'<path d="M{FX + 14} {FY + FH - 2} H{FX + FW - 14}" stroke="#000" stroke-opacity=".6" stroke-width="3" stroke-linecap="round"/>')
    o.append('</g>')
    o.append(rr(FX + .75, FY + .75, FW - 1.5, FH - 1.5, R - T + 2.5, fill="none", stroke="#000", stroke_opacity=".7", stroke_width="1.5"))

    # ---- light leaking from the lit lamp / number window onto the face ----
    if lit:
        o.append('<g clip-path="url(#faceClip)">')
        o.append(f'<ellipse cx="{PLATE["x"] + PLATE["w"] / 2}" cy="{PLATE["y"] + PLATE["h"] / 2}" rx="120" ry="78" fill="#ff9d2e" opacity=".30" filter="url(#b18)"/>')
        o.append(f'<ellipse cx="{LAMP["cx"]}" cy="{LAMP["cy"]}" rx="78" ry="70" fill="#ffab3d" opacity=".42" filter="url(#b18)"/>')
        o.append('</g>')

    # ---- number window ----
    p = PLATE
    o.append(rr(p['x'] - 5, p['y'] - 5, p['w'] + 10, p['h'] + 10, 20, fill="url(#chrome)", stroke="#15110c", stroke_width="1.2"))
    glass = "url(#glassLit)" if lit else "url(#glassDim)" if dim else "url(#glassOff)"
    o.append(rr(p['x'], p['y'], p['w'], p['h'], 15, fill=glass))
    o.append(f'<clipPath id="pc"><rect x="{p["x"]}" y="{p["y"]}" width="{p["w"]}" height="{p["h"]}" rx="15"/></clipPath>')
    o.append('<g clip-path="url(#pc)">')
    o.append(rr(p['x'], p['y'], p['w'], 26, 0, fill="url(#topShade)", opacity=".8"))
    o.append(f'<path d="M{p["x"] + 6} {p["y"] + p["h"] * .5} q{p["w"] * .3} -{p["h"] * .55} {p["w"] * .9} -{p["h"] * .5} V{p["y"]} H{p["x"]} Z" fill="#fff" opacity="{.10 if not lit else .14}"/>')
    o.append('</g>')

    # ---- label window (text is drawn live in the app) ----
    w = WIN
    o.append(rr(w['x'] - 5, w['y'] - 5, w['w'] + 10, w['h'] + 10, 15, fill="url(#chrome)", stroke="#15110c", stroke_width="1.2"))
    o.append(rr(w['x'], w['y'], w['w'], w['h'], 10, fill="#070605"))
    o.append(f'<clipPath id="wc"><rect x="{w["x"]}" y="{w["y"]}" width="{w["w"]}" height="{w["h"]}" rx="10"/></clipPath>')
    o.append('<g clip-path="url(#wc)">')
    if lit:
        o.append(rr(w['x'], w['y'], w['w'], w['h'], 0, fill="url(#winFloorLit)"))
    elif dim:
        o.append(rr(w['x'], w['y'], w['w'], w['h'], 0, fill="url(#winFloorDim)"))
    o.append(rr(w['x'], w['y'], w['w'], 22, 0, fill="url(#topShade)", opacity=".9"))
    o.append(f'<path d="M{w["x"]} {w["y"] + w["h"] * .55} L{w["x"] + w["w"] * .5} {w["y"]} H{w["x"]} Z" fill="#fff" opacity=".055"/>')
    o.append('</g>')

    # ---- lamp ----
    L = LAMP
    if lit:
        o.append(f'<circle cx="{L["cx"]}" cy="{L["cy"]}" r="{L["d"] * .9}" fill="#ffb347" opacity=".65" filter="url(#b9)"/>')
    o.append(f'<circle cx="{L["cx"]}" cy="{L["cy"]}" r="{L["d"] / 2 + 7}" fill="url(#chrome)" stroke="#15110c" stroke-width="1.2"/>')
    o.append(f'<circle cx="{L["cx"]}" cy="{L["cy"]}" r="{L["d"] / 2 + 1.5}" fill="#0a0806"/>')
    lamp_fill = "url(#lampLit)" if lit else "url(#lampDim)" if dim else "url(#lampOff)"
    o.append(f'<circle cx="{L["cx"]}" cy="{L["cy"]}" r="{L["d"] / 2 - 2}" fill="{lamp_fill}"/>')
    o.append(f'<ellipse cx="{L["cx"] - 9}" cy="{L["cy"] - 12}" rx="11" ry="6" fill="#fff" opacity="{.55 if lit else .28 if dim else .22}" transform="rotate(-28 {L["cx"] - 9} {L["cy"] - 12})"/>')
    o.append('</g>')   # end travel group
    o.append('</svg>')
    return "\n".join(o)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for s in ("out", "dim", "lit"):
        open(os.path.join(out, f"key_{s}.svg"), "w").write(sprite(s))
    regions = dict(canvas=[W, H], plate=PLATE, lamp=LAMP, window=WIN, travel=TRAVEL,
                   body=dict(x=X0, y=Y0, w=BW, h=BH))
    json.dump(regions, open(os.path.join(out, "regions.json"), "w"), indent=1)
    print(json.dumps(regions))
