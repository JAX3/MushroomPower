# Mycelial Power

A NeoForge mod for **Minecraft 1.21.1** (Java 21) that adds one machine: the **Mycelial Generator**.

* Burns **mushrooms** and **water** to produce **Forge Energy (FE)**.
* Generators touching on any of their six faces form a **network** whose output grows **exponentially**:
  `Network FE/t = 40 × N × 1.2^(N − 1)` by default (1 → 40, 2 → 96, 3 → 173, 5 → 415, 10 → 2,064 FE/t).
* While running, it slowly turns nearby **dirt into mycelium**. Each failed attempt **raises the chance** until a conversion is guaranteed.
* Every number lives in a **server TOML config**, including mushroom fuels from other mods.
* **JEI integration** (optional): fuel category, water and energy values, a scaling tooltip, an info page and the "+" transfer button.

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x (built against 21.1.172) |
| Java | 21 |
| JEI (optional) | 19.x for 1.21.1 (built against 19.21.0.247) |

---

## Installation (players and servers)

1. Install NeoForge 21.1.x for Minecraft 1.21.1.
2. Put `mycelialpower-1.0.0.jar` in the `mods/` folder of the client **and** the server.
3. Optional: install JEI for 1.21.1. The mod runs normally without it.

## Usage

### Crafting

```
 I R I      I = Iron Ingot      R = Red Mushroom
 B C B      B = Brown Mushroom  C = Cauldron
 I X I      X = Redstone
```

The generator is mined with any pickaxe and drops itself. Its inventory is dropped when it is broken.

### Running a generator

1. Place it. The energy chamber faces you.
2. **Water:** right-click with a water bucket, put a bucket in the bucket slot (the empty bucket comes out below), or pipe water in.
3. **Fuel:** put red or brown mushrooms (or any configured fuel) in the top slot. Hoppers and pipes can insert from any side.
4. Energy is pushed into adjacent cables and machines (any block exposing NeoForge `IEnergyStorage`). Pipes can also pull it out.

| Fuel (default) | Burn time | Energy | FE/t alone | FE per item |
|---|---|---|---|---|
| Brown Mushroom | 400 ticks (20 s) | ×1.0 | 40 | 16,000 |
| Red Mushroom | 600 ticks (30 s) | ×1.25 | 50 | 30,000 |

A generator stops when it runs out of fuel or water. When its buffer is full it pauses and keeps its fuel progress (configurable).

### Networks

Generators connect automatically in all six directions. By default only **running** generators count towards `N`, so a network grows as more members are fed. Networks update instantly when generators are placed, broken, loaded or unloaded. Generators in unloaded chunks simply stop counting, and **no chunk is ever loaded by the mod**. Networks are capped at 64 members by default.

### Mycelium

Every 100 running ticks a generator tries to convert a dirt block with air above it, within 4 blocks, into mycelium. The first attempt has a 1% chance. Each failed attempt adds 0.5 percentage points, so attempt 100 has 50.5% and attempt 199 is guaranteed. After a success the chance resets. Attempts with no valid dirt nearby do not change the chance. Conversions fire a block place event so claim and protection mods can block them, and they respect spawn protection and the world border.

### GUI

* Bars: **energy** (left) and **water tank** (right). The orange box is the remaining burn time of the current mushroom.
* The info panel shows fuel and time left, water and consumption rate, stored, generated and extracted FE, network size, active count, multiplier, total FE/t and status, and the mycelium chance, increase, next-attempt progress and conversions this session.
* Hover over any bar or line for exact numbers and explanations. The lamp shows the machine state.
* All values are computed on the server and sent to the viewing player.

---

## Configuration

See **[docs/CONFIGURATION.md](docs/CONFIGURATION.md)** for every option, and [docs/mycelialpower-server.toml](docs/mycelialpower-server.toml) for the default file. Edits to `<world>/serverconfig/mycelialpower-server.toml` apply while the server runs. JEI refreshes when you rejoin.

Adding a modded fuel takes one line:

```toml
[fuel]
	fuels = ["minecraft:brown_mushroom;burn=400", "minecraft:red_mushroom;burn=600;energy=1.25", "#c:mushrooms;burn=400"]
```

---

## Building from source

Requires JDK 21. Gradle 8.14.3 is downloaded by the wrapper.

```bash
./gradlew build                # compiles, runs unit tests, outputs build/libs/mycelialpower-1.0.0.jar
./gradlew runClient            # dev client (with JEI)
./gradlew runServer            # dev dedicated server
./gradlew runGameTestServer    # runs the in-game GameTests headlessly, then exits
./gradlew test                 # unit tests for the formulas and parsers only
```

To launch the dev runs **without JEI**, set `use_jei_runtime=false` in `gradle.properties` (or pass `-Puse_jei_runtime=false`).

The build downloads from `maven.neoforged.net` (NeoForge, ModDevGradle tooling), Mojang's servers (Minecraft) and `maven.blamejared.com` (JEI).

