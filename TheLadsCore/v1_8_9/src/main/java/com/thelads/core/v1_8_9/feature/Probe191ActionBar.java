package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.client.hud.ActionBarQa;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentText;

/**
 * QA only (LADS_VERIFY_189_ONLY=actionbar): the common Action Bar routine (ActionBarQa) with 1.8.9's action bar chat packet (type 2,
 * as Hypixel sends it), record and screenshots (lads-qa/screenshots/ab-*.png). 1.8.9 has no /title actionbar.
 */
final class Probe191ActionBar {
    private static ActionBarQa qa;

    private Probe191ActionBar() {}

    static boolean run(Minecraft mc) throws Exception {
        if (qa == null) qa = new ActionBarQa();
        if (!qa.tick(GAME)) return retry(0); // every tick: the routine counts game ticks
        check(qa.failures().isEmpty(), "action bar: " + qa.failures());
        return after(5);
    }

    private static final ActionBarQa.Game GAME = new ActionBarQa.Game() {
        @Override public void actionBar(String legacy, String json) { // 1.8 servers (Hypixel) send formatting codes in the text
            Minecraft.getMinecraft().getNetHandler().handleChat(new S02PacketChat(new ChatComponentText(legacy), (byte) 2));
        }

        @Override public void nowPlaying(String song) { Minecraft.getMinecraft().ingameGUI.setRecordPlayingMessage(song); }

        @Override public boolean titleActionBar(String json) { return false; }

        @Override public void hudEditor(boolean open) { Minecraft.getMinecraft().displayGuiScreen(open ? new DraggableHudScreen189(null) : null); }

        @Override public void screenshot(String name) { CoreProbe.screenshot(Minecraft.getMinecraft(), name); }
    };
}
