package com.mycelialpower.gametest;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.blockentity.GeneratorState;
import com.mycelialpower.blockentity.MycelialGeneratorBlockEntity;
import com.mycelialpower.capability.GeneratorItemHandler;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.network.GeneratorNetwork;
import com.mycelialpower.network.GeneratorNetworkManager;
import com.mycelialpower.network.NetworkMath;
import com.mycelialpower.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests. Run with {@code gradlew runGameTestServer}. They assume the default server config.
 */
@GameTestHolder(MycelialPower.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GeneratorGameTests {
    private static final String TEMPLATE = "empty";
    private static final double EPSILON = 1.0E-6;

    private GeneratorGameTests() {
    }

    private static MycelialGeneratorBlockEntity place(GameTestHelper helper, BlockPos pos, ItemStack fuel, int water) {
        helper.setBlock(pos, ModBlocks.MYCELIAL_GENERATOR.get());
        MycelialGeneratorBlockEntity generator = helper.getBlockEntity(pos);
        if (!fuel.isEmpty()) {
            generator.getInventory().setStackInSlot(GeneratorItemHandler.FUEL_SLOT, fuel);
        }
        if (water > 0) {
            generator.getTank().fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE);
        }
        return generator;
    }

    private static GeneratorNetwork network(GameTestHelper helper, MycelialGeneratorBlockEntity generator) {
        return GeneratorNetworkManager.get(helper.getLevel()).getNetwork(generator);
    }

    /** Verification 1 + 6: one generator consumes a mushroom and water and produces FE. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void singleGeneratorProducesEnergy(GameTestHelper helper) {
        MycelialGeneratorBlockEntity generator = place(helper, new BlockPos(2, 1, 2), new ItemStack(Items.RED_MUSHROOM, 2), 1000);
        helper.runAfterDelay(41, () -> {
            helper.assertTrue(generator.getState() == GeneratorState.RUNNING, "Generator should be running, was " + generator.getState());
            helper.assertTrue(generator.getInventory().getStackInSlot(GeneratorItemHandler.FUEL_SLOT).getCount() == 1,
                    "Exactly one mushroom should have been consumed");
            double expected = ServerConfig.get(ServerConfig.BASE_GENERATION) * 1.25D;
            helper.assertTrue(Math.abs(generator.getLastExactOutput() - expected) < EPSILON,
                    "Expected " + expected + " FE/t, got " + generator.getLastExactOutput());
            helper.assertTrue(generator.getEnergyStorage().getEnergyStored() > 0, "Energy should have been generated");
            int per = ServerConfig.get(ServerConfig.WATER_PER_INTERVAL);
            int water = generator.getTank().getFluidAmount();
            helper.assertTrue(water <= 1000 - per && water >= 1000 - 3 * per,
                    "About two water consumptions should have happened, tank has " + water);
            helper.succeed();
        });
    }

    /** Verification 2: adjacent generators share one network in every direction. */
    @GameTest(template = TEMPLATE)
    public static void adjacentGeneratorsFormOneNetwork(GameTestHelper helper) {
        MycelialGeneratorBlockEntity center = place(helper, new BlockPos(3, 2, 3), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity up = place(helper, new BlockPos(3, 3, 3), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity east = place(helper, new BlockPos(4, 2, 3), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity south = place(helper, new BlockPos(3, 2, 4), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity separate = place(helper, new BlockPos(6, 2, 6), ItemStack.EMPTY, 0);
        helper.runAfterDelay(2, () -> {
            GeneratorNetwork network = network(helper, center);
            helper.assertTrue(network.size() == 4, "Expected 4 members, got " + network.size());
            helper.assertTrue(network == network(helper, up) && network == network(helper, east) && network == network(helper, south),
                    "Adjacent generators must share one network");
            helper.assertTrue(network(helper, separate) != network && network(helper, separate).size() == 1,
                    "A detached generator must form its own network");
            helper.succeed();
        });
    }

    /** Verification 3: three running generators follow the exponential formula. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void threeGeneratorsScaleExponentially(GameTestHelper helper) {
        MycelialGeneratorBlockEntity a = place(helper, new BlockPos(2, 1, 2), new ItemStack(Items.BROWN_MUSHROOM, 4), 4000);
        place(helper, new BlockPos(3, 1, 2), new ItemStack(Items.BROWN_MUSHROOM, 4), 4000);
        place(helper, new BlockPos(4, 1, 2), new ItemStack(Items.BROWN_MUSHROOM, 4), 4000);
        helper.succeedWhen(() -> {
            GeneratorNetwork network = network(helper, a);
            double base = ServerConfig.get(ServerConfig.BASE_GENERATION);
            double multiplier = ServerConfig.get(ServerConfig.SCALING_MULTIPLIER);
            double expectedEach = base * Math.pow(multiplier, 2);
            helper.assertTrue(network.activeCount() == 3, "All three should be active");
            helper.assertTrue(Math.abs(a.getLastExactOutput() - expectedEach) < EPSILON,
                    "Expected " + expectedEach + " FE/t per generator, got " + a.getLastExactOutput());
            double expectedTotal = NetworkMath.networkOutput(base, 3, multiplier, 0);
            helper.assertTrue(Math.abs(network.totalOutput() - expectedTotal) < 1.0E-3,
                    "Expected network total " + expectedTotal + ", got " + network.totalOutput());
        });
    }

    /** Verification 4: removing the middle generator splits the network. */
    @GameTest(template = TEMPLATE)
    public static void networkSplitsWhenGeneratorRemoved(GameTestHelper helper) {
        MycelialGeneratorBlockEntity left = place(helper, new BlockPos(2, 1, 2), ItemStack.EMPTY, 0);
        place(helper, new BlockPos(3, 1, 2), ItemStack.EMPTY, 0);
        MycelialGeneratorBlockEntity right = place(helper, new BlockPos(4, 1, 2), ItemStack.EMPTY, 0);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(network(helper, left).size() == 3, "Expected one network of 3");
            helper.setBlock(new BlockPos(3, 1, 2), Blocks.AIR);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(network(helper, left).size() == 1 && network(helper, right).size() == 1,
                        "Network should split into two single generators");
                helper.assertTrue(network(helper, left) != network(helper, right), "Split halves must be separate networks");
                helper.succeed();
            });
        });
    }

    /** Verification 5: without water the generator idles and keeps its mushroom. */
    @GameTest(template = TEMPLATE)
    public static void generatorWithoutWaterStops(GameTestHelper helper) {
        MycelialGeneratorBlockEntity generator = place(helper, new BlockPos(2, 1, 2), new ItemStack(Items.RED_MUSHROOM, 1), 0);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(generator.getState() == GeneratorState.NO_WATER, "Expected NO_WATER, got " + generator.getState());
            helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == 0, "No energy may be produced without water");
            helper.assertTrue(generator.getInventory().getStackInSlot(GeneratorItemHandler.FUEL_SLOT).getCount() == 1,
                    "The mushroom must not be consumed");
            helper.succeed();
        });
    }

    /** Verification 8: a guaranteed attempt converts dirt with air above into mycelium. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void myceliumConvertsDirt(GameTestHelper helper) {
        BlockPos dirt = new BlockPos(4, 1, 2);
        helper.setBlock(dirt, Blocks.DIRT);
        MycelialGeneratorBlockEntity generator = place(helper, new BlockPos(2, 1, 2), new ItemStack(Items.RED_MUSHROOM, 4), 4000);
        generator.setOwnChance(1.0D);
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.MYCELIUM, dirt));
    }

    /** Verification 14: machine state survives a save/load round trip. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void stateSurvivesSaveAndLoad(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MycelialGeneratorBlockEntity generator = place(helper, pos, new ItemStack(Items.BROWN_MUSHROOM, 3), 2000);
        generator.setOwnChance(0.125D);
        helper.runAfterDelay(30, () -> {
            var registries = helper.getLevel().registryAccess();
            CompoundTag saved = generator.saveWithFullMetadata(registries);
            MycelialGeneratorBlockEntity copy = new MycelialGeneratorBlockEntity(generator.getBlockPos(), generator.getBlockState());
            copy.loadWithComponents(saved, registries);
            helper.assertTrue(copy.getEnergyStorage().getEnergyStored() == generator.getEnergyStorage().getEnergyStored(), "Energy lost");
            helper.assertTrue(copy.getBurnRemaining() == generator.getBurnRemaining(), "Burn progress lost");
            helper.assertTrue(copy.getTank().getFluidAmount() == generator.getTank().getFluidAmount(), "Water lost");
            helper.assertTrue(Math.abs(copy.getOwnChance() - generator.getOwnChance()) < EPSILON, "Mycelium chance lost");
            helper.assertTrue(ItemStack.matches(copy.getInventory().getStackInSlot(0), generator.getInventory().getStackInSlot(0)),
                    "Inventory lost");
            helper.succeed();
        });
    }

    /** Verification 16: a full-size network cannot overflow. */
    @GameTest(template = TEMPLATE)
    public static void hugeMultiplierDoesNotOverflow(GameTestHelper helper) {
        double perGenerator = NetworkMath.generatorOutput(1_000_000D, 1000D, 10D, 1024, 0);
        helper.assertTrue(perGenerator > 0 && perGenerator <= Integer.MAX_VALUE, "Output must be clamped to an int range");
        helper.succeed();
    }
}
