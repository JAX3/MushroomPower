"""Colour helpers and the shared, seedable random source used by every generator."""
import random

#: Shared random source. Generators draw from it in a fixed order, so a given seed always
#: produces byte-identical images. Call reseed() before generating.
rng = random.Random(1337)


def reseed(seed):
    rng.seed(seed)


def hexc(value, alpha=255):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color[:3]) + (color[3],)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


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
