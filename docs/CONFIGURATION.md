# Mycelial Power: configuration reference

Mycelial Power uses two NeoForge TOML files.

| File | Type | Location | Contents |
|---|---|---|---|
| `mycelialpower-server.toml` | **server** | `<world>/serverconfig/` | All gameplay and balancing values. Synced to every client on join, so the GUI and JEI always show the server's numbers. |
| `mycelialpower-client.toml` | client | `<instance>/config/` | Cosmetic effects only (particles, sound). |

To give every new world custom defaults, copy a finished `mycelialpower-server.toml` to `<instance>/defaultconfigs/`. A copy of the defaults is in [`mycelialpower-server.toml`](mycelialpower-server.toml).

## Reload behaviour

NeoForge watches the server file while the world runs and reloads it when it is saved. All gameplay values are read live, so **no restart is needed**:

| Change | When it applies |
|---|---|
| Energy, water, mycelium, performance and compatibility values | Next tick |
| `tankCapacity`, `maxEnergyStorage` | Next tick. Lowering them discards water or FE above the new limit. |
| `maxNetworkSize`, `mergeNetworks`, `scalingEnabled` | All networks are rebuilt on the next tick |
| `fuels`, `acceptedFluids`, `convertibleBlocks`, `resultBlock` | Next lookup (cache rebuilt). A mushroom that is already burning keeps the values it started with. |
| `activeLightLevel` | The next time each generator changes state |
| JEI display | **Rejoin the world.** JEI builds its recipe list when you join. |
| Client file | Immediately |

Invalid values are corrected by NeoForge to the default (with a warning in the log). Ranges are enforced by the spec, and the code clamps everything again, so a bad config can never cause negative energy, an invalid probability or overflow.

## `[general]`

| Key | Default | Range | Description |
|---|---|---|---|
| `requireWater` | `true` | | Generators only run while they hold enough water for the next consumption. If `false`, water is consumed when present but never required. |
| `pauseWhenEnergyFull` | `true` | | A full generator pauses and keeps its fuel progress. If `false` it keeps burning and the overflow is discarded. |
| `activeLightLevel` | `10` | 0–15 | Light emitted while running. |
| `allowBucketInteraction` | `true` | | Right-click with a bucket or fluid container to fill the tank. |

## `[energy]`

| Key | Default | Range | Description |
|---|---|---|---|
| `baseGeneration` | `40.0` | 0–1,000,000 | FE/t of one running generator, before multipliers. |
| `maxEnergyStorage` | `100000` | 1–2,147,483,647 | Internal buffer in FE. |
| `outputTransferRate` | `4000` | 0–2,147,483,647 | Max FE/t pushed to or pulled by neighbours. |
| `autoOutput` | `true` | | Push energy into adjacent receivers automatically. |
| `maxNetworkOutput` | `0.0` | 0–1e12 | Optional cap on a network's total FE/t (0 = none). Members are scaled down proportionally. |
| `rounding` | `ACCUMULATE` | `ACCUMULATE`, `NEAREST`, `FLOOR`, `CEILING` | How fractional FE/t becomes whole FE. `ACCUMULATE` carries the fraction, so long-run output equals the exact formula. |

## `[network]`

```
Network FE/t = baseGeneration × N × scalingMultiplier^(N − 1)
```

Each generator produces `baseGeneration × fuelEnergy × (scalingMultiplier + fuelScaling)^(N − 1)`. With identical fuel, the network total is exactly the formula above. With the defaults: 1 → 40, 2 → 96, 3 → 173, 4 → 276, 5 → 415, 10 → 2,064 FE/t.

