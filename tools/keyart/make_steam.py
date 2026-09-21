#!/usr/bin/env python3
"""Brass-and-copper (steam-age) sprites for the top instrument panel, as SVG. Art is drawn at 3x: 3 art px = 1 dp.

9-slice sprites (stretch between the insets; `outset` is the transparent shadow margin the app draws beyond the
composable's bounds so the visible body lines up with them): panel, plate, window, tag, rail.
Fixed-size round sprites: porthole (a ring with a transparent hole for the cover art), gauge (dial face + ticks),
wheel (menu hand-wheel).  All numbers the app needs are written to steam_regions.json.
"""
import json, math, os, sys

FILT = """
  <filter id="b3" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="3"/></filter>
  <filter id="b6" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="6"/></filter>
  <filter id="b9" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="9"/></filter>
  <filter id="brush" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.004 0.85" numOctaves="2" seed="11"/>
    <feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .9 -.3"/>
  </filter>
  <filter id="speck" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="4"/>
    <feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .8 -.32"/>
  </filter>
  <radialGradient id="rivetG" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#fff2c0"/><stop offset=".45" stop-color="#c8973e"/><stop offset="1" stop-color="#5a3c0c"/></radialGradient>
  <radialGradient id="rivetC" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#ffe0c8"/><stop offset=".45" stop-color="#c4784a"/><stop offset="1" stop-color="#5a2c14"/></radialGradient>
"""

BRASS_STOPS = [(0, "#fbe7a6"), (.18, "#d9ad55"), (.5, "#a5741f"), (.56, "#7a5216"), (.82, "#cfa04a"), (1, "#6d4812")]
COPPER_STOPS = [(0, "#f6c3a0"), (.22, "#cf8558"), (.5, "#9a5530"), (.62, "#b9683c"), (1, "#6a341a")]


def lin(gid, stops, y0, y1, x0=0, x1=0):
    s = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}">{s}</linearGradient>'


def rr(x, y, w, h, r, **a):
    at = " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in a.items())
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" {at}/>'


def circ(cx, cy, r, **a):
    at = " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in a.items())
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" {at}/>'


def rivet(cx, cy, r, copper=False):
    return (circ(cx, cy, r, fill="url(#rivetC)" if copper else "url(#rivetG)", stroke="#3a2608", stroke_width=round(r * .2, 2)) +
            circ(cx - r * .32, cy - r * .36, r * .3, fill="#fff", opacity=".55"))


def annulus(cx, cy, ro, ri):
    return (f'M{cx - ro} {cy} a{ro} {ro} 0 1 0 {2 * ro} 0 a{ro} {ro} 0 1 0 {-2 * ro} 0 Z '
            f'M{cx - ri} {cy} a{ri} {ri} 0 1 0 {2 * ri} 0 a{ri} {ri} 0 1 0 {-2 * ri} 0 Z')


