package com.seongmin0218.magoaddon.entity.spell;

import com.seongmin0218.magoaddon.registry.MagoEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public class RailgunBeamVisualEntity
        extends Entity
        implements IEntityWithComplexSpawn {

    public static final int LIFETIME =
            8;

    private Vec3 end =
            Vec3.ZERO;

    private int stage =
            1;

    public RailgunBeamVisualEntity(
            EntityType<?> type,
            Level level
    ) {
        super(
                type,
                level
        );
    }

    public RailgunBeamVisualEntity(
            Level level,
            Vec3 start,
            Vec3 end,
            LivingEntity owner,
            int stage
    ) {
        super(
                MagoEntities.RAILGUN_BEAM.get(),
                level
        );

        this.setPos(
                start
        );

        this.end =
                end;

        this.stage =
                stage;
    }

    public Vec3 getEnd() {
        return end;
    }

    public int getStage() {
        return stage;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder
    ) {
    }

    @Override
    public void tick() {
        super.tick();

        if (
                tickCount
                        >
                LIFETIME
        ) {
            discard();
        }
    }

    @Override
    public boolean shouldRender(
            double x,
            double y,
            double z
    ) {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(
            CompoundTag tag
    ) {
    }

    @Override
    protected void addAdditionalSaveData(
            CompoundTag tag
    ) {
    }

    /*
     * start 위치는 Entity 기본 spawn packet이 동기화한다.
     *
     * 추가로 end + stage만 보낸다.
     */
    @Override
    public void writeSpawnData(
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeDouble(
                end.x
        );

        buffer.writeDouble(
                end.y
        );

        buffer.writeDouble(
                end.z
        );

        buffer.writeVarInt(
                stage
        );
    }

    @Override
    public void readSpawnData(
            RegistryFriendlyByteBuf buffer
    ) {
        end =
                new Vec3(
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble()
                );

        stage =
                buffer.readVarInt();
    }
}