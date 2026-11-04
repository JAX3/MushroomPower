"""Realistic (noisy metal) 16x16 block textures: the original style, available with --style realistic."""
import math

from PIL import Image

from .palette import (GREEN, GREEN_LIGHT, METAL, METAL_DARK, METAL_LIGHT, MYCELIUM, MYCELIUM_LIGHT, PURPLE,
                      PURPLE_DARK, RIVET, hexc, lerp, rng, shade)


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


# Red mushroom shown in the front energy chamber. The model only shows texture pixels 3..12 on both axes,
# so the mushroom is drawn in that 10x10 window. Keys: R cap, r cap edge/shadow, W spot, U cap underside,
# S stem, s stem shadow.
CHAMBER_MUSHROOM = [
    "...rRRr...",
    ".rRRWRRRr.",
    "rRWWRRRWRr",
    "RRWRRRRRRR",
    "RRRRRWRRRR",
    "rRRRRRRRRr",
    ".UUUSSUUU.",
    "....SS....",
    "....Ss....",
    "...sSSs...",
]
CHAMBER_ORIGIN = 3

MUSHROOM_IDLE = {
    "R": hexc("#7a2a2a"), "r": hexc("#561d22"), "W": hexc("#9a8e8a"),
    "U": hexc("#3a2228"), "S": hexc("#8a8072"), "s": hexc("#655c55"),
}


def _draw_mushroom(px, palette, y_offset=0):
    for y, row in enumerate(CHAMBER_MUSHROOM):
        for x, key in enumerate(row):
            if key != ".":
                px[CHAMBER_ORIGIN + x, CHAMBER_ORIGIN + y + y_offset] = palette[key]


def chamber_off():
    """Idle window: dark glass with an unlit red mushroom behind it."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    # Background noise first: it keeps the seeded random sequence (and every later texture) unchanged.
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(hexc("#1c1418"), rng.randint(-5, 5))
    for i in range(16):
        px[i, 0] = px[0, i] = hexc("#4a3a5e")
        px[i, 15] = px[15, i] = hexc("#140c1e")
    _draw_mushroom(px, MUSHROOM_IDLE)
    for i in range(4, 7):  # glass reflection across the top-left of the window
        px[i, 9 - i] = hexc("#5a4a58")
    return img


def chamber_on(frames=8):
    """Running window: the red mushroom glows and pulses, with a warm halo behind it (rendered full-bright)."""
    img = Image.new("RGBA", (16, 16 * frames))
    px = img.load()
    halo_dark, halo_bright = hexc("#2a0c12"), hexc("#b8324a")
    for f in range(frames):
        pulse = 0.5 + 0.5 * math.sin(f / frames * 2 * math.pi)  # 0..1
        top = f * 16
        for y in range(16):
            for x in range(16):
                # Halo centred on the cap
                r = math.hypot(x - 7.5, y - 6.5)
                glow = max(0.0, 1.0 - r / (6.0 + 1.5 * pulse))
                px[x, top + y] = lerp(halo_dark, halo_bright, glow * (0.55 + 0.35 * pulse))
        palette = {
            "R": lerp(hexc("#e8323a"), hexc("#ff6a5a"), pulse),
            "r": lerp(hexc("#b01e2c"), hexc("#e0404a"), pulse),
            "W": lerp(hexc("#ffe8e0"), hexc("#ffffff"), pulse),
            "U": lerp(hexc("#7a2030"), hexc("#a0384a"), pulse),
            "S": lerp(hexc("#f0e2c8"), hexc("#fff4dc"), pulse),
            "s": lerp(hexc("#c8b496"), hexc("#e8d6b8"), pulse),
        }
        for y, row in enumerate(CHAMBER_MUSHROOM):
            for x, key in enumerate(row):
                if key != ".":
                    px[CHAMBER_ORIGIN + x, top + CHAMBER_ORIGIN + y] = palette[key]
        # Spores drifting up from the cap
        for i, (sx, sy) in enumerate([(4, 2), (11, 3), (7, 1), (12, 1)]):
            yy = (sy - f + i * 2) % 4
            if (f + i) % 2 == 0:
                px[sx, top + yy] = lerp(hexc("#ffb0c0"), hexc("#ffffff"), pulse)
        for i in range(16):
            px[i, top] = px[0, top + i] = hexc("#8a3a4a")
            px[i, top + 15] = px[15, top + i] = hexc("#3a1018")
    return img
