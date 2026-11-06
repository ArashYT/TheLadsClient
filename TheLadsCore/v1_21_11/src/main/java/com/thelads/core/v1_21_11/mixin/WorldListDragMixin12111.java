package com.thelads.core.v1_21_11.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.WorldListOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldSelectionList.class)
abstract class WorldListDragMixin12111 extends ObjectSelectionList<WorldSelectionList.Entry> implements ServerListColumns.Layout {
    @Shadow private String filter;
    @Shadow private List<LevelSummary> currentlyDisplayedLevels;
    @Shadow private CompletableFuture<List<LevelSummary>> pendingLevels;
    @Shadow private void fillLevels(String filter, List<LevelSummary> levels) {}

    @Unique private double lads$pressX, lads$pressY, lads$grabX, lads$grabY, lads$mouseX, lads$mouseY;
    @Unique private boolean lads$pressed;
    @Unique private int lads$from = -1, lads$to = -1;

    private WorldListDragMixin12111(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    @ModifyVariable(method = "fillLevels", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<LevelSummary> lads$sortWorlds(List<LevelSummary> levels) {
        if (levels == null) return null;
        return new ArrayList<>(WorldListOrder.sortedCopy(levels, LevelSummary::getLevelId, LevelSummary::getLastPlayed));
    }

    @Inject(method = "fillLevels", at = @At("RETURN"))
    private void lads$afterFill(String filterStr, List<LevelSummary> levels, CallbackInfo ci) {
        lads$layout();
    }

    @Override
    public int lads$columns() {
        return ServerListColumns.fit(ServerListColumns.wanted(), width);
    }

    @Override
    public boolean lads$hidden(Object row) {
        return lads$from >= 0 && children().indexOf(row) == lads$from;
    }

    @Inject(method = "getRowWidth", at = @At("HEAD"), cancellable = true)
    private void lads$columnsWidth(CallbackInfoReturnable<Integer> cir) {
        int columns = lads$columns();
        if (columns > 1) cir.setReturnValue(ServerListColumns.total(columns, width));
    }

    @Override
    protected int contentHeight() {
        int columns = lads$columns();
        return columns == 1 ? super.contentHeight() : ServerListColumns.rows(getItemCount(), columns) * defaultEntryHeight + 4;
    }

    @Unique
    private void lads$fitEntryWidgets(WorldSelectionList.Entry row, int rowWidth) {
        if (row instanceof WorldSelectionList.WorldListEntry) {
            WorldListEntryWidgetsAccessor acc = (WorldListEntryWidgetsAccessor) (Object) row;
            int maxW = Math.max(40, rowWidth - 38);
            if (acc.ladsWorldNameText() != null) acc.ladsWorldNameText().setMaxWidth(maxW);
            if (acc.ladsIdAndLastPlayedText() != null) acc.ladsIdAndLastPlayedText().setMaxWidth(maxW);
            if (acc.ladsInfoText() != null) acc.ladsInfoText().setMaxWidth(maxW);
        }
    }

    @Override
    public void lads$layout() {
        int columns = lads$columns();
        List<WorldSelectionList.Entry> rows = children();
        if (columns == 1 && lads$from < 0) {
            int fullW = super.getRowWidth();
            for (WorldSelectionList.Entry row : rows) {
                lads$fitEntryWidgets(row, fullW);
            }
            return;
        }
        int columnWidth = ServerListColumns.width(columns, width), left = getRowLeft(), top = getY() + 2 - (int) scrollAmount();
        for (int i = 0; i < rows.size(); i++) {
            int slot = ServerListDrag.slot(i, lads$from, lads$to);
            WorldSelectionList.Entry row = rows.get(i);
            row.setX(ServerListColumns.x(slot, columns, left, columnWidth));
            row.setY(ServerListColumns.y(slot, columns, top, defaultEntryHeight));
            row.setWidth(columnWidth);
            lads$fitEntryWidgets(row, columnWidth);
        }
    }

    @Override
    public void replaceEntries(Collection<WorldSelectionList.Entry> entries) {
        super.replaceEntries(entries);
        lads$layout();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        WorldSelectionList.Entry at = getEntryAtPosition(event.x(), event.y());
        lads$pressed = event.button() == InputConstants.MOUSE_BUTTON_LEFT
            && at instanceof WorldSelectionList.WorldListEntry && WorldListOrder.canDrag(filter);
        lads$pressX = event.x();
        lads$pressY = event.y();
        if (at != null) {
            lads$grabX = event.x() - at.getX();
            lads$grabY = event.y() - at.getY();
        }
        lads$from = lads$to = -1;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (lads$pressed && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (!WorldListOrder.canDrag(filter)) {
                lads$pressed = false;
                lads$from = lads$to = -1;
            } else {
                if (lads$from < 0 && ServerListDrag.dragging(lads$pressX, lads$pressY, event.x(), event.y())) {
                    WorldSelectionList.Entry entry = getEntryAtPosition(lads$pressX, lads$pressY);
                    int at = entry instanceof WorldSelectionList.WorldListEntry ? children().indexOf(entry) : -1;
                    lads$from = lads$to = at;
                    lads$pressed = lads$from >= 0;
                }
                if (lads$from >= 0) {
                    lads$mouseX = event.x();
                    lads$mouseY = event.y();
                    return true;
                }
            }
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        lads$pressed = false;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && lads$from >= 0) {
            lads$drop();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Unique
    private void lads$dragFrame() {
        if (lads$mouseY < getY() + ServerListDrag.SCROLL_EDGE) setScrollAmount(scrollAmount() - ServerListDrag.SCROLL_STEP);
        else if (lads$mouseY > getBottom() - ServerListDrag.SCROLL_EDGE) setScrollAmount(scrollAmount() + ServerListDrag.SCROLL_STEP);
        int columns = lads$columns();
        int slot = ServerListColumns.slotAt(lads$mouseX, lads$mouseY, getRowLeft(), getY() + 2 - (int) scrollAmount(),
            ServerListColumns.width(columns, width), defaultEntryHeight, columns, children().size());
        lads$to = ServerListDrag.target(slot, lads$to, children().size());
        lads$layout();
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void lads$beforeRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (lads$from >= 0) lads$dragFrame();
    }

    @ModifyVariable(method = "renderWidget", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int lads$dragMouseX(int mouseX) {
        return lads$from < 0 ? mouseX : -1;
    }

    @ModifyVariable(method = "renderWidget", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int lads$dragMouseY(int mouseY) {
        return lads$from < 0 ? mouseY : -1;
    }

    @Inject(method = "renderWidget", at = @At("RETURN"))
    private void lads$afterRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (lads$from < 0 || lads$from >= children().size()) return;
        WorldSelectionList.Entry row = children().get(lads$from);
        enableScissor(graphics);
        graphics.fill(row.getX(), row.getY(), row.getX() + row.getWidth(), row.getY() + row.getHeight(), ServerListDrag.GAP_FILL);
        graphics.disableScissor();
        int x = (int) (lads$mouseX - lads$grabX), y = (int) (lads$mouseY - lads$grabY);
        graphics.fill(x, y, x + row.getWidth(), y + row.getHeight(), ServerListDrag.GHOST_BACK);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x - row.getX(), y - row.getY());
        ServerListDrag.ghost(true);
        try {
            row.renderContent(graphics, -1, -1, false, partialTick);
        } finally {
            ServerListDrag.ghost(false);
            graphics.pose().popMatrix();
        }
    }

    @Unique
    private void lads$drop() {
        int from = lads$from, to = lads$to;
        lads$from = lads$to = -1;
        if (!WorldListOrder.canDrag(filter)) {
            lads$layout();
            return;
        }
        List<LevelSummary> summaries = new ArrayList<>();
        for (WorldSelectionList.Entry entry : children()) {
            if (entry instanceof WorldSelectionList.WorldListEntry worldEntry) {
                summaries.add(worldEntry.getLevelSummary());
            }
        }
        if (to < 0 || to == from || from >= summaries.size() || to >= summaries.size()) {
            lads$layout();
            return;
        }
        ServerListDrag.move(from, to, summaries::get, summaries::set);
        List<String> ids = new ArrayList<>(summaries.size());
        for (LevelSummary s : summaries) ids.add(s.getLevelId());
        WorldListOrder.saveOrder(ids);
        currentlyDisplayedLevels = summaries;
        pendingLevels = CompletableFuture.completedFuture(summaries);
        fillLevels(filter, summaries);
        if (to < children().size()) {
            setSelected(children().get(to));
        }
    }
}
