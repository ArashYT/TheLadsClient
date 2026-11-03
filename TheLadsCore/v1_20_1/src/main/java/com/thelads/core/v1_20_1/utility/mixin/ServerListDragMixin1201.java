package com.thelads.core.v1_20_1.utility.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerList;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draggable Server List and its columns. On 1.20.1 a row has no place of its own: the list computes one from its index, so here
 * getRowTop, the row's x and the hit test go through each row's slot (ServerListColumns, ServerListDrag). Press a saved server and move
 * past the threshold: the real row follows the mouse as a ghost, the others open a gap, it drops there on release and servers.dat is saved
 * once. 1.20.1 sends a release only to the widget under the mouse, so a release elsewhere is seen from the button state on the next frame.
 */
@Mixin(ServerSelectionList.class)
abstract class ServerListDragMixin1201 extends ObjectSelectionList<ServerSelectionList.Entry> implements ServerListColumns.Rows, ServerListColumns.Hits {
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Unique private double lads$pressX, lads$pressY, lads$grabX, lads$grabY, lads$mouseX, lads$mouseY;
    @Unique private boolean lads$pressed;
    @Unique private int lads$from = -1, lads$to = -1, lads$drawnX;

    private ServerListDragMixin1201(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
    }

    @Override
    public int lads$columns() {
        return ServerListColumns.fit(ServerListColumns.wanted(), width);
    }

    /** With columns the "row" vanilla centres is all of them. */
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

    /** A row's left edge: its selection box starts 2 left of what vanilla hands the row (getRowLeft). */
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

    /** Vanilla's loop, with each row's own x; the dragged row is drawn as the ghost, not in the gap it left. */
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
            renderItem(graphics, mouseX, mouseY, partialTick, i, lads$drawnX + 2, top, rowWidth, height);
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
        ServerSelectionList.Entry at = getEntryAtPosition(x, y);
        lads$pressed = button == 0 && at instanceof ServerSelectionList.OnlineServerEntry online && !LadsNetwork.isPinned(online.getServerData().ip) && ServerListDrag.enabled();
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
            int count = screen.getServers().size();
            if (lads$from < 0 && ServerListDrag.dragging(lads$pressX, lads$pressY, x, y)) {
                // The row under the press now (vanilla's move arrows may have swapped it): only a saved server drags.
                int at = children().indexOf(getEntryAtPosition(lads$pressX, lads$pressY));
                lads$from = lads$to = at < count ? at : -1;
                lads$pressed = lads$from >= 0;
            }
            if (lads$from >= 0) {
                lads$mouseX = x;
                lads$mouseY = y;
                return true;
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

    /** The drag's frame: scroll at the list's edges, and where the row would land now. */
    @Unique
    private void lads$dragFrame() {
        if (lads$mouseY < y0 + ServerListDrag.SCROLL_EDGE) setScrollAmount(getScrollAmount() - ServerListDrag.SCROLL_STEP);
        else if (lads$mouseY > y1 - ServerListDrag.SCROLL_EDGE) setScrollAmount(getScrollAmount() + ServerListDrag.SCROLL_STEP);
        int columns = lads$columns();
        ServerList servers = screen.getServers();
        int slot = ServerListColumns.slotAt(lads$mouseX, lads$mouseY, getRowLeft() - 2, y0 + 4 - (int) getScrollAmount() + headerHeight,
            columns == 1 ? getRowWidth() : ServerListColumns.width(columns, width), itemHeight, columns, getItemCount());
        lads$to = ServerListDrag.target(slot, lads$to, servers.size(), ServerListDrag.firstMovable(servers.get(0).ip));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (lads$from >= 0 && !ServerListDrag.heldForQa && GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) lads$drop();
        if (lads$from >= 0) lads$dragFrame();
        super.render(graphics, lads$from < 0 ? mouseX : -1, lads$from < 0 ? mouseY : -1, partialTick);
        if (lads$from < 0) return;
        int columns = lads$columns(), rowWidth = columns == 1 ? getRowWidth() : ServerListColumns.width(columns, width);
        int gapX = lads$boxLeft(lads$from), gapY = getRowTop(lads$from);
        enableScissor(graphics);
        graphics.fill(gapX, gapY - 2, gapX + rowWidth, gapY - 2 + itemHeight, ServerListDrag.GAP_FILL);
        graphics.disableScissor();
        int x = (int) (lads$mouseX - lads$grabX), y = (int) (lads$mouseY - lads$grabY);
        // This GUI is depth-tested: in front of the other rows' text, then the shader colour makes everything the row draws translucent.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x, y - 2, x + rowWidth, y - 2 + itemHeight, ServerListDrag.GHOST_BACK);
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, ServerListDrag.GHOST_ALPHA / 255f);
        children().get(lads$from).render(graphics, lads$from, y, x + 2, rowWidth, itemHeight - 4, -1, -1, false, partialTick);
        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
        graphics.pose().popPose();
    }

    @Unique
    private void lads$drop() {
        int from = lads$from, to = lads$to;
        lads$from = lads$to = -1;
        lads$pressed = false;
        ServerList servers = screen.getServers();
        if (to < 0 || to == from || from >= servers.size() || to >= servers.size()) return;
        ServerListDrag.move(from, to, servers::get, servers::replace);
        servers.save();
        ((ServerSelectionList) (Object) this).updateOnlineServers(servers);
        setSelected(children().get(to));
    }
}
