package com.mycelialpower.gametest;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.blockentity.GeneratorState;
import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import com.mycelialpower.capability.GeneratorItemHandler;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.network.ConnectionType;
import com.mycelialpower.network.GeneratorNetwork;
import com.mycelialpower.network.GeneratorNetworkManager;
import com.mycelialpower.network.LinkRules;
import com.mycelialpower.network.ManualLinkData;
import com.mycelialpower.network.ManualLinking;
import com.mycelialpower.network.NetworkMath;
import com.mycelialpower.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * In-game tests. Run with {@code gradlew runGameTestServer}. They assume the default server config.
 */
public final class GeneratorGameTests {
    private static final double EPSILON = 1.0E-6;

    private GeneratorGameTests() {
    }

    /** {@code GameTestHelper#assertTrue} takes a Component message since 1.21.5. */
    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    private static MycelialGeneratorBlockEntity place(GameTestHelper helper, BlockPos pos, ItemStack fuel, int water) {
        helper.setBlock(pos, ModBlocks.MYCELIAL_GENERATOR.get());
        MycelialGeneratorBlockEntity generator = helper.getBlockEntity(pos, MycelialGeneratorBlockEntity.class);
        if (!fuel.isEmpty()) {
            generator.getInventory().setStackInSlot(GeneratorItemHandler.FUEL_SLOT, fuel);
        }
        if (water > 0) {
            generator.getTank().fillInternal(FluidResource.of(Fluids.WATER), water);
        }
        return generator;
    }

    private static GeneratorNetwork network(GameTestHelper helper, MycelialGeneratorBlockEntity generator) {
        return GeneratorNetworkManager.get(helper.getLevel()).getNetwork(generator);
    }

    /** Verification 1 + 6: one generator consumes a mushroom and water and produces FE. */
    public static void singleGeneratorProducesEnergy(GameTestHelper helper) {
        MycelialGeneratorBlockEntity generator = place(helper, new BlockPos(2, 1, 2), new ItemStack(Items.RED_MUSHROOM, 2), 1000);
        helper.runAfterDelay(41, () -> {
            check(helper, generator.getState() == GeneratorState.RUNNING, "Generator should be running, was " + generator.getState());
            check(helper, generator.getInventory().getStackInSlot(GeneratorItemHandler.FUEL_SLOT).getCount() == 1,
                    "Exactly one mushroom should have been consumed");
            double expected = ServerConfig.get(ServerConfig.BASE_GENERATION) * 1.25D;
            check(helper, Math.abs(generator.getLastExactOutput() - expected) < EPSILON,
                    "Expected " + expected + " FE/t, got " + generator.getLastExactOutput());
            check(helper, generator.getEnergyStorage().getAmountAsInt() > 0, "Energy should have been generated");
            int per = ServerConfig.get(ServerConfig.WATER_PER_INTERVAL);
            int water = generator.getTank().getFluidAmount();
            check(helper, water <= 1000 - per && water >= 1000 - 3 * per,
                    "About two water consumptions should have happened, tank has " + water);
            helper.succeed();
        });
    }

