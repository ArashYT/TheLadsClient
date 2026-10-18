package com.thelads.smp.features.arise.nb3.snailmail;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    public record MailboxLocation(ResourceKey<Level> dimension, BlockPos pos) {}
    private static final Map<UUID, MailboxLocation> REGISTERED_MAILBOXES = new ConcurrentHashMap<>();
    private static final Map<String, UUID> PLAYER_NAMES = new ConcurrentHashMap<>();

    public static void registerMailbox(UUID owner, Level level, BlockPos pos) {
        if (owner != null && level != null && pos != null) {
            REGISTERED_MAILBOXES.put(owner, new MailboxLocation(level.dimension(), pos.immutable()));
        }
    }

    public static void unregisterMailbox(UUID owner) {
        if (owner != null) {
            REGISTERED_MAILBOXES.remove(owner);
        }
    }

    public static void recordPlayerName(String name, UUID uuid) {
        if (name != null && uuid != null) {
            PLAYER_NAMES.put(name.toLowerCase(java.util.Locale.ROOT), uuid);
        }
    }

    public static void tickDeliveries(net.minecraft.server.MinecraftServer server) {
        if (server == null || !AriseNb3Config.get().snailMail.enabled) return;
        long now = System.currentTimeMillis();
        List<PendingDelivery> ready = getReadyDeliveries(now);
        for (PendingDelivery delivery : ready) {
            UUID targetUuid = PLAYER_NAMES.get(delivery.recipientName().toLowerCase(java.util.Locale.ROOT));
            if (targetUuid == null) {
                net.minecraft.server.level.ServerPlayer online = server.getPlayerList().getPlayerByName(delivery.recipientName());
                if (online != null) {
                    targetUuid = online.getUUID();
                }
            }

            if (targetUuid != null) {
                MailboxLocation loc = REGISTERED_MAILBOXES.get(targetUuid);
                if (loc != null) {
                    net.minecraft.server.level.ServerLevel targetLevel = server.getLevel(loc.dimension());
                    if (targetLevel != null && targetLevel.getBlockEntity(loc.pos()) instanceof MailboxBlockEntity mb) {
                        mb.depositMail(delivery.mailStack());
                        net.minecraft.server.level.ServerPlayer recipient = server.getPlayerList().getPlayer(targetUuid);
                        if (recipient != null) {
                            recipient.sendSystemMessage(net.minecraft.network.chat.Component.literal("You have received mail in your mailbox!")
                                    .withStyle(net.minecraft.ChatFormatting.GOLD));
                        }
                        continue;
                    }
                }
                net.minecraft.server.level.ServerPlayer recipient = server.getPlayerList().getPlayer(targetUuid);
                if (recipient != null) {
                    recipient.getInventory().add(delivery.mailStack());
                    recipient.sendSystemMessage(net.minecraft.network.chat.Component.literal("A mail delivery arrived directly to your inventory!")
                            .withStyle(net.minecraft.ChatFormatting.GOLD));
                }
            }
        }
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
        REGISTERED_MAILBOXES.clear();
        PLAYER_NAMES.clear();
    }
}
