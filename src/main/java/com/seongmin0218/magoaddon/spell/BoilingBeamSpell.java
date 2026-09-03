package com.seongmin0218.magoaddon.spell;

import com.seongmin0218.magoaddon.MagoAddon;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.RaycastBuilder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Optional;

public class BoilingBeamSpell extends AbstractSpell {

    public static final float RANGE = 14.0f;

    /*
     * 빔 중심부용 파란 Dust 파티클.
     *
     * RGB:
     * R = 0.10
     * G = 0.55
     * B = 1.00
     */
    private static final DustParticleOptions BLUE_BEAM =
            new DustParticleOptions(
                    new Vector3f(0.10f, 0.55f, 1.00f),
                    0.8f
            );

    private final ResourceLocation spellId =
            ResourceLocation.fromNamespaceAndPath(
                    MagoAddon.MOD_ID,
                    "boiling_beam"
            );

    private final DefaultConfig defaultConfig =
            new DefaultConfig()
                    .setMinRarity(SpellRarity.COMMON)
                    .setSchoolResource(
                        ResourceLocation.fromNamespaceAndPath(
                            "aces_spell_utils",
                            "hydro"
                        )
                    )
                    .setMaxLevel(5)
                    .setCooldownSeconds(3)
                    .build();

    public BoilingBeamSpell() {
        /*
         * CONTINUOUS 주문이므로 이 마나값은
         * 지속 시전 과정에서 반복 소모된다.
         */
        this.baseManaCost = 4;
        this.manaCostPerLevel = 1;

        /*
         * 주문 공격력.
         * 실제 한 번의 틱 데미지는 아래 getTickDamage()에서
         * 일부 비율만 사용한다.
         */
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 2;

        /*
         * 20 tick = 1초
         * 100 tick = 최대 5초 유지
         */
        this.castTime = 100;
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
        return CastType.CONTINUOUS;
    }

    /**
     * 시전 중 서버에서 매 tick 호출.
     *
     * 여기서는 실제 데미지를 주지 않고
     * 빔 / 물 / 증기 VFX만 생성한다.
     */
    @Override
    public void onServerCastTick(
            Level level,
            int spellLevel,
            LivingEntity caster,
            @Nullable MagicData magicData
    ) {
        super.onServerCastTick(
                level,
                spellLevel,
                caster,
                magicData
        );

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        /*
        * 지속 물 분사 사운드.
        *
        * 매 tick 틀면 사운드가 겹쳐서 난리가 나므로
        * 8 tick, 약 0.4초마다 작게 반복한다.
        */
        if (caster.tickCount % 8 == 0) {
            level.playSound(
                    null,
                    caster.blockPosition(),
                    SoundEvents.GENERIC_SPLASH,
                    SoundSource.PLAYERS,
                    0.35f,
                    1.35f
                            +
                    level.random.nextFloat()
                            *
                    0.12f
            );
        }

        /*
         * 파티클을 매 tick 생성하면 너무 조밀하므로
         * 2 tick마다 한 번 렌더링한다.
         *
         * 초당 10회.
         */
        if (caster.tickCount % 2 != 0) {
            return;
        }

        HitResult hitResult = raycast(
                level,
                caster
        );

        Vec3 start = caster.getEyePosition();
        Vec3 end = hitResult.getLocation();

        spawnBeamParticles(
                serverLevel,
                start,
                end
        );

        /*
         * 무언가에 맞았을 때 끝점에서 증기를 더 강하게 생성.
         */
        if (hitResult.getType() != HitResult.Type.MISS) {
            spawnImpactSteam(
                    serverLevel,
                    end
            );

            /*
            * 명중점 증기 소리.
            *
            * 이것도 매 tick 울리면 너무 시끄러우므로
            * 10 tick마다 한 번.
            */
            if (caster.tickCount % 10 == 0) {
                level.playSound(
                        null,
                        BlockPos.containing(end),
                        SoundEvents.GENERIC_EXTINGUISH_FIRE,
                        SoundSource.PLAYERS,
                        0.30f,
                        1.65f
                                +
                        level.random.nextFloat()
                                *
                        0.20f
                );
            }
        }
    }

    /**
     * Iron's CONTINUOUS 시스템이 약 10 tick마다 호출.
     *
     * 실제 데미지와 발화는 이쪽에서 처리한다.
     */
    @Override
    public void onCast(
            Level level,
            int spellLevel,
            LivingEntity caster,
            CastSource castSource,
            MagicData magicData
    ) {
        HitResult hitResult = raycast(
                level,
                caster
        );

        /*
         * 엔티티를 맞췄을 때만 데미지.
         */
        if (hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity target) {

            float damage =
                    getTickDamage(
                            spellLevel,
                            caster
                    );

            /*
             * AbstractSpell#getDamageSource를 사용하므로
             * 그냥 vanilla generic damage가 아니라
             * Iron's 주문 데미지 소스를 사용한다.
             */
            boolean damaged =
                    DamageSources.applyDamage(
                            target,
                            damage,
                            getDamageSource(caster)
                    );

            if (damaged) {
                /*
                 * 열탕에 맞은 적을 2초간 발화.
                 *
                 * 지속 명중하면 계속 갱신된다.
                 */
                target.igniteForSeconds(2);

                if (level instanceof ServerLevel serverLevel) {
                    spawnDamageParticles(
                            serverLevel,
                            target
                    );
                }
            }
        }

        super.onCast(
                level,
                spellLevel,
                caster,
                castSource,
                magicData
        );
    }

