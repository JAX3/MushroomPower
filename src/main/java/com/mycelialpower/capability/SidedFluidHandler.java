package com.mycelialpower.capability;

import com.mycelialpower.config.ServerConfig;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The tank as seen by pipes and buckets: fill always, drain only if the config allows it. */
public record SidedFluidHandler(GeneratorFluidTank tank) implements IFluidHandler {
    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int index) {
        return tank.getFluidInTank(index);
    }

    @Override
    public int getTankCapacity(int index) {
        return tank.getTankCapacity(index);
    }

    @Override
    public boolean isFluidValid(int index, FluidStack stack) {
        return tank.isFluidValid(index, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return tank.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return ServerConfig.get(ServerConfig.ALLOW_FLUID_EXTRACTION) ? tank.drain(resource, action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return ServerConfig.get(ServerConfig.ALLOW_FLUID_EXTRACTION) ? tank.drain(maxDrain, action) : FluidStack.EMPTY;
    }
}
