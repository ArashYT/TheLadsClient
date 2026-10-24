package com.thelads.core.v1_20_1.social;

import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Screen wrapper for Friends UI on 1.20.1.
 * Supports tabs (Friends, Requests, Blocked, Add), searching/adding friends, and viewing online status.
 */
public final class FriendsScreen1201 extends Screen {
    public enum Tab { FRIENDS, REQUESTS, BLOCKED, ADD }

    private final Screen parent;
    private Tab currentTab = Tab.FRIENDS;
    private EditBox searchOrAddBox;
    private Object logicInstance; // Forwarding target for S4 FriendsScreenLogic when present

    public FriendsScreen1201(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
        tryInitLogic();
    }

    private void tryInitLogic() {
        try {
            Class<?> logicClass = Class.forName("com.thelads.core.client.social.ui.FriendsScreenLogic");
            logicInstance = logicClass.getConstructor(Runnable.class).newInstance((Runnable) this::onClose);
        } catch (Throwable ignored) {
            logicInstance = null;
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        int tabY = 36;
        int tabWidth = 70;
        int startX = (width - (tabWidth * 4 + 12)) / 2;

        addRenderableWidget(Button.builder(Component.literal("Friends"), b -> currentTab = Tab.FRIENDS)
            .bounds(startX, tabY, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Requests"), b -> currentTab = Tab.REQUESTS)
            .bounds(startX + tabWidth + 4, tabY, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Blocked"), b -> currentTab = Tab.BLOCKED)
            .bounds(startX + (tabWidth + 4) * 2, tabY, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add Friend"), b -> currentTab = Tab.ADD)
            .bounds(startX + (tabWidth + 4) * 3, tabY, tabWidth, 20).build());

        searchOrAddBox = new EditBox(font, width / 2 - 100, 68, 200, 20, Component.literal("Player Name"));
        searchOrAddBox.setHint(Component.literal(currentTab == Tab.ADD ? "Enter player username..." : "Search friends..."));
        addRenderableWidget(searchOrAddBox);

        if (currentTab == Tab.ADD) {
            addRenderableWidget(Button.builder(Component.literal("Send Request"), b -> {
                String name = searchOrAddBox.getValue().trim();
                if (!name.isEmpty()) {
                    SocialBridge1201.handleChatAction("friend_add:" + name);
                    searchOrAddBox.setValue("");
                }
            }).bounds(width / 2 - 50, 96, 100, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(width / 2 - 60, height - 32, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g);
        // Header
        g.drawCenteredString(font, "Friends & Social", width / 2, 14, LadsPalette.ACCENT);

        // Content panel
        int panelY = 96;
        if (currentTab == Tab.FRIENDS) {
            int count = SocialBridge1201.getOnlineFriendCount();
            g.drawCenteredString(font, count > 0 ? count + " Friends Online" : "No friends online right now", width / 2, panelY + 20, 0xFFAAAAAA);
            g.drawCenteredString(font, "Invite friends to your world via the Pause Menu -> Multiplayer Options", width / 2, panelY + 40, 0xFF777777);
        } else if (currentTab == Tab.REQUESTS) {
            g.drawCenteredString(font, "No pending friend requests", width / 2, panelY + 20, 0xFFAAAAAA);
        } else if (currentTab == Tab.BLOCKED) {
            g.drawCenteredString(font, "No blocked users", width / 2, panelY + 20, 0xFFAAAAAA);
        }

        // Delegate to S4 logic if available
        if (logicInstance != null) {
            try {
                logicInstance.getClass().getMethod("render", com.thelads.core.client.bridge.LadsGraphics.class, int.class, int.class)
                    .invoke(logicInstance, new GuiGraphicsLadsAdapter1201(g, font), mx, my);
            } catch (Throwable ignored) {}
        }

        super.render(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (logicInstance != null) {
            try {
                Object res = logicInstance.getClass().getMethod("mouseClicked", double.class, double.class, int.class)
                    .invoke(logicInstance, mx, my, button);
                if (Boolean.TRUE.equals(res)) return true;
            } catch (Throwable ignored) {}
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return parent == null || parent.isPauseScreen();
    }
}
