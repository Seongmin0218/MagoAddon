package com.seongmin0218.magoaddon.client;

import com.seongmin0218.magoaddon.MagoAddon;
import com.seongmin0218.magoaddon.client.render.BoilingBeamRenderer;
import com.seongmin0218.magoaddon.spell.MagoSpells;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(
        modid = MagoAddon.MOD_ID,
        value = Dist.CLIENT
)
public final class BoilingBeamClientEvents {

    private BoilingBeamClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderLevel(
            RenderLevelStageEvent event
    ) {
        /*
         * NeoForge가 특수 월드 효과용으로 제공하는
         * AFTER_PARTICLES 단계에서만 한 번 그린다.
         */
        if (
                event.getStage()
                        !=
                RenderLevelStageEvent.Stage.AFTER_PARTICLES
        ) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        float partialTick =
                event
                        .getPartialTick()
                        .getGameTimeDeltaPartialTick(true);

        /*
         * 현재 클라이언트가 알고 있는 모든 플레이어를 검사.
         *
         * 따라서:
         *
         * 자기 자신 1인칭
         * 자기 자신 3인칭
         * 다른 플레이어
         *
         * 전부 렌더링 가능하다.
         */
        for (var player : minecraft.level.players()) {

            var syncedSpellData =
                    ClientMagicData
                            .getSyncedSpellData(
                                    player
                            );

            if (!syncedSpellData.isCasting()) {
                continue;
            }

            /*
             * 현재 캐스팅 중인 주문이
             * magoaddon:boiling_beam 인지 확인.
             */
            if (
                    !syncedSpellData
                            .getCastingSpellId()
                            .equals(
                                    MagoSpells
                                            .BOILING_BEAM
                                            .get()
                                            .getSpellId()
                            )
            ) {
                continue;
            }

            BoilingBeamRenderer.render(
                    player,
                    event.getPoseStack(),
                    event.getCamera(),
                    partialTick
            );
        }
    }
}