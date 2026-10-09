package com.mycelialpower;

import com.mycelialpower.capability.ModCapabilities;
import com.mycelialpower.config.ClientConfig;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.event.CommonEvents;
import com.mycelialpower.net.ModNetworking;
import com.mycelialpower.registry.ModBlockEntities;
import com.mycelialpower.registry.ModBlocks;
import com.mycelialpower.registry.ModCreativeTabs;
import com.mycelialpower.registry.ModItems;
import com.mycelialpower.registry.ModMenus;
import com.mycelialpower.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MycelialPower.MOD_ID)
public final class MycelialPower {
    public static final String MOD_ID = "mycelialpower";
    public static final Logger LOGGER = LoggerFactory.getLogger("MycelialPower");

    public MycelialPower(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.CREATIVE_TABS.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modBus.addListener(ModCapabilities::register);
        modBus.addListener(ModNetworking::register);
        modBus.addListener(CommonEvents::onConfigLoading);
        modBus.addListener(CommonEvents::onConfigReloading);

        NeoForge.EVENT_BUS.addListener(CommonEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(CommonEvents::onServerTickPost);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
