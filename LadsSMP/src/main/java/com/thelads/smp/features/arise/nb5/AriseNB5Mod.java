package com.thelads.smp.features.arise.nb5;

import com.thelads.smp.features.arise.nb5.server.ParkourServerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for NB5: ParCool-style Parkour & Stamina systems.
 */
public final class AriseNB5Mod {
    public static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB5");

    private AriseNB5Mod() {}

    public static void init() {
        AriseNB5Config config = AriseNB5Config.load();
        ParkourServerManager.init();
        LOGGER.info("[Lads SMP] Batch NB5 (ParCool Parkour & Stamina) loaded: enabled={}, maxStamina={}, dodgeRoll={}, wallJump={}, vault={}, slide={}, breakfall={}",
                config.enabled, config.maxStamina, config.dodgeRollEnabled, config.wallJumpEnabled,
                config.vaultEnabled, config.slideEnabled, config.breakfallEnabled);
    }
}
