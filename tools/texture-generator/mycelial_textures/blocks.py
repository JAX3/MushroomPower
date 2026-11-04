"""Cartoon-style 16x16 block textures for the Mycelial Generator (the default style).

Hand-placed pixel art: thick dark outlines, flat colours with one highlight and one shadow tone, chunky
bolts, a glowing port, bright moss and a bold red mushroom in the front window. Each texture is a grid of
characters; edit the grids directly. '.' is transparent (or the window area on the front).
"""
import math

from PIL import Image

from .palette import hexc, lerp

PALETTE = {
    "O": hexc("#231c35"),  # outline
    "M": hexc("#5d6687"), "H": hexc("#8d97bc"), "S": hexc("#3f4766"),  # metal base / highlight / shadow
    "B": hexc("#dfe4f5"), "b": hexc("#9aa3c4"),  # bolt highlight / bolt
    "P": hexc("#9a52f0"), "p": hexc("#5c2aa6"),  # port ring / ring shadow
    "G": hexc("#ffd2fb"), "g": hexc("#ff6ae6"),  # port glow centre / glow
    "L": hexc("#a8ee6c"), "l": hexc("#62c23e"), "d": hexc("#2f7a2a"),  # moss light / moss / moss dark
    "m": hexc("#c9a8d6"), "n": hexc("#8c6aa0"),  # mycelium patch / patch shadow
}

SIDE = [
    "OOOOOOOOOOOOOOOO",
    "OHHHHHHHHHHHHHMO",
    "OHBbMMMMMMMMBbSO",
    "OHbSMMMMMMMMbSSO",
    "OHMMMMOOOOMMMMSO",
    "OHMMMOPPPPOMMMSO",
    "OHMMOPpggpPOMMSO",
    "OHMMOPgGGgPOMMSO",
    "OHMMOPgGGgPOMMSO",
    "OHMMOPpggpPOMMSO",
    "OHMMMOPPPPOMMMSO",
    "OHMlMMOOOOMMlMSO",
    "OHlLlMMMMMMlLlSO",
    "OlLLLlMlMMlLLLlO",
    "OdlLlldLldllLldO",
    "OOOOOOOOOOOOOOOO",
]

TOP = [
    "OOOOOOOOOOOOOOOO",
    "OHHHHHHHHHHHHHMO",
    "OHBbMMMMMMMmmbSO",
    "OHbSmmMMMMmmnSSO",
    "OHMmmnOOOOMnnMSO",
    "OHMMnOPPPPOMMMSO",
    "OHMMOPpggpPOMMSO",
    "OHMMOPgGGgPOmmSO",
    "OHmmOPgGGgPOmnSO",
    "OHmnOPpggpPOMMSO",
    "OHMMMOPPPPOMMMSO",
    "OHMMMMOOOOMMmmSO",
    "OHMmmMMMMMMMmnSO",
    "OHBbnMMMMMMMBbSO",
    "OHbSSSSSSSSSbSSO",
    "OOOOOOOOOOOOOOOO",
]

BOTTOM = [
    "OOOOOOOOOOOOOOOO",
    "OHHHHHHHHHHHHHMO",
    "OHBbMMMMMMMMBbSO",
    "OHbSMMMMMMMMbSSO",
    "OHMMMMOOOOMMMMSO",
    "OHMMMOPPPPOMMMSO",
    "OHMMOPppppPOMMSO",
    "OHMMOPpPPpPOMMSO",
    "OHMMOPpPPpPOMMSO",
    "OHMMOPppppPOMMSO",
    "OHMMMOPPPPOMMMSO",
    "OHMMMMOOOOMMMMSO",
    "OHMMMMMMMMMMMMSO",
    "OHBbMMMMMMMMBbSO",
    "OHbSSSSSSSSSbSSO",
    "OOOOOOOOOOOOOOOO",
]

