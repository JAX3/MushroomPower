package com.mycelialpower.registry;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.item.MyceliumWrenchItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MycelialPower.MOD_ID);

    public static final DeferredItem<BlockItem> MYCELIAL_GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.MYCELIAL_GENERATOR);

    // registerItem supplies Properties with the item's registry id already set (required since 1.21.2).
    public static final DeferredItem<MyceliumWrenchItem> MYCELIUM_WRENCH = ITEMS.registerItem("mycelium_wrench",
            MyceliumWrenchItem::new, properties -> properties.stacksTo(1));

    private ModItems() {
    }
}
