package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.ServerAddressInput;
import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import com.thelads.core.client.ViaForce;
import com.thelads.core.client.gui.ListContextMenu;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.gui.ServerDiscoveryScreen1201;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Multiplayer: Discover Servers (the launcher's curated list) top right, the columns control, the pinned network's Edit and Delete off, and
 * the right-click menu of a server row, whose items press vanilla's own buttons. Essential's overlay buttons are removed.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    @Shadow protected ServerSelectionList serverSelectionList;
    @Shadow private Button editButton;
    @Shadow private Button deleteButton;
    @Shadow private void refreshServerList() {}
    @Shadow public abstract void joinSelectedServer();
    @Shadow public abstract void setSelected(ServerSelectionList.Entry entry);
    @Shadow public abstract ServerList getServers();
    @Shadow private ServerData editingServer;
    @Shadow private void addServerCallback(boolean ok) {}
    @Unique private Button ladsDiscoverButton;
    @Unique private Button ladsColumnsButton;
    @Unique private final ListContextMenu ladsMenu = new ListContextMenu();

    protected JoinMultiplayerScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void ladsAddDiscoverButton(CallbackInfo ci) {
        ladsPurgeEssential();
        ladsMenu.close();
        ladsDiscoverButton = addRenderableWidget(Button.builder(Component.literal("Discover Servers"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new ServerDiscoveryScreen1201(this));
            }
        }).bounds(width - 124, 8, 116, 20).build());
        ladsColumnsButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ServerListColumns.cycle(width);
            serverSelectionList.setScrollAmount(serverSelectionList.getScrollAmount());
            ladsUpdateColumns();
        }).bounds(8, 8, 84, 20).build());
        ladsUpdateColumns();
        org.slf4j.LoggerFactory.getLogger("TheLadsCore-1.20.1").info("Discover Servers button added to the multiplayer screen at {},{}", width - 124, 8);
    }

    /** Hidden when only one column fits; its icon is drawn over it in render. */
    @Unique
    private void ladsUpdateColumns() {
        if (ladsColumnsButton == null) return;
        int columns = ServerListColumns.fit(ServerListColumns.wanted(), width);
        ladsColumnsButton.visible = ServerListColumns.most(width) > 1;
        ladsColumnsButton.setMessage(Component.literal("    Columns: " + columns));
        ladsColumnsButton.setTooltip(Tooltip.create(Component.literal("Server list columns: " + columns + ". Click to change.")));
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"))
    private void ladsSuppressEssential(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ladsPurgeEssential();
        ladsPlaceDiscover();
    }

    /** Nothing under the open menu takes hover, and QA draws its own mouse. */
    @ModifyVariable(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ladsMouseX(int mouseX) {
        return ladsMenu.isOpen() ? -1 : ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseX : mouseX;
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int ladsMouseY(int mouseY) {
        return ladsMenu.isOpen() ? -1 : ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseY : mouseY;
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"))
    private void ladsDrawOver(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var adapter = new GuiGraphicsLadsAdapter1201(graphics, font);
        if (ladsColumnsButton != null && ladsColumnsButton.visible) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1); // this GUI is depth-tested: over the button it sits on
            TitleScreenTheme.icon(adapter, "columns", ladsColumnsButton.getX() + 6, ladsColumnsButton.getY() + 5, 10, 0xFFFFFFFF);
            graphics.pose().popPose();
        }
        // The handlers above hid the mouse from the rows; the menu reads the real one.
        double scale = minecraft.getWindow().getGuiScale();
        int x = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseX : (int) (minecraft.mouseHandler.xpos() / scale);
        int y = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseY : (int) (minecraft.mouseHandler.ypos() / scale);
        ladsMenu.render(adapter, x, y);
    }

    /** Top right, unless another button (ViaFabricPlus' default spot) is there: then top left, the columns control next to it. */
    @Unique
    private void ladsPlaceDiscover() {
        if (ladsDiscoverButton == null) return;
        ladsDiscoverButton.setPosition(width - 124, 8);
        ladsColumnsButton.setPosition(8, 8);
        if (ladsDiscoverCovered()) {
            ladsDiscoverButton.setPosition(8, 8);
            ladsColumnsButton.setPosition(128, 8);
        }
    }

    @Unique
    private boolean ladsDiscoverCovered() {
        Button d = ladsDiscoverButton;
        for (var child : children()) {
            if (child instanceof Button b && b != d && b != ladsColumnsButton && b.visible
                && b.getX() < d.getX() + d.getWidth() && d.getX() < b.getX() + b.getWidth()
                && b.getY() < d.getY() + d.getHeight() && d.getY() < b.getY() + b.getHeight()) {
                b.setY(8); // same row as ours (ViaFabricPlus sits a few px higher)
                return true;
            }
        }
        return false;
    }

    @Unique
    private void ladsPurgeEssential() {
        for (var child : List.copyOf(children())) {
            if (child instanceof AbstractWidget widget && widget != ladsDiscoverButton
                && widget.getClass().getName().startsWith("gg.essential.")) {
                widget.visible = false;
                widget.active = false;
                removeWidget(widget);
            }
        }
    }

    /** The pinned network can't be edited or deleted from here. */
    @Inject(method = "onSelectedChange", at = @At("TAIL"))
    private void ladsPinnedStays(CallbackInfo ci) {
        if (serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry entry && LadsNetwork.isPinned(entry.getServerData().ip)) {
            editButton.active = false;
            deleteButton.active = false;
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (ladsMenu.mouseClicked(x, y)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && ladsOpenMenu(x, y)) return true;
        return super.mouseClicked(x, y, button);
    }

    /** Right click selects the row and opens its menu. False off the rows (the LAN header has none). */
    @Unique
    private boolean ladsOpenMenu(double x, double y) {
        if (!serverSelectionList.isMouseOver(x, y)) return false;
        GuiEventListener row = serverSelectionList.getChildAt(x, y).orElse(null);
        if (row instanceof ServerSelectionList.OnlineServerEntry entry) {
            setSelected(entry);
            ServerData data = entry.getServerData();
            boolean change = data.pinged && data.ping >= 0 && data.protocol != SharedConstants.getCurrentVersion().getProtocolVersion() && ViaForce.supports(data.protocol);
            ladsMenu.open((int) x, (int) y, ServerListEntryUi.serverMenu(LadsNetwork.isPinned(data.ip), change), id -> ladsPickServer(id, data));
            return true;
        }
        if (row instanceof ServerSelectionList.NetworkServerEntry entry) {
            setSelected(entry);
            ladsMenu.open((int) x, (int) y, ServerListEntryUi.lanMenu(), id -> joinSelectedServer());
            return true;
        }
        return false;
    }

    @Unique
    private void ladsPickServer(String id, ServerData data) {
        switch (id) {
            case "join" -> joinSelectedServer();
            case "edit" -> editButton.onPress();
            case "delete" -> deleteButton.onPress();
            case "copy" -> minecraft.keyboardHandler.setClipboard(data.ip);
            case "refresh" -> refreshServerList();
            case "version" -> {
                if (ViaForce.force(data, data.protocol)) {
                    getServers().save();
                    joinSelectedServer();
                }
            }
            default -> {}
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ladsMenuKeys(int key, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!ladsMenu.isOpen()) return;
        if (key == GLFW.GLFW_KEY_ESCAPE) ladsMenu.close();
        cir.setReturnValue(true);
    }

    /** Ctrl+V adds the clipboard's address, Ctrl+C copies the selected IP, Ctrl+A opens Add Server, Del asks to delete. Not while a text field has focus. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ladsShortcuts(int key, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ladsMenu.isOpen() || getFocused() instanceof EditBox || minecraft == null) return;
        if (Screen.isPaste(key)) {
            if (ladsPasteServer()) cir.setReturnValue(true);
        } else if (Screen.isCopy(key)) {
            if (serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry entry) {
                minecraft.keyboardHandler.setClipboard(entry.getServerData().ip);
                cir.setReturnValue(true);
            }
        } else if (Screen.isSelectAll(key)) {
            editingServer = new ServerData(I18n.get("selectServer.defaultName"), "", false);
            minecraft.setScreen(new EditServerScreen(this, ok -> addServerCallback(ok), editingServer));
            cir.setReturnValue(true);
        } else if (key == GLFW.GLFW_KEY_DELETE && !hasControlDown() && deleteButton.active
            && serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry) {
            deleteButton.onPress(); // vanilla's own confirm screen
            cir.setReturnValue(true);
        }
    }

    /** Adds the clipboard's address as a new server (or selects the saved one with the same address). False when it is not an address. */
    @Unique
    private boolean ladsPasteServer() {
        String ip = ServerAddressInput.clean(minecraft.keyboardHandler.getClipboard());
        if (ip == null || !ServerAddress.isValidAddress(ip)) return false;
        ServerList list = getServers();
        List<String> saved = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) saved.add(list.get(i).ip);
        int at = ServerAddressInput.indexOf(saved, ip);
        ServerData data;
        if (at >= 0) {
            data = list.get(at);
        } else {
            data = new ServerData(I18n.get("selectServer.defaultName"), ip, false);
            ServerData hidden = list.unhide(ip); // vanilla's add does the same
            if (hidden != null) data.copyNameIconFrom(hidden);
            list.add(data, false);
            list.save();
            serverSelectionList.updateOnlineServers(list);
        }
        int row = 0;
        for (ServerSelectionList.Entry entry : serverSelectionList.children()) {
            if (entry instanceof ServerSelectionList.OnlineServerEntry online && online.getServerData() == data) {
                setSelected(entry);
                double top = row * 36.0, view = Math.max(36, this.height - 96), now = serverSelectionList.getScrollAmount(); // rows are 36 px, list sits between header and footer
                if (top < now) serverSelectionList.setScrollAmount(top);
                else if (top + 36 > now + view) serverSelectionList.setScrollAmount(top + 36 - view);
                break;
            }
            row++;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scroll) {
        ladsMenu.close();
        return super.mouseScrolled(x, y, scroll);
    }
}
