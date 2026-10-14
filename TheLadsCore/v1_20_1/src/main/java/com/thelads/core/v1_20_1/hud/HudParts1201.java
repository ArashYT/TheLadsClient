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
    /** What a begun part must undo: its pose (the stack and how deep it was before this part pushed) and the Auto Hide scope outside it. */
    public record Scope(PoseStack pose, int depth, float outerOpacity, boolean faded) {
        public static final Scope NOOP = new Scope(null, -1, 1, false);
    }

    private static final java.lang.reflect.Field STACK = stackField();

    private HudParts1201() {}

    /** PoseStack's one Deque field, found by type so it needs no mapped name (plain Fabric and Forge through Connector name it differently). */
    private static java.lang.reflect.Field stackField() {
        for (var field : PoseStack.class.getDeclaredFields()) {
            if (java.util.Deque.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return field;
                } catch (RuntimeException unavailable) {
                    return null;
                }
            }
        }
        return null;
    }

    /** How many entries the pose stack has, or -1 where it cannot be read (then a part ends with the one popPose it always had). */
    private static int depth(PoseStack pose) {
        try {
            return STACK == null ? -1 : ((java.util.Deque<?>) STACK.get(pose)).size();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return -1;
        }
    }

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
        int lift = Raised1201.hotbar();
        boolean faded = opacity < 1;
        if (lift == 0 && !faded) return Scope.NOOP;
        PoseStack pose = graphics.pose();
        int depth = depth(pose);
        pose.pushPose();
        if (lift != 0) pose.translate(0, -lift, 0);
        float outer = NativeAutohide1201.scopeOpacity;
        if (faded) {
            NativeAutohide1201.scopeOpacity = opacity;
            graphics.setColor(1, 1, 1, opacity);
        }
        return new Scope(pose, depth, outer, faded);
    }

    public static void end(GuiGraphics graphics, Scope scope) {
        if (scope == null || scope == Scope.NOOP) return;
        if (scope.faded()) {
            graphics.setColor(1, 1, 1, 1);
            NativeAutohide1201.scopeOpacity = scope.outerOpacity();
        }
        // Another mod's overlay (under Forge) can leave a push of its own inside the part: a single popPose would then remove that push
        // and keep this part's lift on the pose for everything drawn after it, every screen of the frame included. Pop back to the depth
        // the stack had before this part, never below it.
        PoseStack pose = scope.pose();
        if (scope.depth() < 1) {
            pose.popPose();
            return;
        }
        for (int guard = 0; depth(pose) > scope.depth() && guard < 64; guard++) pose.popPose();
    }
}
