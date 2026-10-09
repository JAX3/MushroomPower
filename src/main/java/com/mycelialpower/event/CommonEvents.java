package com.mycelialpower.event;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.config.ServerConfig;
import com.mycelialpower.fuel.FuelRegistry;
import com.mycelialpower.network.GeneratorNetworkManager;
import com.mycelialpower.util.Matchers;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Keeps caches consistent with the config, tags and world lifecycle. */
public final class CommonEvents {
    private CommonEvents() {
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) {
            invalidateCaches();
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) {
            MycelialPower.LOGGER.info("Mycelial Power server config reloaded; rebuilding fuel list and generator networks");
            invalidateCaches();
        }
    }

    private static void invalidateCaches() {
        FuelRegistry.invalidate();
        Matchers.invalidateAll();
        // Config reload events may arrive on the config watcher thread; network rebuilds are flagged
        // here and executed lazily on the server thread.
        GeneratorNetworkManager.markAllDirty();
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        FuelRegistry.invalidate();
        Matchers.invalidateAll();
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GeneratorNetworkManager.removeLevel(level.dimension());
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        GeneratorNetworkManager.clearAll();
    }

    /** Applies pending network changes once per tick even when no generator queried them. */
    public static void onServerTickPost(ServerTickEvent.Post event) {
        GeneratorNetworkManager.forEach(GeneratorNetworkManager::update);
    }
}
