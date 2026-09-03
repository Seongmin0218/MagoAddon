package com.seongmin0218.magoaddon.spell;

import com.seongmin0218.magoaddon.MagoAddon;
import com.seongmin0218.magoaddon.entity.spell.RailgunBeamVisualEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.RaycastBuilder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RailgunSpell extends AbstractSpell {

    public static final int MAX_CHARGE_TICKS = 100;
    public static final float RANGE = 50.0f;

    private static final int BASE_MANA_COST = 80;
    private static final int COOLDOWN_SECONDS = 12;

    /*
     * 5단계 직접 피해.
     *
     * 이후 Lightning Spell Power / 일반 Spell Power의
     * 영향을 받도록 getEntityPowerMultiplier()를 곱한다.
     */
    private static final float[] STAGE_DAMAGE = {
            10.0f,
            15.0f,
            30.0f,
            50.0f,
            85.0f
    };

    /*
     * 충전 단계별 노트블럭 pitch.
     */
    private static final float[] STAGE_PITCH = {
            0.70f,
            0.90f,
            1.15f,
            1.45f,
            1.90f
    };

    /*
     * 블록 파괴 제한.
     *
     * 옵시디언 등 고경도 블록은 보호.
     * BlockEntity도 별도 보호.
     */
    private static final float MAX_BREAK_HARDNESS = 6.0f;

    /*
     * 플레이어별 마지막 충전 단계.
     *
     * 5단계 도달 후 계속 키를 잡고 있어도
     * 효과가 매 tick 반복되지 않게 한다.
     */
    private static final Map<UUID, Integer> LAST_CHARGE_STAGE =
            new ConcurrentHashMap<>();

    private final ResourceLocation spellId =
            ResourceLocation.fromNamespaceAndPath(
                    MagoAddon.MOD_ID,
                    "railgun"
            );

    private final DefaultConfig defaultConfig =
            new DefaultConfig()
                    .setMinRarity(SpellRarity.LEGENDARY)
                    .setSchoolResource(
                            SchoolRegistry.LIGHTNING_RESOURCE
                    )
                    .setMaxLevel(1)
                    .setCooldownSeconds(COOLDOWN_SECONDS)
                    .build();

    public RailgunSpell() {
        this.baseManaCost = BASE_MANA_COST;
        this.manaCostPerLevel = 0;

        /*
         * 실제 피해는 STAGE_DAMAGE 배열을 사용하므로
         * AbstractSpell의 기본 SpellPower는 사용하지 않는다.
         */
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;

        this.castTime = MAX_CHARGE_TICKS;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    /*
     * 레일건은 정확히 5초 충전하도록 고정.
     *
     * Iron's Cast Time Reduction에 의해
     * 5초가 줄어드는 것을 막는다.
     */
    @Override
    public int getEffectiveCastTime(
            int spellLevel,
            @Nullable LivingEntity entity
    ) {
        return MAX_CHARGE_TICKS;
    }

    @Override
    public void onServerPreCast(
            Level level,
            int spellLevel,
            LivingEntity entity,
            @Nullable MagicData magicData
    ) {
        super.onServerPreCast(
                level,
                spellLevel,
                entity,
                magicData
        );

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        LAST_CHARGE_STAGE.put(
                entity.getUUID(),
                1
        );

        /*
         * 최초 1단계 진입 피드백.
         */
        playChargeStageEffect(
                serverLevel,
                entity,
                1
        );
    }

    @Override
    public void onServerCastTick(
            Level level,
            int spellLevel,
            LivingEntity entity,
            @Nullable MagicData magicData
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (magicData == null) {
            return;
        }

        int stage =
                getChargeStage(
                        magicData
                );

        int previousStage =
                LAST_CHARGE_STAGE.getOrDefault(
                        entity.getUUID(),
                        1
                );

        if (stage > previousStage) {
            LAST_CHARGE_STAGE.put(
                    entity.getUUID(),
                    stage
            );

            playChargeStageEffect(
                    serverLevel,
                    entity,
                    stage
            );
        }

        /*
         * 충전 중 작은 전기 입자.
         *
         * 단계가 높을수록 조금 더 자주 나타난다.
         */
        if (
                entity.tickCount
                        %
                Math.max(
                        2,
                        7 - stage
                )
                        ==
                0
        ) {
            Vec3 center =
                    entity.position()
                            .add(
                                    0,
                                    entity.getBbHeight() * 0.65,
                                    0
                            );

            serverLevel.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    center.x,
                    center.y,
                    center.z,
                    1 + stage,
                    0.25 + stage * 0.04,
                    0.35,
                    0.25 + stage * 0.04,
                    0.04
            );
        }
    }

    @Override
    public void onCast(
            Level level,
            int spellLevel,
            LivingEntity caster,
            CastSource castSource,
            MagicData magicData
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int stage =
                getChargeStage(
                        magicData
                );

        fire(
                serverLevel,
                caster,
                stage
        );

        /*
         * 부모 onCast는 기본 finish sound를 재생하므로
         * 이번 레일건은 직접 사운드를 처리하고
         * super.onCast()는 호출하지 않는다.
         */
    }

    @Override
    public void onServerCastComplete(
            Level level,
            int spellLevel,
            LivingEntity entity,
            MagicData magicData,
            boolean cancelled
    ) {
        LAST_CHARGE_STAGE.remove(
                entity.getUUID()
        );

        super.onServerCastComplete(
                level,
                spellLevel,
                entity,
                magicData,
                cancelled
        );
    }

    private void fire(
            ServerLevel level,
            LivingEntity caster,
            int stage
    ) {
        Vec3 start =
                caster.getEyePosition()
                        .add(
                                caster.getLookAngle()
                                        .normalize()
                                        .scale(0.4)
                        );

        Vec3 direction =
                caster.getLookAngle()
                        .normalize();

        Vec3 maximumEnd =
                start.add(
                        direction.scale(
                                RANGE
                        )
                );

        /*
         * 4~5단계는 블록을 뚫는 레일건.
         *
         * 1~3단계는 벽에 막힌다.
         */
        boolean drillThroughBlocks =
                stage >= 4;

        HitResult hitResult =
                RaycastBuilder
                        .begin(
                                level,
                                caster
                        )
                        .start(start)
                        .end(maximumEnd)
                        .checkForBlocks(
                                !drillThroughBlocks
                        )
                        .bbInflation(0.25f)
                        .filter(
                                Utils::canHitWithRaycast
                        )
                        .build();

        Vec3 end =
                hitResult.getLocation();

        /*
         * RaycastBuilder가 MISS 좌표를 정확히 RANGE까지
         * 반환하지 않는 상황을 대비.
         */
        if (
                hitResult.getType()
                        ==
                HitResult.Type.MISS
        ) {
            end = maximumEnd;
        }

        /*
         * 실제 3D 레일건 빔 VFX.
         */
        level.addFreshEntity(
                new RailgunBeamVisualEntity(
                        level,
                        start,
                        end,
                        caster,
                        stage
                )
        );

        /*
         * 엔티티 직접 타격.
         *
         * 여러 명을 관통하지 않고 첫 대상 한 명만.
         */
        if (
                hitResult
                        instanceof
                EntityHitResult entityHit
                        &&
                entityHit.getEntity()
                        instanceof
                LivingEntity target
        ) {
            float damage =
                    getDamageForStage(
                            stage,
                            caster
                    );

            DamageSources.applyDamage(
                    target,
                    damage,
                    getDamageSource(caster)
            );
        }

        /*
         * 4단계 이상부터 블록 파쇄.
         */
        if (stage >= 4) {
            destroyBlocksAlongBeam(
                    level,
                    caster,
                    start,
                    end,
                    stage
            );
        }

        /*
         * 5단계 최대 충전 착탄 폭발.
         */
        if (stage == 5) {
            fullChargeImpact(
                    level,
                    caster,
                    end
            );
        } else {
            normalImpact(
                    level,
                    end,
                    stage
            );
        }

        playFireSound(
                level,
                caster,
                stage
        );
    }

    private float getDamageForStage(
            int stage,
            LivingEntity caster
    ) {
        int index =
                Math.max(
                        0,
                        Math.min(
                                stage - 1,
                                STAGE_DAMAGE.length - 1
                        )
                );

        /*
         * 기본값은 정확히
         * 10 / 15 / 30 / 50 / 85.
         *
         * 이후 플레이어의 Lightning Spell Power 등이
         * 정상 적용된다.
         */
        return STAGE_DAMAGE[index]
                *
                getEntityPowerMultiplier(
                        caster
                );
    }

    public static int getChargeStage(
            MagicData magicData
    ) {
        int elapsed =
                magicData.getCastDuration()
                        -
                magicData.getCastDurationRemaining();

        /*
         * 0.00초 = 1단계
         * 1.25초 = 2단계
         * 2.50초 = 3단계
         * 3.75초 = 4단계
         * 5.00초 = 5단계
         */
        if (elapsed >= 100) {
            return 5;
        }

        if (elapsed >= 75) {
            return 4;
        }

        if (elapsed >= 50) {
            return 3;
        }

        if (elapsed >= 25) {
            return 2;
        }

        return 1;
    }

    private void playChargeStageEffect(
            ServerLevel level,
            LivingEntity caster,
            int stage
    ) {
        Vec3 center =
                caster.position()
                        .add(
                                0,
                                caster.getBbHeight() * 0.65,
                                0
                        );

        int particleCount =
                4
                        +
                stage * 5;

        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                center.x,
                center.y,
                center.z,
                particleCount,
                0.30 + stage * 0.05,
                0.40,
                0.30 + stage * 0.05,
                0.08 + stage * 0.015
        );

        level.playSound(
                null,
                caster.blockPosition(),
                SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.PLAYERS,
                0.8f,
                STAGE_PITCH[
                        stage - 1
                ]
        );

        /*
         * 최대 충전은 화면에서도 확실히 보이도록 Flash.
         */
        if (stage == 5) {
            level.sendParticles(
                    ParticleTypes.FLASH,
                    center.x,
                    center.y,
                    center.z,
                    1,
                    0,
                    0,
                    0,
                    0
            );

            level.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    center.x,
                    center.y,
                    center.z,
                    35,
                    0.70,
                    0.70,
                    0.70,
                    0.18
            );
        }
    }

    private void playFireSound(
            ServerLevel level,
            LivingEntity caster,
            int stage
    ) {
        float pitch =
                1.15f
                        -
                stage * 0.07f;

        level.playSound(
                null,
                caster.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS,
                1.5f,
                pitch
        );

        level.playSound(
                null,
                caster.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS,
                1.1f,
                0.85f
        );
    }

    private void normalImpact(
            ServerLevel level,
            Vec3 end,
            int stage
    ) {
        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                end.x,
                end.y,
                end.z,
                10 + stage * 5,
                0.25,
                0.25,
                0.25,
                0.15
        );

        level.sendParticles(
                ParticleTypes.SMOKE,
                end.x,
                end.y,
                end.z,
                4 + stage * 2,
                0.15,
                0.15,
                0.15,
                0.05
        );
    }

    private void fullChargeImpact(
            ServerLevel level,
            LivingEntity caster,
            Vec3 end
    ) {
        /*
         * 최대 충전 폭발.
         *
         * 현재는 별도 추가 데미지 없음.
         * 직접 타격 85를 그대로 유지한다.
         */
        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                end.x,
                end.y,
                end.z,
                1,
                0,
                0,
                0,
                0
        );

        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                end.x,
                end.y,
                end.z,
                55,
                1.1,
                1.1,
                1.1,
                0.25
        );

        level.sendParticles(
                ParticleTypes.FLASH,
                end.x,
                end.y,
                end.z,
                2,
                0.15,
                0.15,
                0.15,
                0
        );

        level.playSound(
                null,
                BlockPos.containing(end),
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS,
                1.5f,
                1.0f
        );

        /*
         * 폭발 반경 4블록.
         *
         * 현재 피해는 주지 않고 넉백만.
         */
        double radius =
                4.0;

        AABB area =
                new AABB(
                        end.x - radius,
                        end.y - radius,
                        end.z - radius,
                        end.x + radius,
                        end.y + radius,
                        end.z + radius
                );

        for (
                LivingEntity target
                :
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        area
                )
        ) {
            if (
                    target == caster
                            ||
                    !target.isAlive()
            ) {
                continue;
            }

            Vec3 delta =
                    target.position()
                            .subtract(end);

            double distance =
                    delta.length();

            if (
                    distance <= 0.01
                            ||
                    distance > radius
            ) {
                continue;
            }

            double strength =
                    1.25
                            *
                    (
                            1.0
                                    -
                            distance / radius
                    );

            target.push(
                    delta.x / distance * strength,
                    0.20 + strength * 0.25,
                    delta.z / distance * strength
            );
        }
    }

    private void destroyBlocksAlongBeam(
            ServerLevel level,
            LivingEntity caster,
            Vec3 start,
            Vec3 end,
            int stage
    ) {
        Vec3 delta =
                end.subtract(start);

        double length =
                delta.length();

        if (length <= 0.01) {
            return;
        }

        Vec3 direction =
                delta.normalize();

        /*
         * 4단계 = 1블록 중심선
         * 5단계 = 약 3×3 통로
         */
        int radius =
                stage == 5
                        ?
                1
                        :
                0;

        Set<BlockPos> processed =
                new HashSet<>();

        /*
         * 0.4블록 간격으로 샘플링.
         */
        for (
                double distance = 0.5;
                distance <= length;
                distance += 0.4
        ) {
            Vec3 sample =
                    start.add(
                            direction.scale(
                                    distance
                            )
                    );

            BlockPos center =
                    BlockPos.containing(
                            sample
                    );

            for (
                    int x = -radius;
                    x <= radius;
                    x++
            ) {
                for (
                        int y = -radius;
                        y <= radius;
                        y++
                ) {
                    for (
                            int z = -radius;
                            z <= radius;
                            z++
                    ) {
                        BlockPos pos =
                                center.offset(
                                        x,
                                        y,
                                        z
                                );

                        /*
                         * 같은 블록을 계속 검사하지 않음.
                         */
                        if (
                                !processed.add(
                                        pos.immutable()
                                )
                        ) {
                            continue;
                        }

                        destroyRailgunBlock(
                                level,
                                caster,
                                pos,
                                stage == 5
                        );
                    }
                }
            }
        }
    }

    private void destroyRailgunBlock(
            ServerLevel level,
            LivingEntity caster,
            BlockPos pos,
            boolean ignite
    ) {
        BlockState state =
                level.getBlockState(
                        pos
                );

        if (state.isAir()) {
            return;
        }

        /*
         * 물/용암 등 Fluid 블록은 건드리지 않는다.
         */
        if (
                !state.getFluidState()
                        .isEmpty()
        ) {
            return;
        }

        /*
         * 상자, Create 기계 등 BlockEntity 보호.
         */
        if (
                level.getBlockEntity(
                        pos
                )
                        !=
                null
        ) {
            return;
        }

        float hardness =
                state.getDestroySpeed(
                        level,
                        pos
                );

        /*
         * -1 = 기반암 등의 파괴 불가능 블록.
         */
        if (
                hardness < 0
                        ||
                hardness
                        >
                MAX_BREAK_HARDNESS
        ) {
            return;
        }

        boolean destroyed =
                level.destroyBlock(
                        pos,
                        false,
                        caster
                );

        if (
                destroyed
                        &&
                ignite
                        &&
                level.random.nextFloat()
                        <
                0.18f
        ) {
            tryPlaceFire(
                    level,
                    pos
            );
        }
    }

    private void tryPlaceFire(
            ServerLevel level,
            BlockPos pos
    ) {
        if (
                !level.isEmptyBlock(
                        pos
                )
        ) {
            return;
        }

        BlockState fireState =
                BaseFireBlock.getState(
                        level,
                        pos
                );

        if (
                fireState.canSurvive(
                        level,
                        pos
                )
        ) {
            level.setBlockAndUpdate(
                    pos,
                    fireState
            );
        }
    }
}