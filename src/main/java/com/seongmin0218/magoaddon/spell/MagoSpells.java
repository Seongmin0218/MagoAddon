package com.seongmin0218.magoaddon.spell;

import com.seongmin0218.magoaddon.MagoAddon;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class MagoSpells {

    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(
                    SpellRegistry.SPELL_REGISTRY_KEY,
                    MagoAddon.MOD_ID
            );

    public static final Supplier<AbstractSpell> TEST_SPELL =
            registerSpell(new TestSpell());

    public static final Supplier<AbstractSpell> BOILING_BEAM =
        registerSpell(new BoilingBeamSpell());

    private static Supplier<AbstractSpell> registerSpell(AbstractSpell spell) {
        return SPELLS.register(
                spell.getSpellName(),
                () -> spell
        );
    }

    public static void register(IEventBus modEventBus) {
        SPELLS.register(modEventBus);
    }

    private MagoSpells() {
    }
}