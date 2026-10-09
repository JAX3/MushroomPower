#!/usr/bin/env python3
"""Generates the Mycelial Power textures, block models and the GameTest structure.

Run from the repository root:  python3 tools/generate_assets.py
Requires Pillow (pip install pillow). Output is deterministic (fixed random seed).
"""
import gzip
import json
import math
import os
import random
import struct

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "mycelialpower")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "mycelialpower")
rng = random.Random(1337)


def hexc(value, alpha=255):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color[:3]) + (color[3],)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def save(img, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write_json(obj, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# ----------------------------------------------------------------------------- block textures

METAL = hexc("#30333b")
METAL_LIGHT = hexc("#4b505b")
METAL_DARK = hexc("#1b1d22")
RIVET = hexc("#77808f")
PURPLE = hexc("#8a5cd6")
PURPLE_DARK = hexc("#2a1840")
GREEN = hexc("#5c8f3e")
GREEN_LIGHT = hexc("#86c25a")
MYCELIUM = hexc("#7a6a80")
MYCELIUM_LIGHT = hexc("#9c8aa3")


def metal_base(size=16):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            px[x, y] = shade(METAL, rng.randint(-6, 6))
    # panel seams
    for i in range(size):
        px[i, 0] = METAL_LIGHT
        px[0, i] = METAL_LIGHT
        px[i, size - 1] = METAL_DARK
        px[size - 1, i] = METAL_DARK
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px[x, y] = RIVET
        px[x + 1, y + 1] = METAL_DARK
    return img


def connector_port(img):
    """Visible connection point in the middle of a face."""
    px = img.load()
    for y in range(5, 11):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (5, 10)
            px[x, y] = hexc("#5d4a7a") if edge else hexc("#15121c")
    for (x, y) in [(7, 7), (8, 8), (7, 8), (8, 7)]:
        px[x, y] = PURPLE
    px[5, 5] = px[10, 5] = px[5, 10] = px[10, 10] = METAL_LIGHT


def fungal_growth(img, rows, density):
    px = img.load()
    for y in rows:
        for x in range(1, 15):
            if rng.random() < density:
                px[x, y] = GREEN if rng.random() < 0.6 else GREEN_LIGHT
    # creeping tendrils
    for _ in range(3):
        x = rng.randint(2, 13)
        y = max(rows)
        for _ in range(rng.randint(2, 4)):
            px[x, y] = GREEN
            y -= 1
            x += rng.choice((-1, 0, 1))
            x = max(1, min(14, x))


def side_texture():
    img = metal_base()
    connector_port(img)
    fungal_growth(img, rows=[14, 13], density=0.55)
    px = img.load()
    px[3, 12] = hexc("#b33a2c")  # tiny red cap
    px[12, 11] = hexc("#8a6a4a")  # tiny brown cap
    return img


def top_texture():
    img = metal_base()
    px = img.load()
    for _ in range(26):
        x, y = rng.randint(1, 14), rng.randint(1, 14)
        if 4 <= x <= 11 and 4 <= y <= 11:
            continue
        px[x, y] = MYCELIUM if rng.random() < 0.6 else MYCELIUM_LIGHT
    connector_port(img)
    return img


def bottom_texture():
    img = metal_base()
    connector_port(img)
    return img


def front_texture():
    img = metal_base()
    px = img.load()
    for y in range(3, 13):
        for x in range(3, 13):
            px[x, y] = hexc("#121116")
    fungal_growth(img, rows=[14], density=0.5)
    return img


def chamber_off():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(PURPLE_DARK, rng.randint(-5, 5))
    for i in range(16):
        px[i, 0] = px[0, i] = hexc("#4a3a5e")
        px[i, 15] = px[15, i] = hexc("#140c1e")
    for i in range(3, 8):  # glass reflection
        px[i, 11 - i] = hexc("#4d3d66")
    return img


def chamber_on(frames=8):
    img = Image.new("RGBA", (16, 16 * frames))
    px = img.load()
    c1, c2, c3 = hexc("#5a22c9"), hexc("#3d7bff"), hexc("#e0c2ff")
    for f in range(frames):
        phase = f / frames * 2 * math.pi
        for y in range(16):
            for x in range(16):
                cx, cy = x - 7.5, y - 7.5
                r = math.hypot(cx, cy)
                a = math.atan2(cy, cx)
                v = 0.5 + 0.5 * math.sin(r * 0.9 - phase * 2 + a * 2)
                core = max(0.0, 1.0 - r / 6.0)
                col = lerp(c1, c2, v)
                col = lerp(col, c3, min(1.0, core * 0.9 + 0.1 * math.sin(phase + r)))
                px[x, y + f * 16] = col
        for i in range(16):
            px[i, f * 16] = px[0, f * 16 + i] = hexc("#6f54a0")
            px[i, f * 16 + 15] = px[15, f * 16 + i] = hexc("#2a1840")
    return img


# ----------------------------------------------------------------------------- GUI textures

GUI_BG = hexc("#3a3d45")
GUI_LIGHT = hexc("#5f6470")
GUI_SHADOW = hexc("#202228")
GUI_OUTLINE = hexc("#0f1013")
INSET_DARK = hexc("#17181c")
INSET_LIGHT = hexc("#6a6f7c")
SLOT_FILL = hexc("#2a2c33")
PANEL_FILL = hexc("#1b1c21")


def panel(draw, x0, y0, x1, y1):
    draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=GUI_BG)
    draw.line([x0 + 1, y0, x1 - 2, y0], fill=GUI_OUTLINE)
    draw.line([x0 + 1, y1 - 1, x1 - 2, y1 - 1], fill=GUI_OUTLINE)
    draw.line([x0, y0 + 1, x0, y1 - 2], fill=GUI_OUTLINE)
    draw.line([x1 - 1, y0 + 1, x1 - 1, y1 - 2], fill=GUI_OUTLINE)
    draw.line([x0 + 1, y0 + 1, x1 - 3, y0 + 1], fill=GUI_LIGHT)
    draw.line([x0 + 1, y0 + 1, x0 + 1, y1 - 3], fill=GUI_LIGHT)
    draw.line([x0 + 2, y1 - 2, x1 - 2, y1 - 2], fill=GUI_SHADOW)
    draw.line([x1 - 2, y0 + 2, x1 - 2, y1 - 2], fill=GUI_SHADOW)


def inset(draw, x0, y0, x1, y1, fill):
    """Recessed box whose outer bounds are [x0, x1) x [y0, y1)."""
    draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=fill)
    draw.line([x0, y0, x1 - 1, y0], fill=INSET_DARK)
    draw.line([x0, y0, x0, y1 - 1], fill=INSET_DARK)
    draw.line([x0 + 1, y1 - 1, x1 - 1, y1 - 1], fill=INSET_LIGHT)
    draw.line([x1 - 1, y0 + 1, x1 - 1, y1 - 1], fill=INSET_LIGHT)


