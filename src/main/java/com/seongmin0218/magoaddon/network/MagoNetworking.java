package com.seongmin0218.magoaddon.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MagoNetworking {

    private static final String NETWORK_VERSION = "1";

    private MagoNetworking() {
    }

    public static void register(
            RegisterPayloadHandlersEvent event
    ) {
        PayloadRegistrar registrar =
                event.registrar(
                        NETWORK_VERSION
                );

        registrar.playToServer(
                RailgunReleasePayload.TYPE,
                RailgunReleasePayload.STREAM_CODEC,
                RailgunReleasePayload::handle
        );
    }
}