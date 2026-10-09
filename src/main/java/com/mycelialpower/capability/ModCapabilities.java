package com.mycelialpower.capability;

import com.mycelialpower.registry.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Exposes the generator's energy, fluid and item handlers to other mods (cables, pipes, hoppers). */
public final class ModCapabilities {
    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.MYCELIAL_GENERATOR.get(),
                (generator, side) -> generator.getEnergyStorage());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.MYCELIAL_GENERATOR.get(),
                (generator, side) -> generator.getExternalFluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.MYCELIAL_GENERATOR.get(),
                (generator, side) -> generator.getAutomationItemHandler());
    }
}
