package com.mycelialpower.compat.jei;

import com.mycelialpower.fuel.FuelDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * JEI representation of one configured mushroom fuel. Built by {@link MycelialFuelRecipeFactory} from
 * the same {@link com.mycelialpower.fuel.FuelRegistry} and config the generator uses.
 *
 * @param definition       the fuel entry
 * @param inputs           every item that burns as this fuel
 * @param fluids           accepted fluids (shown cycling in the water slot)
 * @param fePerTick        FE/t of a single, unconnected generator burning this fuel
 * @param totalEnergy      total FE produced by one item in a single generator
 * @param waterPerItem     mB of water consumed while one item burns (single generator)
 * @param waterPerInterval mB consumed per water interval while this fuel burns
 * @param waterInterval    ticks between water consumptions
 */
public record MycelialFuelRecipe(FuelDefinition definition, List<ItemStack> inputs, List<Fluid> fluids,
                                 double fePerTick, double totalEnergy, int waterPerItem,
                                 double waterPerInterval, int waterInterval) {
}
