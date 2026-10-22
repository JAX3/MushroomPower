"""Hand-placed 16x16 item textures."""
from PIL import Image

from .palette import hexc

# Mycelium Wrench: silver open-jaw head with a glowing purple gem, moss, a brown binding,
# a purple-glowing handle with pink mushrooms, and a brown ring at the end.
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


def mycelium_wrench():
    return from_grid(WRENCH, WRENCH_PALETTE)
