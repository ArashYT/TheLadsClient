package com.thelads.core.v1_20_1.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.feature.NativeAutohide1201;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The parts of vanilla's HUD that Raised lifts and Auto Hide fades: the hotbar group (hotbar, hearts, hunger, armour, air, XP or
 * jump bar, the selected item's name) and, for Raised only, the action bar message. Plain Fabric calls {@link #group} around those
 * parts inside Gui.render (GuiPartsMixin); Forge's own GUI replaces Gui.render, so there {@link ForgeOverlays1201} brackets each
 * overlay with {@link #begin} and {@link #end} instead.
 */
public final class HudParts1201 {
    /** What a begun part must undo: its pose and the Auto Hide scope outside it. */
    public record Scope(PoseStack pose, float outerOpacity, boolean faded) {}

    private HudParts1201() {}

    /** Draws a part of the hotbar group (lifted by Raised, faded by Auto Hide); skipped while Auto Hide has it fully hidden. */
    public static void group(GuiGraphics graphics, Runnable part) {
        Scope scope = begin(graphics, true);
        if (scope == null) return;
        try {
            part.run();
        } finally {
            end(graphics, scope);
        }
    }

    /** Draws a part that Raised lifts and Auto Hide leaves alone (the action bar message). */
    public static void lifted(GuiGraphics graphics, Runnable part) {
        Scope scope = begin(graphics, false);
        try {
            part.run();
        } finally {
            end(graphics, scope);
        }
    }

    /** Starts a part; null (nothing was changed) when it is fully hidden and must not be drawn. */
    public static Scope begin(GuiGraphics graphics, boolean fades) {
        float opacity = fades && Hud1201.bool("Autohide", "Fade vanilla HUD", true) ? NativeAutohide1201.update() : 1;
        if (opacity <= 0) return null;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        int lift = Raised1201.hotbar();
        if (lift != 0) pose.translate(0, -lift, 0);
        float outer = NativeAutohide1201.scopeOpacity;
        boolean faded = opacity < 1;
        if (faded) {
            NativeAutohide1201.scopeOpacity = opacity;
            graphics.setColor(1, 1, 1, opacity);
        }
        return new Scope(pose, outer, faded);
    }

    public static void end(GuiGraphics graphics, Scope scope) {
        if (scope == null) return;
        if (scope.faded()) {
            graphics.setColor(1, 1, 1, 1);
            NativeAutohide1201.scopeOpacity = scope.outerOpacity();
        }
        scope.pose().popPose();
    }
}
