package com.thelads.core.v26_2.feature;

import com.thelads.core.client.ladsapi.GuiViewLock;
import com.thelads.core.client.ladsapi.ServerSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The cursor of a server GUI page whose view is locked (HUD "Lock View"). The page's shader cursor reads the camera rotation, which stands still now,
 * so this draws the same arrow (the pack's lads:ladsgui glyph U+E001) at the spot the shader would: gain times the player's own yaw/pitch, clamped to
 * the 1920x1080 canvas, which is fitted inside the window as the shader fits it.
 */
public final class GuiCursor26 {
    private static final Component ARROW = Component.literal("\uE001").withStyle(Style.EMPTY.withFont(new FontDescription.Resource(Identifier.parse("lads:ladsgui"))));

    private GuiCursor26() {}

    public static void extract(GuiGraphicsExtractor g) {
        if (!ServerSettings.viewLocked()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        double gui = mc.getWindow().getGuiScale();
        double s = GuiViewLock.scale(w, h);
        double[] c = GuiViewLock.cursor(mc.player.getYRot(), mc.player.getXRot(), ServerSettings.cursorGain());
        double size = ServerSettings.cursorSize();
        double x = w / 2.0 + (c[0] - ServerSettings.clickX() * size) * s;
        double y = h / 2.0 + (c[1] - ServerSettings.clickY() * size) * s;
        g.pose().pushMatrix();
        g.pose().translate((float) (x / gui), (float) (y / gui));
        float k = (float) (size * s / (8.0 * gui)); // the glyph's cell is 8 gui px
        g.pose().scale(k, k);
        g.text(mc.font, ARROW, 0, 0, 0xFFFFFFFF, false);
        g.pose().popMatrix();
    }
}
