package com.thelads.core.v1_20_1.utility.mixin;

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

/**
 * Draggable Server List: press a saved server and move past the threshold; it drops on release where the white line shows, and
 * servers.dat is saved once. 1.20.1 sends a release only to the widget under the mouse, so a release elsewhere is seen from the
 * button state on the next frame.
 */
@Mixin(ServerSelectionList.class)
abstract class ServerListDragMixin1201 extends ObjectSelectionList<ServerSelectionList.Entry> {
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Unique private double lads$pressX, lads$pressY;
    @Unique private boolean lads$pressed;
    @Unique private int lads$from = -1, lads$to = -1;

    private ServerListDragMixin1201(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        lads$pressed = button == 0 && getEntryAtPosition(x, y) instanceof ServerSelectionList.OnlineServerEntry && ServerListDrag.enabled();
        lads$pressX = x;
        lads$pressY = y;
        lads$from = lads$to = -1;
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (lads$pressed && button == 0) {
            int count = screen.getServers().size();
            if (lads$from < 0 && ServerListDrag.dragging(lads$pressY, y)) {
                // The row under the press now (vanilla's move arrows may have swapped it): only a saved server drags.
                int at = children().indexOf(getEntryAtPosition(lads$pressX, lads$pressY));
                lads$from = lads$to = at < count ? at : -1;
                lads$pressed = lads$from >= 0;
            }
            if (lads$from >= 0) {
                lads$to = ServerListDrag.target(children().indexOf(getEntryAtPosition(lads$pressX, y)), lads$to, count);
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (lads$from >= 0 && GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) lads$drop();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (lads$from < 0) return;
        int line = lads$to < lads$from ? getRowTop(lads$to) - 2 : getRowBottom(lads$to) - 2;
        if (line >= y0 && line < y1) graphics.fill(getRowLeft() - 2, line, getRowRight() + 2, line + 1, 0xFFFFFFFF);
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
