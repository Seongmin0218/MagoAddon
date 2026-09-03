package com.seongmin0218.magoaddon.registry;

import com.seongmin0218.magoaddon.MagoAddon;
import com.seongmin0218.magoaddon.entity.spell.RailgunBeamVisualEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class MagoEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(
                    BuiltInRegistries.ENTITY_TYPE,
                    MagoAddon.MOD_ID
            );

    public static final Supplier<EntityType<RailgunBeamVisualEntity>>
            RAILGUN_BEAM =
            ENTITY_TYPES.register(
                    "railgun_beam",
                    () ->
                            EntityType
                                    .Builder
                                    .<RailgunBeamVisualEntity>of(
                                            RailgunBeamVisualEntity::new,
                                            MobCategory.MISC
                                    )
                                    .sized(
                                            0.1f,
                                            0.1f
                                    )
                                    .clientTrackingRange(
                                            64
                                    )
                                    .updateInterval(
                                            1
                                    )
                                    .noSave()
                                    .noSummon()
                                    .build(
                                            MagoAddon.MOD_ID
                                                    +
                                            ":railgun_beam"
                                    )
            );

    public static void register(
            IEventBus modEventBus
    ) {
        ENTITY_TYPES.register(
                modEventBus
        );
    }

    private MagoEntities() {
    }
}