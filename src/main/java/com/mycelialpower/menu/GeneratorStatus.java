package com.mycelialpower.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server-computed snapshot of everything the generator GUI displays. Sent to the viewing player only;
 * the client never computes gameplay values itself.
 */
public record GeneratorStatus(
        int energy, int energyCapacity, double generation, boolean running, double extraction,
        String fluidId, int fluidAmount, int tankCapacity, double waterPerInterval, int waterInterval,
        String fuelItem, int burnRemaining, int burnTotal,
        int networkSize, int networkActive, double networkScale, double networkTotal, int networkStatus, int maxNetworkSize,
        boolean myceliumEnabled, double chance, double chanceIncrease, int attemptProgress, int attemptInterval,
        int converted, boolean trackConversions,
        int state) {

    public static final GeneratorStatus EMPTY = new GeneratorStatus(0, 1, 0, false, 0, "", 0, 1, 0, 20, "", 0, 0,
            1, 0, 1, 0, 1, 64, false, 0, 0, 0, 100, 0, false, 1);

    public static final StreamCodec<FriendlyByteBuf, GeneratorStatus> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> s.write(buf), GeneratorStatus::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(energy);
        buf.writeVarInt(energyCapacity);
        buf.writeDouble(generation);
        buf.writeBoolean(running);
        buf.writeDouble(extraction);
        buf.writeUtf(fluidId, 256);
        buf.writeVarInt(fluidAmount);
        buf.writeVarInt(tankCapacity);
        buf.writeDouble(waterPerInterval);
        buf.writeVarInt(waterInterval);
        buf.writeUtf(fuelItem, 256);
        buf.writeVarInt(burnRemaining);
        buf.writeVarInt(burnTotal);
        buf.writeVarInt(networkSize);
        buf.writeVarInt(networkActive);
        buf.writeDouble(networkScale);
        buf.writeDouble(networkTotal);
        buf.writeVarInt(networkStatus);
        buf.writeVarInt(maxNetworkSize);
        buf.writeBoolean(myceliumEnabled);
        buf.writeDouble(chance);
        buf.writeDouble(chanceIncrease);
        buf.writeVarInt(attemptProgress);
        buf.writeVarInt(attemptInterval);
        buf.writeVarInt(converted);
        buf.writeBoolean(trackConversions);
        buf.writeVarInt(state);
    }

    private static GeneratorStatus read(FriendlyByteBuf buf) {
        return new GeneratorStatus(
                buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readBoolean(), buf.readDouble(),
                buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readVarInt(),
                buf.readUtf(256), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt());
    }
}
