package com.thelads.core.v26_2.social;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Multiplayer Options / Host Screen for 26.3.
 * Configures world sharing scope, permissions, difficulty, PvP and guests management.
 */
public final class HostScreen26 extends Screen {
    private final Screen parent;
    private final HostOptions options;
    private boolean isPublished;
    private Button scopeButton;
    private Button gameModeButton;
    private Button cheatsButton;
    private Button pvpButton;
    private Button backupButton;
    private Button actionHostButton;
    private Button doneButton;

    public HostScreen26(Screen parent) {
        super(Component.literal("Multiplayer Options"));
        this.parent = parent;
        Path worldDir = Minecraft.getInstance().getSingleplayerServer() != null
            ? Minecraft.getInstance().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            : null;
        this.options = worldDir != null ? HostOptions.load(worldDir) : new HostOptions();
        this.isPublished = Minecraft.getInstance().getSingleplayerServer() != null
            && Minecraft.getInstance().getSingleplayerServer().isPublished();
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int top = 40;

        scopeButton = addRenderableWidget(Button.builder(Component.literal("Scope: " + options.scope.name()), b -> {
            options.scope = switch (options.scope) {
                case FRIENDS -> HostOptions.Scope.INVITE;
                case INVITE -> HostOptions.Scope.LAN;
                case LAN -> HostOptions.Scope.OFF;
                case OFF -> HostOptions.Scope.FRIENDS;
            };
            b.setMessage(Component.literal("Scope: " + options.scope.name()));
        }).bounds(cx - 155, top, 150, 20).build());

        gameModeButton = addRenderableWidget(Button.builder(Component.literal("Game Mode: " + options.guestGameMode), b -> {
            options.guestGameMode = switch (options.guestGameMode.toLowerCase()) {
                case "survival" -> "creative";
                case "creative" -> "adventure";
                case "adventure" -> "spectator";
                default -> "survival";
            };
            b.setMessage(Component.literal("Game Mode: " + options.guestGameMode));
        }).bounds(cx + 5, top, 150, 20).build());

        cheatsButton = addRenderableWidget(Button.builder(Component.literal("Allow Cheats: " + (options.guestOpLevel > 0 ? "ON" : "OFF")), b -> {
            options.guestOpLevel = options.guestOpLevel > 0 ? 0 : 2;
            b.setMessage(Component.literal("Allow Cheats: " + (options.guestOpLevel > 0 ? "ON" : "OFF")));
        }).bounds(cx - 155, top + 26, 150, 20).build());

        pvpButton = addRenderableWidget(Button.builder(Component.literal("PvP: " + (options.pvp ? "ON" : "OFF")), b -> {
            options.pvp = !options.pvp;
            b.setMessage(Component.literal("PvP: " + (options.pvp ? "ON" : "OFF")));
        }).bounds(cx + 5, top + 26, 150, 20).build());

        backupButton = addRenderableWidget(Button.builder(Component.literal("Backup World: " + (options.backupOnOpen ? "YES" : "NO")), b -> {
            options.backupOnOpen = !options.backupOnOpen;
            b.setMessage(Component.literal("Backup World: " + (options.backupOnOpen ? "YES" : "NO")));
        }).bounds(cx - 155, top + 52, 150, 20).build());

        String hostLabel = isPublished ? "Stop Hosting" : "Open World";
        actionHostButton = addRenderableWidget(Button.builder(Component.literal(hostLabel), b -> {
            if (isPublished) {
                SocialBridge.get().closeHosting("Host stopped sharing");
                isPublished = false;
                b.setMessage(Component.literal("Open World"));
            } else {
                if (options.scope == HostOptions.Scope.LAN) {
                    SocialBridge.get().openToLan(options);
                } else if (options.scope != HostOptions.Scope.OFF) {
                    SocialBridge.get().openToFriends(options);
                }
                isPublished = Minecraft.getInstance().getSingleplayerServer() != null
                    && Minecraft.getInstance().getSingleplayerServer().isPublished();
                b.setMessage(Component.literal(isPublished ? "Stop Hosting" : "Open World"));
            }
            rebuildWidgets();
        }).bounds(cx + 5, top + 52, 150, 20).build());

        // Guest management rows if hosting
        if (isPublished) {
            List<SocialBridge.GuestInfo> guests = SocialBridge.get().guests();
            int guestY = top + 90;
            for (int i = 0; i < Math.min(guests.size(), 4); i++) {
                SocialBridge.GuestInfo guest = guests.get(i);
                addRenderableWidget(Button.builder(Component.literal("Kick"), b -> {
                    SocialBridge.get().guestAction(guest.id(), SocialBridge.GuestAction.KICK);
                    rebuildWidgets();
                }).bounds(cx + 50, guestY + i * 24, 45, 18).build());

                addRenderableWidget(Button.builder(Component.literal("Ban"), b -> {
                    SocialBridge.get().guestAction(guest.id(), SocialBridge.GuestAction.BAN);
                    rebuildWidgets();
                }).bounds(cx + 100, guestY + i * 24, 45, 18).build());
            }
        }

        doneButton = addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(cx - 50, height - 28, 100, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xCC100B10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);

        int cx = width / 2;
        g.fill(0, 0, width, 2, LadsPalette.ACCENT);
        g.text(font, Component.literal("Multiplayer Options & P2P Hosting"),
            cx - font.width("Multiplayer Options & P2P Hosting") / 2, 14, LadsPalette.TEXT, false);

        if (isPublished) {
            int port = Minecraft.getInstance().getSingleplayerServer() != null
                ? Minecraft.getInstance().getSingleplayerServer().getPort() : -1;
            String status = "World is OPEN (Port: " + port + ", Scope: " + options.scope.name() + ")";
            g.text(font, Component.literal(status), cx - font.width(status) / 2, 115, 0xFF4ADE80, false);

            List<SocialBridge.GuestInfo> guests = SocialBridge.get().guests();
            g.text(font, Component.literal("Connected Guests (" + guests.size() + "):"), cx - 155, 126, LadsPalette.ACCENT, false);
            if (guests.isEmpty()) {
                g.text(font, Component.literal("No guests connected yet."), cx - 155, 140, LadsPalette.MUTED, false);
            } else {
                int y = 138;
                for (int i = 0; i < Math.min(guests.size(), 4); i++) {
                    SocialBridge.GuestInfo guest = guests.get(i);
                    g.text(font, Component.literal(guest.name() + " (" + guest.gameMode() + ", " + guest.ping() + "ms)"), cx - 155, y, LadsPalette.TEXT, false);
                    y += 24;
                }
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
