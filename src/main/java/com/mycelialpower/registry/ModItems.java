package com.mycelialpower.registry;

import com.mycelialpower.MycelialPower;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MycelialPower.MOD_ID);

    public static final DeferredItem<BlockItem> MYCELIAL_GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.MYCELIAL_GENERATOR);

    private ModItems() {
    }
}
