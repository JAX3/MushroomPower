package com.mycelialpower.registry;

import com.mycelialpower.MycelialPower;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MycelialPower.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MYCELIAL_POWER = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mycelialpower"))
                    .icon(() -> new ItemStack(ModItems.MYCELIAL_GENERATOR.get()))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((parameters, output) -> output.accept(ModItems.MYCELIAL_GENERATOR.get()))
                    .build());

    private ModCreativeTabs() {
    }
}
