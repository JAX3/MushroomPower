# Port to Minecraft 26.1.2 / NeoForge 26.1.2

This branch (`port/minecraft-26.1.2`) is the Mycelial Power 1.21.1 mod ported to **Minecraft 26.1.2** with **NeoForge 26.1.2.126**. The 1.21.1 version stays on `claude/mycelial-power-generator-wipojt`.

> **Status: builds and runs.** The port was written against NeoForge's 26.1.2 source code, the official 26.1.2 MDK, JEI's 26.1 API source and NeoForge's porting guides for every version from 1.21.2 to 26.1. The first local build had one compile error (`SoundEvents.ITEM_BREAK` is now a `Holder`), which is fixed. The game now starts with the mod. The GameTests and the manual checks in `TESTING.md` have not been confirmed on this branch yet.

## Versions

| | 1.21.1 branch | This branch |
|---|---|---|
| Minecraft | 1.21.1 | 26.1.2 |
| NeoForge | 21.1.172 | 26.1.2.126 |
| Java | 21 | **25** |
| Gradle | 8.14.3 | 9.2.1 |
| ModDevGradle | 2.0.141 | 2.0.148 |
| Mappings | Parchment | none (Minecraft 26.1 ships unobfuscated) |
| JEI | 19.21.0.247 | 29.37.0.100 (any 29.x for 26.1.2) |
| Mekanism | 10.7.14 (dev only) | **removed**: no Mekanism release exists for 26.1.2 yet |
| Mod version | 1.1.0 | 2.0.0 |

## What changed

**Build**
- Java 25 toolchain, Gradle 9.2.1, ModDevGradle 2.0.148, NeoForge 26.1.2.126 (all from the official NeoForge 26.1.2 MDK).
- Parchment removed. JUnit is now aligned through its BOM (Gradle 9 needs the launcher declared).
- JEI uses the MDK's `localRuntime` configuration. Mekanism is removed.

**Renames across the code**
- `ResourceLocation` → `Identifier`, `ResourceKey#location()` → `identifier()` (1.21.11).
- `Level#isClientSide` field → `isClientSide()`, `level.random` → `getRandom()`.
- Registry lookups: `get(id)` → `getValue(id)`, `getTag` → `get(TagKey)`, `registryOrThrow` → `lookupOrThrow`, `getHolder` → `get` (1.21.2).
- `BlockState#getBlockHolder()` → `typeHolder()` (26.1).
- `getMaxBuildHeight()` → `getMaxY()`. `DustParticleOptions` now takes an RGB `int`.
- `Player#displayClientMessage(msg, true)` → `sendOverlayMessage(msg)` (26.1).

**Registration**
- Blocks and items register with `registerBlock` / `registerItem`, so their properties carry the registry id (required since 1.21.2).
- `BlockEntityType.Builder` → NeoForge's `new BlockEntityType<>(factory, block)`.

**Block and block entity**
- `useItemOn` / `useWithoutItem` / `Item#use` return the unified `InteractionResult` (`ItemInteractionResult` and `InteractionResultHolder` are gone).
- `DirectionProperty` → `EnumProperty<Direction>`.
- `onRemove` was split: the block entity drops its inventory and removes its wrench links in `preRemoveSideEffects`, and the block updates neighbours in `affectNeighborsAfterRemoval` (1.21.5).
- `getAnalogOutputSignal` takes the direction (1.21.9).
- Save and load use `ValueOutput` / `ValueInput` instead of `CompoundTag` (1.21.6). **Old 1.21.1 worlds are not supported by this branch**, since Minecraft does not support upgrading modded worlds across this many versions anyway.

**NeoForge transfer API (the biggest change)**
- `IItemHandler`, `IFluidHandler` and `IEnergyStorage` capabilities were replaced by `ResourceHandler<ItemResource>`, `ResourceHandler<FluidResource>` and `EnergyHandler`, where every change happens inside a `Transaction`.
- The energy buffer is a transactional `EnergyHandler`, still extract-only from outside.
- The water tank is a `FluidStacksResourceHandler`, and the inventory is an `ItemStacksResourceHandler`, with menu slots as `ResourceHandlerSlot`.
- Pushing energy to cables uses `Capabilities.Energy.BLOCK` and a transaction. Bucket emptying uses `ItemAccess` plus `ResourceHandlerUtil.move`. Right-click filling uses the new `transfer.fluid.FluidUtil`.

**Items**
- New `appendHoverText(..., TooltipDisplay, Consumer<Component>, ...)` and `inventoryTick(ItemStack, ServerLevel, Entity, EquipmentSlot)` signatures.
- Item model definitions added under `assets/mycelialpower/items/` (required since 1.21.4).
- Recipe ingredients use the plain-string form (`"I": "minecraft:iron_ingot"`).

**Saved data**
- Wrench links use the codec-based `SavedDataType` and are stored in the server's global data storage (`data/mycelialpower/generator_links.dat`), as the 26.1 guide recommends for data that spans dimensions.

**GUI (26.1 rendering rewrite)**
- `GuiGraphics` → `GuiGraphicsExtractor`. `renderBg` → `extractBackground`, `renderLabels` → `extractLabels`, `renderTooltip` → `extractTooltip`, `drawString` → `text`. The pose is a 2D matrix stack, and `blit` takes a `RenderPipeline`.
- The background size is passed to the screen constructor.
- The water tank is now drawn as a blue gradient bar instead of the animated water texture, because 26.1 replaced the fluid texture lookup.

**JEI 29**
- `RecipeType` → `IRecipeType`. Categories have no `getBackground` (the panel is drawn in `draw`).
- Tooltips go through `getTooltip(ITooltipBuilder, ...)`, fluids through `add(fluid, amount)`, catalysts through `addRecipeCatalysts`.
- The client sends packets with `ClientPacketDistributor`.

**GameTests (1.21.5 rewrite)**
- `@GameTest` / `@GameTestHolder` are gone. The 15 tests are now registered functions (`ModGameTests`) plus JSON test instances in `data/mycelialpower/test_instance/`, with a clear-weather environment in `test_environment/default.json`.
- `GameTestHelper#assertTrue` takes a `Component`, and `getBlockEntity` takes a class.

**Models**
- The block model drops NeoForge's old `render_type` (26.1 detects cutout textures itself). The glowing window uses `light_emission` (vanilla) plus the updated `neoforge_data` format.

## Highest-risk areas (check these first)

These were written from documentation and source excerpts rather than compiled code:

1. **Screen** (`MycelialGeneratorScreen`): exact signatures of `extractBackground`, `extractTooltip` and `blit` in `GuiGraphicsExtractor`.
2. **GameTests**: the JSON test instance format, and `succeedWhen` / `runAfterDelay` still existing on `GameTestHelper`.
3. **Block model glow**: whether `light_emission` and `neoforge_data` are both accepted.
4. **Saved data**: `SavedDataType` constructor and `server.getDataStorage()`.
5. **Fake player**: the `GameProfile(UUID, String)` constructor used for protection checks.

## How to test

```bash
./gradlew build               # compile + unit tests (31)
./gradlew runGameTestServer   # 15 in-game tests
./gradlew runClient           # visual checks from TESTING.md
```
