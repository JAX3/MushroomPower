package com.mycelialpower.capability;

import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.util.Matchers;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Single water tank whose accepted fluids and capacity come from the server config. */
public class GeneratorFluidTank extends FluidStacksResourceHandler {
    private final Runnable onChanged;

    public GeneratorFluidTank(Runnable onChanged) {
        super(1, ServerConfig.get(ServerConfig.TANK_CAPACITY));
        this.onChanged = onChanged;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return Matchers.isAcceptedFluid(resource);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChanged.run();
    }

    /** Re-reads the capacity from the config, discarding fluid above a reduced capacity. */
    public void syncCapacity() {
        int configured = ServerConfig.get(ServerConfig.TANK_CAPACITY);
        if (configured != capacity) {
            capacity = configured;
            if (getFluidAmount() > configured) {
                set(0, getResource(0), configured);
            }
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public int getFluidAmount() {
        return getAmountAsInt(0);
    }

    public FluidStack getFluid() {
        return getResource(0).toStack(getFluidAmount());
    }

    /** Removes up to {@code amount} mB immediately. Returns the amount removed. */
    public int drainInternal(int amount) {
        FluidResource resource = getResource(0);
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int drained = extract(0, resource, amount, transaction);
            transaction.commit();
            return drained;
        }
    }

    /** Adds fluid immediately (used by tests and setup code). Returns the amount accepted. */
    public int fillInternal(FluidResource resource, int amount) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int filled = insert(0, resource, amount, transaction);
            transaction.commit();
            return filled;
        }
    }
}
