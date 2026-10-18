package com.thelads.smp.features.arise.nb6;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Fishing Journal item tracks a player's first catches across all 20 species.
 * Right-clicking opens the journal view and displays discovery progress.
 */
public class FishingJournalItem extends Item {
    public interface ClientScreenOpener {
        void openJournalScreen();
    }

    private static ClientScreenOpener clientOpener = null;

    public static void setClientScreenOpener(ClientScreenOpener opener) {
        clientOpener = opener;
    }

    public FishingJournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            if (clientOpener != null) {
                clientOpener.openJournalScreen();
            }
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            FishingJournalManager.syncToPlayer(serverPlayer);
            FishingJournalData data = FishingJournalManager.get(serverPlayer.getUUID());
            int discovered = data.getDiscoveredCount();
            int percent = (int) Math.round(data.getCompletionPercentage());
            serverPlayer.sendSystemMessage(
                    Component.literal("§6§l[Fishing Journal] §e" + discovered + "/20 §fSpecies Discovered §7(§a" + percent + "%§7)"),
                    false
            );
        }

        return InteractionResult.SUCCESS;
    }
}
