package com.seongmin0218.magoaddon.client;

import com.seongmin0218.magoaddon.MagoAddon;
import com.seongmin0218.magoaddon.client.render.RailgunBeamRenderer;
import com.seongmin0218.magoaddon.registry.MagoEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = MagoAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class MagoClientModEvents {

    private MagoClientModEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerEntityRenderer(
                MagoEntities.RAILGUN_BEAM.get(),
                RailgunBeamRenderer::new
        );
    }
}