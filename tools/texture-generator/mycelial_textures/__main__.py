"""Command line entry point: python -m mycelial_textures [options]."""
import argparse
import json
import os
import shutil
import sys

from . import blocks, gui, items, models, palette, structure

MOD_ID = "mycelialpower"
DEFAULT_OUTPUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "output")
GROUPS = ("textures", "models", "structure")


def save_png(img, root, *parts):
    path = os.path.join(root, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def save_json(obj, root, *parts):
    path = os.path.join(root, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")
    return path


def generate_textures(out):
    """Generates every texture. The call order is fixed so a given seed is reproducible."""
    assets = os.path.join(out, "assets", MOD_ID)
    written = [
        save_png(blocks.side_texture(), assets, "textures", "block", "generator_side.png"),
        save_png(blocks.top_texture(), assets, "textures", "block", "generator_top.png"),
        save_png(blocks.bottom_texture(), assets, "textures", "block", "generator_bottom.png"),
        save_png(blocks.front_texture(), assets, "textures", "block", "generator_front.png"),
        save_png(blocks.chamber_off(), assets, "textures", "block", "generator_chamber_off.png"),
        save_png(blocks.chamber_on(), assets, "textures", "block", "generator_chamber_on.png"),
        save_json({"animation": {"frametime": 3, "interpolate": True}},
                  assets, "textures", "block", "generator_chamber_on.png.mcmeta"),
        save_png(gui.gui_texture(), assets, "textures", "gui", "mycelial_generator.png"),
        save_png(gui.jei_texture(), assets, "textures", "gui", "jei_mycelial_generator.png"),
        save_png(gui.logo(), out, "logo.png"),
        # New textures go after the existing ones so the seeded random order (and their bytes) stay the same.
        save_png(items.mycelium_wrench(), assets, "textures", "item", "mycelium_wrench.png"),
    ]
    return written


def generate_models(out):
    assets = os.path.join(out, "assets", MOD_ID)
    return [
        save_json(models.generator_model(False), assets, "models", "block", "mycelial_generator.json"),
        save_json(models.generator_model(True), assets, "models", "block", "mycelial_generator_on.json"),
        save_json({"parent": f"{MOD_ID}:block/mycelial_generator"}, assets, "models", "item", "mycelial_generator.json"),
        save_json({"parent": "minecraft:item/handheld", "textures": {"layer0": f"{MOD_ID}:item/mycelium_wrench"}},
                  assets, "models", "item", "mycelium_wrench.json"),
        save_json(models.blockstate(), assets, "blockstates", "mycelial_generator.json"),
    ]


def generate_structure(out):
    path = os.path.join(out, "data", MOD_ID, "structure", "empty.nbt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    structure.write_empty_structure(path)
    return [path]


def write_preview(out):
    """Contact sheet of the block textures (8x scale) plus the GUI, for quick visual review."""
    from PIL import Image

    tex = os.path.join(out, "assets", MOD_ID, "textures")
    names = ["generator_side", "generator_top", "generator_bottom", "generator_front",
             "generator_chamber_off", "generator_chamber_on"]
    sheet = Image.new("RGBA", (16 * len(names), 16), (0, 0, 0, 0))
    for i, name in enumerate(names):
        path = os.path.join(tex, "block", name + ".png")
        if os.path.exists(path):
            sheet.paste(Image.open(path).crop((0, 0, 16, 16)), (i * 16, 0))
    written = [save_png(sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST), out, "preview", "blocks.png")]
    wrench_path = os.path.join(tex, "item", "mycelium_wrench.png")
    if os.path.exists(wrench_path):
        wrench = Image.open(wrench_path)
        written.append(save_png(wrench.resize((wrench.width * 16, wrench.height * 16), Image.NEAREST), out, "preview", "mycelium_wrench.png"))
    gui_path = os.path.join(tex, "gui", "mycelial_generator.png")
    if os.path.exists(gui_path):
        img = Image.open(gui_path)
        written.append(save_png(img.resize((img.width * 3, img.height * 3), Image.NEAREST), out, "preview", "gui.png"))
    return written


def parse_args(argv):
    parser = argparse.ArgumentParser(
        prog="mycelial_textures",
        description="Generate Mycelial Power textures, models and the GameTest structure. "
                    "Writes to a separate output folder by default and never touches the mod unless --output points at it.")
    parser.add_argument("-o", "--output", default=DEFAULT_OUTPUT,
                        help="Output root (laid out like src/main/resources). Default: %(default)s")
    parser.add_argument("--only", choices=GROUPS, action="append",
                        help="Generate only this group (repeatable). Default: all groups.")
    parser.add_argument("--seed", type=int, default=1337,
                        help="Random seed. 1337 reproduces the textures shipped with the mod. Default: %(default)s")
    parser.add_argument("--preview", action="store_true", help="Also write enlarged previews to <output>/preview/.")
    parser.add_argument("--clean", action="store_true",
                        help="Delete the output folder first. Refused unless it is the default folder.")
    parser.add_argument("-q", "--quiet", action="store_true", help="Do not list written files.")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)
    out = os.path.abspath(args.output)

    if args.clean:
        if out != os.path.abspath(DEFAULT_OUTPUT):
            print("--clean only works on the default output folder, to avoid deleting real files.", file=sys.stderr)
            return 2
        shutil.rmtree(out, ignore_errors=True)

    palette.reseed(args.seed)
    groups = args.only or list(GROUPS)
    written = []
    if "textures" in groups:
        written += generate_textures(out)
    if "models" in groups:
        written += generate_models(out)
    if "structure" in groups:
        written += generate_structure(out)
    if args.preview and "textures" in groups:
        written += write_preview(out)

    if not args.quiet:
        for path in written:
            print(os.path.relpath(path, out))
    print(f"Generated {len(written)} files in {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