# The model's window element covers pixels 3..12, so only the purple rim around it shows on the front.
FRONT = [
    "OOOOOOOOOOOOOOOO",
    "OHHHHHHHHHHHHHMO",
    "OHPPPPPPPPPPPPSO",
    "OHPOOOOOOOOOOpSO",
    "OHPO........OpSO",
    "OHPO........OpSO",
    "OHPO........OpSO",
    "OHPO........OpSO",
    "OHPO........OpSO",
    "OHPO........OpSO",
    "OHPO........OpSO",
    "OHPO........OpSO",
    "OHPOOOOOOOOOOpSO",
    "OlppppppppppppdO",
    "OdlLlldLldllLldO",
    "OOOOOOOOOOOOOOOO",
]

# Red mushroom in the front window (a 10x10 grid drawn at pixel 3,3, the part of the texture the model shows).
MUSHROOM = [
    "..OOOOOO..",
    ".ORRXRRrO.",
    "ORXXRRRRrO",
    "ORRRRRXRrO",
    "ORRXRRRRrO",
    "OOOOOOOOOO",
    "...OSSO...",
    "...OSsO...",
    "..OSSSsO..",
    "..OOOOOO..",
]
WINDOW_ORIGIN = 3

MUSHROOM_IDLE = {
    "O": hexc("#1a1220"), "R": hexc("#7d3a44"), "r": hexc("#5e2a33"), "X": hexc("#b9aeb4"),
    "S": hexc("#9c9284"), "s": hexc("#776e64"),
}
MUSHROOM_LIT = {
    "O": hexc("#5a1020"), "R": hexc("#ff4b4b"), "r": hexc("#d42a3a"), "X": hexc("#ffffff"),
    "S": hexc("#fff1d6"), "s": hexc("#e8cfa6"),
}


def from_grid(rows, palette, top=0, left=0, img=None):
    width = len(rows[0])
    if img is None:
        img = Image.new("RGBA", (width, len(rows)), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        assert len(row) == width, f"row {y} has {len(row)} pixels, expected {width}"
        for x, key in enumerate(row):
            if key != ".":
                px[left + x, top + y] = palette[key]
    return img


def side_texture():
    return from_grid(SIDE, PALETTE)


def top_texture():
    return from_grid(TOP, PALETTE)


def bottom_texture():
    return from_grid(BOTTOM, PALETTE)


def front_texture():
    return from_grid(FRONT, PALETTE)


def _frame(img, top, colour):
    px = img.load()
    for i in range(16):
        px[i, top] = px[0, top + i] = px[i, top + 15] = px[15, top + i] = colour


def chamber_off():
    """Idle window: an unlit mushroom on dark glass, with a small glass glint."""
    img = Image.new("RGBA", (16, 16), hexc("#1d1626"))
    from_grid(MUSHROOM, MUSHROOM_IDLE, WINDOW_ORIGIN, WINDOW_ORIGIN, img)
    px = img.load()
    px[4, 4] = px[5, 4] = px[4, 5] = hexc("#4a3d5c")
    _frame(img, 0, hexc("#3a2d4a"))
    return img


SPARKLES = [(3, 3), (12, 4), (4, 11), (11, 11)]


def chamber_on(frames=8):
    """Running window: the mushroom glows on a pulsing pink halo, with sparkles twinkling in turn."""
    img = Image.new("RGBA", (16, 16 * frames))
    px = img.load()
    inner, outer = hexc("#ffd6de"), hexc("#ff7a92")
    for f in range(frames):
        pulse = 0.5 + 0.5 * math.sin(f / frames * 2 * math.pi)
        top = f * 16
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 6.0) / (7.0 + pulse)
                px[x, top + y] = lerp(inner, outer, min(1.0, r))
        from_grid(MUSHROOM, MUSHROOM_LIT, top + WINDOW_ORIGIN, WINDOW_ORIGIN, img)
        # One sparkle at a time, as a little white plus sign.
        sx, sy = SPARKLES[(f // 2) % len(SPARKLES)]
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 < sx + dx < 15 and 0 < sy + dy < 15:
                px[sx + dx, top + sy + dy] = hexc("#ffffff") if (dx, dy) == (0, 0) else hexc("#fff3a8")
        _frame(img, top, hexc("#ffeef2"))
    return img
