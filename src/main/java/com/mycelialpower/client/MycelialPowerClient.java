package com.mycelialpower.client;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.client.screen.MycelialGeneratorScreen;
import com.mycelialpower.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point: screens and the in-game config screen. */
@Mod(value = MycelialPower.MOD_ID, dist = Dist.CLIENT)
public final class MycelialPowerClient {
    public MycelialPowerClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(MycelialPowerClient::registerScreens);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MYCELIAL_GENERATOR.get(), MycelialGeneratorScreen::new);
    }
}
