package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge.VoiceChatState;
import com.thelads.core.client.bridge.LadsGameBridge.VoiceMember;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.List;

/** Simple Voice Chat's group list as a Lads HUD element (1.7.0): each member's head and name, green while talking. */
public class VoiceGroupHudElement extends HudElement {
    private static final List<VoiceMember> SAMPLE = List.of(new VoiceMember("Sample talking", "", true, false),
        new VoiceMember("Sample deafened", "", false, true));
    private static final int ROW = 12;
    private List<VoiceMember> members = List.of();

    @Override
    public boolean isAvailable() {
        return com.thelads.core.client.bridge.LadsGameBridge.get() != null && com.thelads.core.client.bridge.LadsGameBridge.get().voiceChat() != null;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        VoiceChatState state = g.getGame().voiceChat();
        members = state != null ? state.group() : List.of();
        if (members.isEmpty() && editor) members = SAMPLE;
        width = 40;
        for (VoiceMember member : members) width = Math.max(width, 18 + g.textWidth(member.name()) + 4);
        height = Math.max(ROW, members.size() * ROW) + 4;
    }

    @Override
    public void render(LadsGraphics g) {
        if (members.isEmpty()) return;
        drawBackground(g);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        for (int i = 0; i < members.size(); i++) {
            VoiceMember member = members.get(i);
            int rowY = y + 2 + i * ROW;
            if (member.talking()) g.fill(x + 2, rowY, x + 14, rowY + 12, 0xFF55FF55); // SVC's talk outline
            g.drawHead(member.name(), member.uuid(), x + 4, rowY + 2, 8);
            if (member.disabled()) g.drawSprite("voicechat:icons/speaker_small_off", x + 4, rowY + 2, 8);
            g.drawText(member.name(), x + 18, rowY + 2, member.talking() ? 0xFF55FF55 : resolveColor(), shadow);
        }
    }
}
