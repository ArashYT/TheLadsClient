package com.thelads.core.v1_20_1.hud.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.hud.Hud1201;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ShulkerBoxUtils' world icon (26.3 ShulkerContents + ShulkerIconRendererMixin): the first item of a placed box floats above it.
 * Client-thread snapshots of actual data, backed by bounded last-observed history: the integrated server's box (singleplayer,
 * LAN host), the contents of the item a box was placed from, and on servers what an opened box's menu packets showed.
 */
public final class ShulkerContents1201 {
    private static final int MAX_ENTRIES = 256, MAX_PENDING = 8;
    private static final long REFRESH_NANOS = 1_000_000_000L;
    private static final LinkedHashMap<ShulkerBoxBlockEntity, Entry> CONTENTS = new LinkedHashMap<>();
    private static ClientLevel level;
    private static ShulkerBoxBlockEntity clicked, openBox;
    private static ShulkerBoxMenu openMenu;
    private static long clickedAt;
    private static int pending;
    private ShulkerContents1201() {}

    private static final class Entry {
        ItemStack first = ItemStack.EMPTY;
        boolean known, pending, uniform = true;
        List<ItemStack> serverSlots;
        long requestedAt;
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
        ShulkerObservations1201.tick();
        if (openMenu != null && (Minecraft.getInstance().player == null
            || Minecraft.getInstance().player.containerMenu != openMenu)) {
            if (!Hud1201.bool("ShulkerBoxUtils", "Remember Observed Contents", true) && openBox != null) {
                CONTENTS.remove(openBox);
                ShulkerObservations1201.invalidate(openBox.getBlockPos());
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
        ShulkerObservations1201.switchLevel(current);
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

    /** BlockItem.updateCustomBlockEntityTag on the client: a box was just placed from {@code stack}, whose contents it now holds. */
    public static void placed(net.minecraft.world.level.Level placedIn, BlockPos pos, ItemStack stack) {
        if (placedIn == null || !placedIn.isClientSide() || !(placedIn.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)) return;
        syncLevel();
        if (!valid(box)) return;
        var data = BlockItem.getBlockEntityData(stack);
        if (data != null && data.contains("LootTable", 8)) return; // filled by the server when first opened: unknown
        Entry value = entry(box);
        observed(box, value, ShulkerSummary1201.of(data));
        value.serverSlots = null;
    }

    private static void observed(ShulkerBoxBlockEntity box, Entry value, ShulkerSummary1201 contents) {
        value.first = contents.first();
        value.uniform = contents.uniform();
        value.known = true;
        ShulkerObservations1201.observed(box, value.first, value.uniform);
    }

    /** Any accepted block change at {@code pos} (Level.setBlock on the client) invalidates what was known there. */
    public static void invalidated(BlockPos pos) {
        syncLevel();
        ShulkerObservations1201.invalidate(pos);
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

    /** Vanilla's open-screen packet has no block position: associate only a recent click on this exact loaded box. */
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
        ShulkerObservations1201.invalidate(openBox.getBlockPos());
    }

    public static void content(int containerId, List<ItemStack> authoritativeSlots) {
        syncLevel();
        if (authoritativeSlots.size() < 27 || openMenu == null || openMenu.containerId != containerId || !valid(openBox)
            || Minecraft.getInstance().player == null || Minecraft.getInstance().player.containerMenu != openMenu) return;
        Entry value = entry(openBox);
        value.serverSlots = new ArrayList<>(27);
        for (int slot = 0; slot < 27; slot++) value.serverSlots.add(authoritativeSlots.get(slot).copy());
        observed(openBox, value, ShulkerSummary1201.of(value.serverSlots));
    }

    public static void slot(int containerId, int slot, ItemStack item) {
        syncLevel();
        if (openMenu == null || openMenu.containerId != containerId || slot < 0 || slot >= 27 || !valid(openBox)
            || Minecraft.getInstance().player == null || Minecraft.getInstance().player.containerMenu != openMenu) return;
        Entry value = CONTENTS.get(openBox);
        if (value == null || value.serverSlots == null) return;
        // Copy the acknowledged slot only. Other menu slots may contain unacknowledged click predictions.
        value.serverSlots.set(slot, item.copy());
        observed(openBox, value, ShulkerSummary1201.of(value.serverSlots));
    }

    /** Another player opening a box (block event "open count") makes what was last seen in it unreliable. */
    public static void otherOpening(BlockPos pos, int event, int viewers) {
        if (event != ShulkerBoxBlockEntity.EVENT_SET_OPEN_COUNT || viewers <= 0) return;
        syncLevel();
        if (level == null || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)) return;
        if (box != openBox) invalidated(pos);
    }

    /** The first item to show above {@code box}, or empty. Item models receive a stable cached copy, never a server-owned stack. */
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
                    ShulkerSummary1201 snapshot = null;
                    try {
                        var serverLevel = server.getLevel(requestedLevel.dimension());
                        if (serverLevel != null && serverLevel.hasChunkAt(pos)
                            && serverLevel.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity authoritative) {
                            var data = authoritative.saveWithoutMetadata();
                            // A box still holding its loot table has no contents until someone opens it.
                            if (!data.contains("LootTable", 8)) snapshot = ShulkerSummary1201.of(data);
                        }
                    } catch (RuntimeException unavailable) {
                        // A closing world or another mod's container can be unavailable. Retry next refresh, without a preview.
                    }
                    ShulkerSummary1201 answer = snapshot;
                    minecraft.execute(() -> {
                        syncLevel();
                        if (level != requestedLevel) return;
                        pending = Math.max(0, pending - 1);
                        value.pending = false;
                        if (valid(box) && CONTENTS.get(box) == value) {
                            if (answer != null) observed(box, value, answer);
                            else {
                                value.first = ItemStack.EMPTY;
                                value.known = false;
                                ShulkerObservations1201.invalidate(box.getBlockPos());
                            }
                        }
                    });
                }); } catch (java.util.concurrent.RejectedExecutionException closing) {
                    value.pending = false;
                    pending = Math.max(0, pending - 1);
                }
            }
        } else if (!Hud1201.bool("ShulkerBoxUtils", "Remember Observed Contents", true) && box != openBox) {
            return ItemStack.EMPTY;
        } else if (!value.known && box != openBox) {
            var saved = ShulkerObservations1201.get(box);
            if (saved != null) {
                value.first = saved.first().copy();
                value.uniform = saved.uniform();
                value.known = true;
            }
        }
        return value.known && (Hud1201.choice("ShulkerBoxUtils", "Display Mode", 0) == 0 || value.uniform)
            ? value.first : ItemStack.EMPTY;
    }

    /** ShulkerBoxRenderer.render TAIL: the box's first item, facing the camera, above the lid (rising with it when it opens upwards). */
    public static void renderIcon(ShulkerBoxBlockEntity box, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        if (box.getLevel() == null || !Hud1201.enabled("ShulkerBoxUtils") || !Hud1201.bool("ShulkerBoxUtils", "World Icon", true)
            || minecraft.options.hideGui) return;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        if (!Vec3.atCenterOf(box.getBlockPos()).closerThan(camera, Hud1201.number("ShulkerBoxUtils", "Distance", 24))) return;
        ItemStack first = resolve(box);
        if (first.isEmpty()) return;
        float height = 1 + (float) Hud1201.number("ShulkerBoxUtils", "Height", 35) / 100;
        var state = box.getBlockState();
        if (!state.hasProperty(ShulkerBoxBlock.FACING) || state.getValue(ShulkerBoxBlock.FACING) == Direction.UP)
            height += box.getProgress(partialTick) * .5f;
        if (Hud1201.bool("ShulkerBoxUtils", "Animate", true) && minecraft.options.screenEffectScale().get() > 0)
            height += (float) Math.sin((box.getLevel().getGameTime() + partialTick) * .12) * .035f;
        float scale = (float) Hud1201.number("ShulkerBoxUtils", "Icon Size", 65) / 100;
        pose.pushPose();
        try {
            pose.translate(.5, height, .5);
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(scale, scale, scale);
            minecraft.getItemRenderer().renderStatic(first, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                box.getLevel(), box.getBlockPos().hashCode());
        } finally {
            pose.popPose();
        }
    }
}
