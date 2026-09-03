package com.seongmin0218.magoaddon.network;

import com.seongmin0218.magoaddon.MagoAddon;
import com.seongmin0218.magoaddon.spell.MagoSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RailgunReleasePayload
        implements CustomPacketPayload {

    public static final Type<RailgunReleasePayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            MagoAddon.MOD_ID,
                            "railgun_release"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RailgunReleasePayload
            > STREAM_CODEC =
            CustomPacketPayload.codec(
                    RailgunReleasePayload::write,
                    RailgunReleasePayload::new
            );

    public RailgunReleasePayload() {
    }

    public RailgunReleasePayload(
            FriendlyByteBuf buffer
    ) {
    }

    private void write(
            FriendlyByteBuf buffer
    ) {
        /*
         * payload 자체에는 데이터가 필요 없다.
         *
         * 서버 MagicData가 충전량을 authoritative하게 판단한다.
         */
    }

    public static void handle(
            RailgunReleasePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(
                () -> {
                    if (
                            !(
                                    context.player()
                                            instanceof
                                    ServerPlayer serverPlayer
                            )
                    ) {
                        return;
                    }

                    MagicData magicData =
                            MagicData.getPlayerMagicData(
                                    serverPlayer
                            );

                    if (
                            !magicData.isCasting()
                    ) {
                        return;
                    }

                    /*
                     * 다른 LONG 주문을 놓았을 때
                     * 레일건으로 오인하지 않는다.
                     */
                    if (
                            !magicData
                                    .getCastingSpellId()
                                    .equals(
                                            MagoSpells
                                                    .RAILGUN
                                                    .get()
                                                    .getSpellId()
                                    )
                    ) {
                        return;
                    }

                    int spellLevel =
                            magicData.getCastingSpellLevel();

                    CastSource castSource =
                            magicData.getCastSource();

                    /*
                     * 현재까지 충전된 MagicData를 그대로 유지한 채
                     * castSpell()을 실행.
                     *
                     * RailgunSpell.onCast()가 여기서 stage를 읽는다.
                     */
                    MagoSpells
                            .RAILGUN
                            .get()
                            .castSpell(
                                    serverPlayer.level(),
                                    spellLevel,
                                    serverPlayer,
                                    castSource,
                                    true
                            );

                    /*
                     * Scroll 사용 시 기존 Iron's와 동일하게 소모.
                     */
                    if (
                            castSource
                                    ==
                            CastSource.SCROLL
                    ) {
                        Scroll.attemptRemoveScrollAfterCast(
                                serverPlayer
                        );
                    }

                    /*
                     * 캐스팅 상태 종료 및 클라이언트 동기화.
                     */
                    MagoSpells
                            .RAILGUN
                            .get()
                            .onServerCastComplete(
                                    serverPlayer.level(),
                                    spellLevel,
                                    serverPlayer,
                                    magicData,
                                    false
                            );
                }
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}