    /** Verification 2: adjacent generators share one network in every direction. */
    public static void adjacentGeneratorsFormOneNetwork(GameTestHelper helper) {
        MycelialGeneratorBlockEntity center = place(helper, new BlockPos(3, 2, 3), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity up = place(helper, new BlockPos(3, 3, 3), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity east = place(helper, new BlockPos(4, 2, 3), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity south = place(helper, new BlockPos(3, 2, 4), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity separate = place(helper, new BlockPos(6, 2, 6), ItemStack.EMPTY, 0);
        helper.runAfterDelay(2, () -> {
            GeneratorNetwork network = network(helper, center);
            check(helper, network.size() == 4, "Expected 4 members, got " + network.size());
            check(helper, network == network(helper, up) && network == network(helper, east) && network == network(helper, south),
                    "Adjacent generators must share one network");
            check(helper, network(helper, separate) != network && network(helper, separate).size() == 1,
                    "A detached generator must form its own network");
            helper.succeed();
        });
    }

    /** Verification 3: three running generators follow the exponential formula. */
    public static void threeGeneratorsScaleExponentially(GameTestHelper helper) {
        MycelialGeneratorBlockEntity a = place(helper, new BlockPos(2, 1, 2), new ItemStack(Items.BROWN_MUSHROOM, 4), 4000);
        place(helper, new BlockPos(3, 1, 2), new ItemStack(Items.BROWN_MUSHROOM, 4), 4000);
        place(helper, new BlockPos(4, 1, 2), new ItemStack(Items.BROWN_MUSHROOM, 4), 4000);
        helper.succeedWhen(() -> {
            GeneratorNetwork network = network(helper, a);
            double base = ServerConfig.get(ServerConfig.BASE_GENERATION);
            double multiplier = ServerConfig.get(ServerConfig.SCALING_MULTIPLIER);
            double expectedEach = base * Math.pow(multiplier, 2);
            check(helper, network.activeCount() == 3, "All three should be active");
            check(helper, Math.abs(a.getLastExactOutput() - expectedEach) < EPSILON,
                    "Expected " + expectedEach + " FE/t per generator, got " + a.getLastExactOutput());
            double expectedTotal = NetworkMath.networkOutput(base, 3, multiplier, 0);
            check(helper, Math.abs(network.totalOutput() - expectedTotal) < 1.0E-3,
                    "Expected network total " + expectedTotal + ", got " + network.totalOutput());
        });
    }

    /** Verification 4: removing the middle generator splits the network. */
    public static void networkSplitsWhenGeneratorRemoved(GameTestHelper helper) {
        MycelialGeneratorBlockEntity left = place(helper, new BlockPos(2, 1, 2), ItemStack.EMPTY, 0);
        place(helper, new BlockPos(3, 1, 2), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity right = place(helper, new BlockPos(4, 1, 2), ItemStack.EMPTY, 0);
        helper.runAfterDelay(2, () -> {
            check(helper, network(helper, left).size() == 3, "Expected one network of 3");
            helper.setBlock(new BlockPos(3, 1, 2), Blocks.AIR);
            helper.runAfterDelay(2, () -> {
                check(helper, network(helper, left).size() == 1 && network(helper, right).size() == 1,
                        "Network should split into two single generators");
                check(helper, network(helper, left) != network(helper, right), "Split halves must be separate networks");
                helper.succeed();
            });
        });
    }

    /** Verification 5: without water the generator idles and keeps its mushroom. */
    public static void generatorWithoutWaterStops(GameTestHelper helper) {
        MycelialGeneratorBlockEntity generator = place(helper, new BlockPos(2, 1, 2), new ItemStack(Items.RED_MUSHROOM, 1), 0);
        helper.runAfterDelay(20, () -> {
            check(helper, generator.getState() == GeneratorState.NO_WATER, "Expected NO_WATER, got " + generator.getState());
            check(helper, generator.getEnergyStorage().getAmountAsInt() == 0, "No energy may be produced without water");
            check(helper, generator.getInventory().getStackInSlot(GeneratorItemHandler.FUEL_SLOT).getCount() == 1,
                    "The mushroom must not be consumed");
            helper.succeed();
        });
    }

    /** Verification 8: a guaranteed attempt converts dirt with air above into mycelium. */
    public static void myceliumConvertsDirt(GameTestHelper helper) {
        BlockPos dirt = new BlockPos(4, 1, 2);
        helper.setBlock(dirt, Blocks.DIRT);
        MycelialGeneratorBlockEntity generator = place(helper, new BlockPos(2, 1, 2), new ItemStack(Items.RED_MUSHROOM, 4), 4000);
        generator.setOwnChance(1.0D);
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.MYCELIUM, dirt));
    }

    /** A converted block far enough from the generator sprouts a huge mushroom when the chance is 100%. */
    public static void convertedBlockSproutsHugeMushroom(GameTestHelper helper) {
        double previousChance = ServerConfig.HUGE_MUSHROOM_CHANCE.get();
        ServerConfig.HUGE_MUSHROOM_CHANCE.set(1.0D);
        BlockPos dirt = new BlockPos(5, 1, 5);
        BlockPos stem = dirt.above();
        helper.setBlock(dirt, Blocks.DIRT);
        MycelialGeneratorBlockEntity generator = place(helper, new BlockPos(1, 1, 1), new ItemStack(Items.RED_MUSHROOM, 4), 4000);
        generator.setOwnChance(1.0D);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.MYCELIUM, dirt);
            helper.assertBlockPresent(Blocks.MUSHROOM_STEM, stem);
            ServerConfig.HUGE_MUSHROOM_CHANCE.set(previousChance);
        });
    }