def slot(draw, item_x, item_y):
    inset(draw, item_x - 1, item_y - 1, item_x + 17, item_y + 17, SLOT_FILL)


def mushroom_icon(img, x, y, cap):
    px = img.load()
    for dx in range(-3, 4):
        px[x + dx, y] = cap
    for dx in range(-2, 3):
        px[x + dx, y - 1] = cap
    px[x - 1, y - 1] = hexc("#f0e6d8") if cap[0] > 150 else cap
    px[x + 1, y] = hexc("#f0e6d8") if cap[0] > 150 else cap
    for dy in range(1, 4):
        px[x, y + dy] = hexc("#d9cfc0")


def gui_texture():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    panel(d, 0, 0, 256, 232)

    # Energy bar and water tank (inner 14x70 at (9,19) and (29,19))
    for bx in (8, 28):
        inset(d, bx, 18, bx + 16, 90, hexc("#141519"))
        for ty in range(19 + 7, 89, 7):
            d.line([bx + 11, ty, bx + 14, ty], fill=hexc("#2b2d35") if bx == 8 else hexc("#2b3340"))
    px = img.load()

    # Machine slots
    slot(d, 52, 19)   # fuel
    slot(d, 52, 57)   # container in
    slot(d, 52, 89)   # container out
    # Burn indicator (16x14 at (52,38))
    inset(d, 51, 37, 69, 53, hexc("#141519"))
    # Arrow between container slots
    for i in range(5):
        d.line([60 - i, 77 + i, 60 + i, 77 + i], fill=hexc("#8b7d90"))
    d.rectangle([59, 74, 61, 76], fill=hexc("#8b7d90"))
    # Hint icons in the empty slots (drawn faintly)
    mushroom_icon(img, 60, 26, hexc("#5a3434"))
    for (x, y) in [(57, 62), (58, 62), (59, 62), (60, 62), (61, 62), (62, 62), (57, 63), (62, 63), (58, 64), (59, 64),
                   (60, 64), (61, 64), (58, 65), (61, 65), (58, 66), (59, 66), (60, 66), (61, 66)]:
        px[x, y] = hexc("#3b4a5c")

    # Info panel (rows of 11px starting at y=19)
    inset(d, 76, 16, 251, 131, PANEL_FILL)
    for sep in (40, 62, 95):
        d.line([79, sep, 247, sep], fill=hexc("#2c2e36"))

    # Player inventory
    for row in range(3):
        for col in range(9):
            slot(d, 48 + col * 18, 150 + row * 18)
    for col in range(9):
        slot(d, 48 + col * 18, 208)

    # Decorations: mushrooms on the casing corners and mycelium creeping along the bottom edge
    mushroom_icon(img, 236, 8, hexc("#c23b2e"))
    mushroom_icon(img, 246, 9, hexc("#9a6b45"))
    mushroom_icon(img, 10, 224, hexc("#9a6b45"))
    for x in range(3, 253):
        if rng.random() < 0.35:
            px[x, 229] = MYCELIUM if rng.random() < 0.5 else GREEN
        if rng.random() < 0.12:
            px[x, 228] = MYCELIUM_LIGHT
    return img


