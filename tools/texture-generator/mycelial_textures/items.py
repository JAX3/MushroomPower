"""Hand-placed 16x16 item textures."""
from PIL import Image

from .palette import hexc

# Original (realistic) Mycelium Wrench, used with --style realistic.
WRENCH_PALETTE = {
    "K": hexc("#1d1626"),  # outline
    "s": hexc("#e3dde8"), "S": hexc("#aaa3b6"), "d": hexc("#6f6879"),  # silver light / mid / dark
    "P": hexc("#b45cff"), "p": hexc("#6d2bb0"), "L": hexc("#f4d8ff"),  # purple glow / dark / highlight
    "g": hexc("#7fb045"), "G": hexc("#4a7230"),  # moss
    "b": hexc("#9a6c3c"), "B": hexc("#5e3d20"), "w": hexc("#c9a066"),  # wood / leather
    "h": hexc("#4b3d5c"), "H": hexc("#74608f"),  # handle
    "m": hexc("#e06ccc"), "n": hexc("#ffb6ef"), "t": hexc("#efe4d4"),  # pink mushroom cap / spot / stem
}

WRENCH = [
    ".........KK...K.",
    "........KsSK.KsK",
    ".......KSsgK.KSK",
    ".......KSsSSKSSK",
    "......KSsPLSSgdK",
    "..KKK.KdSpPSdddK",
    ".KmnmKbBdSdGdKK.",
    "..KtKHbBKKKKK...",
    "...KHhgK.KKK....",
    "..KhPhK.KmnmK...",
    ".KHgK....KtK....",
    "KwwbK.....K.....",
    "Kw.bK...........",
    "KbBBK...........",
    ".KKK............",
    "................",
]


def from_grid(grid, palette):
    height, width = len(grid), len(grid[0])
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        assert len(row) == width, f"row {y} has {len(row)} pixels, expected {width}"
        for x, key in enumerate(row):
            if key != ".":
                img.putpixel((x, y), palette[key])
    return img


# Cartoon Mycelium Wrench (default): chunky silver open-jaw head with a purple gem, a wooden handle with a
# red mushroom sprouting from it, a bit of moss and a gold ring at the end.
WRENCH_CARTOON_PALETTE = {
    "O": hexc("#231c35"),  # outline
    "s": hexc("#ffffff"), "S": hexc("#c8cee2"), "d": hexc("#8a91ad"),  # silver highlight / base / shadow
    "P": hexc("#b25cff"), "p": hexc("#7031c9"),  # gem
    "W": hexc("#e0924e"), "w": hexc("#a65a2c"),  # wood / wood shadow
    "R": hexc("#ff4b4b"), "r": hexc("#c42a3a"), "X": hexc("#ffffff"), "t": hexc("#fff1d6"),  # cap / spots / stem
    "y": hexc("#ffc94a"), "Y": hexc("#c98a1e"),  # gold ring
    "G": hexc("#8ae25c"),  # moss
}

WRENCH_CARTOON = [
    ".........OO..OO.",
    "........OssO.OsO",
    ".......OssSO.OSO",
    ".OOOO..OsSSSOSSO",
    "ORXRRO.OsSPPSSdO",
    "ORRRXrOOSSPpSSdO",
    "OrrrrrOdSSSSddO.",
    ".OOtOOWwdOOOOO..",
    "..OtOWwO........",
    "..OtWwO.........",
    "..OWwGO.........",
    ".OyWwOO.........",
    "Oy.yO...........",
    "OyYYO...........",
    ".OOO............",
    "................",
]


def mycelium_wrench(style="cartoon"):
    if style == "realistic":
        return from_grid(WRENCH, WRENCH_PALETTE)
    return from_grid(WRENCH_CARTOON, WRENCH_CARTOON_PALETTE)
