#!/usr/bin/env python3
"""Procedural texture generation for WARFRONT.

Generates every PNG the mod ships: soldier skins (player-model layout), the
"parts" swatch atlas used by gun item models and box-rendered entities (tank,
flags, gadgets), block textures and gadget item icons. All art is original.

    python3 tools/gen_textures.py
"""
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "warfront", "textures")
R = random.Random(42)


def save(img, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    print("wrote", os.path.join(*path))


def clamp(v):
    return max(0, min(255, int(v)))


def jitter(c, amt):
    d = R.uniform(-amt, amt)
    return tuple(clamp(ch + d) for ch in c[:3]) + ((c[3],) if len(c) > 3 else (255,))


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i : i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(clamp(ch * f) for ch in c[:3]) + (c[3] if len(c) > 3 else 255,)


# ------------------------------------------------------------------ parts atlas
# 64x64 atlas, 4x4 grid of 16px swatches. Index -> (u, v) = ((i % 4) * 16, (i // 4) * 16)
SWATCHES = [
    ("gunmetal", "#2b2e33", 10),
    ("steel", "#4a4f57", 10),
    ("polymer", "#1d1e21", 6),
    ("tan", "#9d8a62", 12),
    ("wood", "#6e4a2a", 14),
    ("olive", "#4f5627", 12),
    ("bright_steel", "#9aa0a8", 10),
    ("lens", "#2f6fb0", 0),
    ("red", "#c22a22", 8),
    ("tank_green", "#59633b", 16),
    ("tank_tan", "#a08d5f", 16),
    ("rubber", "#222222", 8),
    ("orange", "#d67a1d", 10),
    ("white", "#e6e6e6", 6),
    ("team_blue", "#2f6fd8", 6),
    ("team_red", "#d83a2e", 6),
]


def gen_parts():
    img = Image.new("RGBA", (64, 64))
    px = img.load()
    for i, (name, col, noise) in enumerate(SWATCHES):
        u, v = (i % 4) * 16, (i // 4) * 16
        base = hexc(col)
        for y in range(16):
            for x in range(16):
                c = jitter(base, noise)
                if name in ("tank_green", "tank_tan"):
                    # blotchy camo
                    if ((x // 3 + y // 4 + (x * y) % 3) % 5) == 0:
                        c = shade(c, 0.78)
                if name == "lens":
                    # glassy gradient with highlight
                    f = 0.6 + 0.6 * (1 - y / 15)
                    c = shade(base, f)
                    if x + y < 6:
                        c = (200, 225, 255, 255)
                if name == "wood" and y % 4 == 0:
                    c = shade(c, 0.85)
                if x in (0, 15) or y in (0, 15):
                    c = shade(c, 0.82)  # subtle bevel edge
                px[u + x, v + y] = c
    save(img, "entity", "parts.png")
    save(img, "item", "parts.png")  # item models may only use the block/item atlas


# ------------------------------------------------------------------ soldier skins
# Player skin 64x64 UV layout helpers: each part is (x, y, w, h, d) cuboid net.
def cuboid_faces(x, y, w, h, d):
    """Return dict of face rects (x0, y0, x1, y1) for a model cuboid net."""
    return {
        "top": (x + d, y, x + d + w, y + d),
        "bottom": (x + d + w, y, x + d + 2 * w, y + d),
        "right": (x, y + d, x + d, y + d + h),
        "front": (x + d, y + d, x + d + w, y + d + h),
        "left": (x + d + w, y + d, x + 2 * d + w, y + d + h),
        "back": (x + 2 * d + w, y + d, x + 2 * d + 2 * w, y + d + h),
    }


HEAD = cuboid_faces(0, 0, 8, 8, 8)
HAT = cuboid_faces(32, 0, 8, 8, 8)
BODY = cuboid_faces(16, 16, 8, 12, 4)
JACKET = cuboid_faces(16, 32, 8, 12, 4)
RARM = cuboid_faces(40, 16, 4, 12, 4)
RSLEEVE = cuboid_faces(40, 32, 4, 12, 4)
LARM = cuboid_faces(32, 48, 4, 12, 4)
LSLEEVE = cuboid_faces(48, 48, 4, 12, 4)
RLEG = cuboid_faces(0, 16, 4, 12, 4)
RPANTS = cuboid_faces(0, 32, 4, 12, 4)
LLEG = cuboid_faces(16, 48, 4, 12, 4)
LPANTS = cuboid_faces(0, 48, 4, 12, 4)

FACTIONS = {
    "coalition": {
        "camo": [hexc("#4b5a32"), hexc("#3a4527"), hexc("#6b7448"), hexc("#2c3320")],
        "vest": hexc("#3d4a2c"),
        "helmet": hexc("#4a5634"),
        "team": hexc("#2f6fd8"),
        "boot": hexc("#2a2620"),
    },
    "legion": {
        "camo": [hexc("#a48e64"), hexc("#7f6a45"), hexc("#c2ad82"), hexc("#5f4e33")],
        "vest": hexc("#6d5a3b"),
        "helmet": hexc("#8a7652"),
        "team": hexc("#d83a2e"),
        "boot": hexc("#3a2e22"),
    },
}
SKIN_TONES = [hexc("#e0b08a"), hexc("#c68c5f"), hexc("#8d5a3b"), hexc("#f0c9a5"), hexc("#a8724c")]
ROLES = {
    "assault": hexc("#e8e8e8"),  # white medic cross patch
    "engineer": hexc("#e0b020"),  # yellow wrench
    "support": hexc("#5fbf3a"),  # green ammo
    "recon": hexc("#9a6ad0"),  # purple scope
}


def fill_rect(px, rect, colfn):
    x0, y0, x1, y1 = rect
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = colfn(x - x0, y - y0)


def camo_fn(palette, seed):
    rr = random.Random(seed)
    blobs = [(rr.randint(0, 63), rr.randint(0, 63), rr.randint(2, 4), rr.choice(palette[1:])) for _ in range(140)]

    def fn_at(ax, ay):
        c = palette[0]
        for bx, by, br, bc in blobs:
            if (ax - bx) ** 2 + ((ay - by) * 1.3) ** 2 < br * br:
                c = bc
        return jitter(c, 6)

    return fn_at


def gen_soldier(faction, role, variant):
    f = FACTIONS[faction]
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    camo = camo_fn(f["camo"], hash((faction, role, variant)) & 0xFFFF)
    skin = SKIN_TONES[variant % len(SKIN_TONES)]

    def camo_rect(rect):
        fill_rect(px, rect, lambda x, y, r=rect: camo(r[0] + x, r[1] + y))

    # ---- head (face)
    for k, rect in HEAD.items():
        fill_rect(px, rect, lambda x, y: jitter(skin, 5))
    fx0, fy0, _, _ = HEAD["front"]
    # eyes
    eye = (40, 40, 40, 255)
    px[fx0 + 2, fy0 + 4] = (240, 240, 240, 255)
    px[fx0 + 3, fy0 + 4] = eye
    px[fx0 + 5, fy0 + 4] = eye
    px[fx0 + 6, fy0 + 4] = (240, 240, 240, 255)
    # brows
    for x in (2, 3, 5, 6):
        px[fx0 + x, fy0 + 3] = shade(skin, 0.55)
    # mouth
    for x in range(3, 6):
        px[fx0 + x, fy0 + 6] = shade(skin, 0.7)
    # balaclava / face wrap for recon + legion variant: cover lower face
    if role == "recon" or (faction == "legion" and variant % 2 == 0):
        wrap = f["camo"][3] if role != "recon" else hexc("#3b3b32")
        for side in ("front", "left", "right", "back"):
            x0, y0, x1, y1 = HEAD[side]
            for y in range(y0 + 5, y1):
                for x in range(x0, x1):
                    px[x, y] = jitter(wrap, 6)
    # ---- helmet on hat layer: top + sides, rim over brow
    hel = f["helmet"]
    for k, rect in HAT.items():
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                if k in ("top", "bottom"):
                    if k == "top":
                        px[x, y] = jitter(hel, 8)
                    continue
                ry = y - y0
                if ry <= 2 or (k in ("left", "right", "back") and ry <= 4):
                    px[x, y] = jitter(hel if ry < 2 else shade(hel, 0.8), 8)
    # helmet strap + team band on helmet
    for side in ("left", "right"):
        x0, y0, x1, y1 = HAT[side]
        for x in range(x0, x1):
            px[x, y0 + 1] = f["team"]
    # goggles on helmet front (recon gets ghillie fringe)
    gx0, gy0, _, _ = HAT["front"]
    if role == "recon":
        ghillie = [hexc("#4f5a30"), hexc("#3a4424"), hexc("#68713c")]
        for k, rect in HAT.items():
            x0, y0, x1, y1 = rect
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if k == "top" or (k != "bottom" and (y - y0) < 4 + R.randint(0, 3)):
                        if R.random() < 0.85:
                            px[x, y] = R.choice(ghillie)
    else:
        for x in range(1, 7):
            px[gx0 + x, gy0 + 2] = (25, 25, 25, 255) if x in (1, 6) else (90, 140, 160, 255)
    # ---- body: camo shirt + plate carrier vest
    for k, rect in BODY.items():
        camo_rect(rect)
    vest = f["vest"]
    for k in ("front", "back", "left", "right"):
        x0, y0, x1, y1 = BODY[k]
        for y in range(y0, y0 + 9):
            for x in range(x0, x1):
                if k in ("left", "right") and (y - y0) < 2:
                    continue
                px[x, y] = jitter(vest, 6)
    # pouches on vest front
    bx0, by0, _, _ = BODY["front"]
    for i in range(3):
        for y in range(5, 8):
            for x in range(i * 3 + 0, i * 3 + 2):
                px[bx0 + x, by0 + y] = shade(vest, 0.72)
    # belt
    for k in ("front", "back", "left", "right"):
        x0, y0, x1, y1 = BODY[k]
        for x in range(x0, x1):
            px[x, y0 + 9] = (35, 32, 28, 255)
    # role patch on chest
    pc = ROLES[role]
    px[bx0 + 5, by0 + 2] = pc
    px[bx0 + 6, by0 + 2] = pc
    if role == "assault":
        px[bx0 + 5, by0 + 1] = pc
        px[bx0 + 5, by0 + 3] = pc
        px[bx0 + 6, by0 + 2] = pc
        px[bx0 + 4, by0 + 2] = pc
    # ---- arms: camo sleeves, team armband, gloves
    for arm in (RARM, LARM):
        for k, rect in arm.items():
            camo_rect(rect)
        for k in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = arm[k]
            for x in range(x0, x1):
                px[x, y0 + 2] = f["team"]
                px[x, y0 + 3] = f["team"]
            for y in range(y1 - 3, y1):
                for x in range(x0, x1):
                    px[x, y] = jitter(hexc("#2b2b2b"), 5)
        x0, y0, x1, y1 = arm["bottom"]
        fill_rect(px, arm["bottom"], lambda x, y: hexc("#2b2b2b"))
    # ---- legs: camo pants, knee pads, boots
    for leg in (RLEG, LLEG):
        for k, rect in leg.items():
            camo_rect(rect)
        x0, y0, x1, y1 = leg["front"]
        for y in range(y0 + 4, y0 + 6):
            for x in range(x0, x1):
                px[x, y] = jitter(shade(vest, 0.8), 5)
        for k in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = leg[k]
            for y in range(y1 - 3, y1):
                for x in range(x0, x1):
                    px[x, y] = jitter(f["boot"], 5)
        fill_rect(px, leg["bottom"], lambda x, y: f["boot"])
    # thigh pouch for support/engineer on jacket layer of right leg
    if role in ("support", "engineer"):
        x0, y0, x1, y1 = RPANTS["right"]
        for y in range(y0 + 1, y0 + 4):
            for x in range(x0, x1):
                px[x, y] = shade(vest, 0.8)
    # backpack on jacket layer back for support/engineer/assault
    if role != "recon":
        x0, y0, x1, y1 = JACKET["back"]
        pack = shade(vest, 0.9)
        for y in range(y0 + 1, y0 + 9):
            for x in range(x0 + 1, x1 - 1):
                px[x, y] = jitter(pack, 6)
        if role == "engineer":  # rocket tubes
            for y in range(y0, y0 + 11):
                px[x0 + 1, y] = hexc("#565c3a")
                px[x0 + 2, y] = hexc("#565c3a")
    save(img, "entity", "soldier", f"{faction}_{role}_{variant}.png")


# ------------------------------------------------------------------ blocks
def tex16(fn):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = fn(x, y)
    return img


def gen_blocks():
    sand = hexc("#b8a273")
    sand_dark = hexc("#8f7c52")

    def sandbag(x, y):
        row = y // 4
        off = 0 if row % 2 == 0 else 4
        xx = (x + off) % 8
        if y % 4 == 3 or xx == 0:
            return jitter(sand_dark, 6)
        c = jitter(sand, 10)
        if y % 4 == 0:
            c = shade(c, 1.08)
        if (xx == 4 and y % 4 == 1):
            c = shade(c, 0.85)  # tie
        return c

    save(tex16(sandbag), "block", "sandbag.png")
    save(tex16(lambda x, y: jitter(shade(sand, 1.05), 8) if (x + y) % 7 else jitter(sand_dark, 6)), "block", "sandbag_top.png")

    # war table: map top with contour lines and grid, wooden sides
    def war_top(x, y):
        c = jitter(hexc("#6b8a54"), 8)
        if (x * 3 + y * 5) % 11 == 0:
            c = shade(c, 0.85)
        if x % 5 == 0 or y % 5 == 0:
            c = shade(c, 0.9)
        if x in (0, 15) or y in (0, 15):
            c = jitter(hexc("#4a3420"), 6)
        if (x, y) in ((4, 4), (11, 10)):
            c = hexc("#2f6fd8")
        if (x, y) in ((11, 4), (4, 11)):
            c = hexc("#d83a2e")
        if (x, y) == (8, 8):
            c = hexc("#e6e6e6")
        return c

    save(tex16(war_top), "block", "war_table_top.png")
    save(tex16(lambda x, y: jitter(hexc("#5a3d22") if y % 5 else hexc("#3e2914"), 8) if 2 < y < 14 or x in (0, 15) else jitter(hexc("#4a3420"), 6)), "block", "war_table_side.png")

    # military crate (resupply): olive with stencil
    def crate(x, y, stripe):
        c = jitter(hexc("#4f5a32"), 8)
        if x in (0, 15) or y in (0, 15) or x in (1, 14) and y % 3 == 0:
            c = jitter(hexc("#3a4226"), 6)
        if 5 <= y <= 10 and 3 <= x <= 12:
            c = jitter(hexc(stripe), 8)
            if 6 <= y <= 9 and (x in (5, 6, 9, 10)):
                c = hexc("#1c1c1c")
        return c

    save(tex16(lambda x, y: crate(x, y, "#c9a227")), "block", "resupply_crate.png")
    save(tex16(lambda x, y: jitter(hexc("#4f5a32"), 8) if 1 < x < 14 and 1 < y < 14 else jitter(hexc("#3a4226"), 6)), "block", "resupply_crate_top.png")

    # HQ block: concrete with team-neutral beacon light
    def hq(x, y):
        c = jitter(hexc("#6d6f72"), 8)
        if x in (0, 15) or y in (0, 15):
            c = hexc("#3c3e41")
        if 5 <= x <= 10 and 5 <= y <= 10:
            c = hexc("#dddddd") if (x + y) % 2 else hexc("#bbbbbb")
        return c

    save(tex16(hq), "block", "hq_beacon.png")

    # flag base: steel plate with letter ring
    def flagbase(x, y):
        c = jitter(hexc("#55595e"), 8)
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if 5.5 < d < 7:
            c = hexc("#e6e6e6")
        if x in (0, 15) or y in (0, 15):
            c = hexc("#2e3134")
        return c

    save(tex16(flagbase), "block", "capture_point.png")
    save(tex16(lambda x, y: jitter(hexc("#55595e"), 8) if 0 < x < 15 and 0 < y < 15 else hexc("#2e3134")), "block", "capture_point_side.png")

    # reinforced concrete (tower) + tower core
    def concrete(x, y):
        c = jitter(hexc("#8a8c8e"), 9)
        if R.random() < 0.05:
            c = shade(c, 0.8)
        if y == 15 or x == 15:
            c = shade(c, 0.85)
        return c

    save(tex16(concrete), "block", "reinforced_concrete.png")

    def core(x, y):
        c = concrete(x, y)
        if 3 <= x <= 12 and 3 <= y <= 12:
            c = jitter(hexc("#3a3d41"), 6)
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 6:
                c = hexc("#e0a020") if (x + y) % 2 else hexc("#ffcc33")
        return c

    save(tex16(core), "block", "tower_core.png")

    # vehicle pad: yellow-black hazard border on asphalt
    def pad(x, y):
        c = jitter(hexc("#2e2f31"), 6)
        if x < 2 or x > 13 or y < 2 or y > 13:
            c = hexc("#e0b020") if ((x + y) // 2) % 2 else hexc("#1a1a1a")
        elif 6 <= x <= 9 and 4 <= y <= 11 and not (6 <= y <= 9 and 7 <= x <= 8):
            c = hexc("#d6d6d6")
        return c

    save(tex16(pad), "block", "vehicle_pad.png")
    # spawn beacon block (placed radio beacon)
    save(tex16(lambda x, y: jitter(hexc("#3a3d41"), 6) if not (6 <= x <= 9 and y < 9) else (hexc("#cc2222") if y < 3 else hexc("#1c1c1c"))), "block", "spawn_beacon.png")
    # c4 charge texture
    save(tex16(lambda x, y: jitter(hexc("#c8b98a"), 6) if not (y in (5, 10)) else hexc("#222222")), "block", "c4.png")


# ------------------------------------------------------------------ item icons
def icon(draw_fn, name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_fn(d, img.load())
    save(img, "item", name + ".png")


def gen_icons():
    def grenade(d, px):
        d.ellipse((3, 5, 12, 14), fill=hexc("#4f5a32"), outline=hexc("#2a3018"))
        d.rectangle((6, 2, 9, 5), fill=hexc("#6b6e72"))
        d.line((9, 3, 12, 5), fill=hexc("#9aa0a8"))
        d.ellipse((12, 4, 14, 6), outline=hexc("#c0c4c8"))
        d.line((5, 9, 10, 9), fill=hexc("#2a3018"))
        px[5, 7] = hexc("#7a8650")

    icon(grenade, "frag_grenade")

    def smoke(d, px):
        d.rectangle((5, 4, 10, 14), fill=hexc("#8a8f96"), outline=hexc("#45484d"))
        d.rectangle((5, 7, 10, 8), fill=hexc("#e6e6e6"))
        d.rectangle((6, 2, 9, 4), fill=hexc("#45484d"))
        d.line((9, 3, 12, 5), fill=hexc("#9aa0a8"))
        px[12, 2] = hexc("#d0d0d0")
        px[13, 1] = hexc("#e0e0e0")

    icon(smoke, "smoke_grenade")

    def medbag(d, px):
        d.rounded_rectangle((2, 4, 13, 13), 2, fill=hexc("#e6e6e6"), outline=hexc("#9a9a9a"))
        d.rectangle((6, 6, 9, 11), fill=hexc("#c22a22"))
        d.rectangle((4, 7, 11, 10), fill=hexc("#c22a22"))
        d.line((5, 2, 10, 2), fill=hexc("#555555"))
        d.line((5, 2, 5, 4), fill=hexc("#555555"))
        d.line((10, 2, 10, 4), fill=hexc("#555555"))

    icon(medbag, "medic_bag")

    def ammobag(d, px):
        d.rounded_rectangle((2, 4, 13, 13), 2, fill=hexc("#4f5a32"), outline=hexc("#2a3018"))
        for x in (5, 8, 11):
            d.rectangle((x - 1, 6, x, 11), fill=hexc("#d4a62a"))
            px[x - 1, 6] = hexc("#b87333")
            px[x, 6] = hexc("#b87333")
        d.line((5, 2, 10, 2), fill=hexc("#333333"))

    icon(ammobag, "ammo_bag")

    def c4(d, px):
        d.rectangle((2, 5, 13, 12), fill=hexc("#c8b98a"), outline=hexc("#7a6e4e"))
        d.line((2, 8, 13, 8), fill=hexc("#222222"))
        d.rectangle((9, 3, 12, 5), fill=hexc("#2a2a2a"))
        px[10, 4] = hexc("#ff3030")
        d.line((7, 5, 9, 3), fill=hexc("#d83a2e"))

    icon(c4, "c4")

    def detonator(d, px):
        d.rectangle((5, 5, 11, 14), fill=hexc("#2a2c30"), outline=hexc("#111111"))
        d.line((10, 1, 10, 5), fill=hexc("#555555"))
        d.rectangle((7, 7, 9, 9), fill=hexc("#d83a2e"))

    icon(detonator, "detonator")

    def defib(d, px):
        for x0 in (1, 8):
            d.rounded_rectangle((x0, 4, x0 + 6, 12), 2, fill=hexc("#e0b020"), outline=hexc("#5a4a10"))
            d.rectangle((x0 + 2, 12, x0 + 4, 14), fill=hexc("#333333"))
        d.line((4, 7, 5, 9), fill=hexc("#ffffff"))
        d.line((11, 7, 12, 9), fill=hexc("#ffffff"))

    icon(defib, "defibrillator")

    def repair(d, px):
        d.rectangle((2, 7, 10, 11), fill=hexc("#e0b020"), outline=hexc("#6a5410"))
        d.rectangle((10, 8, 14, 10), fill=hexc("#6b6e72"))
        d.rectangle((4, 11, 6, 14), fill=hexc("#2a2a2a"))
        px[14, 7] = hexc("#7fd0ff")
        px[15, 9] = hexc("#7fd0ff")
        px[14, 11] = hexc("#7fd0ff")

    icon(repair, "repair_tool")

    def beacon(d, px):
        d.rectangle((5, 6, 10, 14), fill=hexc("#3a3d41"), outline=hexc("#1c1c1c"))
        d.line((8, 1, 8, 6), fill=hexc("#888888"))
        px[8, 1] = hexc("#ff3030")
        px[7, 9] = hexc("#5fbf3a")

    icon(beacon, "spawn_beacon")

    def rocket(d, px):
        d.polygon([(2, 8), (5, 6), (12, 6), (12, 10), (5, 10)], fill=hexc("#4f5a32"))
        d.polygon([(0, 8), (2, 6), (2, 10)], fill=hexc("#2b2e33"))
        d.rectangle((12, 5, 14, 11), fill=hexc("#2b2e33"))

    icon(rocket, "rocket")

    def tank_kit(d, px):
        d.rectangle((1, 9, 14, 13), fill=hexc("#59633b"), outline=hexc("#2a3018"))
        d.rectangle((4, 6, 10, 9), fill=hexc("#6b7448"))
        d.line((10, 7, 15, 7), fill=hexc("#2b2e33"))
        for x in (3, 6, 9, 12):
            px[x, 13] = hexc("#222222")

    icon(tank_kit, "tank_deployer")

    def buggy_kit(d, px):
        d.rectangle((2, 8, 13, 11), fill=hexc("#59633b"), outline=hexc("#2a3018"))
        d.line((4, 8, 4, 4), fill=hexc("#2b2e33"))
        d.line((11, 8, 11, 4), fill=hexc("#2b2e33"))
        d.line((4, 4, 11, 4), fill=hexc("#2b2e33"))
        d.line((7, 7, 13, 5), fill=hexc("#45484d"))
        for x in (3, 12):
            d.ellipse((x - 2, 10, x + 2, 14), fill=hexc("#222222"))

    icon(buggy_kit, "buggy_deployer")

    def dogtag(d, px):
        d.rounded_rectangle((4, 4, 11, 13), 2, fill=hexc("#b8bcc2"), outline=hexc("#6b6e72"))
        d.line((6, 7, 9, 7), fill=hexc("#555555"))
        d.line((6, 9, 9, 9), fill=hexc("#555555"))
        d.line((7, 1, 7, 4), fill=hexc("#8a8a8a"))

    icon(dogtag, "dog_tag")

    def binos(d, px):
        d.rectangle((2, 6, 6, 12), fill=hexc("#2b2e33"))
        d.rectangle((9, 6, 13, 12), fill=hexc("#2b2e33"))
        d.rectangle((6, 7, 9, 9), fill=hexc("#45484d"))
        d.rectangle((3, 11, 5, 12), fill=hexc("#2f6fb0"))
        d.rectangle((10, 11, 12, 12), fill=hexc("#2f6fb0"))

    icon(binos, "field_binoculars")


# ------------------------------------------------------------------ misc entity textures
def gen_misc():
    # flag cloth emblem textures (white base, tinted in code)
    img = Image.new("RGBA", (32, 16))
    px = img.load()
    for y in range(16):
        for x in range(32):
            c = (255, 255, 255, 255)
            if (x + y) % 9 == 0:
                c = (230, 230, 230, 255)
            if x > 26 and (y * 3 + x) % 4 == 0:
                c = (215, 215, 215, 255)  # frayed edge shading
            px[x, y] = c
    # emblem: chevron
    d = ImageDraw.Draw(img)
    d.polygon([(9, 3), (15, 8), (9, 13), (12, 8)], fill=(40, 40, 40, 255))
    d.polygon([(15, 3), (21, 8), (15, 13), (18, 8)], fill=(40, 40, 40, 255))
    save(img, "entity", "flag_cloth.png")
    # muzzle flash: radial star (additive)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    import math

    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            r = math.hypot(dx, dy) / 16
            a = math.atan2(dy, dx)
            star = 0.55 + 0.45 * abs(math.cos(a * 3))
            v = max(0, 1 - r / star)
            if v > 0:
                px[x, y] = (255, clamp(200 + 55 * v), clamp(120 + 120 * v), clamp(255 * v ** 0.8))
    save(img, "entity", "muzzle_flash.png")
    # scope overlay is drawn in code; generate a small particle "smoke puff" too
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5 / 8
            if r < 1:
                g = clamp(200 - 30 * R.random())
                px[x, y] = (g, g, g, clamp(255 * (1 - r) ** 1.5))
    save(img, "particle", "smoke_puff.png")


if __name__ == "__main__":
    gen_parts()
    for fac in FACTIONS:
        for role in ROLES:
            for v in range(2):
                gen_soldier(fac, role, v)
    gen_blocks()
    gen_icons()
    gen_misc()
