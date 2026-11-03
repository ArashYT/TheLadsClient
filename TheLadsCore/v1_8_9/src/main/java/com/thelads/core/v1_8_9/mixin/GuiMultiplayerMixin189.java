package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import com.thelads.core.client.gui.ListContextMenu;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.gui.ServerDiscoveryScreen189;
import java.io.IOException;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ServerListEntryLanDetected;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Multiplayer: Discover Servers, the columns control, the pinned network's Edit and Delete and moves off, arrow keys in columns, and the
 * right-click menu of a server row, whose items press vanilla's own buttons. No ViaFabricPlus here: a mismatched server just shows red.
 */
@Mixin(GuiMultiplayer.class)
public abstract class GuiMultiplayerMixin189 extends GuiScreen {
    @Shadow private ServerSelectionList serverListSelector;
    @Shadow private ServerList savedServerList;
    @Shadow private GuiButton btnEditServer;
    @Shadow private GuiButton btnDeleteServer;
    @Shadow public abstract void selectServer(int index);
    @Shadow public abstract void connectToSelected();
    @Shadow private void refreshServerList() {}
    @Unique private GuiButton ladsDiscoverButton, ladsColumnsButton;
    @Unique private final ListContextMenu ladsMenu = new ListContextMenu();

    @Inject(method = "initGui", at = @At("TAIL"))
    private void ladsAddDiscoverButton(CallbackInfo ci) {
        ladsPurgeEssential();
        ladsMenu.close();
        ladsDiscoverButton = new GuiButton(0x4C414453 + 20, width - 124, 8, 116, 20, "Discover Servers");
        buttonList.add(ladsDiscoverButton);
        ladsColumnsButton = new GuiButton(0x4C414453 + 21, 8, 8, 36, 20, "");
        buttonList.add(ladsColumnsButton);
        ladsUpdateColumns();
    }

