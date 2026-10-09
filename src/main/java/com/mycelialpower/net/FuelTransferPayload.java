package com.mycelialpower.net;

import com.mycelialpower.MycelialPower;
import com.mycelialpower.menu.MycelialGeneratorMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client to server: move matching mushrooms from the player's inventory into the fuel slot of the open
 * generator (used by JEI recipe transfer). The server re-validates everything.
 */
public record FuelTransferPayload(int containerId, List<ResourceLocation> items, boolean maxTransfer) implements CustomPacketPayload {
    public static final int MAX_ITEMS = 256;
    public static final Type<FuelTransferPayload> TYPE = new Type<>(MycelialPower.id("fuel_transfer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FuelTransferPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FuelTransferPayload::containerId,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), FuelTransferPayload::items,
            ByteBufCodecs.BOOL, FuelTransferPayload::maxTransfer,
            FuelTransferPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FuelTransferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu instanceof MycelialGeneratorMenu menu && menu.containerId == payload.containerId()
                    && menu.stillValid(player)) {
                Set<Item> items = new HashSet<>();
                for (ResourceLocation id : payload.items()) {
                    BuiltInRegistries.ITEM.getOptional(id).ifPresent(items::add);
                }
                menu.transferFuelFromInventory(items, payload.maxTransfer());
            }
        });
    }
}
