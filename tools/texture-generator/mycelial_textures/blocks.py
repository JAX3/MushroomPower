"""16x16 block textures for the Mycelial Generator."""
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
