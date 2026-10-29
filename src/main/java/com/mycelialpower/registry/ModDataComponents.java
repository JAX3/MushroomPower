package com.mycelialpower.registry;

import com.mojang.serialization.Codec;
import com.mycelialpower.MycelialPower;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MycelialPower.MOD_ID);

    /** The generator a Mycelium Wrench has selected as the first end of a link. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> SELECTED_GENERATOR =
            COMPONENTS.register("selected_generator", () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC)
                    .build());

    /** Successful links made with a Mycelium Wrench (for the configurable durability). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WRENCH_USES =
            COMPONENTS.register("wrench_uses", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * Game time until which a Mycelium Wrench shows the enchantment glint after a successful link.
     * Not saved to disk (a short visual effect), but synced so the client renders the glint.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> LINK_GLOW_UNTIL =
            COMPONENTS.register("link_glow_until", () -> DataComponentType.<Long>builder()
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build());

    private ModDataComponents() {
    }
}
