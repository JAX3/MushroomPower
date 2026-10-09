package com.mycelialpower.capability;

import com.mycelialpower.fuel.FuelRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The generator inventory:
 * <ol start="0">
 *     <li>{@link #FUEL_SLOT} mushroom fuel</li>
 *     <li>{@link #CONTAINER_IN_SLOT} filled fluid containers (buckets) to empty into the tank</li>
 *     <li>{@link #CONTAINER_OUT_SLOT} emptied containers (output only)</li>
 * </ol>
 */
public class GeneratorItemHandler extends ItemStackHandler {
    public static final int FUEL_SLOT = 0;
    public static final int CONTAINER_IN_SLOT = 1;
    public static final int CONTAINER_OUT_SLOT = 2;
    public static final int SLOTS = 3;

    private final Runnable onChanged;

    public GeneratorItemHandler(Runnable onChanged) {
        super(SLOTS);
        this.onChanged = onChanged;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case FUEL_SLOT -> FuelRegistry.isFuel(stack);
            case CONTAINER_IN_SLOT -> stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
            default -> false;
        };
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }

    /**
     * Inserts into the output slot, bypassing {@link #isItemValid}. Returns the remainder that did not fit.
     */
    public ItemStack insertOutput(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = getStackInSlot(CONTAINER_OUT_SLOT);
        int limit = Math.min(getSlotLimit(CONTAINER_OUT_SLOT), stack.getMaxStackSize());
        if (existing.isEmpty()) {
            int moved = Math.min(limit, stack.getCount());
            if (!simulate) {
                setStackInSlot(CONTAINER_OUT_SLOT, stack.copyWithCount(moved));
            }
            return stack.getCount() > moved ? stack.copyWithCount(stack.getCount() - moved) : ItemStack.EMPTY;
        }
        if (!ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }
        int moved = Math.min(limit - existing.getCount(), stack.getCount());
        if (moved <= 0) {
            return stack;
        }
        if (!simulate) {
            setStackInSlot(CONTAINER_OUT_SLOT, existing.copyWithCount(existing.getCount() + moved));
        }
        return stack.getCount() > moved ? stack.copyWithCount(stack.getCount() - moved) : ItemStack.EMPTY;
    }
}
