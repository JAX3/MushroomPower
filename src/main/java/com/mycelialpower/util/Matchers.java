package com.mycelialpower.util;

import com.mycelialpower.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** Shared config-driven matchers. */
public final class Matchers {
    public static final ConfiguredMatcher<Fluid> ACCEPTED_FLUIDS = new ConfiguredMatcher<>(
            BuiltInRegistries.FLUID, Registries.FLUID, () -> ServerConfig.get(ServerConfig.ACCEPTED_FLUIDS));
    public static final ConfiguredMatcher<Block> CONVERTIBLE_BLOCKS = new ConfiguredMatcher<>(
            BuiltInRegistries.BLOCK, Registries.BLOCK, () -> ServerConfig.get(ServerConfig.CONVERTIBLE_BLOCKS));

    private static volatile BlockState resultState;

    private Matchers() {
    }

    @SuppressWarnings("deprecation")
    public static boolean isAcceptedFluid(FluidStack stack) {
        return !stack.isEmpty() && ACCEPTED_FLUIDS.test(stack.getFluid().builtInRegistryHolder());
    }

    public static boolean isConvertible(BlockState state) {
        if (ServerConfig.get(ServerConfig.ONLY_DIRT)) {
            return state.is(Blocks.DIRT);
        }
        return CONVERTIBLE_BLOCKS.test(state.getBlockHolder());
    }

    /** The block placed by a successful conversion. Falls back to mycelium for invalid config values. */
    public static BlockState conversionResult() {
        BlockState s = resultState;
        if (s == null) {
            ResourceLocation id = ResourceLocation.tryParse(ServerConfig.get(ServerConfig.RESULT_BLOCK));
            Block block = id == null ? Blocks.MYCELIUM : BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.MYCELIUM);
            if (block == Blocks.AIR) {
                block = Blocks.MYCELIUM;
            }
            s = block.defaultBlockState();
            resultState = s;
        }
        return s;
    }

    public static void invalidateAll() {
        ACCEPTED_FLUIDS.invalidate();
        CONVERTIBLE_BLOCKS.invalidate();
        resultState = null;
    }
}
