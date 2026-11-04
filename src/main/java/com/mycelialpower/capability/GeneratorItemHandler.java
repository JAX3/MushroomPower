package com.mycelialpower.capability;

import com.mycelialpower.fuel.FuelRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The generator inventory:
 * <ol start="0">
 *     <li>{@link #FUEL_SLOT} mushroom fuel</li>
 *     <li>{@link #CONTAINER_IN_SLOT} filled fluid containers (buckets) to empty into the tank</li>
 *     <li>{@link #CONTAINER_OUT_SLOT} emptied containers (output only)</li>
 * </ol>
 * {@link #isValid} controls what players and automation may insert; the generator itself writes with
 * {@link #set}, which bypasses it.
 */
public class GeneratorItemHandler extends ItemStacksResourceHandler {
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
    public boolean isValid(int index, ItemResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        return switch (index) {
            case FUEL_SLOT -> FuelRegistry.isFuel(resource.toStack());
            case CONTAINER_IN_SLOT -> isFluidContainer(resource.toStack());
            default -> false;
        };
    }

    public static boolean isFluidContainer(ItemStack stack) {
        return !stack.isEmpty() && ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Fluid.ITEM) != null;
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }

    public ItemStack getStackInSlot(int index) {
        return getResource(index).toStack(getAmountAsInt(index));
    }

    public void setStackInSlot(int index, ItemStack stack) {
        set(index, stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack), stack.getCount());
    }

    /** Removes one item from a slot. */
    public void shrink(int index) {
        ItemStack stack = getStackInSlot(index);
        if (!stack.isEmpty()) {
            stack.shrink(1);
            setStackInSlot(index, stack);
        }
    }

    /** Whether {@code stack} fits completely into the output slot. */
    public boolean canOutput(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        ItemStack existing = getStackInSlot(CONTAINER_OUT_SLOT);
        if (existing.isEmpty()) {
            return stack.getCount() <= stack.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(existing, stack)
                && existing.getCount() + stack.getCount() <= existing.getMaxStackSize();
    }

    /** Adds {@code stack} to the output slot, bypassing {@link #isValid}. Call {@link #canOutput} first. */
    public void addOutput(ItemStack stack) {
        if (stack.isEmpty() || !canOutput(stack)) {
            return;
        }
        ItemStack existing = getStackInSlot(CONTAINER_OUT_SLOT);
        setStackInSlot(CONTAINER_OUT_SLOT, existing.isEmpty() ? stack.copy() : existing.copyWithCount(existing.getCount() + stack.getCount()));
    }
}
