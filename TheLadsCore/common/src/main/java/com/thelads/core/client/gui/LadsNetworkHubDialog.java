package com.thelads.core.client.gui;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.LadsNetworkStatus;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsSettingsScreen.Rect;
import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.LadsPresence;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.thelads.core.client.gui.MenuGraphics.fit;
import static com.thelads.core.client.gui.MenuGraphics.round;

/**
 * Modal hub for {@link LadsNetwork} ({@code mc.arashyt.ca}): live SLP player count, ping, MOTD,
 * active Lads Client presence, current game/server from 1.13.0 {@code hello}, and 1-click Join / Sub-Server switcher
 * (Lobby, Parkour, PvP Arena, PvP Duels, SMP).
 */
public final class LadsNetworkHubDialog {
    private boolean open = false;
    private Runnable onAfterJoin = () -> {};
    private Consumer<LadsNetwork.SubServer> customSubJoin = null;
    private Runnable customMainJoin = null;

    private Rect box = new Rect(0, 0, 0, 0);
    private Rect closeBtn = new Rect(0, 0, 0, 0);
    private Rect refreshBtn = new Rect(0, 0, 0, 0);
    private Rect joinMainBtn = new Rect(0, 0, 0, 0);
    private final Map<String, Rect> subCards = new LinkedHashMap<>();

    public void setOnAfterJoin(Runnable action) {
        this.onAfterJoin = action != null ? action : () -> {};
    }

    public void setCustomJoinHandlers(Runnable mainJoin, Consumer<LadsNetwork.SubServer> subJoin) {
        this.customMainJoin = mainJoin;
        this.customSubJoin = subJoin;
    }

    public void open() {
        this.open = true;
        LadsNetworkStatus.refreshIfStale();
    }

