package com.seongmin0218.magoaddon.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.seongmin0218.magoaddon.entity.spell.RailgunBeamVisualEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class RailgunBeamRenderer
        extends EntityRenderer<RailgunBeamVisualEntity> {

    private static final ResourceLocation GUARDIAN_BEAM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "textures/entity/guardian_beam.png"
            );

    private static final RenderType BEAM_RENDER_TYPE =
            RenderType.entityTranslucentEmissive(
                    GUARDIAN_BEAM_TEXTURE
            );

    private static final RenderType SOLID_CORE_RENDER_TYPE =
        RenderType.lightning();

    public RailgunBeamRenderer(
            EntityRendererProvider.Context context
    ) {
        super(
                context
        );
    }

    @Override
    public boolean shouldRender(
            RailgunBeamVisualEntity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ
    ) {
        /*
         * Entity 자체 히트박스는 작지만
         * 빔은 최대 50블록이므로 강제 렌더.
         */
        return true;
    }

    @Override
    public void render(
            RailgunBeamVisualEntity entity,
            float yaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        Vec3 beamVector =
                entity.getEnd()
                        .subtract(
                                entity.position()
                        );

        double distance =
                beamVector.length();

        if (distance <= 0.01) {
            return;
        }

        Vec3 direction =
                beamVector.normalize();

        float horizontal =
                Mth.sqrt(
                        (float)
                                (
                                        direction.x
                                                *
                                        direction.x
                                                +
                                        direction.z
                                                *
                                        direction.z
                                )
                );

        /*
         * local +Z 축을 실제 빔 방향으로 회전.
         */
        float yRotation =
                (float)
                        Mth.atan2(
                                direction.x,
                                direction.z
                        );

        float xRotation =
                (float)
                        -Mth.atan2(
                                direction.y,
                                horizontal
                        );

        float age =
                entity.tickCount
                        +
                partialTick;

        float fade =
                Mth.clamp(
                        1.0f
                                -
                        age
                                /
                        RailgunBeamVisualEntity.LIFETIME,
                        0.0f,
                        1.0f
                );

        int stage =
                entity.getStage();

        /*
         * 충전 단계에 따라 실제 빔이 굵어진다.
         */
        float outerRadius;
        float coreRadius;

        switch (stage) {
            case 1 -> {
                outerRadius = 0.18f;
                coreRadius = 0.05f;
            }

            case 2 -> {
                outerRadius = 0.3f;
                coreRadius = 0.1f;
            }

            case 3 -> {
                outerRadius = 0.5f;
                coreRadius = 0.19f;
            }

            case 4 -> {
                outerRadius = 1.0f;
                coreRadius = 0.33f;
            }

            default -> {
                // 5단계
                outerRadius = 1.5f;
                coreRadius = 0.5f;
            }
        }

        float laserRadius =
            switch (stage) {
                case 1 -> 0.025f;
                case 2 -> 0.035f;
                case 3 -> 0.050f;
                case 4 -> 0.24f;
                default -> 0.38f;
            };

        poseStack.pushPose();

        poseStack.mulPose(
                Axis.YP.rotation(
                        yRotation
                )
        );

        poseStack.mulPose(
                Axis.XP.rotation(
                        xRotation
                )
        );

        VertexConsumer consumer =
                bufferSource.getBuffer(
                        BEAM_RENDER_TYPE
                );

        /*
         * OUTER
         *
         * 가디언 빔 텍스처가 빠르게 회전.
         */
        poseStack.pushPose();

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        age * 22.0f
                )
        );

        drawLayer(
                poseStack,
                consumer,
                (float) distance,
                outerRadius,
                age,
                stage,
                60,
                150,
                255,
                (int) (155 * fade),
                1.0f
        );

        poseStack.popPose();

        /*
        * INNER CORE
        *
        * 외곽 guardian beam.
        * OUTER보다 좁고 밝은 두 번째 에너지층.
        */
        poseStack.pushPose();

        poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                        age * -38.0f
                )
        );

        drawLayer(
                poseStack,
                consumer,
                (float) distance,
                coreRadius,
                age,
                stage,
                220,
                245,
                255,
                (int) (255 * fade),
                1.75f
        );

        poseStack.popPose();

        /*
        * DEEP CORE LASER
        *
        * guardian_beam 텍스처의 텅 빈 중심을
        * 실제 단색 레이저로 채운다.
        *
        * 이 consumer는 위의 guardian beam consumer와 별개다.
        */
        VertexConsumer solidConsumer =
                bufferSource.getBuffer(
                        SOLID_CORE_RENDER_TYPE
                );

        drawSolidLaser(
                poseStack,
                solidConsumer,
                (float) distance,
                laserRadius,
                245,
                252,
                255,
                (int) (255 * fade)
        );


        /*
        * render() 초반의 poseStack.pushPose() 종료.
        */
        poseStack.popPose();
        super.render(
                entity,
                yaw,
                partialTick,
                poseStack,
                bufferSource,
                packedLight
        );
    }

    private static void drawLayer(
            PoseStack poseStack,
            VertexConsumer consumer,
            float distance,
            float baseRadius,
            float age,
            int stage,
            int red,
            int green,
            int blue,
            int alpha,
            float pulseSpeed
    ) {
        if (alpha <= 0) {
            return;
        }

        float segmentLength =
                0.55f;

        /*
         * 길게 한 덩어리를 그리지 않고
         * 짧은 구간을 연속으로 배치한다.
         *
         * Eldritch Blast 같은 단단한 덩어리감을 만드는 부분.
         */
        for (
                float z = 0;
                z < distance;
                z += segmentLength
        ) {
            float nextZ =
                    Math.min(
                            z + segmentLength,
                            distance
                    );

            float pulseStrength =
                stage >= 4
                        ? 0.075f + (stage - 4) * 0.025f
                        : 0.025f + stage * 0.008f;

            float pulse =
                    1.0f
                            +
                    Mth.sin(
                            age
                                    *
                            pulseSpeed
                                    +
                            z
                                    *
                            2.3f
                    )
                            *
                    pulseStrength;

            float radius =
                    baseRadius
                            *
                    pulse;

            float scroll =
                    (
                            age * 0.06f
                                    +
                            z * 0.035f
                    )
                            %
                    1.0f;

            drawPrismSegment(
                    poseStack.last(),
                    consumer,
                    z,
                    nextZ,
                    radius,
                    red,
                    green,
                    blue,
                    alpha,
                    scroll
            );
        }
    }

    private static void drawPrismSegment(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float z0,
            float z1,
            float radius,
            int red,
            int green,
            int blue,
            int alpha,
            float scroll
    ) {
        float v0 =
                scroll;

        float v1 =
                scroll
                        +
                (z1 - z0) * 0.65f;

        /*
         * +X
         */
        emitQuad(
                pose,
                consumer,

                radius,
                -radius,
                z0,

                radius,
                -radius,
                z1,

                radius,
                radius,
                z1,

                radius,
                radius,
                z0,

                1,
                0,
                0,

                red,
                green,
                blue,
                alpha,

                v0,
                v1
        );

        /*
         * -X
         */
        emitQuad(
                pose,
                consumer,

                -radius,
                radius,
                z0,

                -radius,
                radius,
                z1,

                -radius,
                -radius,
                z1,

                -radius,
                -radius,
                z0,

                -1,
                0,
                0,

                red,
                green,
                blue,
                alpha,

                v0,
                v1
        );

        /*
         * +Y
         */
        emitQuad(
                pose,
                consumer,

                radius,
                radius,
                z0,

                radius,
                radius,
                z1,

                -radius,
                radius,
                z1,

                -radius,
                radius,
                z0,

                0,
                1,
                0,

                red,
                green,
                blue,
                alpha,

                v0,
                v1
        );

        /*
         * -Y
         */
        emitQuad(
                pose,
                consumer,

                -radius,
                -radius,
                z0,

                -radius,
                -radius,
                z1,

                radius,
                -radius,
                z1,

                radius,
                -radius,
                z0,

                0,
                -1,
                0,

                red,
                green,
                blue,
                alpha,

                v0,
                v1
        );
    }

    private static void emitQuad(
            PoseStack.Pose pose,
            VertexConsumer consumer,

            float x1,
            float y1,
            float z1,

            float x2,
            float y2,
            float z2,

            float x3,
            float y3,
            float z3,

            float x4,
            float y4,
            float z4,

            float normalX,
            float normalY,
            float normalZ,

            int red,
            int green,
            int blue,
            int alpha,

            float v0,
            float v1
    ) {
        vertex(
                pose,
                consumer,
                x1,
                y1,
                z1,
                0,
                v0,
                normalX,
                normalY,
                normalZ,
                red,
                green,
                blue,
                alpha
        );

        vertex(
                pose,
                consumer,
                x2,
                y2,
                z2,
                0,
                v1,
                normalX,
                normalY,
                normalZ,
                red,
                green,
                blue,
                alpha
        );

        vertex(
                pose,
                consumer,
                x3,
                y3,
                z3,
                1,
                v1,
                normalX,
                normalY,
                normalZ,
                red,
                green,
                blue,
                alpha
        );

        vertex(
                pose,
                consumer,
                x4,
                y4,
                z4,
                1,
                v0,
                normalX,
                normalY,
                normalZ,
                red,
                green,
                blue,
                alpha
        );
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float x,
            float y,
            float z,
            float u,
            float v,
            float normalX,
            float normalY,
            float normalZ,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        consumer
                .addVertex(
                        pose.pose(),
                        x,
                        y,
                        z
                )
                .setColor(
                        red,
                        green,
                        blue,
                        alpha
                )
                .setUv(
                        u,
                        v
                )
                .setOverlay(
                        OverlayTexture.NO_OVERLAY
                )
                .setLight(
                        LightTexture.FULL_BRIGHT
                )
                .setNormal(
                        pose,
                        normalX,
                        normalY,
                        normalZ
                );
    }

    @Override
    public ResourceLocation getTextureLocation(
            RailgunBeamVisualEntity entity
    ) {
        return GUARDIAN_BEAM_TEXTURE;
    }

    private static void drawSolidLaser(
            PoseStack poseStack,
            VertexConsumer consumer,
            float distance,
            float radius,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        PoseStack.Pose pose =
                poseStack.last();

        /*
        * 가로 방향 판.
        *
        * 빔 중심을 그대로 관통하기 때문에
        * guardian beam의 빈 속을 메운다.
        */
        emitSolidQuad(
                pose,
                consumer,

                -radius,
                0,
                0,

                -radius,
                0,
                distance,

                radius,
                0,
                distance,

                radius,
                0,
                0,

                red,
                green,
                blue,
                alpha
        );

        /*
        * 세로 방향 판.
        */
        emitSolidQuad(
                pose,
                consumer,

                0,
                -radius,
                0,

                0,
                -radius,
                distance,

                0,
                radius,
                distance,

                0,
                radius,
                0,

                red,
                green,
                blue,
                alpha
        );

        /*
        * 대각선 판 하나 추가.
        *
        * 카메라 각도에 따라 중심이 얇게 보이는 현상을 줄인다.
        */
        float diagonal =
                radius * 0.7071f;

        emitSolidQuad(
                pose,
                consumer,

                -diagonal,
                -diagonal,
                0,

                -diagonal,
                -diagonal,
                distance,

                diagonal,
                diagonal,
                distance,

                diagonal,
                diagonal,
                0,

                red,
                green,
                blue,
                alpha
        );

        /*
        * 반대 대각선.
        */
        emitSolidQuad(
                pose,
                consumer,

                diagonal,
                -diagonal,
                0,

                diagonal,
                -diagonal,
                distance,

                -diagonal,
                diagonal,
                distance,

                -diagonal,
                diagonal,
                0,

                red,
                green,
                blue,
                alpha
        );
    }

    private static void emitSolidQuad(
            PoseStack.Pose pose,
            VertexConsumer consumer,

            float x1,
            float y1,
            float z1,

            float x2,
            float y2,
            float z2,

            float x3,
            float y3,
            float z3,

            float x4,
            float y4,
            float z4,

            int red,
            int green,
            int blue,
            int alpha
    ) {
        solidVertex(
                pose,
                consumer,
                x1,
                y1,
                z1,
                red,
                green,
                blue,
                alpha
        );

        solidVertex(
                pose,
                consumer,
                x2,
                y2,
                z2,
                red,
                green,
                blue,
                alpha
        );

        solidVertex(
                pose,
                consumer,
                x3,
                y3,
                z3,
                red,
                green,
                blue,
                alpha
        );

        solidVertex(
                pose,
                consumer,
                x4,
                y4,
                z4,
                red,
                green,
                blue,
                alpha
        );
    }

    private static void solidVertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float x,
            float y,
            float z,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        consumer
                .addVertex(
                        pose.pose(),
                        x,
                        y,
                        z
                )
                .setColor(
                        red,
                        green,
                        blue,
                        alpha
                );
    }
}