def svg(w, h, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><defs>{FILT}</defs>{body}</svg>'


def grain(clip_id, x, y, w, h, r, op=.05):
    return (f'<clipPath id="{clip_id}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}"/></clipPath>'
            f'<g clip-path="url(#{clip_id})"><rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#fff" filter="url(#brush)" opacity="{op}"/>'
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#fff" filter="url(#speck)" opacity="{op}"/></g>')


REG = {}


# ------------------------------------------------------------------------------------------------ panel
def panel():
    W = H = 192
    bx, by, bw, bh, R = 12, 6, 168, 156, 60
    o = [f'<defs>{lin("pf", [(0, "#211a12"), (.6, "#17120d"), (1, "#100c08")], by, by + bh)}</defs>',
         rr(bx + 4, by + 18, bw - 8, bh, R, fill="#000", opacity=".6", filter="url(#b9)"),
         rr(bx, by, bw, bh, R, fill="url(#pf)"),
         grain("pg", bx, by, bw, bh, R, .04),
         rr(bx + 1.5, by + 1.5, bw - 3, bh - 3, R - 1.5, fill="none", stroke="#4a3712", stroke_width=3),
         rr(bx + 6, by + 6, bw - 12, bh - 12, R - 6, fill="none", stroke="#17120d", stroke_width=6),
         rr(bx + 10.5, by + 10.5, bw - 21, bh - 21, R - 10.5, fill="none", stroke="#7a5518", stroke_width=3),
         rivet(bx + 40, by + 40, 15), rivet(bx + bw - 40, by + 40, 15)]
    REG["panel"] = dict(canvas=[W, H], inset=[bx + 64, by + 62, bx + 64, (H - by - bh) + 62], outset=[bx, by, bx, H - by - bh])
    return svg(W, H, "".join(o))


# ------------------------------------------------------------------------------------------------ nameplate
def plate():
    W, H = 240, 183
    bx, by, bw, bh, R = 9, 3, 222, 162, 27
    o = [f'<defs>{lin("bf", BRASS_STOPS, by, by + bh)}</defs>',
         rr(bx + 3, by + 9, bw - 6, bh, R, fill="#000", opacity=".6", filter="url(#b6)"),
         rr(bx - 1.5, by - 1.5, bw + 3, bh + 3, R + 1.5, fill="#2f1f07"),
         rr(bx, by, bw, bh, R, fill="url(#bf)"),
         grain("plg", bx, by, bw, bh, R, .22),
         f'<path d="M{bx + R} {by + 3.5} H{bx + bw - R}" stroke="#fff" stroke-opacity=".6" stroke-width="3" stroke-linecap="round"/>',
         f'<path d="M{bx + R} {by + bh - 3} H{bx + bw - R}" stroke="#000" stroke-opacity=".35" stroke-width="4" stroke-linecap="round"/>']
    for px, py in ((bx + 20, by + 20), (bx + bw - 20, by + 20), (bx + 20, by + bh - 20), (bx + bw - 20, by + bh - 20)):
        o.append(rivet(px, py, 13))
    REG["plate"] = dict(canvas=[W, H], inset=[bx + 45, by + 45, (W - bx - bw) + 45, (H - by - bh) + 45], outset=[bx, by, W - bx - bw, H - by - bh])
    return svg(W, H, "".join(o))


# ------------------------------------------------------------------------------------------------ readout window
def window():
    W, H = 180, 138
    bx, by, bw, bh, R, t = 6, 3, 168, 120, 27, 9
    gx, gy, gw, gh, gr = bx + t, by + t, bw - 2 * t, bh - 2 * t, R - t
    o = [f'<defs>{lin("wf", [(0, "#f2d585"), (.4, "#b98a35"), (.58, "#6b4712"), (1, "#c8973e")], by, by + bh)}'
         f'<radialGradient id="gl" cx=".5" cy="0" r=".9"><stop offset="0" stop-color="#241a0d"/><stop offset=".75" stop-color="#0a0806"/></radialGradient>'
         f'<linearGradient id="gs" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity=".9"/><stop offset="1" stop-color="#000" stop-opacity="0"/></linearGradient>'
         f'<clipPath id="gc"><rect x="{gx}" y="{gy}" width="{gw}" height="{gh}" rx="{gr}"/></clipPath></defs>',
         rr(bx + 2, by + 7, bw - 4, bh, R, fill="#000", opacity=".6", filter="url(#b6)"),
         rr(bx - 1.5, by - 1.5, bw + 3, bh + 3, R + 1.5, fill="#2f1f07"),
         rr(bx, by, bw, bh, R, fill="url(#wf)"),
         f'<path d="M{bx + R} {by + 3.5} H{bx + bw - R}" stroke="#fff" stroke-opacity=".5" stroke-width="3" stroke-linecap="round"/>',
         rr(gx, gy, gw, gh, gr, fill="url(#gl)"),
         '<g clip-path="url(#gc)">',
         f'<rect x="{gx}" y="{gy}" width="{gw}" height="27" fill="url(#gs)"/>',
         f'<path d="M{gx} {gy + gh * .55} L{gx + gw * .4} {gy} H{gx} Z" fill="#fff" opacity=".11"/>']
    for yy in range(int(gy) + 4, int(gy + gh), 9):
        o.append(f'<rect x="{gx}" y="{yy}" width="{gw}" height="3" fill="#000" opacity=".16"/>')
    o.append('</g>')
    o.append(rr(gx + .75, gy + .75, gw - 1.5, gh - 1.5, gr - .75, fill="none", stroke="#000", stroke_opacity=".7", stroke_width="1.5"))
    o.append(f'<path d="M{gx + gr} {gy + gh + 1.5} H{gx + gw - gr}" stroke="#ffdc96" stroke-opacity=".22" stroke-width="2"/>')
    REG["window"] = dict(canvas=[W, H], inset=[bx + 30, by + 30, (W - bx - bw) + 30, (H - by - bh) + 30], outset=[bx, by, W - bx - bw, H - by - bh],
                         glass=t)
    return svg(W, H, "".join(o))


# ------------------------------------------------------------------------------------------------ label tag
def tag():
    W, H = 120, 66
    bx, by, bw, bh, R = 6, 3, 108, 54, 12
    o = [f'<defs>{lin("tf", [(0, "#f2d585"), (.45, "#c8973e"), (.55, "#8f6120"), (1, "#b98a35")], by, by + bh)}</defs>',
         rr(bx + 1, by + 4, bw - 2, bh, R, fill="#000", opacity=".6", filter="url(#b3)"),
         rr(bx - 1.5, by - 1.5, bw + 3, bh + 3, R + 1.5, fill="#3a2608"),
         rr(bx, by, bw, bh, R, fill="url(#tf)"),
         f'<path d="M{bx + R} {by + 3} H{bx + bw - R}" stroke="#fff" stroke-opacity=".5" stroke-width="2.5" stroke-linecap="round"/>']
    REG["tag"] = dict(canvas=[W, H], inset=[bx + 18, by + 18, (W - bx - bw) + 18, (H - by - bh) + 18], outset=[bx, by, W - bx - bw, H - by - bh])
    return svg(W, H, "".join(o))


# ------------------------------------------------------------------------------------------------ slide-rule rail
def rail():
    W, H = 240, 117
    bx, by, bw, bh, R = 9, 3, 222, 102, 21
    o = [f'<defs>{lin("rf", BRASS_STOPS, by, by + bh)}</defs>',
         rr(bx + 3, by + 9, bw - 6, bh, R, fill="#000", opacity=".6", filter="url(#b6)"),
         rr(bx - 1.5, by - 1.5, bw + 3, bh + 3, R + 1.5, fill="#2f1f07"),
         rr(bx, by, bw, bh, R, fill="url(#rf)"),
         grain("rg", bx, by, bw, bh, R, .2),
         f'<path d="M{bx + R} {by + 3.5} H{bx + bw - R}" stroke="#fff" stroke-opacity=".55" stroke-width="3" stroke-linecap="round"/>',
         f'<path d="M{bx + R} {by + bh - 3} H{bx + bw - R}" stroke="#000" stroke-opacity=".35" stroke-width="4" stroke-linecap="round"/>']
    REG["rail"] = dict(canvas=[W, H], inset=[bx + 27, by + 27, (W - bx - bw) + 27, (H - by - bh) + 27], outset=[bx, by, W - bx - bw, H - by - bh])
    return svg(W, H, "".join(o))


# ------------------------------------------------------------------------------------------------ rings (porthole + gauge)
CX, CY = 180, 174
R_BRASS, R_COPPER, R_LIP, R_HOLE = 156, 132, 120, 111


def ring_common(gid, brass_grad):
    o = [f'<defs>{brass_grad}'
         f'<linearGradient id="{gid}c" gradientUnits="userSpaceOnUse" x1="{CX - 100}" y1="{CY - 150}" x2="{CX + 100}" y2="{CY + 150}">'
         + "".join(f'<stop offset="{off}" stop-color="{c}"/>' for off, c in COPPER_STOPS) + '</linearGradient>'
         f'<mask id="outside"><rect width="360" height="360" fill="#fff"/><circle cx="{CX}" cy="{CY}" r="{R_BRASS}" fill="#000"/></mask></defs>',
         f'<g mask="url(#outside)">' + circ(CX, CY + 9, R_BRASS, fill="#000", opacity=".45", filter="url(#b9)") + '</g>',
         f'<path d="{annulus(CX, CY, R_BRASS, R_COPPER)}" fill-rule="evenodd" fill="url(#{gid})" stroke="#2f1f07" stroke-width="4"/>',
         f'<path d="{annulus(CX, CY, R_COPPER, R_LIP)}" fill-rule="evenodd" fill="url(#{gid}c)" stroke="#3a1a0c" stroke-width="3.5"/>']
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        o.append(rivet(CX + 144 * math.cos(a), CY + 144 * math.sin(a), 8.5))
    return o


def brass_diag(gid):
    return (f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{CX - 130}" y1="{CY - 130}" x2="{CX + 130}" y2="{CY + 130}">'
            '<stop offset="0" stop-color="#fbe7a6"/><stop offset=".3" stop-color="#c8973e"/><stop offset=".6" stop-color="#7a5216"/><stop offset="1" stop-color="#d9ad55"/></linearGradient>')


def porthole():
    o = ring_common("ph", brass_diag("ph"))
    o.append(f'<path d="{annulus(CX, CY, R_LIP, R_HOLE)}" fill-rule="evenodd" fill="#0a0806"/>')
    o.append(circ(CX, CY, R_HOLE, fill="none", stroke="#000", stroke_opacity=".75", stroke_width=6))
    REG["porthole"] = dict(canvas=[360, 360], centre=[CX, CY], hole=R_HOLE)
    return svg(360, 360, "".join(o))


PIVOT = (CX, CY + 12)


def gauge():
    o = ring_common("gg", brass_diag("gg"))
    o.append(f'<defs><radialGradient id="dial" cx=".5" cy=".42" r=".7"><stop offset="0" stop-color="#f6ecd0"/><stop offset=".7" stop-color="#e8d9b0"/><stop offset="1" stop-color="#c9b587"/></radialGradient></defs>')
    o.append(circ(CX, CY, R_LIP, fill="url(#dial)", stroke="#3a2608", stroke_width=4.5))
    px, py = PIVOT
    for i in range(11):
        a = math.radians(-120 + i * 24)
        big = i % 5 == 0
        r1, r2 = (81, 108) if big else (93, 108)
        o.append(f'<line x1="{px + r1 * math.sin(a):.1f}" y1="{py - r1 * math.cos(a):.1f}" x2="{px + r2 * math.sin(a):.1f}" y2="{py - r2 * math.cos(a):.1f}" '
                 f'stroke="#2a1a05" stroke-width="{6 if big else 3.3}" stroke-linecap="round"/>')
    REG["gauge"] = dict(canvas=[360, 360], centre=[CX, CY], pivot=list(PIVOT), dial=R_LIP)
    return svg(360, 360, "".join(o))


# ------------------------------------------------------------------------------------------------ hand-wheel
def wheel():
    S = 3
    o = [f'<defs>{lin("wv", BRASS_STOPS, 10 * S, 90 * S)}'
         f'<linearGradient id="wc" gradientUnits="userSpaceOnUse" x1="0" y1="{35 * S}" x2="0" y2="{65 * S}">' + "".join(f'<stop offset="{off}" stop-color="{c}"/>' for off, c in COPPER_STOPS) + '</linearGradient>'
         f'<linearGradient id="wd" gradientUnits="userSpaceOnUse" x1="{40 * S}" y1="{40 * S}" x2="{60 * S}" y2="{60 * S}"><stop offset="0" stop-color="#fbe7a6"/><stop offset=".5" stop-color="#a5741f"/><stop offset="1" stop-color="#d9ad55"/></linearGradient></defs>',
         circ(150, 150, 44 * S, fill="none", stroke="#000", stroke_opacity=".35", stroke_width=12 * S),
         circ(150, 150, 40 * S, fill="none", stroke="url(#wv)", stroke_width=9 * S),
         circ(150, 150, 44.6 * S, fill="none", stroke="#2f1f07", stroke_width=S)]
    for k in range(6):
        a = math.radians(k * 60)
        x1, y1 = 150 + 37 * S * math.sin(a), 150 - 37 * S * math.cos(a)
        o.append(f'<path d="M150 150 L{x1:.1f} {y1:.1f}" stroke="#2f1f07" stroke-width="{9.5 * S}" stroke-linecap="round"/>')
    for k in range(6):
        a = math.radians(k * 60)
        x1, y1 = 150 + 36 * S * math.sin(a), 150 - 36 * S * math.cos(a)
        o.append(f'<path d="M150 150 L{x1:.1f} {y1:.1f}" stroke="url(#wv)" stroke-width="{6.5 * S}" stroke-linecap="round"/>')
    o += [circ(150, 150, 15 * S, fill="url(#wc)", stroke="#3a1a0c", stroke_width=1.2 * S),
          circ(150, 150, 8 * S, fill="url(#wd)", stroke="#3a2608", stroke_width=S),
          circ(150 - 2.5 * S, 150 - 2.5 * S, 2.4 * S, fill="#fff", opacity=".55")]
    REG["wheel"] = dict(canvas=[300, 300], rim=44 * S)
    return svg(300, 300, "".join(o))


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for name, fn in (("panel", panel), ("plate", plate), ("window", window), ("tag", tag), ("rail", rail),
                     ("porthole", porthole), ("gauge", gauge), ("wheel", wheel)):
        open(os.path.join(out, f"steam_{name}.svg"), "w").write(fn())
    json.dump(REG, open(os.path.join(out, "steam_regions.json"), "w"), indent=1)
    print({k: v["canvas"] for k, v in REG.items()})
