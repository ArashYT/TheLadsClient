package com.thelads.core.v1_20_1.utility.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.WorldListOrder;
import com.thelads.core.v1_20_1.mixin.menus.WorldListEntryAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldSelectionList.class)
abstract class WorldListDragMixin1201 extends ObjectSelectionList<WorldSelectionList.Entry> implements ServerListColumns.Rows, ServerListColumns.Hits {
    @Shadow private String filter;
    @Shadow private List<LevelSummary> currentlyDisplayedLevels;
    @Shadow private CompletableFuture<List<LevelSummary>> pendingLevels;
    @Shadow private void fillLevels(String filter, List<LevelSummary> levels) {}

    @Unique private double lads$pressX, lads$pressY, lads$grabX, lads$grabY, lads$mouseX, lads$mouseY;
    @Unique private boolean lads$pressed;
    @Unique private int lads$from = -1, lads$to = -1, lads$drawnX;

    private WorldListDragMixin1201(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
    }

    @ModifyVariable(method = "fillLevels", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<LevelSummary> lads$sortWorlds(List<LevelSummary> levels) {
        if (levels == null) return null;
        return new ArrayList<>(WorldListOrder.sortedCopy(levels, LevelSummary::getLevelId, LevelSummary::getLastPlayed));
    }

    @Override
    public int lads$columns() {
        return ServerListColumns.fit(ServerListColumns.wanted(), width);
    }

    @Inject(method = "getRowWidth", at = @At("HEAD"), cancellable = true)
    private void lads$columnsWidth(CallbackInfoReturnable<Integer> cir) {
        int columns = lads$columns();
        if (columns > 1) cir.setReturnValue(ServerListColumns.total(columns, width));
    }

    @Inject(method = "getScrollbarPosition", at = @At("HEAD"), cancellable = true)
    private void lads$columnsScrollbar(CallbackInfoReturnable<Integer> cir) {
        int columns = lads$columns();
        if (columns > 1) cir.setReturnValue(width / 2 + ServerListColumns.total(columns, width) / 2 + 2);
    }

    @Override
    protected int getMaxPosition() {
        int columns = lads$columns();
        return columns == 1 ? super.getMaxPosition() : ServerListColumns.rows(getItemCount(), columns) * itemHeight + headerHeight;
    }

    @Override
    protected int getRowTop(int row) {
        int columns = lads$columns();
        return ServerListColumns.y(ServerListDrag.slot(row, lads$from, lads$to), columns, y0 + 4 - (int) getScrollAmount() + headerHeight, itemHeight);
    }

    @Unique
    private int lads$boxLeft(int row) {
        int columns = lads$columns();
        return ServerListColumns.x(ServerListDrag.slot(row, lads$from, lads$to), columns, getRowLeft() - 2, columns == 1 ? getRowWidth() : ServerListColumns.width(columns, width));
    }

    @Override
    public int lads$rowLeft(int row) {
        return lads$boxLeft(row) + 2;
    }

    @Override
    public int lads$rowTop(int row) {
        return getRowTop(row);
    }

    @Override
    public Object lads$rowAt(double x, double y) {
        int columns = lads$columns(), slot = ServerListColumns.slotAt(x, y, getRowLeft() - 2, y0 + 4 - (int) getScrollAmount() + headerHeight,
            ServerListColumns.width(columns, width), itemHeight, columns, getItemCount());
        return slot < 0 ? null : children().get(slot);
    }

    @Override
    protected void renderList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int columns = lads$columns();
        if (columns == 1 && lads$from < 0) {
            super.renderList(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int rowWidth = columns == 1 ? getRowWidth() : ServerListColumns.width(columns, width), height = itemHeight - 4;
        for (int i = 0; i < getItemCount(); i++) {
            int top = getRowTop(i);
            if (i == lads$from || top + itemHeight < y0 || top > y1) continue;
            lads$drawnX = lads$boxLeft(i);
            if (columns > 1) {
                graphics.enableScissor(lads$drawnX, Math.max(y0, top - 2), lads$drawnX + rowWidth, Math.min(y1, top + height + 2));
            }
            renderItem(graphics, mouseX, mouseY, partialTick, i, lads$drawnX + 2, top, rowWidth, height);
            if (columns > 1) {
                graphics.disableScissor();
            }
        }
    }

    @Override
    protected void renderSelection(GuiGraphics graphics, int top, int rowWidth, int height, int outer, int inner) {
        if (lads$columns() == 1) {
            super.renderSelection(graphics, top, rowWidth, height, outer, inner);
            return;
        }
        graphics.fill(lads$drawnX, top - 2, lads$drawnX + rowWidth, top + height + 2, outer);
        graphics.fill(lads$drawnX + 1, top - 1, lads$drawnX + rowWidth - 1, top + height + 1, inner);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        WorldSelectionList.Entry at = getEntryAtPosition(x, y);
        lads$pressed = button == 0 && at instanceof WorldSelectionList.WorldListEntry && WorldListOrder.canDrag(filter);
        lads$pressX = x;
        lads$pressY = y;
        if (at != null) {
            int row = children().indexOf(at);
            lads$grabX = x - lads$boxLeft(row);
            lads$grabY = y - getRowTop(row);
        }
        lads$from = lads$to = -1;
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (lads$pressed && button == 0) {
            if (!WorldListOrder.canDrag(filter)) {
                lads$pressed = false;
                lads$from = lads$to = -1;
            } else {
                if (lads$from < 0 && ServerListDrag.dragging(lads$pressX, lads$pressY, x, y)) {
                    WorldSelectionList.Entry entry = getEntryAtPosition(lads$pressX, lads$pressY);
                    int at = entry instanceof WorldSelectionList.WorldListEntry ? children().indexOf(entry) : -1;
                    lads$from = lads$to = at;
                    lads$pressed = lads$from >= 0;
                }
                if (lads$from >= 0) {
                    lads$mouseX = x;
                    lads$mouseY = y;
                    return true;
                }
            }
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        lads$pressed = false;
        if (button == 0 && lads$from >= 0) {
            lads$drop();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Unique
    private void lads$dragFrame() {
        if (lads$mouseY < y0 + ServerListDrag.SCROLL_EDGE) setScrollAmount(getScrollAmount() - ServerListDrag.SCROLL_STEP);
        else if (lads$mouseY > y1 - ServerListDrag.SCROLL_EDGE) setScrollAmount(getScrollAmount() + ServerListDrag.SCROLL_STEP);
        int columns = lads$columns();
        int slot = ServerListColumns.slotAt(lads$mouseX, lads$mouseY, getRowLeft() - 2, y0 + 4 - (int) getScrollAmount() + headerHeight,
            columns == 1 ? getRowWidth() : ServerListColumns.width(columns, width), itemHeight, columns, getItemCount());
        lads$to = ServerListDrag.target(slot, lads$to, getItemCount());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (lads$from >= 0 && !ServerListDrag.heldForQa && GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) lads$drop();
        if (lads$from >= 0) lads$dragFrame();
        super.render(graphics, lads$from < 0 ? mouseX : -1, lads$from < 0 ? mouseY : -1, partialTick);
        if (lads$from < 0 || lads$from >= children().size()) return;
        int columns = lads$columns(), rowWidth = columns == 1 ? getRowWidth() : ServerListColumns.width(columns, width);
        int gapX = lads$boxLeft(lads$from), gapY = getRowTop(lads$from);
        enableScissor(graphics);
        graphics.fill(gapX, gapY - 2, gapX + rowWidth, gapY - 2 + itemHeight, ServerListDrag.GAP_FILL);
        graphics.disableScissor();
        int x = (int) (lads$mouseX - lads$grabX), y = (int) (lads$mouseY - lads$grabY);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x, y - 2, x + rowWidth, y - 2 + itemHeight, ServerListDrag.GHOST_BACK);
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, ServerListDrag.GHOST_ALPHA / 255f);
        if (columns > 1) {
            graphics.enableScissor(x, y - 2, x + rowWidth, y - 2 + itemHeight);
        }
        children().get(lads$from).render(graphics, lads$from, y, x + 2, rowWidth, itemHeight - 4, -1, -1, false, partialTick);
        if (columns > 1) {
            graphics.disableScissor();
        }
        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
        graphics.pose().popPose();
    }

    @Unique
    private void lads$drop() {
        int from = lads$from, to = lads$to;
        lads$from = lads$to = -1;
        lads$pressed = false;
        if (!WorldListOrder.canDrag(filter)) return;
        List<LevelSummary> summaries = new ArrayList<>();
        for (WorldSelectionList.Entry entry : children()) {
            if (entry instanceof WorldSelectionList.WorldListEntry worldEntry) {
                summaries.add(((WorldListEntryAccessor) (Object) worldEntry).ladsSummary());
            }
        }
        if (to < 0 || to == from || from >= summaries.size() || to >= summaries.size()) return;
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
