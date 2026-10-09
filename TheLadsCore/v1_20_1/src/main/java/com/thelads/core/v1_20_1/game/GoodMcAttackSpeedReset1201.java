package com.thelads.core.v1_20_1.game;

import com.thelads.core.mods.GoodMcResidue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.LoggerFactory;

/**
 * Worlds played with GoodMC keep its attack_speed base in the player data. Reset it as the integrated server (singleplayer or LAN
 * host) loads a player: damage and the saved data live on the server side. A client-only entrypoint never runs on a dedicated
 * server, so remote servers keep their own data.
 */
public final class GoodMcAttackSpeedReset1201 {
    private GoodMcAttackSpeedReset1201() {}

    public static void register() {
        boolean goodMcLoaded = FabricLoader.getInstance().isModLoaded(GoodMcResidue.MOD_ID);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
            if (speed == null || !GoodMcResidue.isResidue(speed.getBaseValue(), goodMcLoaded)) return;
            double before = speed.getBaseValue();
            // Same effect as /attribute <player> minecraft:generic.attack_speed base reset.
            speed.setBaseValue(speed.getAttribute().getDefaultValue());
            LoggerFactory.getLogger("TheLadsCore").info("Lads GoodMC cleanup: reset {}'s attack_speed base from {} to {}",
                player.getName().getString(), before, speed.getBaseValue());
        });
    }
}
