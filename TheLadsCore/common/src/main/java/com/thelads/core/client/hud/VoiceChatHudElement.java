package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge.VoiceChatState;
import com.thelads.core.client.bridge.LadsGraphics;

/** Simple Voice Chat's microphone / muted / deafened / disconnected indicator as a Lads HUD element (1.7.0). */
public class VoiceChatHudElement extends HudElement {
    private static final String SAMPLE = "voicechat:icons/microphone";
    private String icon, label;

    @Override
    public boolean isAvailable() {
        return com.thelads.core.client.bridge.LadsGameBridge.get() != null && com.thelads.core.client.bridge.LadsGameBridge.get().voiceChat() != null;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        VoiceChatState state = g.getGame().voiceChat();
        icon = state != null ? state.icon() : null;
        if (icon == null && editor) icon = SAMPLE;
        label = icon == null || !optBool("Show label", true) ? null : label(icon);
        width = 20 + (label == null ? 0 : g.textWidth(label) + 4);
        height = 20;
    }

    @Override
    public void render(LadsGraphics g) {
        if (icon == null) return;
        drawBackground(g);
        g.drawSprite(icon, x + 2, y + 2, 16);
        if (label != null) g.drawText(label, x + 20, y + (height - g.fontHeight()) / 2 + 1, resolveColor(), com.thelads.core.config.HudSettings.getInstance().isTextShadow());
    }

    static String label(String icon) {
        String name = icon.substring(icon.lastIndexOf('/') + 1);
        return switch (name) {
            case "microphone" -> "Talking";
            case "microphone_whisper" -> "Whispering";
            case "microphone_off" -> "Muted";
            case "speaker_off" -> "Deafened";
            case "disconnected" -> "Voice disconnected";
            default -> "";
        };
    }
}
