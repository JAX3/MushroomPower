package com.mycelialpower.registry;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.item.MyceliumWrenchItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MycelialPower.MOD_ID);

    public static final DeferredItem<BlockItem> MYCELIAL_GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.MYCELIAL_GENERATOR);

    public static final DeferredItem<MyceliumWrenchItem> MYCELIUM_WRENCH = ITEMS.register("mycelium_wrench",
            () -> new MyceliumWrenchItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