def jei_texture():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w, h = 168, 74
    d.rectangle([0, 0, w - 1, h - 1], fill=hexc("#2b2d35"))
    d.rectangle([0, 0, w - 1, h - 1], outline=hexc("#5f6470"))
    slot(d, 6, 6)
    inset(d, 6, 26, 22, 68, hexc("#141519"))
    d.line([26, 4, 26, h - 5], fill=hexc("#3f434d"))
    px = img.load()
    for x in range(28, w - 2):
        if rng.random() < 0.3:
            px[x, h - 3] = MYCELIUM if rng.random() < 0.5 else GREEN
    return img


def logo():
    img = Image.new("RGBA", (128, 128), hexc("#16151c"))
    d = ImageDraw.Draw(img)
    for r in range(56, 0, -2):
        t = r / 56
        d.ellipse([64 - r, 64 - r, 64 + r, 64 + r], fill=lerp(hexc("#e0c2ff"), hexc("#2a1060"), t))
    d.rectangle([34, 70, 94, 112], fill=hexc("#30333b"), outline=hexc("#4b505b"))
    d.rectangle([50, 80, 78, 102], fill=hexc("#5a22c9"), outline=hexc("#c79bff"))
    d.rectangle([60, 46, 68, 72], fill=hexc("#d9cfc0"))
    d.pieslice([36, 22, 92, 70], 180, 360, fill=hexc("#c23b2e"))
    for (x, y) in [(50, 36), (64, 30), (76, 38)]:
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=hexc("#f0e6d8"))
    return img.resize((128, 128))


# ----------------------------------------------------------------------------- models