    public void close() {
        this.open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public Rect bounds() { return box; }
    public Rect joinMainBounds() { return joinMainBtn; }
    public Rect subServerBounds(String id) { return subCards.get(id); }

    public void render(LadsGraphics g, int mx, int my) {
        if (!open) return;
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        int dw = Math.min(420, width - 20);
        int dh = Math.min(248, height - 16);
        int dx = (width - dw) / 2, dy = (height - dh) / 2;
        box = new Rect(dx, dy, dw, dh);

        g.fill(0, 0, width, height, 0xB8050507);
        // Voxel Noir obsidian frame with 2px crimson top accent
        g.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, 0xFF2A2A35);
        g.fill(dx, dy, dx + dw, dy + dh, 0xFF121217);
        g.fill(dx, dy, dx + dw, dy + 2, LadsPalette.ACCENT);

        // Header
        g.drawText("THE LADS NETWORK", dx + 12, dy + 10, LadsPalette.TEXT);

        LadsNetworkStatus.Snapshot snap = LadsNetworkStatus.cached();
        LadsGameBridge bridge = LadsGameBridge.get();
        boolean connected = LadsNetwork.isConnected(bridge);

        String badgeText;
        int badgeDot;
        if (connected) {
            int ladsOnline = LadsPresence.size();
            badgeText = ladsOnline > 0 ? "CONNECTED · " + ladsOnline + " LADS" : "CONNECTED";
            badgeDot = 0xFF34D399;
        } else if (snap.online()) {
            int shownOnline = Math.max(0, snap.onlinePlayers());
            int shownMax = Math.max(shownOnline, snap.maxPlayers());
            String pingPart = snap.pingMs() > 0 ? " · " + snap.pingMs() + "ms" : "";
            badgeText = (shownMax > 0 ? shownOnline + "/" + shownMax : shownOnline + " online") + pingPart;
            badgeDot = 0xFF34D399;
        } else if (snap.checking()) {
            badgeText = "PINGING...";
            badgeDot = 0xFFFBBF24;
        } else {
            badgeText = "MC.ARASHYT.CA";
            badgeDot = LadsPalette.ACCENT;
        }

        closeBtn = new Rect(dx + dw - 22, dy + 7, 15, 15);
        boolean hoverClose = closeBtn.contains(mx, my);
        round(g, closeBtn.x(), closeBtn.y(), closeBtn.width(), closeBtn.height(), hoverClose ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("x", closeBtn.x() + closeBtn.width() / 2, closeBtn.y() + 3, hoverClose ? LadsPalette.TEXT : LadsPalette.MUTED);

        refreshBtn = new Rect(closeBtn.x() - 56, dy + 7, 52, 15);
        boolean hoverRefresh = refreshBtn.contains(mx, my);
        round(g, refreshBtn.x(), refreshBtn.y(), refreshBtn.width(), refreshBtn.height(), hoverRefresh ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("Refresh", refreshBtn.x() + refreshBtn.width() / 2, refreshBtn.y() + 4, LadsPalette.TEXT);

        int badgeW = g.textWidth(badgeText) + 18;
        int badgeX = refreshBtn.x() - badgeW - 6;
        if (badgeX > dx + 118) {
            round(g, badgeX, dy + 7, badgeW, 15, 0xFF1B1B23);
            g.fill(badgeX + 5, dy + 12, badgeX + 9, dy + 16, badgeDot);
            g.drawText(badgeText, badgeX + 13, dy + 11, LadsPalette.TEXT);
        }

        // Telemetry & MOTD strip
        int infoY = dy + 27;
        round(g, dx + 12, infoY, dw - 24, 30, 0xFF181820);
        String line1;
        if (connected) {
            String activeGame = LadsNetwork.game();
            String apiSrv = LadsApiClient.serverName();
            String where = activeGame != null && !activeGame.isEmpty()
                ? activeGame
                : (apiSrv != null && !apiSrv.isEmpty() ? apiSrv : LadsNetwork.ADDRESS);
            line1 = "Active Mode: " + where + "  ·  Server Resource Packs: Enabled";
        } else if (!snap.motd().isEmpty()) {
            line1 = snap.motd();
        } else {
            line1 = LadsNetwork.NAME + " (" + LadsNetwork.ADDRESS + ")  ·  Official Lads Client Network";
        }
        g.drawText(fit(g, line1, dw - 36), dx + 18, infoY + 5, LadsPalette.TEXT);
        String line2 = connected
            ? "Click any mode below to switch instantly via /server without disconnecting."
            : "Server Resource Packs auto-enabled · Select a destination to join immediately:";
        g.drawText(fit(g, line2, dw - 36), dx + 18, infoY + 17, LadsPalette.MUTED);

        // Primary Join / Return-to-Hub Banner
        int mainY = infoY + 36;
        joinMainBtn = new Rect(dx + 12, mainY, dw - 24, 24);
        boolean hoverMain = joinMainBtn.contains(mx, my);
        round(g, joinMainBtn.x(), joinMainBtn.y(), joinMainBtn.width(), joinMainBtn.height(),
            hoverMain ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.fill(joinMainBtn.x(), joinMainBtn.y(), joinMainBtn.x() + 3, joinMainBtn.y() + joinMainBtn.height(), 0xFFFF6B81);
        String hubCmd = LadsNetwork.hubCommand();
        String effectiveHub = hubCmd != null ? hubCmd : "/server lobby";
        String mainLabel = connected
            ? "Return to Network Hub (" + effectiveHub + ")"
            : "Join The Lads Network (" + LadsNetwork.ADDRESS + ")";
        g.drawCenteredText(fit(g, mainLabel, joinMainBtn.width() - 16),
            joinMainBtn.x() + joinMainBtn.width() / 2, joinMainBtn.y() + 8, LadsPalette.TEXT);

        // Sub-server cards grid (2 columns x 3 rows)
        subCards.clear();
        int gridY = mainY + 30;
        int gap = 6;
        int cols = 2;
        int cardW = (dw - 24 - gap) / cols;
        int availH = dh - (gridY - dy) - 10;
        int rows = (LadsNetwork.SUB_SERVERS.size() + cols - 1) / cols;
        int cardH = Math.max(24, Math.min(36, (availH - (rows - 1) * gap) / Math.max(1, rows)));

        for (int i = 0; i < LadsNetwork.SUB_SERVERS.size(); i++) {
            LadsNetwork.SubServer sub = LadsNetwork.SUB_SERVERS.get(i);
            int col = i % cols;
            int row = i / cols;
            int actualW = (col == 0 && i == LadsNetwork.SUB_SERVERS.size() - 1) ? (dw - 24) : cardW;
            Rect r = new Rect(dx + 12 + col * (cardW + gap), gridY + row * (cardH + gap), actualW, cardH);
            subCards.put(sub.id(), r);

            boolean hover = r.contains(mx, my);
            g.fill(r.x() - 1, r.y() - 1, r.x() + r.width() + 1, r.y() + r.height() + 1, hover ? LadsPalette.ACCENT : 0xFF252530);
            g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), hover ? 0xFF23232E : 0xFF191922);
            g.fill(r.x(), r.y(), r.x() + 2, r.y() + r.height(), hover ? LadsPalette.ACCENT : 0xFF3A3A48);

            g.drawText(fit(g, sub.name(), r.width() - 48), r.x() + 8, r.y() + 5, LadsPalette.TEXT);
            String actionTag = connected ? "Switch >" : "Join >";
            g.drawText(actionTag, r.x() + r.width() - g.textWidth(actionTag) - 6, r.y() + 5, hover ? LadsPalette.ACCENT : LadsPalette.MUTED);
            if (cardH >= 30) {
                g.drawText(fit(g, sub.subtitle(), r.width() - 14), r.x() + 8, r.y() + 17, LadsPalette.MUTED);
            }
        }
    }

    public boolean click(double x, double y, int button) {
        if (!open) return false;
        if (button != 0) return true;
        if (closeBtn.contains(x, y) || !box.contains(x, y)) {
            close();
            return true;
        }
        if (refreshBtn.contains(x, y)) {
            LadsNetworkStatus.forceRefresh();
            return true;
        }
        if (joinMainBtn.contains(x, y)) {
            LadsGameBridge bridge = LadsGameBridge.get();
            close();
            // Run onAfterJoin BEFORE initiating ConnectScreen so LadsSettingsScreen::close never overwrites ConnectScreen!
            onAfterJoin.run();
            if (LadsNetwork.isConnected(bridge) && bridge.hasPlayer()) {
                LadsNetwork.join(bridge);
            } else if (customMainJoin != null) {
                customMainJoin.run();
            } else {
                LadsNetwork.join(bridge);
            }
            return true;
        }
        for (LadsNetwork.SubServer sub : LadsNetwork.SUB_SERVERS) {
            Rect r = subCards.get(sub.id());
            if (r != null && r.contains(x, y)) {
                close();
                onAfterJoin.run();
                if (customSubJoin != null) {
                    customSubJoin.accept(sub);
                } else {
                    LadsNetwork.joinSubServer(LadsGameBridge.get(), sub);
                }
                return true;
            }
        }
        return true;
    }

    public boolean key(int key) {
        if (!open) return false;
        if (key == 256) {
            close();
        }
        return true;
    }
}
