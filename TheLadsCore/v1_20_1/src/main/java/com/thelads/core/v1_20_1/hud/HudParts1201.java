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
    /** What a begun part must undo: its pose (the stack and how deep it was before this part pushed), the Auto Hide scope outside it and
     *  the pose's translation right after this part's lift (x, y, z), to tell a translate another mod made inside the part. */
    public record Scope(PoseStack pose, int depth, float outerOpacity, boolean faded, float x, float y, float z) {
        public static final Scope NOOP = new Scope(null, -1, 1, false, 0, 0, 0);
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
    static int depth(PoseStack pose) {
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
        var lifted = pose.last().pose();
        return new Scope(pose, depth, outer, faded, lifted.m30(), lifted.m31(), lifted.m32());
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
        // A translate another mod leaves inside the part is meant for the overlays after it too: Quark's Hotbar Changer lifts the hearts,
        // armour, food and XP bar over its open rows with one translate at player_health and takes it back at potion_icons. Popped with
        // this part, the armour, food and XP bar stayed on the rows and the take-back moved everything after it (Mahou's mana text, the
        // inventory and its dark background) down. So it is carried over the pop, also past a push left inside the part. On Zero SMP a mod
        // pushes in player_health and pops in armor_level, i.e. this part's own push: then nothing is left to pop or carry.
        // ponytail: translation only, what such mods do.
        PoseStack pose = scope.pose();
        boolean ours = scope.depth() < 1 || depth(pose) > scope.depth();
        if (!ours) return;
        var now = pose.last().pose();
        float dx = now.m30() - scope.x(), dy = now.m31() - scope.y(), dz = now.m32() - scope.z();
        if (scope.depth() < 1) {
            pose.popPose();
        } else {
            for (int guard = 0; depth(pose) > scope.depth() && guard < 64; guard++) pose.popPose();
        }
        if (dx != 0 || dy != 0 || dz != 0) pose.last().pose().translateLocal(dx, dy, dz);
    }
}
