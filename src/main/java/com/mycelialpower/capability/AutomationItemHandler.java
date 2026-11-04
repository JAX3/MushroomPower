package com.mycelialpower.capability;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The inventory as seen by hoppers and pipes: mushrooms and filled containers may be inserted, only
 * emptied containers may be extracted.
 */
public record AutomationItemHandler(GeneratorItemHandler inventory) implements ResourceHandler<ItemResource> {
    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return inventory.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return inventory.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return inventory.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index != GeneratorItemHandler.CONTAINER_OUT_SLOT && inventory.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index == GeneratorItemHandler.CONTAINER_OUT_SLOT) {
            return 0;
        }
        return inventory.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index != GeneratorItemHandler.CONTAINER_OUT_SLOT) {
            return 0;
        }
        return inventory.extract(index, resource, amount, transaction);
    }
}