    /** Verification 14: machine state survives a save/load round trip. */
    public static void stateSurvivesSaveAndLoad(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MycelialGeneratorBlockEntity generator = place(helper, pos, new ItemStack(Items.BROWN_MUSHROOM, 3), 2000);
        generator.setOwnChance(0.125D);
        helper.runAfterDelay(30, () -> {
            var registries = helper.getLevel().registryAccess();
            CompoundTag saved = generator.saveWithFullMetadata(registries);
            MycelialGeneratorBlockEntity copy = new MycelialGeneratorBlockEntity(generator.getBlockPos(), generator.getBlockState());
            try (ProblemReporter.ScopedCollector problems = new ProblemReporter.ScopedCollector(copy.problemPath(), MycelialPower.LOGGER)) {
                copy.loadWithComponents(TagValueInput.create(problems, registries, saved));
            }
            check(helper, copy.getEnergyStorage().getAmountAsInt() == generator.getEnergyStorage().getAmountAsInt(), "Energy lost");
            check(helper, copy.getBurnRemaining() == generator.getBurnRemaining(), "Burn progress lost");
            check(helper, copy.getTank().getFluidAmount() == generator.getTank().getFluidAmount(), "Water lost");
            check(helper, Math.abs(copy.getOwnChance() - generator.getOwnChance()) < EPSILON, "Mycelium chance lost");
            check(helper, ItemStack.matches(copy.getInventory().getStackInSlot(0), generator.getInventory().getStackInSlot(0)),
                    "Inventory lost");
            helper.succeed();
        });
    }

    /** Verification 16: a full-size network cannot overflow. */
    public static void hugeMultiplierDoesNotOverflow(GameTestHelper helper) {
        double perGenerator = NetworkMath.generatorOutput(1_000_000D, 1000D, 10D, 1024, 0);
        check(helper, perGenerator > 0 && perGenerator <= Integer.MAX_VALUE, "Output must be clamped to an int range");
        helper.succeed();
    }

    // ------------------------------------------------------------------ Mycelium Wrench (manual links)