def face(texture, uv=None, cull=None):
    f = {"texture": texture}
    if uv is not None:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, texture, uv=None, emissive=False, cull_all=False):
    faces = {}
    for name in ("north", "east", "south", "west", "up", "down"):
        faces[name] = face(texture, uv, name if cull_all else None)
    element = {"from": frm, "to": to, "faces": faces}
    if emissive:
        element["shade"] = False
        element["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return element


def generator_model(active):
    chamber = "#chamber"
    elements = [
        {
            "from": [0, 0, 0], "to": [16, 16, 16],
            "faces": {
                "north": face("#front", [0, 0, 16, 16], "north"),
                "east": face("#side", [0, 0, 16, 16], "east"),
                "south": face("#side", [0, 0, 16, 16], "south"),
                "west": face("#side", [0, 0, 16, 16], "west"),
                "up": face("#top", [0, 0, 16, 16], "up"),
                "down": face("#bottom", [0, 0, 16, 16], "down"),
            },
        },
        # Energy chamber window on the front
        box([3, 3, -0.5], [13, 13, 0], chamber, [3, 3, 13, 13], emissive=active),
        # Red mushroom growing on the roof
        box([3, 16, 3], [5, 19, 5], "#stem", [6, 6, 8, 9]),
        box([1.5, 19, 1.5], [6.5, 21, 6.5], "#cap_red", [2, 2, 7, 7]),
        # Brown mushroom on the roof
        box([11, 16, 10], [12, 18, 11], "#stem", [7, 7, 8, 9]),
        box([9.5, 18, 8.5], [13.5, 19.5, 12.5], "#cap_brown", [4, 4, 8, 8]),
        # Bracket fungi on the casing sides
        box([16, 4, 9], [17, 5, 14], "#cap_brown", [4, 4, 9, 5]),
        box([-1, 10, 2], [0, 11, 6], "#cap_red", [4, 4, 8, 5]),
        box([5, 2, 16], [9, 3, 17], "#cap_brown", [3, 3, 7, 4]),
    ]
    model = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": "mycelialpower:block/generator_side",
            "front": "mycelialpower:block/generator_front",
            "side": "mycelialpower:block/generator_side",
            "top": "mycelialpower:block/generator_top",
            "bottom": "mycelialpower:block/generator_bottom",
            "chamber": "mycelialpower:block/generator_chamber_on" if active else "mycelialpower:block/generator_chamber_off",
            "stem": "minecraft:block/mushroom_stem",
            "cap_red": "minecraft:block/red_mushroom_block",
            "cap_brown": "minecraft:block/brown_mushroom_block",
        },
        "elements": elements,
    }
    return model


def blockstate():
    variants = {}
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in rotations.items():
        for active in (False, True):
            key = f"active={'true' if active else 'false'},facing={facing}"
            entry = {"model": "mycelialpower:block/mycelial_generator" + ("_on" if active else "")}
            if y:
                entry["y"] = y
            variants[key] = entry
    return {"variants": variants}


# ----------------------------------------------------------------------------- NBT (GameTest structure)

def nbt_string(s):
    data = s.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def nbt_named(tag_type, name, payload):
    return struct.pack(">b", tag_type) + nbt_string(name) + payload


def nbt_int(v):
    return struct.pack(">i", v)


def nbt_list(elem_type, payloads):
    return struct.pack(">bi", elem_type, len(payloads)) + b"".join(payloads)


def nbt_compound(entries):
    return b"".join(entries) + b"\x00"


def empty_structure(size):
    palette_entry = nbt_compound([nbt_named(8, "Name", nbt_string("minecraft:air"))])
    root = nbt_compound([
        nbt_named(3, "DataVersion", nbt_int(3955)),
        nbt_named(9, "size", nbt_list(3, [nbt_int(v) for v in size])),
        nbt_named(9, "palette", nbt_list(10, [palette_entry])),
        nbt_named(9, "blocks", nbt_list(10, [])),
        nbt_named(9, "entities", nbt_list(10, [])),
    ])
    return struct.pack(">b", 10) + nbt_string("") + root


def main():
    tex = os.path.join(ASSETS, "textures")
    save(side_texture(), tex, "block", "generator_side.png")
    save(top_texture(), tex, "block", "generator_top.png")
    save(bottom_texture(), tex, "block", "generator_bottom.png")
    save(front_texture(), tex, "block", "generator_front.png")
    save(chamber_off(), tex, "block", "generator_chamber_off.png")
    save(chamber_on(), tex, "block", "generator_chamber_on.png")
    write_json({"animation": {"frametime": 3, "interpolate": True}}, tex, "block", "generator_chamber_on.png.mcmeta")
    save(gui_texture(), tex, "gui", "mycelial_generator.png")
    save(jei_texture(), tex, "gui", "jei_mycelial_generator.png")
    save(logo(), ROOT, "src", "main", "resources", "logo.png")

    write_json(generator_model(False), ASSETS, "models", "block", "mycelial_generator.json")
    write_json(generator_model(True), ASSETS, "models", "block", "mycelial_generator_on.json")
    write_json({"parent": "mycelialpower:block/mycelial_generator"}, ASSETS, "models", "item", "mycelial_generator.json")
    write_json(blockstate(), ASSETS, "blockstates", "mycelial_generator.json")

    path = os.path.join(DATA, "structure", "empty.nbt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(empty_structure([8, 5, 8]))
    print("Assets generated.")


if __name__ == "__main__":
    main()
