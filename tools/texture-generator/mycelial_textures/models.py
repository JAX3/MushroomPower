"""Block model, item model and blockstate JSON."""


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
