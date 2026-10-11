"""GUI, JEI background and logo textures."""
from PIL import Image, ImageDraw

from .palette import GREEN, MYCELIUM, MYCELIUM_LIGHT, hexc, lerp, rng


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
