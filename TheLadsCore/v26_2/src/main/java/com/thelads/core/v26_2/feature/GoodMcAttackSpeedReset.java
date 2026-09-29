package com.thelads.core.v26_2.feature;

import com.thelads.core.mods.GoodMcResidue;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.LoggerFactory;

/**
 * Worlds played with GoodMC keep its attack_speed base in the player data. Reset it as the integrated
 * server (singleplayer or LAN host) loads a player: damage and the saved data live on the server side.
 * A client-only entrypoint never runs on a dedicated server, so remote servers keep their own data.
 */
public final class GoodMcAttackSpeedReset {
    private GoodMcAttackSpeedReset() {}

    public static void register() {
        boolean goodMcLoaded = FabricLoader.getInstance().isModLoaded(GoodMcResidue.MOD_ID);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            if (!GoodMcResidue.isResidue(player.getAttributeBaseValue(Attributes.ATTACK_SPEED), goodMcLoaded)) return;
            // Same call as /attribute <player> minecraft:attack_speed base reset.
            player.getAttributes().resetBaseValue(Attributes.ATTACK_SPEED);
            LoggerFactory.getLogger("TheLadsCore").info("Lads GoodMC cleanup: reset {}'s attack_speed base from {} to {}",
                player.getName().getString(), GoodMcResidue.ATTACK_SPEED_BASE, player.getAttributeBaseValue(Attributes.ATTACK_SPEED));
        });
    }
}
