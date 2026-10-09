package com.mycelialpower.config;

import com.mycelialpower.fuel.FuelEntryParser;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Server-side (per world, synced to clients) configuration. Every gameplay and balancing value of the
 * mod lives here. The generated file is {@code <world>/serverconfig/mycelialpower-server.toml}; a copy
 * placed in {@code defaultconfigs/} is used for new worlds.
 * <p>
 * NeoForge watches the file and reloads it while the server runs. All values are read live each tick,
 * so edits apply without a restart. The only exception is JEI, which shows the values received when the
 * player joined; rejoin the world to refresh JEI.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;

    // [general]
    public static final ModConfigSpec.BooleanValue REQUIRE_WATER;
    public static final ModConfigSpec.BooleanValue PAUSE_WHEN_ENERGY_FULL;
    public static final ModConfigSpec.IntValue ACTIVE_LIGHT_LEVEL;
    public static final ModConfigSpec.BooleanValue ALLOW_BUCKET_INTERACTION;

    // [energy]
    public static final ModConfigSpec.DoubleValue BASE_GENERATION;
    public static final ModConfigSpec.IntValue MAX_ENERGY_STORAGE;
    public static final ModConfigSpec.IntValue OUTPUT_TRANSFER_RATE;
    public static final ModConfigSpec.BooleanValue AUTO_OUTPUT;
    public static final ModConfigSpec.DoubleValue MAX_NETWORK_OUTPUT;
    public static final ModConfigSpec.EnumValue<EnergyRounding> ROUNDING;

    // [network]
    public static final ModConfigSpec.BooleanValue SCALING_ENABLED;
    public static final ModConfigSpec.DoubleValue SCALING_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_NETWORK_SIZE;
    public static final ModConfigSpec.BooleanValue COUNT_INACTIVE;
    public static final ModConfigSpec.BooleanValue MERGE_NETWORKS;
    public static final ModConfigSpec.BooleanValue SHARE_RESOURCES;

    // [fuel]
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FUELS;

    // [water]
    public static final ModConfigSpec.IntValue TANK_CAPACITY;
    public static final ModConfigSpec.IntValue WATER_PER_INTERVAL;
    public static final ModConfigSpec.IntValue WATER_INTERVAL;
    public static final ModConfigSpec.BooleanValue WATER_SCALES_WITH_NETWORK;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ACCEPTED_FLUIDS;
    public static final ModConfigSpec.BooleanValue ALLOW_FLUID_EXTRACTION;

    // [mycelium]
    public static final ModConfigSpec.BooleanValue MYCELIUM_ENABLED;
    public static final ModConfigSpec.DoubleValue INITIAL_CHANCE;
    public static final ModConfigSpec.DoubleValue CHANCE_INCREASE;
    public static final ModConfigSpec.DoubleValue MAX_CHANCE;
    public static final ModConfigSpec.IntValue ATTEMPT_INTERVAL;
    public static final ModConfigSpec.IntValue SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue MAX_CONVERSIONS;
    public static final ModConfigSpec.BooleanValue ONLY_DIRT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CONVERTIBLE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<String> RESULT_BLOCK;
    public static final ModConfigSpec.BooleanValue REQUIRE_AIR_ABOVE;
    public static final ModConfigSpec.BooleanValue REQUIRE_LIGHT;
    public static final ModConfigSpec.IntValue MIN_LIGHT;
    public static final ModConfigSpec.BooleanValue RESET_ON_SUCCESS;
    public static final ModConfigSpec.BooleanValue RESET_ON_STOP;
    public static final ModConfigSpec.BooleanValue SHARE_CHANCE;
    public static final ModConfigSpec.BooleanValue NETWORK_GROWTH_BONUS;
    public static final ModConfigSpec.DoubleValue GROWTH_BONUS_PER_GENERATOR;
    public static final ModConfigSpec.BooleanValue NETWORK_EXTRA_CONVERSIONS;
    public static final ModConfigSpec.IntValue GENERATORS_PER_EXTRA;
    public static final ModConfigSpec.IntValue MAX_EXTRA_CONVERSIONS;
    public static final ModConfigSpec.BooleanValue TRACK_CONVERSIONS;

    // [performance]
    public static final ModConfigSpec.IntValue GUI_SYNC_INTERVAL;
    public static final ModConfigSpec.IntValue CONTAINER_INTERVAL;
    public static final ModConfigSpec.IntValue OUTPUT_INTERVAL;

    // [compatibility]
    public static final ModConfigSpec.BooleanValue FIRE_PLACE_EVENTS;
    public static final ModConfigSpec.BooleanValue RESPECT_SPAWN_PROTECTION;
    public static final ModConfigSpec.ConfigValue<String> FAKE_PLAYER_NAME;
    public static final ModConfigSpec.IntValue JEI_EXAMPLE_NETWORK_SIZE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("General machine behaviour.").push("general");
        REQUIRE_WATER = b.comment("If true, a generator only runs while its tank holds enough water for its next consumption.",
                        "If false, water is still consumed when present but its absence never stops the generator.")
                .define("requireWater", true);
        PAUSE_WHEN_ENERGY_FULL = b.comment("If true, a generator whose energy buffer is full pauses: it keeps its fuel progress and",
                        "consumes no mushrooms or water until energy is extracted. If false, it keeps burning and the",
                        "energy that does not fit is discarded.")
                .define("pauseWhenEnergyFull", true);
        ACTIVE_LIGHT_LEVEL = b.comment("Light level emitted by a running generator (0 disables light).")
                .defineInRange("activeLightLevel", 10, 0, 15);
        ALLOW_BUCKET_INTERACTION = b.comment("If true, right-clicking the generator with a water bucket or other fluid container fills its tank.")
                .define("allowBucketInteraction", true);
        b.pop();

        b.comment("Energy generation, storage and output.").push("energy");
        BASE_GENERATION = b.comment("Base FE/t produced by one running generator before fuel and network multipliers.")
                .defineInRange("baseGeneration", 40.0D, 0.0D, 1_000_000.0D);
        MAX_ENERGY_STORAGE = b.comment("Internal energy buffer of each generator, in FE.")
                .defineInRange("maxEnergyStorage", 100_000, 1, Integer.MAX_VALUE);
        OUTPUT_TRANSFER_RATE = b.comment("Maximum FE per tick each generator pushes to (or lets be pulled by) neighbouring blocks.")
                .defineInRange("outputTransferRate", 4_000, 0, Integer.MAX_VALUE);
        AUTO_OUTPUT = b.comment("If true, generators actively push energy into adjacent energy receivers (cables, machines).",
                        "If false, energy must be pulled out by the neighbouring block.")
                .define("autoOutput", true);
        MAX_NETWORK_OUTPUT = b.comment("Optional maximum total FE/t of one generator network. 0 disables the cap.",
                        "When the formula exceeds the cap, every member's output is scaled down proportionally.")
                .defineInRange("maxNetworkOutput", 0.0D, 0.0D, 1.0E12D);
        ROUNDING = b.comment("How fractional FE/t values become whole FE each tick:",
                        "ACCUMULATE = carry fractions between ticks (exact long-run output),",
                        "NEAREST = round each tick, FLOOR = round down, CEILING = round up.",
                        "Displayed network totals use NEAREST for ACCUMULATE and the chosen mode otherwise.")
                .defineEnum("rounding", EnergyRounding.ACCUMULATE);
        b.pop();

        b.comment("Connected generator networks. Generators touching on any of their six faces form one network.",
                "Network FE/t = baseGeneration x Generators x scalingMultiplier ^ (Generators - 1)").push("network");
        SCALING_ENABLED = b.comment("If false, every generator produces baseGeneration x fuel multiplier regardless of its network.")
                .define("scalingEnabled", true);
        SCALING_MULTIPLIER = b.comment("Exponential scaling multiplier applied per additional counted generator.")
                .defineInRange("scalingMultiplier", 1.20D, 0.1D, 10.0D);
        MAX_NETWORK_SIZE = b.comment("Maximum number of generators in one network. Extra generators form separate networks.")
                .defineInRange("maxNetworkSize", 64, 1, 1024);
        COUNT_INACTIVE = b.comment("If true, idle generators (no fuel, no water or full) also count towards the exponent.",
                        "If false (default), only generators that are actively consuming mushrooms and water count.")
                .define("countInactiveGenerators", false);
        MERGE_NETWORKS = b.comment("If true, placing a generator that touches two separate networks merges them.",
                        "If false, a new generator joins only the largest touching network and existing networks never merge.")
                .define("mergeNetworks", true);
        SHARE_RESOURCES = b.comment("If true, generators in one network share water (a dry generator drinks from its neighbours'",
                        "tanks) and energy (production that does not fit in a full buffer flows into members with space).")
                .define("shareResources", false);
        b.pop();

        b.comment("Mushroom fuels. Each entry is a string: \"<item id or #tag>;burn=<ticks>;energy=<x>;water=<x>;mycelium=<x>;scaling=<x>\"",
                "  burn     (required) ticks of generation per item, 1-1000000",
                "  energy   multiplier on the base FE/t (default 1.0)",
                "  water    multiplier on water consumption (default 1.0)",
                "  mycelium multiplier on the mycelium chance increase per failed attempt (default 1.0)",
                "  scaling  bonus added to the network scaling multiplier for this generator's own output (default 0.0)",
                "Exact item entries take priority over tag entries. Example for another mod: \"#c:mushrooms;burn=400\"").push("fuel");
        FUELS = b.comment("Accepted mushroom fuels.")
                .defineListAllowEmpty("fuels",
                        List.of(
                                "minecraft:brown_mushroom;burn=400;energy=1.0;water=1.0;mycelium=1.0;scaling=0.0",
                                "minecraft:red_mushroom;burn=600;energy=1.25;water=1.0;mycelium=1.0;scaling=0.0"),
                        () -> "minecraft:brown_mushroom;burn=400",
                        FuelEntryParser::isValid);
        b.pop();

        b.comment("Water tank and consumption.").push("water");
        TANK_CAPACITY = b.comment("Water tank capacity of each generator in mB. Lowering it discards water above the new capacity.")
                .defineInRange("tankCapacity", 8_000, 1_000, 1_000_000);
        WATER_PER_INTERVAL = b.comment("mB of water a running generator consumes every 'consumptionInterval' ticks (before fuel multipliers).")
                .defineInRange("consumptionAmount", 10, 0, 100_000);
        WATER_INTERVAL = b.comment("Ticks between water consumptions.")
                .defineInRange("consumptionInterval", 20, 1, 72_000);
        WATER_SCALES_WITH_NETWORK = b.comment("If true, each generator's water use is also multiplied by its network scaling factor",
                        "(scalingMultiplier ^ (Generators - 1)). Total network use always grows with the number of active generators.")
                .define("scaleConsumptionWithNetwork", false);
        ACCEPTED_FLUIDS = b.comment("Fluids accepted into the tank. Fluid ids or #tags.")
                .defineListAllowEmpty("acceptedFluids", List.of("minecraft:water"), () -> "minecraft:water", ServerConfig::isResourceIdOrTag);
        ALLOW_FLUID_EXTRACTION = b.comment("If true, pipes and buckets may drain water back out of a generator.")
                .define("allowFluidExtraction", false);
        b.pop();

        b.comment("Mycelium spreading. While running, a generator periodically tries to turn a nearby block into mycelium.",
                "Each eligible attempt (one with at least one valid target) that fails raises the chance; at 100% the next attempt always succeeds.",
                "All chances are fractions: 0.01 = 1%.").push("mycelium");
        MYCELIUM_ENABLED = b.comment("Enable mycelium conversion.").define("enabled", true);
        INITIAL_CHANCE = b.comment("Chance of the first attempt, and the value the chance resets to.")
                .defineInRange("initialChance", 0.01D, 0.0D, 1.0D);
        CHANCE_INCREASE = b.comment("Increase in chance after each unsuccessful eligible attempt (0.005 = +0.5 percentage points).")
                .defineInRange("chanceIncrease", 0.005D, 0.0D, 1.0D);
        MAX_CHANCE = b.comment("Upper limit of the chance. With 1.0 the attempt after reaching the limit is guaranteed to succeed.")
                .defineInRange("maxChance", 1.0D, 0.0D, 1.0D);
        ATTEMPT_INTERVAL = b.comment("Ticks a generator must run between conversion attempts.")
                .defineInRange("attemptInterval", 100, 1, 72_000);
        SEARCH_RADIUS = b.comment("Radius (in blocks, on every axis) of the cube searched for convertible blocks.")
                .defineInRange("searchRadius", 4, 0, 8);
        MAX_CONVERSIONS = b.comment("Blocks converted by one successful attempt (before network bonuses).")
                .defineInRange("maxConversionsPerAttempt", 1, 1, 64);
        ONLY_DIRT = b.comment("If true, only minecraft:dirt is converted. If false, the 'convertibleBlocks' list is used instead.")
                .define("onlyDirt", true);
        CONVERTIBLE_BLOCKS = b.comment("Blocks (ids or #tags) that may be converted when onlyDirt = false.")
                .defineListAllowEmpty("convertibleBlocks", List.of("minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                        () -> "minecraft:dirt", ServerConfig::isResourceIdOrTag);
        RESULT_BLOCK = b.comment("Block placed by a successful conversion.")
                .define("resultBlock", "minecraft:mycelium", ServerConfig::isResourceId);
        REQUIRE_AIR_ABOVE = b.comment("If true, the block above the target must be air.").define("requireAirAbove", true);
        REQUIRE_LIGHT = b.comment("If true, the block above the target must have at least 'minimumLightLevel' light.")
                .define("requireLight", false);
        MIN_LIGHT = b.comment("Minimum light level used when requireLight = true (vanilla mycelium spreading uses 9).")
                .defineInRange("minimumLightLevel", 9, 0, 15);
        RESET_ON_SUCCESS = b.comment("If true, the chance resets to initialChance after a successful conversion.")
                .define("resetChanceOnSuccess", true);
        RESET_ON_STOP = b.comment("If true, the chance resets to initialChance whenever the generator stops running.")
                .define("resetChanceOnStop", false);
        SHARE_CHANCE = b.comment("If true, all generators in a network share (and together raise) one conversion chance.")
                .define("shareChanceAcrossNetwork", false);
        NETWORK_GROWTH_BONUS = b.comment("If true, larger networks raise the chance faster:",
                        "increase x (1 + growthBonusPerGenerator x (ActiveGenerators - 1)).")
                .define("networkIncreasesGrowthRate", false);
        GROWTH_BONUS_PER_GENERATOR = b.comment("Growth rate bonus per additional active generator in the network.")
                .defineInRange("growthBonusPerGenerator", 0.10D, 0.0D, 10.0D);
        NETWORK_EXTRA_CONVERSIONS = b.comment("If true, larger networks convert extra blocks per successful attempt.")
                .define("networkExtraConversions", false);
        GENERATORS_PER_EXTRA = b.comment("One extra conversion per this many active generators in the network.")
                .defineInRange("generatorsPerExtraConversion", 4, 1, 1024);
        MAX_EXTRA_CONVERSIONS = b.comment("Upper limit of extra conversions from network size.")
                .defineInRange("maxExtraConversions", 4, 0, 64);
        TRACK_CONVERSIONS = b.comment("If true, the GUI shows how many blocks each generator converted since it was loaded.")
                .define("trackConversions", true);
        b.pop();

        b.comment("Performance tuning.").push("performance");
        GUI_SYNC_INTERVAL = b.comment("Ticks between GUI updates sent to players viewing a generator.")
                .defineInRange("guiSyncInterval", 5, 1, 100);
        CONTAINER_INTERVAL = b.comment("Ticks between attempts to empty fluid containers placed in the bucket slot.")
                .defineInRange("containerProcessInterval", 10, 1, 200);
        OUTPUT_INTERVAL = b.comment("Ticks between energy pushes to neighbours. Each push sends up to outputTransferRate x interval FE.")
                .defineInRange("energyOutputInterval", 1, 1, 20);
        b.pop();

        b.comment("Compatibility with other mods.").push("compatibility");
        FIRE_PLACE_EVENTS = b.comment("If true, each mycelium conversion fires a block place event as a fake player so protection",
                        "mods (claims, regions) can cancel it.")
                .define("firePlaceEvents", true);
        RESPECT_SPAWN_PROTECTION = b.comment("If true, conversions never happen inside vanilla spawn protection or outside the world border.")
                .define("respectSpawnProtection", true);
        FAKE_PLAYER_NAME = b.comment("Name of the fake player used for protection checks.")
                .define("fakePlayerName", "[MycelialPower]");
        JEI_EXAMPLE_NETWORK_SIZE = b.comment("Largest network size listed in the JEI scaling tooltip.")
                .defineInRange("jeiExampleNetworkSize", 10, 1, 64);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {
    }

    private static boolean isResourceId(Object o) {
        return o instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    private static boolean isResourceIdOrTag(Object o) {
        if (!(o instanceof String s)) {
            return false;
        }
        return ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s) != null;
    }

    /** Whether the server config has been loaded (false on the title screen). */
    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    /** Reads a value, falling back to its default when the config is not loaded yet (e.g. JEI on the title screen). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** Effective maximum chance, never below the initial chance. */
    public static double maxChance() {
        return Math.max(get(MAX_CHANCE), 0.0D);
    }

    public static double initialChance() {
        return Math.min(get(INITIAL_CHANCE), maxChance());
    }
}