    /**
     * 시전자의 시선 방향으로 Raycast.
     */
    private HitResult raycast(
            Level level,
            LivingEntity caster
    ) {
        Vec3 start =
                caster.getEyePosition();

        Vec3 end =
                start.add(
                        caster.getLookAngle()
                                .normalize()
                                .scale(RANGE)
                );

        return RaycastBuilder
                .begin(level, caster)
                .start(start)
                .end(end)
                .checkForBlocks(true)

                /*
                 * 아주 정확하게 점 하나를 겨누지 않아도
                 * 어느 정도 맞도록 히트박스를 살짝 확장.
                 */
                .bbInflation(0.20f)

                /*
                 * Iron's 기본 Raycast 필터 사용.
                 */
                .filter(Utils::canHitWithRaycast)

                .build();
    }

    /**
     * 파란 빔 + 물방울 + 약간의 증기.
     */
    private void spawnBeamParticles(
            ServerLevel level,
            Vec3 start,
            Vec3 end
    ) {
        Vec3 direction =
                end.subtract(start);

        double distance =
                direction.length();

        if (distance <= 0.01) {
            return;
        }

        Vec3 normalized =
                direction.normalize();

        /*
         * 눈 바로 앞에서 파티클이 화면을 가리는 걸 방지하기 위해
         * 0.7블록 앞부터 생성한다.
         */
        double startDistance = 0.7;

        /*
         * 0.35블록마다 파티클 하나.
         */
        double spacing = 0.35;

        for (
                double travelled = startDistance;
                travelled < distance;
                travelled += spacing
        ) {
            Vec3 point =
                    start.add(
                            normalized.scale(travelled)
                    );

            /*
             * 1. 빔 코어
             */
            level.sendParticles(
                    BLUE_BEAM,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    0.015,
                    0.015,
                    0.015,
                    0
            );

            /*
             * 2. 물방울
             *
             * 모든 점에서 만들면 너무 많으므로
             * 약 1.4블록마다 생성.
             */
            if (((int) (travelled * 10)) % 14 == 0) {
                level.sendParticles(
                        ParticleTypes.SPLASH,
                        point.x,
                        point.y,
                        point.z,
                        1,
                        0.08,
                        0.08,
                        0.08,
                        0.02
                );
            }

            /*
             * 3. 증기
             *
             * 물보다 조금 더 드물게.
             */
            if (((int) (travelled * 10)) % 20 == 0) {
                level.sendParticles(
                        ParticleTypes.CLOUD,
                        point.x,
                        point.y,
                        point.z,
                        1,
                        0.06,
                        0.06,
                        0.06,
                        0.01
                );
            }
        }
    }

    /**
     * 빔이 블록이나 엔티티에 닿는 위치의 증기.
     */
    private void spawnImpactSteam(
        ServerLevel level,
        Vec3 hitPosition
    ) {
        /*
        * 고온 증기
        *
        * 퍼지는 범위를 크게 잡아
        * 물줄기가 뜨거운 표면/적에게 부딪히는 느낌.
        */
        level.sendParticles(
                ParticleTypes.CLOUD,
                hitPosition.x,
                hitPosition.y,
                hitPosition.z,
                10,
                0.20,
                0.20,
                0.20,
                0.08
        );

        /*
        * 튀어 나가는 물방울.
        *
        * speed를 높여서 기존보다 훨씬 강하게 튄다.
        */
        level.sendParticles(
                ParticleTypes.SPLASH,
                hitPosition.x,
                hitPosition.y,
                hitPosition.z,
                12,
                0.24,
                0.24,
                0.24,
                0.18
        );

        /*
        * 작은 물방울 잔여물.
        */
        level.sendParticles(
                ParticleTypes.DRIPPING_WATER,
                hitPosition.x,
                hitPosition.y,
                hitPosition.z,
                4,
                0.16,
                0.10,
                0.16,
                0.02
        );
    }

    /**
     * 실제 적에게 데미지가 들어갔을 때의 효과.
     */
    private void spawnDamageParticles(
            ServerLevel level,
            LivingEntity target
    ) {
        Vec3 center =
                target.getBoundingBox()
                        .getCenter();

        /*
         * 뜨거운 증기
         */
        level.sendParticles(
                ParticleTypes.CLOUD,
                center.x,
                center.y,
                center.z,
                8,
                target.getBbWidth() * 0.35,
                target.getBbHeight() * 0.25,
                target.getBbWidth() * 0.35,
                0.03
        );

        /*
         * 실제 발화 여부를 눈으로 확인하기 쉽게
         * 작은 불꽃도 생성.
         * 안어울려서 취소.
        
        level.sendParticles(
                ParticleTypes.FLAME,
                center.x,
                center.y,
                center.z,
                4,
                target.getBbWidth() * 0.25,
                target.getBbHeight() * 0.2,
                target.getBbWidth() * 0.25,
                0.02
        );
        */
    }

    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastStartSound() {
        return Optional.of(
                SoundEvents.GENERIC_SPLASH
        );
    }
    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    /**
     * CONTINUOUS onCast가 10 tick마다 들어오므로
     * 한 번의 판정에서 전체 SpellPower의 35%를 준다.
     *
     * 초당 약 2회 공격.
     */
    private float getTickDamage(
            int spellLevel,
            LivingEntity caster
    ) {
        return getSpellPower(
                spellLevel,
                caster
        ) * 0.35f;
    }
}