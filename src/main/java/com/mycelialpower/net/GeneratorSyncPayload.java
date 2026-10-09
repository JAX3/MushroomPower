package com.mycelialpower.net;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.menu.GeneratorStatus;
import com.mycelialpower.menu.MycelialGeneratorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the latest {@link GeneratorStatus} for an open generator menu. */
public record GeneratorSyncPayload(int containerId, GeneratorStatus status) implements CustomPacketPayload {
    public static final Type<GeneratorSyncPayload> TYPE = new Type<>(MycelialPower.id("generator_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GeneratorSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GeneratorSyncPayload::containerId,
            GeneratorStatus.STREAM_CODEC, GeneratorSyncPayload::status,
            GeneratorSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Uses only common classes ({@link Player#containerMenu}) so it is safe to load on a dedicated server. */
    public static void handle(GeneratorSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu instanceof MycelialGeneratorMenu menu && menu.containerId == payload.containerId()) {
                menu.setStatus(payload.status());
            }
        });
    }
}
