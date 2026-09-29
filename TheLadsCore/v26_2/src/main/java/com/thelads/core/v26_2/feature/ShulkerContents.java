package com.thelads.core.v26_2.feature;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Client-thread snapshots of actual component/menu data, backed by bounded last-observed history. */
public final class ShulkerContents {
    private static final int MAX_ENTRIES = 256, MAX_PENDING = 8;
    private static final long REFRESH_NANOS = 1_000_000_000L;
    private static final LinkedHashMap<ShulkerBoxBlockEntity, Entry> CONTENTS = new LinkedHashMap<>();
    private static ClientLevel level;
    private static ShulkerBoxBlockEntity clicked, openBox;
    private static ShulkerBoxMenu openMenu;
    private static long clickedAt;
    private static int pending;
    private ShulkerContents() {}

    private static final class Entry {
        ItemStack first = ItemStack.EMPTY;
        boolean known, pending, uniform = true;
        List<ItemStack> serverSlots;
        long requestedAt;
    }

    public static ItemStack first(ItemContainerContents contents) {
        return contents == null ? ItemStack.EMPTY : contents.nonEmptyItemCopyStream().findFirst().orElse(ItemStack.EMPTY);
    }

    public static ItemStack first(List<ItemStack> slots, int count) {
        for (int slot = 0; slot < Math.min(count, slots.size()); slot++) {
            ItemStack stack = slots.get(slot);
            if (!stack.isEmpty()) return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    public static void tick() {
        syncLevel();
        ShulkerObservations.tick();
        if (openMenu != null && (Minecraft.getInstance().player == null
            || Minecraft.getInstance().player.containerMenu != openMenu)) {
            if (!NativeQualityOfLife.bool("ShulkerBoxUtils", "Remember Observed Contents", true) && openBox != null) {
                CONTENTS.remove(openBox);
                ShulkerObservations.invalidate(openBox.getBlockPos());
            }
            openMenu = null;
            openBox = null;
        }
    }

    private static void syncLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == level) return;
        level = current;
        CONTENTS.clear();
        clicked = openBox = null;
        openMenu = null;
        pending = 0;
        ShulkerObservations.switchLevel(current);
    }

    private static Entry entry(ShulkerBoxBlockEntity box) {
        Entry value = CONTENTS.get(box);
        if (value == null) {
            if (CONTENTS.size() >= MAX_ENTRIES) CONTENTS.remove(CONTENTS.keySet().iterator().next());
            value = new Entry();
            CONTENTS.put(box, value);
        }
        return value;
    }

    private static boolean valid(ShulkerBoxBlockEntity box) {
        return level != null && box != null && box.getLevel() == level && !box.isRemoved()
            && level.hasChunkAt(box.getBlockPos()) && level.getBlockEntity(box.getBlockPos()) == box;
    }

    public static void placed(ShulkerBoxBlockEntity box, ItemStack stack) {
        if (box.getLevel() == null || !box.getLevel().isClientSide()) return;
        syncLevel();
        if (!valid(box)) return;
        Entry value = entry(box);
        observed(box, value, ShulkerSummary.of(stack.get(DataComponents.CONTAINER)));
        value.serverSlots = null;
    }

    private static void observed(ShulkerBoxBlockEntity box, Entry value, ShulkerSummary contents) {
        value.first = contents.first();
        value.uniform = contents.uniform();
        value.known = true;
        ShulkerObservations.observed(box, value.first, value.uniform);
    }

    public static void invalidated(BlockPos pos) {
        syncLevel();
        ShulkerObservations.invalidate(pos);
        if (level != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)
            CONTENTS.remove(box);
    }

    public static void removed(ShulkerBoxBlockEntity box) {
        if (box.getLevel() == null || !box.getLevel().isClientSide()) return;
        CONTENTS.remove(box);
        if (box == clicked) clicked = null;
        if (box == openBox) { openBox = null; openMenu = null; }
    }

    public static void clicked(BlockPos pos) {
        syncLevel();
        clicked = level != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box ? box : null;
        clickedAt = System.nanoTime();
    }

