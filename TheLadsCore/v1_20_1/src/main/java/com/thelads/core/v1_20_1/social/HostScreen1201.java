package com.thelads.core.v1_20_1.social;

import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Screen wrapper for Multiplayer Options (P2P hosting + LAN) on 1.20.1.
 * Replaces the vanilla Open to LAN screen in the pause menu.
 */
public final class HostScreen1201 extends Screen {
    private final Screen parent;
    private HostOptions.Scope scope = HostOptions.Scope.FRIENDS;
    private HostOptions.Approval approval = HostOptions.Approval.AUTO_FRIENDS;
    private String guestGameMode = "survival";
    private int guestOpLevel = 0;
    private boolean pvp = true;
    private boolean relayOnly = false;
    private boolean backupOnOpen = true;
    private boolean showJoinable = true;
    private int maxPlayers = 8;
    private Object logicInstance; // Forwarding target for S4 HostScreenLogic when present

    public HostScreen1201(Screen parent) {
        super(Component.literal("Multiplayer Options"));
        this.parent = parent;
        tryInitLogic();
    }

    private void tryInitLogic() {
        try {
            Class<?> logicClass = Class.forName("com.thelads.core.client.social.ui.HostScreenLogic");
            logicInstance = logicClass.getConstructor(Runnable.class).newInstance((Runnable) this::onClose);
        } catch (Throwable ignored) {
            logicInstance = null;
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        SocialBridge bridge = SocialBridge.get();
        boolean canHost = bridge.canHost();
        Minecraft mc = Minecraft.getInstance();
        boolean hostingNow = mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null && mc.getSingleplayerServer().isPublished();

        int leftX = width / 2 - 155;
        int rightX = width / 2 + 5;
        int y = 46;

        // Scope toggle
        addRenderableWidget(Button.builder(Component.literal("Scope: " + scope.name()), b -> {
            scope = switch (scope) {
                case OFF -> HostOptions.Scope.FRIENDS;
                case FRIENDS -> HostOptions.Scope.INVITE;
                case INVITE -> HostOptions.Scope.LAN;
                case LAN -> HostOptions.Scope.OFF;
            };
            b.setMessage(Component.literal("Scope: " + scope.name()));
        }).bounds(leftX, y, 150, 20).build());

        // Guest Game Mode
        addRenderableWidget(Button.builder(Component.literal("Game Mode: " + guestGameMode), b -> {
            guestGameMode = switch (guestGameMode) {
                case "survival" -> "creative";
                case "creative" -> "adventure";
                case "adventure" -> "spectator";
                default -> "survival";
            };
            b.setMessage(Component.literal("Game Mode: " + guestGameMode));
        }).bounds(rightX, y, 150, 20).build());

        y += 24;

        // Allow Cheats / Op level
        addRenderableWidget(Button.builder(Component.literal("Cheats: " + (guestOpLevel > 0 ? "ON" : "OFF")), b -> {
            guestOpLevel = guestOpLevel > 0 ? 0 : 2;
            b.setMessage(Component.literal("Cheats: " + (guestOpLevel > 0 ? "ON" : "OFF")));
        }).bounds(leftX, y, 150, 20).build());

        // PvP
        addRenderableWidget(Button.builder(Component.literal("PvP: " + (pvp ? "ON" : "OFF")), b -> {
            pvp = !pvp;
            b.setMessage(Component.literal("PvP: " + (pvp ? "ON" : "OFF")));
        }).bounds(rightX, y, 150, 20).build());

        y += 24;

        // Backup on open
        addRenderableWidget(Button.builder(Component.literal("Backup World: " + (backupOnOpen ? "ON" : "OFF")), b -> {
            backupOnOpen = !backupOnOpen;
            b.setMessage(Component.literal("Backup World: " + (backupOnOpen ? "ON" : "OFF")));
        }).bounds(leftX, y, 150, 20).build());

        // Always relay (hide IP)
        addRenderableWidget(Button.builder(Component.literal("Hide IP (Relay): " + (relayOnly ? "ON" : "OFF")), b -> {
            relayOnly = !relayOnly;
            b.setMessage(Component.literal("Hide IP (Relay): " + (relayOnly ? "ON" : "OFF")));
        }).bounds(rightX, y, 150, 20).build());

        y += 36;

        // Action buttons
        if (!hostingNow) {
            addRenderableWidget(Button.builder(Component.literal("Open to Friends"), b -> {
                HostOptions opts = buildOptions();
                int port = bridge.openToFriends(opts);
                if (port > 0) {
                    onClose();
                }
            }).bounds(leftX, y, 150, 20).build()).active = canHost;

            addRenderableWidget(Button.builder(Component.literal("Open to LAN"), b -> {
                HostOptions opts = buildOptions();
                bridge.openToLan(opts);
                onClose();
            }).bounds(rightX, y, 150, 20).build()).active = canHost;
        } else {
            addRenderableWidget(Button.builder(Component.literal("Apply Changes"), b -> {
                bridge.applyHostOptions(buildOptions());
                onClose();
            }).bounds(leftX, y, 150, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Stop Hosting"), b -> {
                bridge.closeHosting("Host stopped sharing");
                onClose();
            }).bounds(rightX, y, 150, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(width / 2 - 60, height - 28, 120, 20).build());
    }

    private HostOptions buildOptions() {
        HostOptions opts = new HostOptions();
        opts.scope = scope;
        opts.approval = approval;
        opts.maxPlayers = maxPlayers;
        opts.guestGameMode = guestGameMode;
        opts.guestOpLevel = guestOpLevel;
        opts.pvp = pvp;
        opts.difficulty = null;
        opts.gamerules = Collections.emptyMap();
        opts.viewDistanceCap = 0;
        opts.relayOnly = relayOnly;
        opts.backupOnOpen = backupOnOpen;
        opts.showJoinable = showJoinable;
        opts.welcome = "";
        return opts;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        var adapter = new GuiGraphicsLadsAdapter1201(graphics, font);
        adapter.fill(0, 0, width, 32, LadsPalette.PANEL);
        adapter.fill(0, 32, width, 33, LadsPalette.BORDER);

        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);

        SocialBridge bridge = SocialBridge.get();
        List<SocialBridge.GuestInfo> guests = bridge.guests();
        if (!guests.isEmpty()) {
            graphics.drawString(font, "Connected Guests (" + guests.size() + "):", width / 2 - 155, height - 60, 0xAAAAAA);
            int gx = width / 2 - 155;
            for (SocialBridge.GuestInfo g : guests) {
                graphics.drawString(font, g.name() + " (" + g.ping() + "ms)", gx, height - 48, 0x55FF55);
                gx += font.width(g.name() + " (" + g.ping() + "ms) ") + 10;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
