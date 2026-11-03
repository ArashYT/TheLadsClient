package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import com.thelads.core.client.ViaForce;
import com.thelads.core.client.gui.ListContextMenu;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.gui.ServerDiscoveryScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Multiplayer: Discover Servers, the columns control, the pinned network's Edit and Delete off, and the right-click menu of a server row,
 * whose items press vanilla's own buttons. Essential's overlay buttons are removed.
 */
@Mixin(value = JoinMultiplayerScreen.class, priority = 1100)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    @Shadow protected ServerSelectionList serverSelectionList;
    @Shadow private Button editButton;
    @Shadow private Button deleteButton;
    @Shadow private void refreshServerList() {}
    @Shadow public abstract void join(ServerData data);
    @Shadow public abstract ServerList getServers();
    @Unique private Button ladsDiscoverButton;
    @Unique private Button ladsColumnsButton;
    @Unique private final ListContextMenu ladsMenu = new ListContextMenu();

    protected JoinMultiplayerScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ladsAddDiscoverButton(CallbackInfo ci) {
        ladsPurgeEssential();
        ladsMenu.close();
        ladsDiscoverButton = addRenderableWidget(Button.builder(Component.literal("Discover Servers"), b -> {
            if (minecraft != null) {
                minecraft.setScreenAndShow(new ServerDiscoveryScreen(this));
            }
        }).bounds(width - 124, 8, 116, 20).build());
        ladsColumnsButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ServerListColumns.cycle(width);
            serverSelectionList.setScrollAmount(serverSelectionList.scrollAmount());
            ladsUpdateColumns();
        }).bounds(8, 8, 36, 20).build());
        ladsUpdateColumns();
        ladsPlaceDiscover();
    }

    /** 26.x has no extractRenderState here: every relayout (resize) ends in repositionElements, after ViaFabricPlus' hook (priority). */
    @Inject(method = "repositionElements", at = @At("RETURN"), require = 1)
    private void ladsAfterLayout(CallbackInfo ci) {
        ladsPurgeEssential();
        ladsUpdateColumns();
        ladsPlaceDiscover();
    }

    /** Hidden when only one column fits; its icon is drawn over it in extractRenderState. */
    @Unique
    private void ladsUpdateColumns() {
        if (ladsColumnsButton == null) return;
        int columns = ServerListColumns.fit(ServerListColumns.wanted(), width);
        ladsColumnsButton.visible = ServerListColumns.most(width) > 1;
        ladsColumnsButton.setMessage(Component.literal("    " + columns));
        ladsColumnsButton.setTooltip(Tooltip.create(Component.literal("Server list columns: " + columns + ". Click to change.")));
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
                return true;
            }
        }
        return false;
    }

    @Unique
    private void ladsPurgeEssential() {
        for (var child : List.copyOf(children())) {
            if (child instanceof AbstractWidget widget && widget != ladsDiscoverButton) {
                if (widget.getClass().getName().startsWith("gg.essential.")) {
                    widget.visible = false;
                    widget.active = false;
                    removeWidget(widget);
                }
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (ladsMenu.mouseClicked(event.x(), event.y())) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && ladsOpenMenu((int) event.x(), (int) event.y())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    /** Right click selects the row and opens its menu. False off the rows (the LAN header has none). */
    @Unique
    private boolean ladsOpenMenu(int x, int y) {
        if (!serverSelectionList.isMouseOver(x, y)) return false;
        GuiEventListener row = serverSelectionList.getChildAt(x, y).orElse(null);
        if (row instanceof ServerSelectionList.OnlineServerEntry entry) {
            serverSelectionList.setSelected(entry);
            ServerData data = entry.getServerData();
            boolean change = data.state() == ServerData.State.INCOMPATIBLE && ViaForce.supports(data.protocol);
            ladsMenu.open(x, y, ServerListEntryUi.serverMenu(LadsNetwork.isPinned(data.ip), change), id -> ladsPickServer(id, data));
            return true;
        }
        if (row instanceof ServerSelectionList.NetworkServerEntry entry) {
            serverSelectionList.setSelected(entry);
            ladsMenu.open(x, y, ServerListEntryUi.lanMenu(), id -> entry.join());
            return true;
        }
        return false;
    }

    @Unique
    private void ladsPickServer(String id, ServerData data) {
        KeyEvent press = new KeyEvent(InputConstants.KEY_RETURN, 0, 0);
        switch (id) {
            case "join" -> join(data);
            case "edit" -> editButton.onPress(press);
            case "delete" -> deleteButton.onPress(press);
            case "copy" -> minecraft.keyboardHandler.setClipboard(data.ip);
            case "refresh" -> refreshServerList();
            case "version" -> {
                if (ViaForce.force(data, data.protocol)) {
                    getServers().save();
                    join(data);
                }
            }
            default -> {}
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ladsMenuKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!ladsMenu.isOpen()) return;
        if (event.isEscape()) ladsMenu.close();
        cir.setReturnValue(true);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        ladsMenu.close();
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (ServerListDrag.qaMouseX >= 0) {
            mouseX = ServerListDrag.qaMouseX;
            mouseY = ServerListDrag.qaMouseY;
        }
        boolean open = ladsMenu.isOpen();
        super.extractRenderState(graphics, open ? -1 : mouseX, open ? -1 : mouseY, partialTick);
        var adapter = new GuiGraphicsExtractorLadsAdapter(graphics, font);
        if (ladsColumnsButton != null && ladsColumnsButton.visible) {
            TitleScreenTheme.icon(adapter, "columns", ladsColumnsButton.getX() + 6, ladsColumnsButton.getY() + 5, 10, 0xFFFFFFFF);
        }
        ladsMenu.render(adapter, mouseX, mouseY);
    }
}