### IntelliJ IDEA

1. **File → Open…** and select the project folder (the one with `build.gradle`). Choose **Open as Project** and trust it.
2. In **Settings → Build, Execution, Deployment → Build Tools → Gradle**, set *Gradle JVM* to a JDK 21. Leave *Build and run using* on **Gradle**.
3. Wait for the first Gradle sync. It decompiles Minecraft, which takes a few minutes. ModDevGradle generates the run configurations `Client`, `Server` and `GameTestServer`.
4. Run **Client** from the run-configuration dropdown. If the configurations do not appear, run the Gradle task `neoforge ide setup › ideSyncTask` (or just re-sync Gradle).

### Regenerating textures and models

Textures, block models, the blockstate and the GameTest structure come from `tools/generate_assets.py` (Python 3 + Pillow):

```bash
python3 tools/generate_assets.py
```

---

## Project layout

```
src/main/java/com/mycelialpower/
├── MycelialPower.java                 main mod entry point (registration, configs, events)
├── registry/                          blocks, items, block entity types, menus, sounds
├── block/MycelialGeneratorBlock       the block (facing/active/light states, interaction, particles)
├── blockentity/                       generator block entity (tick logic, persistence) + state enum
├── capability/                        energy buffer, water tank, inventory, sided wrappers, capability registration
├── fuel/                              FuelDefinition, FuelEntryParser (config format), FuelRegistry (item/tag resolution)
├── network/                           GeneratorNetworkManager (connected components), GeneratorNetwork, NetworkMath
├── mycelium/                          MyceliumChance (probability rules), MyceliumSpreadManager (search + protected conversion)
├── energy/EnergyAccumulator           configurable rounding / fractional FE carry
├── menu/                              container menu + GeneratorStatus (synced display data)
├── net/                               sync packet (server → client) and JEI fuel-transfer packet (client → server)
├── client/                            client entry point and the generator screen
├── compat/jei/                        JEI plugin, category, recipe model/factory, recipe transfer handler
├── config/                            ServerConfig (all gameplay), ClientConfig (effects), EnergyRounding
├── event/CommonEvents                 cache invalidation, world lifecycle
├── gametest/GeneratorGameTests        in-game automated tests
└── util/                              number formatting, config-driven id/tag matchers
```

### Design notes

* **Server authority.** All generation, consumption and conversion runs in the block entity's server ticker. Clients receive only the block state (facing, active, light) and, while the GUI is open, a `GeneratorStatus` packet when values change.
* **Networks.** `GeneratorNetworkManager` keeps an in-memory map of *loaded* generators per dimension. A change marks positions dirty. On the next query only the affected networks are rebuilt, with an iterative BFS capped at `maxNetworkSize`. Statistics refresh at most once per tick from the members' previous-tick state, so the result never depends on tick order and nothing recurses.
* **No duplication.** The energy buffer is extract-only to the outside world, so generators cannot feed each other or loop. Transfers use the simulate-then-execute pattern and clamp to what was actually accepted. Inventories drop exactly once in `onRemove`. A burning mushroom's stats are snapshotted, so config changes never retroactively change an item.
* **No overflow.** The formula is evaluated in `double`, saturates at finite limits and is clamped to `int` before touching FE. Config ranges are validated, then clamped again in code.
* **Data driven.** The GUI, JEI and gameplay all read the same `ServerConfig`, `FuelRegistry` and `NetworkMath`.

---

## Verification status

The development container this mod was written in **could not reach `maven.neoforged.net`, Mojang's servers or the JEI Maven** (blocked by its network policy). So the Minecraft-dependent code **has not been compiled or run yet**. Here is exactly what was and was not verified:

| Item | Status |
|---|---|
| Pure logic (network formula, overflow clamps, rounding, mycelium chance table, fuel parser, number formatting) | ✅ 25 JUnit tests compiled and passed with `javac` + JUnit 5 (`src/test/java`). |
| Default config file vs. spec keys | ✅ Checked by script. |
| Language keys used in code are all defined | ✅ Checked by script. |
| Gradle project compiles (`./gradlew build`) | ⚠️ Not verified. Run `./gradlew build` and fix any API mismatch the compiler reports. |
| In-game behaviour: single generator, 2- and 3-generator networks, network split, starvation, mycelium conversion, save/load round trip, overflow guard | ⚠️ Written as GameTests in `GeneratorGameTests`. Run `./gradlew runGameTestServer`. |
| GUI layout and rendering, particles, sound, block model appearance | ⚠️ Not verified. Needs a visual check in `runClient`. |
| JEI category, info page, recipe transfer, mod runs without JEI | ⚠️ Not verified. Check with `runClient` both with and without JEI (`-Puse_jei_runtime=false`). |
| Energy output into third-party cables or machines; multiplayer sync on a dedicated server | ⚠️ Not verified. Uses the standard NeoForge capability and payload APIs. |

## License

MIT, see [LICENSE](LICENSE).
