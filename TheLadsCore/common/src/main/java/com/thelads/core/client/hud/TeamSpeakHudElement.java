package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.media.TeamSpeakBridge;
import java.util.List;

/**
 * TeamSpeak 3 HUD (Lunar / Badlion TeamSpeak overlay):
 * Renders the active TeamSpeak channel and participant voice status.
 * Green indicators denote users who are actively speaking.
 */
public class TeamSpeakHudElement extends HudElement {
    public static final String NAME = "TeamSpeak";
    private static final int TALKING_COLOR = 0xFF55FF55; // Bright green
    private static final int IDLE_COLOR = 0xFFAAAAAA;    // Muted gray
    private static final int HEADER_COLOR = 0xFFFFAA00;  // Orange accent

    private boolean editor;
    private int clientCount;

    public TeamSpeakHudElement() {
        this.width = 120;
        this.height = 36;
        this.x = 10;
        this.y = 80;
        this.enabled = false;
        this.moduleName = NAME;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        this.editor = editor;
        TeamSpeakBridge.start(); // Ensure worker thread is listening
        int count = 0;
        if (editor || !TeamSpeakBridge.isConnected()) {
            count = 2; // Preview lines
        } else {
            count = TeamSpeakBridge.getClients().size();
        }
        clientCount = count;
        this.width = 130;
        this.height = 14 + Math.max(1, count) * 11 + 4;
    }

    @Override
    public void render(LadsGraphics g) {
        int rx = x;
        int ry = y;
        boolean shadow = com.thelads.core.config.HudSettings.getInstance().isTextShadow();

        // Background box
        g.fill(rx, ry, rx + width, ry + height, 0x80000000);

        String channel = TeamSpeakBridge.isConnected() ? TeamSpeakBridge.getCurrentChannel() : "TeamSpeak";
        if (editor) channel = "TeamSpeak: Lobby";

        g.drawText(channel, rx + 4, ry + 3, HEADER_COLOR, shadow);

        int lineY = ry + 15;
        if (!TeamSpeakBridge.isConnected() && !editor) {
            g.drawText("○ Not Connected", rx + 6, lineY, IDLE_COLOR, shadow);
            return;
        }

        if (editor) {
            // Editor preview
            g.drawText("● You (Talking)", rx + 6, lineY, TALKING_COLOR, shadow);
            g.drawText("○ Friend", rx + 6, lineY + 11, IDLE_COLOR, shadow);
            return;
        }

        List<TeamSpeakBridge.TsClient> clients = TeamSpeakBridge.getClients();
        if (clients.isEmpty()) {
            g.drawText("○ No users in channel", rx + 6, lineY, IDLE_COLOR, shadow);
            return;
        }

        for (TeamSpeakBridge.TsClient client : clients) {
            int dotColor = client.talking() ? TALKING_COLOR : IDLE_COLOR;
            String symbol = client.talking() ? "● " : "○ ";
            g.drawText(symbol + client.nickname(), rx + 6, lineY, dotColor, shadow);
            lineY += 11;
        }
    }
}
