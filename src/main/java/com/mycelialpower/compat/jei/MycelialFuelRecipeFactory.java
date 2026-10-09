package com.mycelialpower.compat.jei;

import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.fuel.FuelDefinition;
import com.mycelialpower.fuel.FuelRegistry;
import com.mycelialpower.network.NetworkMath;
import com.mycelialpower.util.Matchers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/** Generates the JEI fuel recipes from the live config; nothing is hardcoded. */
public final class MycelialFuelRecipeFactory {
    private MycelialFuelRecipeFactory() {
    }

    public static List<MycelialFuelRecipe> create() {
        List<Fluid> fluids = acceptedFluids();
        double base = ServerConfig.get(ServerConfig.BASE_GENERATION);
        double cap = ServerConfig.get(ServerConfig.MAX_NETWORK_OUTPUT);
        double multiplier = ServerConfig.get(ServerConfig.SCALING_MULTIPLIER);
        int waterAmount = ServerConfig.get(ServerConfig.WATER_PER_INTERVAL);
        int waterInterval = ServerConfig.get(ServerConfig.WATER_INTERVAL);

        List<MycelialFuelRecipe> recipes = new ArrayList<>();
        for (FuelDefinition definition : FuelRegistry.definitions()) {
            List<ItemStack> items = FuelRegistry.itemsFor(definition);
            if (items.isEmpty()) {
                continue;
            }
            double perTick = NetworkMath.generatorOutput(base, definition.energyMultiplier(),
                    multiplier + definition.scalingBonus(), 1, cap);
            double total = NetworkMath.safeMultiply(perTick, definition.burnTicks());
            double perInterval = waterAmount * definition.waterMultiplier();
            long waterPerItem = (long) Math.floor(perInterval * (definition.burnTicks() / (double) waterInterval));
            recipes.add(new MycelialFuelRecipe(definition, items, fluids, perTick, total,
                    (int) Math.min(Integer.MAX_VALUE, waterPerItem), perInterval, waterInterval));
        }
        return recipes;
    }

    /** Source fluids accepted by the generator tank. */
    public static List<Fluid> acceptedFluids() {
        List<Fluid> result = new ArrayList<>();
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) {
                continue;
            }
            @SuppressWarnings("deprecation")
            Holder<Fluid> holder = fluid.builtInRegistryHolder();
            if (Matchers.ACCEPTED_FLUIDS.test(holder)) {
                result.add(fluid);
            }
        }
        if (result.isEmpty()) {
            result.add(Fluids.WATER);
        }
        return result;
    }
}
