package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.v1_8_9.feature.WorldSlotDraw189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.GuiSelectWorld$List")
abstract class WorldListColumnsMixin189 extends GuiSlot implements ServerListColumns.Rows, ServerListColumns.Gap, WorldSlotDraw189 {
    @Unique private int lads$from = -1, lads$to = -1;

    private WorldListColumnsMixin189(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
        super(mc, width, height, top, bottom, slotHeight);
    }

    @Shadow
    protected abstract void drawSlot(int entryID, int x, int y, int height, int mouseXIn, int mouseYIn);

    @Unique
    private int lads$columns() {
        return ServerListColumns.fit(ServerListColumns.wanted(), width);
    }

    @Override
    public int getListWidth() {
        int columns = lads$columns();
        return columns > 1 ? ServerListColumns.width(columns, width) : super.getListWidth();
    }

    @Override
    protected int getScrollBarX() {
        int columns = lads$columns();
        return columns > 1 ? width / 2 + ServerListColumns.total(columns, width) / 2 + 2 : super.getScrollBarX();
    }

    @Inject(method = "getContentHeight", at = @At("HEAD"), cancellable = true)
    private void lads$contentHeight(CallbackInfoReturnable<Integer> cir) {
        int columns = lads$columns();
        if (columns > 1) {
            cir.setReturnValue(ServerListColumns.rows(getSize(), columns) * slotHeight + headerPadding);
        }
    }

    /** WorldListMenuMixin189 handles left-click selection and double-click across all columns cleanly. */
    @Inject(method = "elementClicked", at = @At("HEAD"), cancellable = true)
    private void lads$skipVanillaSlotClick(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
    }

    @Redirect(
        method = "drawSlot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiSelectWorld;drawString(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V"
        )
    )
    private void lads$clipWorldText(GuiSelectWorld screen, FontRenderer font, String text, int x, int y, int color) {
        String shown = text;
        if (shown != null && lads$columns() > 1) {
            int maxWidth = Math.max(40, getListWidth() - 8);
            if (font.getStringWidth(shown) > maxWidth) {
                shown = font.trimStringToWidth(shown, Math.max(12, maxWidth - font.getStringWidth("..."))) + "...";
            }
        }
        screen.drawString(font, shown, x, y, color);
    }

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

    @Override
    protected void drawSelectionBox(int x, int y, int mouseX, int mouseY) {
        if (lads$columns() == 1 && lads$from < 0) {
            super.drawSelectionBox(x, y, mouseX, mouseY);
            return;
        }
        int colWidth = getListWidth(), height = slotHeight - 4;
        for (int i = 0; i < getSize(); i++) {
            int rowLeft = lads$rowLeft(i), rowTop = lads$rowTop(i);
            if (i == lads$from || rowTop > bottom || rowTop + height < top) continue;
            if (showSelectionBox && isSelected(i)) {
                Gui.drawRect(rowLeft - 2, rowTop - 2, rowLeft - 2 + colWidth, rowTop + height + 2, 0xFF808080);
                Gui.drawRect(rowLeft - 1, rowTop - 1, rowLeft - 3 + colWidth, rowTop + height + 1, 0xFF000000);
            }
            drawSlot(i, rowLeft, rowTop, height, lads$from < 0 ? mouseX : -1000, lads$from < 0 ? mouseY : -1000);
        }
    }

    @Override
    public void lads$drawWorldSlot(int index, int x, int y, int height, int mouseX, int mouseY) {
        drawSlot(index, x, y, height, mouseX, mouseY);
    }
}
