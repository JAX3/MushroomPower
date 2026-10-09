package com.mycelialpower.capability;

import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.util.Matchers;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Water tank whose accepted fluids and capacity come from the server config. */
public class GeneratorFluidTank extends FluidTank {
    private final Runnable onChanged;

    public GeneratorFluidTank(Runnable onChanged) {
        super(ServerConfig.get(ServerConfig.TANK_CAPACITY), Matchers::isAcceptedFluid);
        this.onChanged = onChanged;
    }

    /** Re-reads the capacity from the config, discarding fluid above a reduced capacity. */
    public void syncCapacity() {
        int configured = ServerConfig.get(ServerConfig.TANK_CAPACITY);
        if (configured != capacity) {
            setCapacity(configured);
            if (fluid.getAmount() > configured) {
                fluid.setAmount(configured);
                onContentsChanged();
            }
        }
    }

    @Override
    protected void onContentsChanged() {
        onChanged.run();
    }
}
