package com.mycelialpower.registry;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.menu.MycelialGeneratorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MycelialPower.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<MycelialGeneratorMenu>> MYCELIAL_GENERATOR =
            MENUS.register("mycelial_generator", () -> IMenuTypeExtension.create(MycelialGeneratorMenu::fromNetwork));

    private ModMenus() {
    }
}
