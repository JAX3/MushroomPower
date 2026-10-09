package com.mycelialpower.capability;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The inventory as seen by hoppers and pipes: mushrooms and filled containers may be inserted, only
 * emptied containers may be extracted.
 */
public record AutomationItemHandler(GeneratorItemHandler inventory) implements IItemHandler {
    @Override
    public int getSlots() {
        return inventory.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot == GeneratorItemHandler.CONTAINER_OUT_SLOT) {
            return stack;
        }
        return inventory.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != GeneratorItemHandler.CONTAINER_OUT_SLOT) {
            return ItemStack.EMPTY;
        }
        return inventory.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot != GeneratorItemHandler.CONTAINER_OUT_SLOT && inventory.isItemValid(slot, stack);
    }
}
