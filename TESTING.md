# Testing Mycelial Power

There are three layers of tests. Run them in this order after any change.

## 1. Unit tests (no Minecraft needed)

```bash
./gradlew test
```

These cover the pure logic: the network formula and its overflow guards, energy rounding, the mycelium chance table, the fuel config parser, number formatting, and the Mycelium Wrench link rules (distance, dimension rules, link limits, durability). That's 31 tests in `src/test/java`.

## 2. Automated in-game tests (GameTests)

```bash
./gradlew runGameTestServer
```

This starts a headless server, runs every `@GameTest` in `GeneratorGameTests`, then exits. A non-zero exit code means a failure; the log names the failing test. The tests assume the **default server config**.

| GameTest | Checks |
|---|---|
| `singleGeneratorProducesEnergy` | A generator burns one mushroom, uses water and produces 50 FE/t on red mushrooms |
| `adjacentGeneratorsFormOneNetwork` | Touching generators share a network in every direction; a detached one doesn't |
| `threeGeneratorsScaleExponentially` | 3 running generators give 57.6 FE/t each (172.8 total) |
| `networkSplitsWhenGeneratorRemoved` | Breaking the middle of a line splits the network |
| `generatorWithoutWaterStops` | No water: state NO_WATER, no FE, the mushroom is kept |
| `myceliumConvertsDirt` | A 100% chance converts dirt with air above into mycelium |
| `stateSurvivesSaveAndLoad` | Energy, burn progress, water, chance and inventory survive a save/load |
| `hugeMultiplierDoesNotOverflow` | Extreme values stay within int range |
| `manualLinkJoinsDistantGenerators` | Two generators 5 blocks apart, linked by wrench: one network, 48 FE/t each, connection MANUAL |
| `unlinkingSplitsNetwork` | Removing a generator's links splits a link-only network |
| `destroyedGeneratorLosesLinks` | Breaking a linked generator removes the link from its partner too |
| `duplicateLinksRejectedAndChainsMerge` | Reverse duplicates and self links are refused; A–B plus B–C makes one network of 3 |
| `physicalAndManualConnectionsCombine` | Touching plus linked generators form one network; connection types are PHYSICAL, BOTH and MANUAL |
| `linkDataSurvivesSaveAndLoad` | Saved link data, including a cross-dimension link, survives NBT save and load |

## 3. Manual checks in the dev client

Run `./gradlew runClient` and create a **creative, superflat** world with cheats on. Items are in the **Mycelial Power** creative tab.

### Generator basics
1. Place a generator, right-click it with a water bucket, and put red mushrooms in the top slot. The lamp turns green and the GUI shows **+50 FE/t**.
2. Connect a Mekanism cable or machine. Energy flows out, and the GUI's *out* value rises to match.
3. Place 3 touching generators, all fed. Each GUI shows **3 linked, 3 active**, **x1.44** and a total of about **173 FE/t** (on brown mushrooms; red ones run 1.25× higher).
4. Hover the orange burn box. You should see one clean tooltip. With JEI installed, clicking it opens the fuel list.

### Mycelium Wrench
| # | Action | Expected |
|---|---|---|
| W1 | Right-click generator A with the wrench | Action bar: *Selected generator at …*. A has a purple outline while you hold the wrench, and the wrench glints. Its tooltip shows the selected position. |
| W2 | Right-click generator B, **20 blocks** away | Action bar: *Linked …*. Green particles run from A to B, with a chime. Both GUIs show *Manual, 1/8 links* and **2 linked**. The selection is cleared. |
| W3 | Feed both A and B | Each produces **48 FE/t** (40 × 1.2): they scale as one network. |
| W4 | Link A to B again | *These generators are already linked*. |
| W5 | Select A, then right-click A again | *That generator is already selected…* |
| W6 | Select A, right-click C **70 blocks** away | *Too far apart (maximum 64 blocks)*. |
| W7 | Place D touching B, then open B's GUI | B: *Physical + manual*. D: *Physical*. Network shows 3 linked. |
| W8 | Shift + right-click A | *Removed 1 manual link(s)*. A shows *Standalone*. B and D stay together. |
| W9 | Link A and B again, then break A | B shows *0/8 links*. Its network drops to the touching generators only. |
| W10 | Link 9 generators to one generator | The 9th link is refused: *…maximum of 8 links*. |
| W11 | Shift + right-click air while a generator is selected | *Selection cleared*. The glint disappears. |
| W12 | Set `wrench_durability = 3` in survival mode | A durability bar appears. After 3 links the wrench breaks with a message. |
| W13 | Set `allow_network_merging = false`. Build two separate pairs of touching generators, then link one generator from each pair | *…merging is disabled*. Linking a single, unconnected generator into a pair still works. |
| W14 | Set `enabled = false` | Wrench clicks show *disabled*. Existing links stop counting; GUIs show the networks split. Set it back to true and they rejoin. |

### Saving, reloading and unloaded chunks
| # | Action | Expected |
|---|---|---|
| S1 | Link A and B, then save and quit to the title screen, and reload | Links remain: *Manual, 1/8 links*, same network, same FE/t. |
| S2 | Link A to B **300+ blocks** away (raise `max_link_distance` first), then fly far enough from B that its chunk unloads | A shows the link but *0 loaded and active*, and drops back to single-generator output. Nothing loads B's chunk (check with F3: its chunk is not loaded). |
| S3 | Return to B | The network rejoins automatically, with no further clicks. |
| S4 | Stand near B with A's chunk unloaded, and run `/setblock` to replace B with stone | B's links are removed from both ends immediately, without loading A's chunk. When you return to A, it shows 0 links. |

### Multiplayer
1. Run `./gradlew runServer`, set `online-mode=false` in `run/server.properties`, and connect a client.
2. Repeat W1–W3 and S1. All messages and particles appear for the player who used the wrench, and nearby players see the particles too.
3. Open the same generator GUI on two clients. Both show the same link counts and FE/t.

### Without JEI
Run `./gradlew runClient -Puse_jei_runtime=false`. The game loads, and the generator and wrench work. Only JEI's recipe pages are missing.
