package com.thelads.core.v1_20_1.social;

import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

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
    private String difficulty = null; // null = world default
    private int viewDistanceCap = 0; // 0 = host view distance
    private boolean relayOnly = false;
    private boolean backupOnOpen = true;
    private boolean showJoinable = true;
    private int maxPlayers = 8;
    private Object logicInstance; // Forwarding target for S4 HostScreenLogic when present

    public HostScreen1201(Screen parent) {
        super(Component.literal("Multiplayer Options"));
        this.parent = parent;
        loadSavedWorldOptions();
        tryInitLogic();
    }

    private static Path getWorldDir() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
        }
        return null;
    }

    private void loadSavedWorldOptions() {
        Path worldDir = getWorldDir();
        if (worldDir != null) {
            HostOptions saved = HostOptions.load(worldDir);
            this.scope = saved.scope;
            this.approval = saved.approval;
            this.guestGameMode = saved.guestGameMode;
            this.guestOpLevel = saved.guestOpLevel;
            this.pvp = saved.pvp;
            this.difficulty = saved.difficulty;
            this.viewDistanceCap = saved.viewDistanceCap;
            this.relayOnly = saved.relayOnly;
            this.backupOnOpen = saved.backupOnOpen;
            this.showJoinable = saved.showJoinable;
            this.maxPlayers = saved.maxPlayers;
        }
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
        int y = 42;

        // Row 1: Scope toggle & Guest Game Mode
        addRenderableWidget(Button.builder(Component.literal("Scope: " + scope.name()), b -> {
            scope = switch (scope) {
                case OFF -> HostOptions.Scope.FRIENDS;
                case FRIENDS -> HostOptions.Scope.INVITE;
                case INVITE -> HostOptions.Scope.LAN;
                case LAN -> HostOptions.Scope.OFF;
            };
            b.setMessage(Component.literal("Scope: " + scope.name()));
        }).bounds(leftX, y, 150, 20).build());

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

        // Row 2: Cheats & PvP
        addRenderableWidget(Button.builder(Component.literal("Cheats: " + (guestOpLevel > 0 ? "ON" : "OFF")), b -> {
            guestOpLevel = guestOpLevel > 0 ? 0 : 2;
            b.setMessage(Component.literal("Cheats: " + (guestOpLevel > 0 ? "ON" : "OFF")));
        }).bounds(leftX, y, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("PvP: " + (pvp ? "ON" : "OFF")), b -> {
            pvp = !pvp;
            b.setMessage(Component.literal("PvP: " + (pvp ? "ON" : "OFF")));
        }).bounds(rightX, y, 150, 20).build());

        y += 24;

        // Row 3: Difficulty & View Distance Cap
        addRenderableWidget(Button.builder(Component.literal("Difficulty: " + (difficulty == null ? "Default" : difficulty)), b -> {
            if (difficulty == null) difficulty = "PEACEFUL";
            else if ("PEACEFUL".equalsIgnoreCase(difficulty)) difficulty = "EASY";
            else if ("EASY".equalsIgnoreCase(difficulty)) difficulty = "NORMAL";
            else if ("NORMAL".equalsIgnoreCase(difficulty)) difficulty = "HARD";
            else difficulty = null;
            b.setMessage(Component.literal("Difficulty: " + (difficulty == null ? "Default" : difficulty)));
        }).bounds(leftX, y, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("View Distance: " + (viewDistanceCap == 0 ? "Default" : viewDistanceCap + " Chunks")), b -> {
            viewDistanceCap = switch (viewDistanceCap) {
                case 0 -> 8;
                case 8 -> 12;
                case 12 -> 16;
                case 16 -> 24;
                case 24 -> 32;
                default -> 0;
            };
            b.setMessage(Component.literal("View Distance: " + (viewDistanceCap == 0 ? "Default" : viewDistanceCap + " Chunks")));
        }).bounds(rightX, y, 150, 20).build());

        y += 24;

        // Row 4: Backup on open & Relay only
        addRenderableWidget(Button.builder(Component.literal("Backup World: " + (backupOnOpen ? "ON" : "OFF")), b -> {
            backupOnOpen = !backupOnOpen;
            b.setMessage(Component.literal("Backup World: " + (backupOnOpen ? "ON" : "OFF")));
        }).bounds(leftX, y, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Hide IP (Relay): " + (relayOnly ? "ON" : "OFF")), b -> {
            relayOnly = !relayOnly;
            b.setMessage(Component.literal("Hide IP (Relay): " + (relayOnly ? "ON" : "OFF")));
        }).bounds(rightX, y, 150, 20).build());

        y += 32;

        // Action buttons
        if (!hostingNow) {
            addRenderableWidget(Button.builder(Component.literal("Open to Friends"), b -> {
                HostOptions opts = buildOptions();
                saveOptions(opts);
                onClose();
                bridge.openToFriends(opts);
            }).bounds(leftX, y, 150, 20).build()).active = canHost;

            addRenderableWidget(Button.builder(Component.literal("Open to LAN"), b -> {
                HostOptions opts = buildOptions();
                saveOptions(opts);
                bridge.openToLan(opts);
                onClose();
            }).bounds(rightX, y, 150, 20).build()).active = canHost;
        } else {
            addRenderableWidget(Button.builder(Component.literal("Apply Changes"), b -> {
                HostOptions opts = buildOptions();
                saveOptions(opts);
                bridge.applyHostOptions(opts);
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

    private void saveOptions(HostOptions opts) {
        Path worldDir = getWorldDir();
        if (worldDir != null && opts != null) {
            try {
                opts.save(worldDir);
            } catch (Exception ignored) {}
        }
    }

    private HostOptions buildOptions() {
        HostOptions opts = new HostOptions();
        opts.scope = scope;
        opts.approval = approval;
        opts.maxPlayers = maxPlayers;
        opts.guestGameMode = guestGameMode;
        opts.guestOpLevel = guestOpLevel;
        opts.pvp = pvp;
        opts.difficulty = difficulty;
        opts.gamerules = Collections.emptyMap();
        opts.viewDistanceCap = viewDistanceCap;
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
}
