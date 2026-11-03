package com.thelads.core.v1_21_11.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draggable Server List and its columns. Rows know their own place here, so columns and the drag's gap only set each row's slot
 * (ServerListColumns, ServerListDrag); hit tests, hover and scrolling then follow. Press a saved server and move past the threshold: the
 * real row follows the mouse as a ghost, the others open a gap, it drops there on release and servers.dat is saved once. 1.21.11 sends the
 * release to the list it was dragging in wherever the mouse is.
 */
@Mixin(ServerSelectionList.class)
abstract class ServerListDragMixin12111 extends ObjectSelectionList<ServerSelectionList.Entry> implements ServerListColumns.Layout {
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Unique private double lads$pressX, lads$pressY, lads$grabX, lads$grabY, lads$mouseX, lads$mouseY;
    @Unique private boolean lads$pressed;
    @Unique private int lads$from = -1, lads$to = -1;

    private ServerListDragMixin12111(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    @Override
    public int lads$columns() {
        return ServerListColumns.fit(ServerListColumns.wanted(), width);
    }

    /** The dragged row is drawn as the ghost, not in the gap it left. */
    @Override
    public boolean lads$hidden(Object row) {
        return lads$from >= 0 && children().indexOf(row) == lads$from;
    }

    /** With columns the "row" vanilla centres and hangs the scrollbar on is all of them. */
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

    /** Runs after vanilla positioned the rows (ServerListLayoutMixin); one column with no drag stays vanilla's. */
    @Override
    public void lads$layout() {
        int columns = lads$columns();
        if (columns == 1 && lads$from < 0) return;
        int columnWidth = ServerListColumns.width(columns, width), left = getRowLeft(), top = getY() + 2 - (int) scrollAmount();
        List<ServerSelectionList.Entry> rows = children();
        for (int i = 0; i < rows.size(); i++) {
            int slot = ServerListDrag.slot(i, lads$from, lads$to);
            ServerSelectionList.Entry row = rows.get(i);
            row.setX(ServerListColumns.x(slot, columns, left, columnWidth));
            row.setY(ServerListColumns.y(slot, columns, top, defaultEntryHeight));
            row.setWidth(columnWidth);
        }
    }

    @Override
    public void replaceEntries(Collection<ServerSelectionList.Entry> entries) {
        super.replaceEntries(entries);
        lads$layout();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ServerSelectionList.Entry at = getEntryAtPosition(event.x(), event.y());
        lads$pressed = event.button() == InputConstants.MOUSE_BUTTON_LEFT
            && at instanceof ServerSelectionList.OnlineServerEntry online && !LadsNetwork.isPinned(online.getServerData().ip) && ServerListDrag.enabled();
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
            int count = screen.getServers().size();
            if (lads$from < 0 && ServerListDrag.dragging(lads$pressX, lads$pressY, event.x(), event.y())) {
                // The row under the press now (vanilla's move arrows may have swapped it): only a saved server drags.
                int at = children().indexOf(getEntryAtPosition(lads$pressX, lads$pressY));
                lads$from = lads$to = at < count ? at : -1;
                lads$pressed = lads$from >= 0;
            }
            if (lads$from >= 0) {
                lads$mouseX = event.x();
                lads$mouseY = event.y();
                return true;
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

    /** The drag's frame: scroll at the list's edges, where the row would land now, and the gap there. */
    @Unique
    private void lads$dragFrame() {
        if (lads$mouseY < getY() + ServerListDrag.SCROLL_EDGE) setScrollAmount(scrollAmount() - ServerListDrag.SCROLL_STEP);
        else if (lads$mouseY > getBottom() - ServerListDrag.SCROLL_EDGE) setScrollAmount(scrollAmount() + ServerListDrag.SCROLL_STEP);
        int columns = lads$columns();
        ServerList servers = screen.getServers();
        int slot = ServerListColumns.slotAt(lads$mouseX, lads$mouseY, getRowLeft(), getY() + 2 - (int) scrollAmount(),
            ServerListColumns.width(columns, width), defaultEntryHeight, columns, children().size());
        lads$to = ServerListDrag.target(slot, lads$to, servers.size(), ServerListDrag.firstMovable(servers.get(0).ip));
        lads$layout();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (lads$from >= 0) lads$dragFrame();
        super.renderWidget(graphics, lads$from < 0 ? mouseX : -1, lads$from < 0 ? mouseY : -1, partialTick);
        if (lads$from < 0) return;
        ServerSelectionList.Entry row = children().get(lads$from);
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
        ServerList servers = screen.getServers();
        if (to < 0 || to == from || from >= servers.size() || to >= servers.size()) {
            lads$layout();
            return;
        }
        ServerListDrag.move(from, to, servers::get, servers::replace);
        servers.save();
        ((ServerSelectionList) (Object) this).updateOnlineServers(servers);
        setSelected(children().get(to));
    }
}
