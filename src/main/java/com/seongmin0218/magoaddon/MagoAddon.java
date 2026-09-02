package com.seongmin0218.magoaddon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(MagoAddon.MOD_ID)
public class MagoAddon {
    public static final String MOD_ID = "magoaddon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MagoAddon(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Mago Addon loaded.");
    }
}