    /** Vanilla OpenScreen has no block position: associate only a recent click on this exact loaded box. */
    public static void opened() {
        syncLevel();
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof ShulkerBoxMenu menu)
            || !valid(clicked) || System.nanoTime() - clickedAt > 3_000_000_000L
            || player.distanceToSqr(clicked.getBlockPos().getX() + .5, clicked.getBlockPos().getY() + .5,
                clicked.getBlockPos().getZ() + .5) > 64) {
            openBox = null;
            openMenu = null;
            return;
        }
        openBox = clicked;
        openMenu = menu;
        clicked = null;
        // The menu starts empty before its first server content packet; do not display a prediction.
        Entry value = entry(openBox);
        value.known = false;
        value.serverSlots = null;
        ShulkerObservations.invalidate(openBox.getBlockPos());
    }

    public static void content(int containerId, List<ItemStack> authoritativeSlots) {
        syncLevel();
        if (authoritativeSlots.size() < 27 || openMenu == null || openMenu.containerId != containerId || !valid(openBox)
            || Minecraft.getInstance().player == null || Minecraft.getInstance().player.containerMenu != openMenu) return;
        Entry value = entry(openBox);
        value.serverSlots = new ArrayList<>(27);
        for (int slot = 0; slot < 27; slot++) value.serverSlots.add(authoritativeSlots.get(slot).copy());
        observed(openBox, value, ShulkerSummary.of(value.serverSlots));
    }

    public static void slot(int containerId, int slot, ItemStack item) {
        syncLevel();
        if (openMenu == null || openMenu.containerId != containerId || slot < 0 || slot >= 27 || !valid(openBox)
            || Minecraft.getInstance().player == null || Minecraft.getInstance().player.containerMenu != openMenu) return;
        Entry value = CONTENTS.get(openBox);
        if (value == null || value.serverSlots == null) return;
        // Copy the acknowledged slot only. Other native menu slots may contain unacknowledged click predictions.
        value.serverSlots.set(slot, item.copy());
        observed(openBox, value, ShulkerSummary.of(value.serverSlots));
    }

    public static void otherOpening(BlockPos pos, int event, int viewers) {
        if (event != ShulkerBoxBlockEntity.EVENT_SET_OPEN_COUNT || viewers <= 0) return;
        syncLevel();
        if (level == null || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)) return;
        if (box != openBox) invalidated(pos);
    }

    /** Read only on the extraction thread. Item models receive a stable cached copy, never a server-owned stack. */
    public static ItemStack resolve(ShulkerBoxBlockEntity box) {
        syncLevel();
        if (!valid(box)) return ItemStack.EMPTY;
        Entry value = entry(box);
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            long now = System.nanoTime();
            if (!value.pending && pending < MAX_PENDING && (value.requestedAt == 0 || now - value.requestedAt >= REFRESH_NANOS)) {
                value.pending = true;
                value.requestedAt = now;
                pending++;
                ClientLevel requestedLevel = level;
                BlockPos pos = box.getBlockPos().immutable();
                try { server.execute(() -> {
                    ShulkerSummary snapshot = null;
                    boolean known = false;
                    try {
                        var serverLevel = server.getLevel(requestedLevel.dimension());
                        if (serverLevel != null && serverLevel.hasChunkAt(pos)
                            && serverLevel.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity authoritative
                            && authoritative.getLootTable() == null) {
                            snapshot = ShulkerSummary.of(authoritative.collectComponents().get(DataComponents.CONTAINER));
                            known = true;
                        }
                    } catch (RuntimeException unavailable) {
                        // A closing world or another mod's container can be unavailable. Retry next refresh, without a preview.
                    }
                    ShulkerSummary answer = snapshot;
                    boolean available = known;
                    minecraft.execute(() -> {
                        syncLevel();
                        if (level != requestedLevel) return;
                        pending = Math.max(0, pending - 1);
                        value.pending = false;
                        if (valid(box) && CONTENTS.get(box) == value) {
                            if (available && answer != null) observed(box, value, answer);
                            else {
                                value.first = ItemStack.EMPTY;
                                value.known = false;
                                ShulkerObservations.invalidate(box.getBlockPos());
                            }
                        }
                    });
                }); } catch (java.util.concurrent.RejectedExecutionException closing) {
                    value.pending = false;
                    pending = Math.max(0, pending - 1);
                }
            }
        } else if (!NativeQualityOfLife.bool("ShulkerBoxUtils", "Remember Observed Contents", true) && box != openBox) {
            return ItemStack.EMPTY;
        } else if (!value.known && box != openBox) {
            var saved = ShulkerObservations.get(box);
            if (saved != null) {
                value.first = saved.first().copy();
                value.uniform = saved.uniform();
                value.known = true;
            }
        }
        return value.known && (NativeQualityOfLife.choice("ShulkerBoxUtils", "Display Mode", 0) == 0 || value.uniform)
            ? value.first : ItemStack.EMPTY;
    }
}
