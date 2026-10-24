package com.thelads.core.v26_2.utility.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ServerListDrag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Draggable Server List: press a saved server and move past the threshold; it drops on release where the white line shows, and
 * servers.dat is saved once. 26.3 sends the release to the list it was dragging in wherever the mouse is.
 */
@Mixin(ServerSelectionList.class)
abstract class ServerListDragMixin26 extends ObjectSelectionList<ServerSelectionList.Entry> {
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Unique private double lads$pressX, lads$pressY;
    @Unique private boolean lads$pressed;
    @Unique private int lads$from = -1, lads$to = -1;

    private ServerListDragMixin26(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        lads$pressed = event.button() == InputConstants.MOUSE_BUTTON_LEFT
            && getEntryAtPosition(event.x(), event.y()) instanceof ServerSelectionList.OnlineServerEntry && ServerListDrag.enabled();
        lads$pressX = event.x();
        lads$pressY = event.y();
        lads$from = lads$to = -1;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (lads$pressed && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int count = screen.getServers().size();
            if (lads$from < 0 && ServerListDrag.dragging(lads$pressY, event.y())) {
                // The row under the press now (vanilla's move arrows may have swapped it): only a saved server drags.
                int at = children().indexOf(getEntryAtPosition(lads$pressX, lads$pressY));
                lads$from = lads$to = at < count ? at : -1;
                lads$pressed = lads$from >= 0;
            }
            if (lads$from >= 0) {
                lads$to = ServerListDrag.target(children().indexOf(getEntryAtPosition(lads$pressX, event.y())), lads$to, count);
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

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        if (lads$from < 0) return;
        int line = lads$to < lads$from ? getRowTop(lads$to) : getRowBottom(lads$to) - 1;
        if (line >= getY() && line < getBottom()) graphics.fill(getRowLeft() - 2, line, getRowRight() + 2, line + 1, 0xFFFFFFFF);
    }

    @Unique
    private void lads$drop() {
        int from = lads$from, to = lads$to;
        lads$from = lads$to = -1;
        ServerList servers = screen.getServers();
        if (to < 0 || to == from || from >= servers.size() || to >= servers.size()) return;
        ServerListDrag.move(from, to, servers::get, servers::replace);
        servers.save();
        ((ServerSelectionList) (Object) this).updateOnlineServers(servers);
        setSelected(children().get(to));
    }
}
