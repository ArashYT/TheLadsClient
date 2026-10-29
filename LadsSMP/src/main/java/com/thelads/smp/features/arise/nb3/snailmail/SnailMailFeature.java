package com.thelads.smp.features.arise.nb3.snailmail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Snail Mail Feature:
 * Postal mail delivery system with locked mailboxes, delivery scheduling,
 * and recipient delivery routing.
 */
public final class SnailMailFeature {
    /**
     * Mail is never dropped: the recipient is resolved to a UUID when sending (unknown names are refused before the
     * item is taken) and a delivery stays queued until it lands in the recipient's mailbox or inventory. Queue, known
     * names and mailbox registry are saved with the world (data/ladssmp/snail_mail.dat), in the same save as the
     * sender's inventory.
     */
    public record PendingDelivery(
            UUID senderUuid,
            UUID recipientUuid,
            String recipientName,
            ItemStack mailStack,
            long deliverAtTimestampMs
    ) {
        static final Codec<PendingDelivery> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("sender").forGetter(PendingDelivery::senderUuid),
                UUIDUtil.STRING_CODEC.fieldOf("recipient").forGetter(PendingDelivery::recipientUuid),
                Codec.STRING.fieldOf("recipient_name").forGetter(PendingDelivery::recipientName),
                ItemStack.lenientOptionalFieldOf("item").forGetter(PendingDelivery::mailStack),
                Codec.LONG.fieldOf("deliver_at").forGetter(PendingDelivery::deliverAtTimestampMs)
        ).apply(i, PendingDelivery::new));
    }

    public record MailboxLocation(ResourceKey<Level> dimension, BlockPos pos) {
        static final Codec<MailboxLocation> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.resources.ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION).fieldOf("dimension").forGetter(MailboxLocation::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(MailboxLocation::pos)
        ).apply(i, MailboxLocation::new));
    }

    public static final class MailData extends SavedData {
        static final Codec<MailData> CODEC = RecordCodecBuilder.create(i -> i.group(
                PendingDelivery.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(d -> d.pending),
                Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC).optionalFieldOf("names", Map.of()).forGetter(d -> d.names),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, MailboxLocation.CODEC).optionalFieldOf("mailboxes", Map.of()).forGetter(d -> d.mailboxes)
        ).apply(i, MailData::new));
        static final SavedDataType<MailData> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath("ladssmp", "snail_mail"), MailData::new, CODEC, null);

        final List<PendingDelivery> pending;
        final Map<String, UUID> names; // lower-case name -> uuid, recorded on join
        final Map<UUID, MailboxLocation> mailboxes;

        MailData() {
            this(List.of(), Map.of(), Map.of());
        }

        private MailData(List<PendingDelivery> pending, Map<String, UUID> names, Map<UUID, MailboxLocation> mailboxes) {
            this.pending = new ArrayList<>(pending);
            this.names = new HashMap<>(names);
            this.mailboxes = new HashMap<>(mailboxes);
        }
    }

    /** The running server's mail data (in-memory only until a server starts, e.g. in unit tests). */
    private static MailData data = new MailData();

    private SnailMailFeature() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> data = server.getDataStorage().computeIfAbsent(MailData.TYPE));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> data = new MailData());
    }

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
    public static PendingDelivery scheduleDelivery(UUID sender, UUID recipient, String recipientName, ItemStack stack, long nowMs) {
        long delayMs = AriseNb3Config.get().snailMail.deliveryDelayMinutes * 60_000L;
        PendingDelivery delivery = new PendingDelivery(sender, recipient, recipientName, stack.copy(), nowMs + delayMs);
        data.pending.add(delivery);
        data.setDirty();
        return delivery;
    }

    /** Online player, or anyone who joined since this was saved; null = unknown (refuse instead of losing the item). */
    public static UUID resolveRecipient(MinecraftServer server, String name) {
        if (name == null || name.isBlank()) return null;
        ServerPlayer online = server != null ? server.getPlayerList().getPlayerByName(name) : null;
        if (online != null) {
            return online.getUUID();
        }
        UUID fromData = data.names.get(name.toLowerCase(Locale.ROOT));
        if (fromData != null) {
            return fromData;
        }
        if (server != null && server.services() != null) {
            try {
                if (server.services().nameToIdCache() != null) {
                    var cached = server.services().nameToIdCache().get(name);
                    if (cached.isPresent()) {
                        return cached.get().id();
                    }
                }
            } catch (Exception ignored) {}
            try {
                if (server.services().profileResolver() != null) {
                    var profile = server.services().profileResolver().fetchByName(name);
                    if (profile.isPresent()) {
                        return profile.get().id();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static void registerMailbox(UUID owner, Level level, BlockPos pos) {
        if (owner != null && level != null && pos != null) {
            data.mailboxes.put(owner, new MailboxLocation(level.dimension(), pos.immutable()));
            data.setDirty();
        }
    }

    /** Mailboxes placed before the registry was saved re-register when their chunk loads. */
    public static void registerMailboxIfAbsent(UUID owner, Level level, BlockPos pos) {
        if (owner != null && !data.mailboxes.containsKey(owner)) registerMailbox(owner, level, pos);
    }

    /** Only forgets the registration if it points at this mailbox (an owner may have placed a newer one). */
    public static void unregisterMailbox(UUID owner, Level level, BlockPos pos) {
        if (owner != null && level != null && pos != null && new MailboxLocation(level.dimension(), pos).equals(data.mailboxes.get(owner))) {
            data.mailboxes.remove(owner);
            data.setDirty();
        }
    }

    public static void recordPlayerName(String name, UUID uuid) {
        if (name != null && uuid != null && !uuid.equals(data.names.put(name.toLowerCase(Locale.ROOT), uuid))) {
            data.setDirty();
        }
    }

    public static void tickDeliveries(MinecraftServer server) {
        // Once a second is plenty; a delivery that cannot land yet (recipient offline, no mailbox) just stays queued
        if (server == null || !AriseNb3Config.get().snailMail.enabled || server.getTickCount() % 20 != 0) return;
        long now = System.currentTimeMillis();
        Iterator<PendingDelivery> it = data.pending.iterator();
        while (it.hasNext()) {
            PendingDelivery delivery = it.next();
            if (delivery.deliverAtTimestampMs() <= now && tryDeliver(server, delivery)) {
                it.remove();
                data.setDirty();
            }
        }
    }

    private static boolean tryDeliver(MinecraftServer server, PendingDelivery delivery) {
        ItemStack stack = delivery.mailStack();
        if (stack.isEmpty()) return true; // nothing left to deliver (e.g. the item's mod was removed)
        UUID targetUuid = delivery.recipientUuid();
        ServerPlayer recipient = server.getPlayerList().getPlayer(targetUuid);
        MailboxLocation loc = data.mailboxes.get(targetUuid);
        if (loc != null) {
            ServerLevel targetLevel = server.getLevel(loc.dimension());
            if (targetLevel != null && targetLevel.getBlockEntity(loc.pos()) instanceof MailboxBlockEntity mb
                    && targetUuid.equals(mb.getOwnerUuid())) {
                mb.depositMail(stack);
                if (recipient != null) {
                    recipient.sendSystemMessage(Component.literal("You have received mail in your mailbox!").withStyle(ChatFormatting.GOLD));
                }
                return true;
            }
            data.mailboxes.remove(targetUuid); // stale registration: stop loading that chunk
            data.setDirty();
        }
        if (recipient != null) {
            recipient.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY); // a full inventory drops the rest at their feet
            recipient.sendSystemMessage(Component.literal("A mail delivery arrived directly to your inventory!").withStyle(ChatFormatting.GOLD));
            return true;
        }
        return false;
    }

    public static List<PendingDelivery> getReadyDeliveries(long nowMs) {
        List<PendingDelivery> ready = new ArrayList<>();
        for (PendingDelivery d : data.pending) {
            if (d.deliverAtTimestampMs() <= nowMs) ready.add(d);
        }
        return ready;
    }

    public static List<PendingDelivery> getPendingDeliveries() {
        return data.pending;
    }

    public static void clearPendingForTesting() {
        data = new MailData();
    }
}
