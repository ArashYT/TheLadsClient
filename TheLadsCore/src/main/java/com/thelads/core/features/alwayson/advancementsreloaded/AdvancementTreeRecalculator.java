package com.thelads.core.features.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.utils.Utils;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;

public final class AdvancementTreeRecalculator {
    private AdvancementTreeRecalculator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void recalculateAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        ClientAdvancements advancements = minecraft.player.connection.getAdvancements();
        if (advancements == null) {
            return;
        }
        Utils.LOGGER.info("Recalculating all advancement tree positions due to configuration change");
        for (AdvancementNode root : advancements.getTree().roots()) {
            if (!root.advancement().display().isPresent()) continue;
            AdvancementTreePositioning.run(root);
        }
        Utils.LOGGER.info("Advancement tree positions recalculated");
    }
}