| Key | Default | Range | Description |
|---|---|---|---|
| `scalingEnabled` | `true` | | If `false`, N is always 1. |
| `scalingMultiplier` | `1.2` | 0.1–10 | Exponential multiplier per extra generator. |
| `maxNetworkSize` | `64` | 1–1024 | Members per network. Extra touching generators form a separate network. |
| `countInactiveGenerators` | `false` | | If `true`, idle members also count towards N. |
| `mergeNetworks` | `true` | | If `false`, a new generator joins only the largest touching network and networks never merge. Membership is stored per generator, so it survives reloads. |
| `shareResources` | `false` | | Members share water (drink from neighbours' tanks) and energy (overflow goes into members with space). |

## `[fuel]`

`fuels` is a list of strings:

```
"<item id or #item tag>;burn=<ticks>;energy=<x>;water=<x>;mycelium=<x>;scaling=<x>"
```

| Field | Required | Default | Range | Meaning |
|---|---|---|---|---|
| id / `#tag` | yes | | | Item id, or `#` followed by an item tag. |
| `burn` | yes | | 1–1,000,000 | Ticks one item runs the generator. |
| `energy` | no | `1.0` | 0–1000 | Multiplier on `baseGeneration`. |
| `water` | no | `1.0` | 0–1000 | Multiplier on water use. |
| `mycelium` | no | `1.0` | 0–100 | Multiplier on the mycelium chance growth per miss. |
| `scaling` | no | `0.0` | 0–10 | Added to `scalingMultiplier` for this generator's own output. |

Defaults:

```toml
fuels = [
  "minecraft:brown_mushroom;burn=400;energy=1.0;water=1.0;mycelium=1.0;scaling=0.0",
  "minecraft:red_mushroom;burn=600;energy=1.25;water=1.0;mycelium=1.0;scaling=0.0"
]
```

Fuels from other mods need no code. For example:

```toml
fuels = [
  "minecraft:brown_mushroom;burn=400",
  "minecraft:red_mushroom;burn=600;energy=1.25",
  "minecraft:crimson_fungus;burn=800;energy=1.5;water=2.0",
  "minecraft:warped_fungus;burn=800;energy=1.5;mycelium=2.0",
  "#c:mushrooms;burn=400",
  "somemod:glowshroom;burn=1200;energy=2.0;scaling=0.05"
]
```

Exact item entries win over tag entries. If the same item appears twice, the first entry wins. Malformed entries are removed by NeoForge's validation and logged. Entries for items that are not installed are skipped with a warning.

## `[water]`

| Key | Default | Range | Description |
|---|---|---|---|
| `tankCapacity` | `8000` | 1000–1,000,000 | mB per generator. |
| `consumptionAmount` | `10` | 0–100,000 | mB used every `consumptionInterval` ticks (× fuel `water`). |
| `consumptionInterval` | `20` | 1–72,000 | Ticks between consumptions. |
| `scaleConsumptionWithNetwork` | `false` | | Multiply each generator's water use by `scalingMultiplier^(N−1)`. Without it, total network water use is still `amount × active generators`. |
| `acceptedFluids` | `["minecraft:water"]` | | Fluid ids or `#tags`. |
| `allowFluidExtraction` | `false` | | Allow pipes or buckets to drain the tank. |

## `[mycelium]`

While running, every `attemptInterval` ticks the generator collects every **eligible target** within `searchRadius`. An attempt is eligible only if at least one target exists. A target is a convertible block in a loaded chunk, with air above (if `requireAirAbove`) and enough light (if `requireLight`). Then:

* **Success** (`random < chance`, or the chance is 100%): convert up to `maxConversionsPerAttempt` (+ network bonus) random targets, then reset the chance if `resetChanceOnSuccess`.
* **Failure:** `chance += chanceIncrease × fuel.mycelium × networkFactor`, capped at `maxChance`.

With the defaults, attempt *k* has chance `1% + (k − 1) × 0.5%`: 1 → 1.0%, 2 → 1.5%, 10 → 5.5%, 20 → 10.5%, 100 → 50.5%, 198 → 99.5%, 199 → 100% (guaranteed).

| Key | Default | Range | Description |
|---|---|---|---|
| `enabled` | `true` | | Master switch. |
| `initialChance` | `0.01` | 0–1 | First and reset chance. |
| `chanceIncrease` | `0.005` | 0–1 | Added per unsuccessful eligible attempt. |
| `maxChance` | `1.0` | 0–1 | Cap. At 1.0 the next attempt always succeeds. |
| `attemptInterval` | `100` | 1–72,000 | Running ticks between attempts. |
| `searchRadius` | `4` | 0–8 | Cube radius on every axis (at most 17³ = 4,913 blocks per attempt). |
| `maxConversionsPerAttempt` | `1` | 1–64 | Blocks converted per success. |
| `onlyDirt` | `true` | | Convert only `minecraft:dirt`. If `false`, `convertibleBlocks` is used. |
| `convertibleBlocks` | dirt, coarse dirt, rooted dirt | | Block ids or `#tags` (used when `onlyDirt = false`). |
| `resultBlock` | `minecraft:mycelium` | | Block placed on success. |
| `requireAirAbove` | `true` | | Target must have air above it. |
| `requireLight` | `false` | | Target must have `minimumLightLevel` light above. |
| `minimumLightLevel` | `9` | 0–15 | See above. |
| `resetChanceOnSuccess` | `true` | | Reset after converting. |
| `resetChanceOnStop` | `false` | | Reset whenever the generator stops. |
| `shareChanceAcrossNetwork` | `false` | | One chance per network, raised by every member's misses. |
| `networkIncreasesGrowthRate` | `false` | | `networkFactor = 1 + growthBonusPerGenerator × (active − 1)`. |
| `growthBonusPerGenerator` | `0.1` | 0–10 | See above. |
| `networkExtraConversions` | `false` | | Extra conversions per success for large networks. |
| `generatorsPerExtraConversion` | `4` | 1–1024 | One extra per this many active members. |
| `maxExtraConversions` | `4` | 0–64 | Cap on extra conversions. |
| `trackConversions` | `true` | | Show "converted this session" in the GUI. |

The chance and the attempt timer are saved with each generator.

## `[performance]`

| Key | Default | Range | Description |
|---|---|---|---|
| `guiSyncInterval` | `5` | 1–100 | Ticks between GUI packets to viewers (only sent when something changed). |
| `containerProcessInterval` | `10` | 1–200 | Ticks between emptying the bucket slot. |
| `energyOutputInterval` | `1` | 1–20 | Ticks between pushes; each push sends up to `rate × interval`. |

## `[compatibility]`

| Key | Default | Description |
|---|---|---|
| `firePlaceEvents` | `true` | Each conversion fires `BlockEvent.EntityPlaceEvent` as a fake player. If a protection mod cancels it, the original block is restored. |
| `respectSpawnProtection` | `true` | No conversions inside spawn protection or outside the world border. |
| `fakePlayerName` | `[MycelialPower]` | Fake player name (max 16 characters) used for permission checks. |
| `jeiExampleNetworkSize` | `10` | Rows in the JEI scaling tooltip. |

## Client file (`mycelialpower-client.toml`)

| Key | Default | Range | Description |
|---|---|---|---|
| `effects.particles` | `true` | | Spore and energy particles. |
| `effects.particleDensity` | `2` | 0–8 | Particles per animation tick. |
| `effects.sounds` | `true` | | Machine hum. |
| `effects.soundVolume` | `0.35` | 0–1 | Hum volume. |
