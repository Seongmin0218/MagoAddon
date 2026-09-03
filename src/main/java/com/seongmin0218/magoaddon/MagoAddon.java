package com.seongmin0218.magoaddon;

import com.mojang.logging.LogUtils;
import com.seongmin0218.magoaddon.network.MagoNetworking;
import com.seongmin0218.magoaddon.registry.MagoEntities;
import com.seongmin0218.magoaddon.spell.MagoSpells;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MagoAddon.MOD_ID)
public class MagoAddon {

    public static final String MOD_ID = "magoaddon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MagoAddon(
            IEventBus modEventBus,
            ModContainer modContainer
    ) {
        MagoSpells.register(modEventBus);
        MagoEntities.register(modEventBus);

        modEventBus.addListener(
                MagoNetworking::register
        );

        LOGGER.info("Mago Addon loaded.");
    }
}