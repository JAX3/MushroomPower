package com.mycelialpower.gametest;

import com.mycelialpower.MycelialPower;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

/**
 * Registers the GameTest functions (Minecraft 1.21.5+ data-driven GameTests). Each function is paired with a
 * test instance in {@code data/mycelialpower/test_instance/<name>.json}, which sets the structure, the
 * environment and the time limit. Run them with {@code gradlew runGameTestServer} or {@code /test runall}.
 */
public final class ModGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, MycelialPower.MOD_ID);

    static {
        FUNCTIONS.register("single_generator_produces_energy", () -> GeneratorGameTests::singleGeneratorProducesEnergy);
        FUNCTIONS.register("adjacent_generators_form_one_network", () -> GeneratorGameTests::adjacentGeneratorsFormOneNetwork);
        FUNCTIONS.register("three_generators_scale_exponentially", () -> GeneratorGameTests::threeGeneratorsScaleExponentially);
        FUNCTIONS.register("network_splits_when_generator_removed", () -> GeneratorGameTests::networkSplitsWhenGeneratorRemoved);
        FUNCTIONS.register("generator_without_water_stops", () -> GeneratorGameTests::generatorWithoutWaterStops);
        FUNCTIONS.register("mycelium_converts_dirt", () -> GeneratorGameTests::myceliumConvertsDirt);
        FUNCTIONS.register("converted_block_sprouts_huge_mushroom", () -> GeneratorGameTests::convertedBlockSproutsHugeMushroom);
        FUNCTIONS.register("state_survives_save_and_load", () -> GeneratorGameTests::stateSurvivesSaveAndLoad);
        FUNCTIONS.register("huge_multiplier_does_not_overflow", () -> GeneratorGameTests::hugeMultiplierDoesNotOverflow);
        FUNCTIONS.register("manual_link_joins_distant_generators", () -> GeneratorGameTests::manualLinkJoinsDistantGenerators);
        FUNCTIONS.register("unlinking_splits_network", () -> GeneratorGameTests::unlinkingSplitsNetwork);
        FUNCTIONS.register("destroyed_generator_loses_links", () -> GeneratorGameTests::destroyedGeneratorLosesLinks);
        FUNCTIONS.register("duplicate_links_rejected_and_chains_merge", () -> GeneratorGameTests::duplicateLinksRejectedAndChainsMerge);
        FUNCTIONS.register("physical_and_manual_connections_combine", () -> GeneratorGameTests::physicalAndManualConnectionsCombine);
        FUNCTIONS.register("link_data_survives_save_and_load", () -> GeneratorGameTests::linkDataSurvivesSaveAndLoad);
    }

    private ModGameTests() {
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
    }
}
