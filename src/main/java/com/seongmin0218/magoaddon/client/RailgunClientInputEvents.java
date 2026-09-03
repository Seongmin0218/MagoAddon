package com.seongmin0218.magoaddon.client;

import com.seongmin0218.magoaddon.MagoAddon;
import com.seongmin0218.magoaddon.network.RailgunReleasePayload;
import com.seongmin0218.magoaddon.spell.MagoSpells;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.player.KeyMappings;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = MagoAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class RailgunClientInputEvents {

    private static boolean wasCastKeyDown =
            false;

    private RailgunClientInputEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(
            ClientTickEvent.Post event
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();

        boolean castKeyDown =
                KeyMappings
                        .SPELLBOOK_CAST_ACTIVE_KEYMAP
                        .isDown();

        boolean released =
                wasCastKeyDown
                        &&
                !castKeyDown;

        /*
         * GUI를 열어서 키가 풀린 경우는
         * "발사"로 취급하지 않는다.
         *
         * Iron's 자체 CancelCast 로직에게 맡긴다.
         */
        if (
                released
                        &&
                minecraft.screen == null
                        &&
                ClientMagicData.isCasting()
                        &&
                ClientMagicData
                        .getCastingSpellId()
                        .equals(
                                MagoSpells
                                        .RAILGUN
                                        .get()
                                        .getSpellId()
                        )
        ) {
            PacketDistributor.sendToServer(
                    new RailgunReleasePayload()
            );
        }

        wasCastKeyDown =
                castKeyDown;
    }
}