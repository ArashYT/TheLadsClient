package com.thelads.core.v1_8_9.utility.mixin;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.gui.ListContextMenu;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draggable Server List on 1.8.9: press a saved server and move past the threshold; the real row follows the mouse as a ghost, the others
 * open a gap (ServerListColumnsMixin189), it drops there on release and servers.dat is saved once. While a drag runs the list's own
 * drag-to-scroll is held off (the wheel still scrolls), and a release outside the screen's events is seen from the button state on the next frame.
 */
@Mixin(GuiMultiplayer.class)
abstract class ServerListDragMixin189 extends GuiScreen {
    @Shadow private ServerSelectionList serverListSelector;
    @Shadow private ServerList savedServerList;
    @Shadow public abstract void selectServer(int index);
    @Unique private int lads$pressX, lads$pressY, lads$grabX, lads$grabY, lads$from = -1, lads$to = -1;
    @Unique private boolean lads$pressed;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void lads$press(int x, int y, int button, CallbackInfo ci) {
        int slot = serverListSelector.isMouseYWithinSlotBounds(y) ? serverListSelector.getSlotIndexFromScreenCoords(x, y) : -1;
        lads$pressed = button == 0 && slot >= 0 && slot < savedServerList.countServers() && !ListContextMenu.anyOpen()
            && !LadsNetwork.isPinned(savedServerList.getServerData(slot).serverIP) && ServerListDrag.enabled();
        lads$pressX = x;
        lads$pressY = y;
        if (slot >= 0) {
            ServerListColumns.Rows rows = (ServerListColumns.Rows) serverListSelector;
            lads$grabX = x - rows.lads$rowLeft(slot);
            lads$grabY = y - rows.lads$rowTop(slot);
        }
        lads$setFrom(-1);
    }

    @Unique
    private void lads$setFrom(int from) {
        lads$from = lads$to = from;
        ((ServerListColumns.Gap) serverListSelector).lads$gap(from, from);
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
        if (!ServerListDrag.heldForQa && !Mouse.isButtonDown(0)) {
            lads$drop();
            return;
        }
        int count = savedServerList.countServers();
        ServerSelectionList list = serverListSelector;
        if (lads$from < 0 && ServerListDrag.dragging(lads$pressX, lads$pressY, mouseX, mouseY)) {
            // The row under the press now (vanilla's move arrows may have swapped it): only a saved server drags.
            int at = list.getSlotIndexFromScreenCoords(lads$pressX, lads$pressY);
            lads$setFrom(at < count ? at : -1);
            lads$pressed = lads$from >= 0;
        }
        if (lads$from < 0) return;
        if (mouseY < list.top + ServerListDrag.SCROLL_EDGE) list.scrollBy(-ServerListDrag.SCROLL_STEP);
        else if (mouseY > list.bottom - ServerListDrag.SCROLL_EDGE) list.scrollBy(ServerListDrag.SCROLL_STEP);
        ServerListColumns.Rows rows = (ServerListColumns.Rows) list;
        // The gap's row reads the slot it shows in, so the hit test is by slot: unmoved rows' slots are their indices.
        ((ServerListColumns.Gap) list).lads$gap(-1, -1);
        int slot = list.getSlotIndexFromScreenCoords(mouseX, mouseY);
        lads$to = ServerListDrag.target(slot, lads$to, count, ServerListDrag.firstMovable(savedServerList.getServerData(0).serverIP));
        ((ServerListColumns.Gap) list).lads$gap(lads$from, lads$to);
        int width = list.getListWidth(), height = list.slotHeight;
        int gapLeft = rows.lads$rowLeft(lads$from) - 2, gapTop = rows.lads$rowTop(lads$from);
        GlStateManager.disableAlpha(); // the alpha test would drop so faint a fill
        if (gapTop + height > list.top && gapTop < list.bottom) Gui.drawRect(gapLeft, Math.max(gapTop - 2, list.top), gapLeft + width, Math.min(gapTop - 2 + height, list.bottom), ServerListDrag.GAP_FILL);
        GlStateManager.enableAlpha();
        int x = mouseX - lads$grabX, y = mouseY - lads$grabY;
        Gui.drawRect(x - 2, y - 2, x - 2 + width, y - 2 + height, ServerListDrag.GHOST_BACK);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        ServerListDrag.ghost(true);
        list.getListEntry(lads$from).drawEntry(lads$from, x, y, width, height - 4, -1000, -1000, false);
        ServerListDrag.ghost(false);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.disableBlend();
    }

    @Unique
    private void lads$drop() {
        int from = lads$from, to = lads$to;
        lads$setFrom(-1);
        lads$pressed = false;
        if (to < 0 || to == from || from >= savedServerList.countServers() || to >= savedServerList.countServers()) return;
        ServerList servers = savedServerList;
        ServerListDrag.<ServerData>move(from, to, servers::getServerData, servers::func_147413_a);
        servers.saveServerList();
        serverListSelector.func_148195_a(servers);
        selectServer(to);
    }
}