    private static GlobalPos global(GameTestHelper helper, BlockPos relative) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
    }

    /** Linked generators that are far apart form one network and share exponential scaling. */
    public static void manualLinkJoinsDistantGenerators(GameTestHelper helper) {
        BlockPos posA = new BlockPos(1, 1, 1);
        BlockPos posB = new BlockPos(6, 1, 6);
        MycelialGeneratorBlockEntity a = place(helper, posA, new ItemStack(Items.BROWN_MUSHROOM, 4), 4000);
        MycelialGeneratorBlockEntity b = place(helper, posB, new ItemStack(Items.BROWN_MUSHROOM, 4), 4000);
        helper.runAfterDelay(1, () -> {
            LinkRules.Result result = ManualLinking.tryLink(helper.getLevel().getServer(), global(helper, posA), global(helper, posB));
            check(helper, result == LinkRules.Result.SUCCESS, "Link should succeed, got " + result);
            helper.succeedWhen(() -> {
                GeneratorNetwork network = network(helper, a);
                check(helper, network == network(helper, b) && network.size() == 2, "Linked generators must share one network");
                check(helper, network.activeCount() == 2, "Both generators should be running");
                double expected = ServerConfig.get(ServerConfig.BASE_GENERATION) * ServerConfig.get(ServerConfig.SCALING_MULTIPLIER);
                check(helper, Math.abs(a.getLastExactOutput() - expected) < EPSILON,
                        "Expected " + expected + " FE/t (scaled for 2 generators), got " + a.getLastExactOutput());
                check(helper, GeneratorNetworkManager.get(helper.getLevel()).connectionType(a) == ConnectionType.MANUAL,
                        "Connection type should be MANUAL");
            });
        });
    }

    /** Shift-clicking (unlinkAll) splits a network that was only held together by the link. */
    public static void unlinkingSplitsNetwork(GameTestHelper helper) {
        BlockPos posA = new BlockPos(1, 1, 1);
        BlockPos posB = new BlockPos(6, 1, 6);
        MycelialGeneratorBlockEntity a = place(helper, posA, ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity b = place(helper, posB, ItemStack.EMPTY, 0);
        helper.runAfterDelay(1, () -> {
            ManualLinking.tryLink(helper.getLevel().getServer(), global(helper, posA), global(helper, posB));
            check(helper, network(helper, a) == network(helper, b), "Should be linked");
            int removed = ManualLinking.unlinkAll(helper.getLevel().getServer(), global(helper, posA));
            check(helper, removed == 1, "Exactly one link should be removed, removed " + removed);
            check(helper, network(helper, a) != network(helper, b) && network(helper, a).size() == 1, "Network should split");
            helper.succeed();
        });
    }

    /** Destroying a generator removes its links from the partner too. */
    public static void destroyedGeneratorLosesLinks(GameTestHelper helper) {
        BlockPos posA = new BlockPos(1, 1, 1);
        BlockPos posB = new BlockPos(6, 1, 6);
        place(helper, posA, ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity b = place(helper, posB, ItemStack.EMPTY, 0);
        helper.runAfterDelay(1, () -> {
            ManualLinking.tryLink(helper.getLevel().getServer(), global(helper, posA), global(helper, posB));
            ManualLinkData data = ManualLinkData.get(helper.getLevel().getServer());
            check(helper, data.count(global(helper, posB)) == 1, "Link should exist");
            helper.setBlock(posA, Blocks.AIR);
            helper.runAfterDelay(1, () -> {
                check(helper, data.count(global(helper, posB)) == 0, "Link must be removed with the destroyed generator");
                check(helper, network(helper, b).size() == 1, "Partner should be alone again");
                helper.succeed();
            });
        });
    }

    /** Duplicate and self links are refused; a third generator can join through the link chain. */
    public static void duplicateLinksRejectedAndChainsMerge(GameTestHelper helper) {
        BlockPos posA = new BlockPos(1, 1, 1);
        BlockPos posB = new BlockPos(6, 1, 1);
        BlockPos posC = new BlockPos(6, 1, 6);
        MycelialGeneratorBlockEntity a = place(helper, posA, ItemStack.EMPTY, 0);
        place(helper, posB, ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity c = place(helper, posC, ItemStack.EMPTY, 0);
        helper.runAfterDelay(1, () -> {
            var server = helper.getLevel().getServer();
            check(helper, ManualLinking.tryLink(server, global(helper, posA), global(helper, posB)).success(), "A-B should link");
            check(helper, ManualLinking.tryLink(server, global(helper, posB), global(helper, posA)) == LinkRules.Result.ALREADY_LINKED,
                    "Reverse duplicate must be refused");
            check(helper, ManualLinking.tryLink(server, global(helper, posA), global(helper, posA)) == LinkRules.Result.SAME_GENERATOR,
                    "Self link must be refused");
            check(helper, ManualLinking.tryLink(server, global(helper, posB), global(helper, posC)).success(), "B-C should link");
            check(helper, network(helper, a) == network(helper, c) && network(helper, a).size() == 3,
                    "A, B and C should form one network through the chain");
            helper.succeed();
        });
    }

    /** Touching and linked generators combine into one network; the bridge generator reports BOTH. */
    public static void physicalAndManualConnectionsCombine(GameTestHelper helper) {
        BlockPos posA = new BlockPos(1, 1, 1);
        BlockPos posB = new BlockPos(2, 1, 1);
        BlockPos posC = new BlockPos(6, 1, 6);
        MycelialGeneratorBlockEntity a = place(helper, posA, ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity b = place(helper, posB, ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity c = place(helper, posC, ItemStack.EMPTY, 0);
        helper.runAfterDelay(1, () -> {
            ManualLinking.tryLink(helper.getLevel().getServer(), global(helper, posB), global(helper, posC));
            GeneratorNetworkManager manager = GeneratorNetworkManager.get(helper.getLevel());
            check(helper, network(helper, a) == network(helper, c) && network(helper, a).size() == 3, "Expected one network of 3");
            check(helper, manager.connectionType(a) == ConnectionType.PHYSICAL, "A is only touching");
            check(helper, manager.connectionType(b) == ConnectionType.BOTH, "B touches A and is linked to C");
            check(helper, manager.connectionType(c) == ConnectionType.MANUAL, "C is only linked");
            helper.succeed();
        });
    }

    /** Link storage round-trips through NBT (world save and reload). */
    public static void linkDataSurvivesSaveAndLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        GlobalPos a = global(helper, new BlockPos(1, 1, 1));
        GlobalPos b = global(helper, new BlockPos(6, 1, 6));
        GlobalPos c = GlobalPos.of(net.minecraft.world.level.Level.NETHER, new BlockPos(10, 64, -10));
        ManualLinkData data = new ManualLinkData();
        data.add(a, b);
        data.add(b, c);
        check(helper, !data.add(b, a), "Duplicates must be refused");
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ManualLinkData loaded = ManualLinkData.CODEC.parse(ops, ManualLinkData.CODEC.encodeStart(ops, data).getOrThrow()).getOrThrow();
        check(helper, loaded.isLinked(a, b) && loaded.isLinked(b, a), "A-B lost");
        check(helper, loaded.isLinked(b, c) && loaded.isLinked(c, b), "B-C (cross dimension) lost");
        check(helper, loaded.totalLinks() == 2, "Expected 2 links, got " + loaded.totalLinks());
        helper.succeed();
    }
}
