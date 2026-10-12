package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.hud.ActionBarQa;
import com.thelads.core.v1_20_1.gui.DraggableHudScreen1201;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;

/**
 * QA only: the script verb {@code actionbar} runs the common Action Bar routine (ActionBarQa) with 1.20.1's action bar packet, record,
 * /title and screenshots (screenshots/ab-*.png); a failed check fails the step. Plain Fabric and Zero SMP.
 */
final class ActionBarQa1201 {
    private static ActionBarQa qa;

    private ActionBarQa1201() {}

    static void register() {
        if (Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null)
            QaProbe1201.registerStep("actionbar", args -> {
                if (qa == null) qa = new ActionBarQa();
                try {
                    if (!qa.tick(GAME)) return false;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                if (!qa.failures().isEmpty()) throw new IllegalStateException("action bar QA: " + qa.failures());
                return true;
            });
    }

    private static final ActionBarQa.Game GAME = new ActionBarQa.Game() {
        @Override public void actionBar(String text) {
            Minecraft.getInstance().getConnection().setActionBarText(new ClientboundSetActionBarTextPacket(Component.literal(text)));
        }

        @Override public void nowPlaying(String song) { Minecraft.getInstance().gui.setNowPlaying(Component.literal(song)); }

        @Override public boolean titleActionBar(String json) {
            Minecraft.getInstance().getConnection().sendCommand("title @s actionbar " + json);
            return true;
        }

        @Override public void hudEditor(boolean open) { Minecraft.getInstance().setScreen(open ? new DraggableHudScreen1201(null) : null); }

        @Override public void screenshot(String name) throws Exception { QaProbe1201.shot(Minecraft.getInstance(), name); }
    };
}
