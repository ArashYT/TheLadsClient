package com.thelads.smp.features.arise.nb2;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint and lifecycle coordinator for Native Batch NB2 features.
 */
public final class NB2Features {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/NB2");

    private NB2Features() {}

    public static void init() {
        long start = System.currentTimeMillis();
        NB2Config config = NB2Config.load();
        PaxiManager.ensureDirectoriesExist();
        CpmSvcCompatManager.init();

        // Hitmarker attack callback (fires when player attacks an entity)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && entity instanceof LivingEntity) {
                HitmarkerManager.onHit();
            }
            return InteractionResult.PASS;
        });

        LOGGER.info("[NB2] Initialized Native Batch NB2 features in {} ms (Paxi={}, RealisticNametag={}, RemoveChatBracket={}, TooManyEntities={}, TradeUses={}, YungsMenuTweaks={})",
                System.currentTimeMillis() - start,
                config.paxi,
                config.realisticNametag,
                config.removeChatBracket,
                config.tooManyEntities,
                config.tradeUses,
                config.rightClickReversesCycleButtons);
    }
}
