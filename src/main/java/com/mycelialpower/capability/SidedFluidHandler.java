package com.mycelialpower.capability;

import com.mycelialpower.config.ServerConfig;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** The tank as seen by pipes and buckets: filling always works, draining only if the config allows it. */
public record SidedFluidHandler(GeneratorFluidTank tank) implements ResourceHandler<FluidResource> {
    @Override
    public int size() {
        return tank.size();
    }

    @Override
    public FluidResource getResource(int index) {
        return tank.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return tank.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return tank.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return tank.isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return tank.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return ServerConfig.get(ServerConfig.ALLOW_FLUID_EXTRACTION) ? tank.extract(index, resource, amount, transaction) : 0;
    }
}
