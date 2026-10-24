package com.thelads.core.v1_8_9.utility.mixin;

import com.thelads.core.client.ServerListDrag;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draggable Server List on 1.8.9: press a saved server and move past the threshold; it drops on release where the white line shows, and
 * servers.dat is saved once. While a drag runs the list's own drag-to-scroll is held off (the wheel still scrolls), and a release outside
 * the screen's events is seen from the button state on the next frame.
 */
@Mixin(GuiMultiplayer.class)
abstract class ServerListDragMixin189 extends GuiScreen {
    @Shadow private ServerSelectionList serverListSelector;
    @Shadow private ServerList savedServerList;
    @Shadow public abstract void selectServer(int index);
    @Unique private int lads$pressX, lads$pressY, lads$from = -1, lads$to = -1;
    @Unique private boolean lads$pressed;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void lads$press(int x, int y, int button, CallbackInfo ci) {
        int slot = serverListSelector.getSlotIndexFromScreenCoords(x, y);
        lads$pressed = button == 0 && slot >= 0 && slot < savedServerList.countServers() && ServerListDrag.enabled();
        lads$pressX = x;
        lads$pressY = y;
        lads$from = lads$to = -1;
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void lads$release(int x, int y, int button, CallbackInfo ci) {
        if (button == 0) lads$drop();
    }

    /** The list scrolls when dragged: not while a server is dragged, apart from the wheel. */
    @Redirect(method = "handleMouseInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ServerSelectionList;handleMouseInput()V"))
    private void lads$listInput(ServerSelectionList list) {
        if (lads$from < 0) {
            list.handleMouseInput();
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) list.scrollBy(wheel > 0 ? -list.slotHeight / 2 : list.slotHeight / 2);
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void lads$drag(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!lads$pressed) return;
        if (!Mouse.isButtonDown(0)) {
            lads$drop();
            return;
        }
        int count = savedServerList.countServers();
        if (lads$from < 0 && ServerListDrag.dragging(lads$pressY, mouseY)) {
            // The row under the press now (vanilla's move arrows may have swapped it): only a saved server drags.
            int at = serverListSelector.getSlotIndexFromScreenCoords(lads$pressX, lads$pressY);
            lads$from = lads$to = at < count ? at : -1;
            lads$pressed = lads$from >= 0;
        }
        if (lads$from < 0) return;
        lads$to = ServerListDrag.target(serverListSelector.getSlotIndexFromScreenCoords(lads$pressX, mouseY), lads$to, count);
        ServerSelectionList list = serverListSelector;
        int rowTop = list.top + 4 - list.getAmountScrolled() + list.headerPadding + lads$to * list.slotHeight;
        int line = lads$to < lads$from ? rowTop - 2 : rowTop + list.slotHeight - 2;
        int left = list.left + list.width / 2 - list.getListWidth() / 2;
        if (line >= list.top && line < list.bottom) Gui.drawRect(left - 2, line, left + list.getListWidth() + 2, line + 1, 0xFFFFFFFF);
    }

    @Unique
    private void lads$drop() {
        int from = lads$from, to = lads$to;
        lads$from = lads$to = -1;
        lads$pressed = false;
        if (to < 0 || to == from || from >= savedServerList.countServers() || to >= savedServerList.countServers()) return;
        ServerList servers = savedServerList;
        ServerListDrag.<ServerData>move(from, to, servers::getServerData, servers::func_147413_a);
        servers.saveServerList();
        serverListSelector.func_148195_a(servers);
        selectServer(to);
    }
}
