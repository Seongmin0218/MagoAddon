package com.seongmin0218.magoaddon.spell;

import com.seongmin0218.magoaddon.MagoAddon;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class TestSpell extends AbstractSpell {

    private final ResourceLocation spellId =
            ResourceLocation.fromNamespaceAndPath(
                    MagoAddon.MOD_ID,
                    "test_spell"
            );

    private final DefaultConfig defaultConfig =
            new DefaultConfig()
                    .setMinRarity(SpellRarity.COMMON)
                    .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
                    .setMaxLevel(1)
                    .setCooldownSeconds(1)
                    .build();

    public TestSpell() {
        this.baseManaCost = 1;
        this.manaCostPerLevel = 0;

        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;

        this.castTime = 0;
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
        return CastType.INSTANT;
    }

    @Override
    public void onCast(
            Level level,
            int spellLevel,
            LivingEntity entity,
            CastSource castSource,
            MagicData magicData
    ) {
        if (entity instanceof ServerPlayer player) {
            player.sendSystemMessage(
                    Component.literal(
                            "[MagoAddon] Test Spell cast successful!"
                    )
            );
        }

        super.onCast(
                level,
                spellLevel,
                entity,
                castSource,
                magicData
        );
    }
}