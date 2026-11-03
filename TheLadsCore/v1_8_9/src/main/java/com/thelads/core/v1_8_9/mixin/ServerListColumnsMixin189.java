package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Columns and the drag's gap on GuiListExtended, which draws and hits rows by index alone: each row goes through its slot here
 * (ServerListColumns, ServerListDrag). "List width" becomes a column's, as that is what rows are drawn with; one column with no drag stays vanilla's.
 */
@Mixin(ServerSelectionList.class)
abstract class ServerListColumnsMixin189 extends GuiListExtended implements ServerListColumns.Rows, ServerListColumns.Gap {
    @Unique private int lads$from = -1, lads$to = -1;

    private ServerListColumnsMixin189(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
        super(mc, width, height, top, bottom, slotHeight);
    }

    @Unique
    private int lads$columns() {
        return ServerListColumns.fit(ServerListColumns.wanted(), width);
    }

    @Inject(method = "getListWidth", at = @At("HEAD"), cancellable = true)
    private void lads$columnWidth(CallbackInfoReturnable<Integer> cir) {
        int columns = lads$columns();
        if (columns > 1) cir.setReturnValue(ServerListColumns.width(columns, width));
    }

    @Inject(method = "getScrollBarX", at = @At("HEAD"), cancellable = true)
    private void lads$columnsScrollbar(CallbackInfoReturnable<Integer> cir) {
        int columns = lads$columns();
        if (columns > 1) cir.setReturnValue(width / 2 + ServerListColumns.total(columns, width) / 2 + 2);
    }

    @Override
    protected int getContentHeight() {
        int columns = lads$columns();
        return columns == 1 ? super.getContentHeight() : ServerListColumns.rows(getSize(), columns) * slotHeight + headerPadding;
    }

    /** All columns' left edge. */
    @Unique
    private int lads$left(int columns) {
        return left + width / 2 - (columns == 1 ? getListWidth() : ServerListColumns.total(columns, width)) / 2;
    }

    @Unique
    private int lads$firstTop() {
        return top + 4 - getAmountScrolled() + headerPadding;
    }

    @Override
    public int lads$size() {
        return getSize();
    }

    @Override
    public void lads$gap(int from, int to) {
        lads$from = from;
        lads$to = to;
    }

    @Override
    public int lads$rowLeft(int row) {
        int columns = lads$columns();
        return ServerListColumns.x(ServerListDrag.slot(row, lads$from, lads$to), columns, lads$left(columns), getListWidth()) + 2;
    }

    @Override
    public int lads$rowTop(int row) {
        return ServerListColumns.y(ServerListDrag.slot(row, lads$from, lads$to), lads$columns(), lads$firstTop(), slotHeight);
    }

    @Override
    public int getSlotIndexFromScreenCoords(int x, int y) {
        int columns = lads$columns();
        if (columns == 1) return super.getSlotIndexFromScreenCoords(x, y);
        return ServerListColumns.slotAt(x, y, lads$left(columns), lads$firstTop(), getListWidth(), slotHeight, columns, getSize());
    }

    /** Vanilla's press, with the row's own position for the relative coordinates (the icon's join and arrows). */
    @Override
    public boolean mouseClicked(int x, int y, int button) {
        if (lads$columns() == 1) return super.mouseClicked(x, y, button);
        if (!isMouseYWithinSlotBounds(y)) return false;
        int row = getSlotIndexFromScreenCoords(x, y);
        if (row < 0 || !getListEntry(row).mousePressed(row, x, y, button, x - lads$rowLeft(row), y - lads$rowTop(row))) return false;
        setEnabled(false);
        return true;
    }

    /** Vanilla's loop with each row's own place, its box as vanilla's colours; the dragged row is drawn as the ghost, not in the gap it left. */
    @Override
    protected void drawSelectionBox(int x, int y, int mouseX, int mouseY) {
        if (lads$columns() == 1 && lads$from < 0) {
            super.drawSelectionBox(x, y, mouseX, mouseY);
            return;
        }
        int width = getListWidth(), height = slotHeight - 4;
        for (int i = 0; i < getSize(); i++) {
            int rowLeft = lads$rowLeft(i), rowTop = lads$rowTop(i);
            if (i == lads$from || rowTop > bottom || rowTop + height < top) continue;
            if (showSelectionBox && isSelected(i)) {
                Gui.drawRect(rowLeft - 2, rowTop - 2, rowLeft - 2 + width, rowTop + height + 2, 0xFF808080);
                Gui.drawRect(rowLeft - 1, rowTop - 1, rowLeft - 3 + width, rowTop + height + 1, 0xFF000000);
            }
            drawSlot(i, rowLeft, rowTop, height, lads$from < 0 ? mouseX : -1000, lads$from < 0 ? mouseY : -1000);
        }
    }
}