    /** Hidden when only one column fits; its icon is drawn over it in drawScreen. */
    @Unique
    private void ladsUpdateColumns() {
        ladsColumnsButton.visible = ServerListColumns.most(width) > 1;
        ladsColumnsButton.displayString = "    " + ServerListColumns.fit(ServerListColumns.wanted(), width);
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void ladsSuppressEssential(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ladsPurgeEssential();
    }

    /** Nothing under the open menu takes hover, and QA draws its own mouse. */
    @ModifyVariable(method = "drawScreen", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ladsMouseX(int mouseX) {
        return ladsMenu.isOpen() ? -1 : ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseX : mouseX;
    }

    @ModifyVariable(method = "drawScreen", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int ladsMouseY(int mouseY) {
        return ladsMenu.isOpen() ? -1 : ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseY : mouseY;
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void ladsDrawOver(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiLadsAdapter adapter = new GuiLadsAdapter(fontRendererObj, width, height);
        if (ladsColumnsButton != null && ladsColumnsButton.visible) {
            TitleScreenTheme.icon(adapter, "columns", ladsColumnsButton.xPosition + 6, ladsColumnsButton.yPosition + 5, 10, 0xFFFFFFFF);
        }
        // The handlers above hid the mouse from the rows; the menu reads the real one.
        int x = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseX : org.lwjgl.input.Mouse.getX() * width / mc.displayWidth;
        int y = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseY : height - org.lwjgl.input.Mouse.getY() * height / mc.displayHeight - 1;
        ladsMenu.render(adapter, x, y);
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void ladsDiscoverAction(GuiButton button, CallbackInfo ci) {
        if (button == ladsDiscoverButton) {
            mc.displayGuiScreen(new ServerDiscoveryScreen189(this));
            ci.cancel();
        } else if (button != null && button == ladsColumnsButton) {
            ServerListColumns.cycle(width);
            ladsUpdateColumns();
            ci.cancel();
        }
    }

    @Unique
    private void ladsPurgeEssential() {
        for (GuiButton btn : new ArrayList<>(buttonList)) {
            if (btn != ladsDiscoverButton && btn.getClass().getName().startsWith("gg.essential.")) {
                btn.visible = false;
                btn.enabled = false;
                buttonList.remove(btn);
            }
        }
    }

    @Unique
    private int ladsFirst() {
        return savedServerList.countServers() == 0 ? 0 : ServerListDrag.firstMovable(savedServerList.getServerData(0).serverIP);
    }

    /** The pinned network can't be edited or deleted from here. */
    @Inject(method = "selectServer", at = @At("TAIL"))
    private void ladsPinnedStays(int index, CallbackInfo ci) {
        if (index >= 0 && index < savedServerList.countServers() && LadsNetwork.isPinned(savedServerList.getServerData(index).serverIP)) {
            btnEditServer.enabled = false;
            btnDeleteServer.enabled = false;
        }
    }

    /** Whether the row's up arrow shows and works. */
    @Inject(method = "func_175392_a", at = @At("HEAD"), cancellable = true)
    private void ladsCanMoveUp(ServerListEntryNormal entry, int row, CallbackInfoReturnable<Boolean> cir) {
        if (!ServerListDrag.canSwap(row, row - 1, ladsFirst())) cir.setReturnValue(false);
    }

    /** Whether the row's down arrow shows and works. */
    @Inject(method = "func_175394_b", at = @At("HEAD"), cancellable = true)
    private void ladsCanMoveDown(ServerListEntryNormal entry, int row, CallbackInfoReturnable<Boolean> cir) {
        if (!ServerListDrag.canSwap(row, row + 1, ladsFirst())) cir.setReturnValue(false);
    }

    /** Shift+up arrow moves to the top: under the pinned row, not over it. */
    @Inject(method = "func_175391_a", at = @At("HEAD"), cancellable = true)
    private void ladsMoveToTop(ServerListEntryNormal entry, int row, boolean toTop, CallbackInfo ci) {
        if (!toTop || ladsFirst() == 0) return;
        savedServerList.swapServers(row, 1);
        if (serverListSelector.func_148193_k() == row) selectServer(1);
        serverListSelector.func_148195_a(savedServerList);
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ladsMenuClick(int x, int y, int button, CallbackInfo ci) {
        if (ladsMenu.mouseClicked(x, y) || button == 1 && ladsOpenMenu(x, y)) ci.cancel();
    }

    /** Right click selects the row and opens its menu. False off the rows (the LAN header has none). */
    @Unique
    private boolean ladsOpenMenu(int x, int y) {
        if (!serverListSelector.isMouseYWithinSlotBounds(y)) return false;
        int row = serverListSelector.getSlotIndexFromScreenCoords(x, y);
        if (row < 0) return false;
        GuiListExtended.IGuiListEntry entry = serverListSelector.getListEntry(row);
        if (entry instanceof ServerListEntryNormal) {
            selectServer(row);
            ServerData data = ((ServerListEntryNormal) entry).getServerData();
            ladsMenu.open(x, y, ServerListEntryUi.serverMenu(LadsNetwork.isPinned(data.serverIP), false), id -> ladsPickServer(id, data));
            return true;
        }
        if (entry instanceof ServerListEntryLanDetected) {
            selectServer(row);
            ladsMenu.open(x, y, ServerListEntryUi.lanMenu(), id -> connectToSelected());
            return true;
        }
        return false;
    }

    @Unique
    private void ladsPickServer(String id, ServerData data) {
        try {
            switch (id) {
                case "join": connectToSelected(); break;
                case "edit": actionPerformed(btnEditServer); break;
                case "delete": actionPerformed(btnDeleteServer); break;
                case "copy": setClipboardString(data.serverIP); break;
                case "refresh": refreshServerList(); break;
                default: break;
            }
        } catch (IOException failure) {
            throw new RuntimeException(failure);
        }
    }

    /** The open menu takes every key (Esc closes it); Shift+arrows stop at the pinned row; plain arrows move in the grid with columns. */
    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void ladsKeys(char typed, int key, CallbackInfo ci) {
        if (ladsMenu.isOpen()) {
            if (key == Keyboard.KEY_ESCAPE) ladsMenu.close();
            ci.cancel();
            return;
        }
        int selected = serverListSelector.func_148193_k();
        if (selected < 0) return;
        if (isShiftKeyDown()) {
            if (key == Keyboard.KEY_UP && !ServerListDrag.canSwap(selected, selected - 1, ladsFirst()) || key == Keyboard.KEY_DOWN && !ServerListDrag.canSwap(selected, selected + 1, ladsFirst())) ci.cancel();
            return;
        }
        int columns = ServerListColumns.fit(ServerListColumns.wanted(), width);
        int dx = key == Keyboard.KEY_LEFT ? -1 : key == Keyboard.KEY_RIGHT ? 1 : 0, dy = key == Keyboard.KEY_UP ? -1 : key == Keyboard.KEY_DOWN ? 1 : 0;
        if (columns == 1 || dx == 0 && dy == 0) return;
        ci.cancel();
        int next = ServerListColumns.next(selected, ((ServerListColumns.Gap) serverListSelector).lads$size(), columns, dx, dy);
        if (next < 0) return;
        selectServer(next);
        int top = ((ServerListColumns.Rows) serverListSelector).lads$rowTop(next);
        if (top < serverListSelector.top + 4) serverListSelector.scrollBy(top - serverListSelector.top - 4);
        else if (top + serverListSelector.slotHeight > serverListSelector.bottom) serverListSelector.scrollBy(top + serverListSelector.slotHeight - serverListSelector.bottom);
    }
}
