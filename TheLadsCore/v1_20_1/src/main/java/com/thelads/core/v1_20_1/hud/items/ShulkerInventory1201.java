package com.thelads.core.v1_20_1.hud.items;

import com.thelads.core.v1_20_1.hud.Hud1201;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * ShulkerBoxUtils for box items (26.3 ShulkerInventory + ShulkerTooltip): the 9x3 contents grid in tooltips, and the first-item
 * badge and free-slot bar over boxes in inventories and the hotbar. Contents are the item's {@code BlockEntityTag.Items}.
 */
public final class ShulkerInventory1201 {
    private static final ThreadLocal<Boolean> DRAWING_BADGE = ThreadLocal.withInitial(() -> false);
    private ShulkerInventory1201() {}

    /** A shulker box item whose contents its tooltip may show ("HideFlags" can hide the additional tooltip, where they live). */
    public static boolean eligible(ItemStack item) {
        if (!Hud1201.enabled("ShulkerBoxUtils") || item.isEmpty()
            || !(item.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) return false;
        return (hideFlags(item) & ItemStack.TooltipPart.ADDITIONAL.getMask()) == 0;
    }
    private static int hideFlags(ItemStack item) {
        CompoundTag tag = item.getTag();
        return tag != null && tag.contains("HideFlags", 99) ? tag.getInt("HideFlags") : 0;
    }

    /** The grid replaces vanilla's item list; a box still waiting for its loot table keeps vanilla's "???????". */
    public static boolean preview(ItemStack stack) {
        if (!eligible(stack) || !Hud1201.bool("ShulkerBoxUtils", "Contents Preview", true)) return false;
        CompoundTag data = BlockItem.getBlockEntityData(stack);
        return data == null || !data.contains("LootTable", 8);
    }

    public static ShulkerSummary1201 contents(ItemStack stack) { return ShulkerSummary1201.of(BlockItem.getBlockEntityData(stack)); }

    /** GuiGraphics.renderItem TAIL (after the item model, before count and bar decorations). */
    public static void decorate(GuiGraphics graphics, ItemStack item, int x, int y) {
        if (DRAWING_BADGE.get() || !eligible(item)) return;
        boolean badge = Hud1201.bool("ShulkerBoxUtils", "Inventory Badge", true);
        boolean bar = Hud1201.bool("ShulkerBoxUtils", "Free Slot Bar", true);
        if (!badge && !bar) return;
        // ponytail: parses the box NBT per drawn box per frame, as 26.3 copies its component; cache by tag if profiling shows it.
        ShulkerSummary1201 contents = contents(item);
        if (badge && contents.badgeVisible()) {
            var pose = graphics.pose();
            pose.pushPose();
            DRAWING_BADGE.set(true);
            try {
                // Half size over the box's centre; z +100 keeps the small model in front of the box model's depth.
                pose.translate(x + 4, y + 4, 100);
                pose.scale(.5f, .5f, .5f);
                graphics.renderItem(contents.first(), 0, 0);
            } finally {
                DRAWING_BADGE.set(false);
                pose.popPose();
            }
        }
        if (bar) {
            graphics.fill(RenderType.guiOverlay(), x + 2, y + 13, x + 15, y + 15, 0xff000000);
            graphics.fill(RenderType.guiOverlay(), x + 2, y + 13, x + 2 + contents.freeBarWidth(), y + 14, contents.freeBarColor());
        }
    }

    /** Lads-owned 9x3 preview with native stack-count decorations. */
    public record Tooltip(ShulkerSummary1201 contents) implements TooltipComponent, ClientTooltipComponent {
        @Override public int getWidth(Font font) { return 174; }
        @Override public int getHeight() { return 76; }
        @Override public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            graphics.fill(x, y, x + 174, y + 73, 0xff151a21);
            graphics.fill(x, y, x + 174, y + 1, 0xff61d9b4);
            graphics.drawString(font, contents.occupied() + "/27 slots  ·  " + contents.count() + " items", x + 6, y + 4, 0xffb8c9d0, false);
            boolean counts = Hud1201.bool("ShulkerBoxUtils", "Item Counts", true);
            for (int slot = 0; slot < 27; slot++) {
                int left = x + 6 + slot % 9 * 18, top = y + 16 + slot / 9 * 18;
                graphics.fill(left, top, left + 17, top + 17, 0xff252e39);
                ItemStack item = contents.slots().get(slot);
                if (item.isEmpty()) continue;
                graphics.renderItem(item, left, top);
                if (counts) graphics.renderItemDecorations(font, item, left, top);
            }
        }
    }
}
