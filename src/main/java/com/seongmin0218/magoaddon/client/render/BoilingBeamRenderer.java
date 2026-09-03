package com.seongmin0218.magoaddon.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.seongmin0218.magoaddon.spell.BoilingBeamSpell;
import io.redspace.ironsspellbooks.api.util.RaycastBuilder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BoilingBeamRenderer {

    /*
     * 일단 별도 텍스처 없이 바닐라 Beacon Beam 텍스처 사용.
     *
     * 나중에는:
     * assets/magoaddon/textures/spell/boiling_beam.png
     *
     * 로 교체할 예정.
     */
    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "textures/entity/beacon_beam.png"
            );

    private static final RenderType BEAM_RENDER_TYPE =
            RenderType.entityTranslucentEmissive(
                    BEAM_TEXTURE
            );

    private BoilingBeamRenderer() {
    }

    public static void render(
            Player player,
            PoseStack poseStack,
            Camera camera,
            float partialTick
    ) {
        /*
         * 렌더링용 시선 좌표.
         *
         * 서버의 판정 좌표와는 별개이며
         * 순수하게 화면에 빔을 그리기 위해 사용한다.
         */
        Vec3 eyePosition =
                player.getEyePosition(partialTick);

        Vec3 lookDirection =
                player.getViewVector(partialTick)
                        .normalize();

        /*
         * 서버 BoilingBeamSpell과 동일한 Raycast.
         */
        HitResult hitResult =
                RaycastBuilder
                        .begin(
                                player.level(),
                                player
                        )
                        .start(
                                eyePosition
                        )
                        .end(
                                eyePosition.add(
                                        lookDirection.scale(
                                                BoilingBeamSpell.RANGE
                                        )
                                )
                        )
                        .checkForBlocks(true)
                        .bbInflation(0.20f)
                        .filter(Utils::canHitWithRaycast)
                        .build();

        /*
         * 실제 빔 시작점.
         *
         * 눈 바로 정중앙에서 시작하면 1인칭에서
         * 화면을 너무 많이 가리므로 살짝 앞으로 보내고
         * 약간 아래로 내린다.
         *
         * 나중에 여기만 손/지팡이 위치로 바꾸면 된다.
         */
        Vec3 beamStart =
                eyePosition
                        .add(
                                lookDirection.scale(0.35)
                        )
                        .add(
                                0,
                                -0.12,
                                0
                        );

        Vec3 beamEnd =
                hitResult.getLocation();

        Vec3 difference =
                beamEnd.subtract(
                        beamStart
                );

        double distance =
                difference.length();

        if (distance <= 0.01) {
            return;
        }

        Vec3 direction =
                difference.normalize();

        poseStack.pushPose();

        /*
         * RenderLevelStageEvent는 카메라 기준 렌더링이므로
         * 월드 좌표에서 카메라 좌표를 빼준다.
         */
        Vec3 cameraPosition =
                camera.getPosition();

        poseStack.translate(
                beamStart.x - cameraPosition.x,
                beamStart.y - cameraPosition.y,
                beamStart.z - cameraPosition.z
        );

        /*
         * 기본 +Z 방향으로 그릴 빔을
         * 실제 시선 방향으로 회전시킨다.
         *
         * Iron's의 Ray 렌더링도 동일한 방식의
         * atan2 회전을 사용한다.
         */
        float dx =
                (float) direction.x;

        float dy =
                (float) direction.y;

        float dz =
                (float) direction.z;

        float horizontalLength =
                Mth.sqrt(
                        dx * dx
                                +
                        dz * dz
                );

        float yRotation =
                (float) Mth.atan2(
                        dz,
                        dx
                )
                        -
                        ((float) Math.PI / 2.0f);

        float xRotation =
                (float) Mth.atan2(
                        dy,
                        horizontalLength
                );

        poseStack.mulPose(
                Axis.YP.rotation(
                        -yRotation
                )
        );

        poseStack.mulPose(
                Axis.XP.rotation(
                        -xRotation
                )
        );

        MultiBufferSource.BufferSource bufferSource =
                Minecraft
                        .getInstance()
                        .renderBuffers()
                        .bufferSource();

        VertexConsumer consumer =
                bufferSource.getBuffer(
                        BEAM_RENDER_TYPE
                );

        /*
         * 빔은 3겹으로 만든다.
         *
         * 1. 넓은 외곽 수증기성 광채
         * 2. 파란 물 코어
         * 3. 거의 흰색에 가까운 뜨거운 중심부
         *
         * 같은 공간에 크기가 다른 hull을 겹쳐
         * 레이저처럼 평평하지 않고 두께가 있게 보이게 한다.
         */

        Vec3 localStart =
                Vec3.ZERO;

        Vec3 localEnd =
                new Vec3(
                        0,
                        0,
                        distance
                );

        /*
         * OUTER GLOW
         */
        float time =
                player.tickCount
                        +
                partialTick;

        double segmentLength =
                0.45;

        for (
                double z = 0;
                z < distance;
                z += segmentLength
        ) {
            double nextZ =
                    Math.min(
                            z + segmentLength,
                            distance
                    );

            /*
            * 시간 + 빔상의 위치를 같이 사용해서
            * 물줄기 굵기가 파도처럼 흐르게 한다.
            */
            float pulse =
                    1.0f
                            +
                    Mth.sin(
                            time * 0.75f
                                    +
                            (float) z * 1.65f
                    )
                            *
                    0.10f;

            /*
            * 너무 기계적인 정현파가 되지 않도록
            * 두 번째 파형을 살짝 섞는다.
            */
            pulse +=
                    Mth.sin(
                            time * 1.25f
                                    -
                            (float) z * 0.85f
                    )
                            *
                    0.035f;

            Vec3 segmentStart =
                    new Vec3(
                            0,
                            0,
                            z
                    );

            Vec3 segmentEnd =
                    new Vec3(
                            0,
                            0,
                            nextZ
                    );

            /*
            * 외부 수막
            */
            drawHull(
                    segmentStart,
                    segmentEnd,
                    0.34f * pulse,
                    0.34f * pulse,
                    poseStack.last(),
                    consumer,
                    50,
                    145,
                    255,
                    65
            );

            /*
            * 물 본체
            */
            drawHull(
                    segmentStart,
                    segmentEnd,
                    0.20f * pulse,
                    0.20f * pulse,
                    poseStack.last(),
                    consumer,
                    40,
                    170,
                    255,
                    180
            );

            /*
            * 밝은 중심부는 흔들림을 약하게.
            */
            float corePulse =
                    1.0f
                            +
                    (pulse - 1.0f)
                            *
                    0.35f;

            drawHull(
                    segmentStart,
                    segmentEnd,
                    0.075f * corePulse,
                    0.075f * corePulse,
                    poseStack.last(),
                    consumer,
                    210,
                    245,
                    255,
                    235
            );
        }

        /*
         * 우리가 사용한 RenderType만 flush.
         */
        bufferSource.endBatch(
                BEAM_RENDER_TYPE
        );

        poseStack.popPose();
    }

    /**
     * 직육면체 형태의 빔.
     *
     * 위 / 아래 / 왼쪽 / 오른쪽
     * 총 네 면을 그린다.
     */
    private static void drawHull(
            Vec3 from,
            Vec3 to,
            float width,
            float height,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        /*
         * 아래
         */
        drawQuad(
                from.subtract(
                        0,
                        height * 0.5f,
                        0
                ),
                to.subtract(
                        0,
                        height * 0.5f,
                        0
                ),
                width,
                0,
                pose,
                consumer,
                red,
                green,
                blue,
                alpha
        );

        /*
         * 위
         */
        drawQuad(
                from.add(
                        0,
                        height * 0.5f,
                        0
                ),
                to.add(
                        0,
                        height * 0.5f,
                        0
                ),
                width,
                0,
                pose,
                consumer,
                red,
                green,
                blue,
                alpha
        );

        /*
         * 왼쪽
         */
        drawQuad(
                from.subtract(
                        width * 0.5f,
                        0,
                        0
                ),
                to.subtract(
                        width * 0.5f,
                        0,
                        0
                ),
                0,
                height,
                pose,
                consumer,
                red,
                green,
                blue,
                alpha
        );

        /*
         * 오른쪽
         */
        drawQuad(
                from.add(
                        width * 0.5f,
                        0,
                        0
                ),
                to.add(
                        width * 0.5f,
                        0,
                        0
                ),
                0,
                height,
                pose,
                consumer,
                red,
                green,
                blue,
                alpha
        );
    }

    private static void drawQuad(
            Vec3 from,
            Vec3 to,
            float width,
            float height,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        float halfWidth =
                width * 0.5f;

        float halfHeight =
                height * 0.5f;

        /*
         * Vertex 1
         */
        consumer
                .addVertex(
                        pose.pose(),
                        (float) from.x - halfWidth,
                        (float) from.y - halfHeight,
                        (float) from.z
                )
                .setColor(
                        red,
                        green,
                        blue,
                        alpha
                )
                .setUv(
                        0.0f,
                        1.0f
                )
                .setOverlay(
                        OverlayTexture.NO_OVERLAY
                )
                .setLight(
                        LightTexture.FULL_BRIGHT
                )
                .setNormal(
                        0.0f,
                        1.0f,
                        0.0f
                );

        /*
         * Vertex 2
         */
        consumer
                .addVertex(
                        pose.pose(),
                        (float) from.x + halfWidth,
                        (float) from.y + halfHeight,
                        (float) from.z
                )
                .setColor(
                        red,
                        green,
                        blue,
                        alpha
                )
                .setUv(
                        1.0f,
                        1.0f
                )
                .setOverlay(
                        OverlayTexture.NO_OVERLAY
                )
                .setLight(
                        LightTexture.FULL_BRIGHT
                )
                .setNormal(
                        0.0f,
                        1.0f,
                        0.0f
                );

        /*
         * Vertex 3
         */
        consumer
                .addVertex(
                        pose.pose(),
                        (float) to.x + halfWidth,
                        (float) to.y + halfHeight,
                        (float) to.z
                )
                .setColor(
                        red,
                        green,
                        blue,
                        alpha
                )
                .setUv(
                        1.0f,
                        0.0f
                )
                .setOverlay(
                        OverlayTexture.NO_OVERLAY
                )
                .setLight(
                        LightTexture.FULL_BRIGHT
                )
                .setNormal(
                        0.0f,
                        1.0f,
                        0.0f
                );

        /*
         * Vertex 4
         */
        consumer
                .addVertex(
                        pose.pose(),
                        (float) to.x - halfWidth,
                        (float) to.y - halfHeight,
                        (float) to.z
                )
                .setColor(
                        red,
                        green,
                        blue,
                        alpha
                )
                .setUv(
                        0.0f,
                        0.0f
                )
                .setOverlay(
                        OverlayTexture.NO_OVERLAY
                )
                .setLight(
                        LightTexture.FULL_BRIGHT
                )
                .setNormal(
                        0.0f,
                        1.0f,
                        0.0f
                );
    }
}