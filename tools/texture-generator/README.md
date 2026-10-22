# Mycelial Power texture generator

A standalone Python tool that procedurally generates the Mycelial Power art: block textures, the animated energy chamber, GUI and JEI backgrounds, the mod logo, block and item models, the blockstate and the empty GameTest structure.

It is **independent of the mod build**. Gradle never runs it, and by default it writes only to its own `output/` folder (git-ignored), so running it cannot change the mod. You decide when to copy results into the mod.

## Setup

Requires Python 3.9+ and Pillow.

```bash
cd tools/texture-generator
python3 -m venv .venv            # optional
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

## Usage

```bash
python generate.py                      # everything -> ./output
python generate.py --preview            # also enlarged previews in ./output/preview/
python generate.py --only textures      # just textures (--only can be repeated)
python generate.py --seed 42            # different random detail (rivets, fungus, mycelium specks)
python generate.py --clean              # wipe ./output first
python generate.py -o some/other/dir    # write somewhere else
python -m mycelial_textures --help      # same tool, run as a module
```

The output folder is laid out exactly like `src/main/resources`:

```
output/
├── assets/mycelialpower/
│   ├── blockstates/mycelial_generator.json
│   ├── models/block/mycelial_generator.json, mycelial_generator_on.json
│   ├── models/item/mycelial_generator.json
│   ├── models/item/mycelium_wrench.json
│   └── textures/block/*.png (+ .mcmeta), textures/gui/*.png, textures/item/mycelium_wrench.png
├── data/mycelialpower/structure/empty.nbt
├── logo.png
└── preview/blocks.png, gui.png, mycelium_wrench.png   (only with --preview)
```

## Putting results into the mod

Review the output (`--preview` helps), then either copy the files you want into `src/main/resources/`, or write straight into the mod on purpose:

```bash
python generate.py -o ../../src/main/resources --only textures
```

With the default seed (`1337`) the tool reproduces the files that ship with the mod byte for byte, so re-running it on unchanged code is harmless.

`--clean` refuses to delete anything except the default `output/` folder.

## Layout

| Module | Contents |
|---|---|
| `mycelial_textures/palette.py` | Colour helpers, shared colours, the seeded random source |
| `mycelial_textures/blocks.py` | 16×16 block textures and the 8-frame animated chamber |
| `mycelial_textures/gui.py` | Machine GUI (256×256), JEI background, logo |
| `mycelial_textures/items.py` | Hand-placed item icons (Mycelium Wrench), editable as a character grid |
| `mycelial_textures/models.py` | Block/item models (with mushroom and fungus elements) and the blockstate |
| `mycelial_textures/structure.py` | Tiny NBT writer for the GameTest structure |
| `mycelial_textures/__main__.py` | Command line interface |
| `generate.py` | Launcher (`python generate.py` = `python -m mycelial_textures`) |

Generators draw from the shared random source in a fixed order. If you add a new texture and want existing ones to stay identical, generate it **after** the existing ones in `generate_textures()`.

**Keep in sync with the mod:** the GUI texture positions match constants in `MycelialGeneratorScreen` and `MycelialGeneratorMenu`, and the JEI background matches `MycelialGeneratorCategory`. Change both sides together.
