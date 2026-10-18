package com.thelads.smp.features.arise.nb3.snailmail;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Snail Mail Feature:
 * Postal mail delivery system with locked mailboxes, delivery scheduling,
 * and recipient delivery routing.
 */
public final class SnailMailFeature {
    private static final List<PendingDelivery> PENDING_DELIVERIES = new ArrayList<>();

    public record PendingDelivery(
            UUID senderUuid,
            String recipientName,
            ItemStack mailStack,
            long deliverAtTimestampMs
    ) {}

    private SnailMailFeature() {}

    /**
     * Checks if a player has permission to open/access a mailbox.
     */
    public static boolean canAccessMailbox(Player player, UUID ownerUuid, int playerOpLevel) {
        if (!AriseNb3Config.get().snailMail.enabled) return true;
        if (!AriseNb3Config.get().snailMail.lockBoxes) return true;
        if (ownerUuid == null) return true; // Unclaimed mailbox can be accessed to claim
        if (player == null) return false;

        // Owner can always access
        if (player.getUUID().equals(ownerUuid)) {
            return true;
        }

        // OP bypass check
        if (AriseNb3Config.get().snailMail.opBypassLock) {
            return playerOpLevel >= AriseNb3Config.get().snailMail.bypassLockOpLevel;
        }

        return false;
    }

    /**
     * Schedules mail delivery to a destination player with the configured transit delay.
     */
    public static PendingDelivery scheduleDelivery(UUID sender, String recipient, ItemStack stack, long nowMs) {
        long delayMs = AriseNb3Config.get().snailMail.deliveryDelayMinutes * 60_000L;
        PendingDelivery delivery = new PendingDelivery(sender, recipient, stack.copy(), nowMs + delayMs);
        PENDING_DELIVERIES.add(delivery);
        return delivery;
    }

    public static List<PendingDelivery> getReadyDeliveries(long nowMs) {
        List<PendingDelivery> ready = new ArrayList<>();
        Iterator<PendingDelivery> it = PENDING_DELIVERIES.iterator();
        while (it.hasNext()) {
            PendingDelivery d = it.next();
            if (d.deliverAtTimestampMs() <= nowMs) {
                ready.add(d);
                it.remove();
            }
        }
        return ready;
    }

    public static List<PendingDelivery> getPendingDeliveries() {
        return PENDING_DELIVERIES;
    }

    public static void clearPendingForTesting() {
        PENDING_DELIVERIES.clear();
    }
}
