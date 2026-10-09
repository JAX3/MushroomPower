package com.mycelialpower.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(GeneratorSyncPayload.TYPE, GeneratorSyncPayload.STREAM_CODEC, GeneratorSyncPayload::handle);
        registrar.playToServer(FuelTransferPayload.TYPE, FuelTransferPayload.STREAM_CODEC, FuelTransferPayload::handle);
    }
}
