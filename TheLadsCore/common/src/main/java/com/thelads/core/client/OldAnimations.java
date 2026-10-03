package com.thelads.core.client;

/**
 * Minecraft 1.7.10 item animations as transform recipes. Every adapter replays the same ops (1.8.9 through
 * GlStateManager, 1.21.x and 26.x through PoseStack), so each constant lives only here. Written from 1.7.10's
 * observable ItemRenderer, RenderPlayer, RenderItem and EntityPlayerSP behaviour; no mod source.
 *
 * <p>Each op post-multiplies the current matrix, as a GL call does. Recipes are authored for the right hand;
 * {@code side} -1 mirrors them for the left hand the way vanilla 1.9+ does (x translation and Y/Z turns negated).
 * Frames: <b>arm origin</b> is the matrix at the push in 1.8.9's {@code renderItemInFirstPerson} and in modern
 * {@code renderArmWithItem}; <b>legacy hand</b> is the arm origin after the 45° turn and 0.4 scale; <b>item mesh</b>
 * is the centred item model, drawn with no display transform (1.8.9 {@code TransformType.NONE}, modern
 * {@code ItemDisplayContext.NONE}).
 *
 * <p>Size: 1.7.10's icon (a unit quad at 0.4 x 1.5) is on screen about 1.8.9's own item (0.4 x 1.7 display x 0.5 RenderItem x 2
 * RenderItem.preTransform for flat models), so the recipes keep 1.7.10's full size; an adapter must not scale the mesh twice.
 */
public final class OldAnimations {
    /** 1.7.10 ModelBiped: a blocking arm only pitches (x * 0.5 - 3π/10); 1.8 and later also turn it 30° inward. */
    public static final float BLOCKING_ARM_YAW = 0;
    private static final float PI = (float) Math.PI;

    private OldAnimations() {}

    /** Receives recipe ops in order. */
    public interface Sink {
        void translate(float x, float y, float z);

        /** Degrees about a unit axis; recipes only use exactly (1,0,0), (0,1,0) or (0,0,1). */
        void rotate(float degrees, float x, float y, float z);

        void scale(float x, float y, float z);
    }

    /** What the hand does with its item: 1.7.10's EnumAction while the use count is above 0, else NONE. */
    public enum Use { NONE, EAT_DRINK, BLOCK, BOW }

    /** Flat item kinds 1.7.10 renders differently. Blocks and special models keep vanilla rendering. */
    public enum Held {
        /** isFull3D: swords and tools. */
        TOOL,
        /** isFull3D and rotated around: fishing rod, carrot on a stick. */
        ROD,
        BOW,
        /** Every other flat item. */
        ITEM
    }

    /**
     * 1.7.10 first-person hand, arm origin to legacy hand frame: renderItemInFirstPerson up to the item draw.
     *
     * @param equip     0 raised to 1 lowered (1.8.9 {@code 1 - equippedProgress}, modern {@code equippedProgress})
     * @param swing     attack swing 0..1 to show; while using, pass what the module's {@code swingShown} returns
     * @param remaining use ticks left as vanilla counts them; with {@code partial} and {@code maxUse} only read for
     *                  EAT_DRINK and BOW
     */
    public static void hand(Sink out, int side, float equip, float swing, Use use, int remaining, float partial, int maxUse) {
        float left = remaining - partial + 1;                         // 1.7.10: getItemInUseCount() - partialTicks + 1
        int max = Math.max(1, maxUse);
        if (use == Use.EAT_DRINK) eat(out, side, left, max);
        else if (use == Use.NONE) swingOffset(out, side, swing);
        translate(out, side, 0.56f, -0.52f - equip * 0.6f, -0.72f);   // 1.7.10: (0.7, -0.65, -0.9) x 0.8, 0.6 lower while equipping
        rotate(out, side, 45, 0, 1, 0);                               // 1.7.10: held items turned 45° about Y
        float quick = sin(swing * swing * PI), arc = sin(sqrt(swing) * PI);
        rotate(out, side, quick * -20, 0, 1, 0);                      // 1.7.10 swing turn: -20° Y, -20° Z, -80° X,
        rotate(out, side, arc * -20, 0, 0, 1);                        // kept while using an item: that is blockhitting
        rotate(out, side, arc * -80, 1, 0, 0);
        out.scale(0.4f, 0.4f, 0.4f);                                  // 1.7.10: held items at 0.4 scale
        if (use == Use.BLOCK) block(out, side);
        else if (use == Use.BOW) bow(out, side, max - left);
    }

    /** Modern first person, after vanilla applyItemArmTransform and swingArm (or LegacySwing): to the legacy hand frame. */
    public static void fromModernHand(Sink out, int side) {
        rotate(out, side, 45, 0, 1, 0);                               // modern swingArm ends 45° back about Y; 1.7 did not
        out.scale(0.4f, 0.4f, 0.4f);                                  // modern item models fold in 1.7's 0.4 hand scale
    }

    /**
     * Which swing a first-person hand shows when both modules are on: Legacy Swing's for every hand outside a 1.7 use pose
     * (attacking, placing blocks, idle items, whether 1.7 or vanilla places the item); 1.7's while the hand blocks, draws a
     * bow or eats, where 1.7's own swing is the point (blockhitting).
     */
    public static boolean legacySwingShown(boolean legacySwingOn, Use use) {
        return legacySwingOn && use == Use.NONE;
    }

    /**
     * Legacy Swing (Legacy Console Edition's swing, LegacySwing module) in modern {@code swingArm}'s place, right after the arm
     * transform: replaces vanilla's swing translation and turn, and ends 45° back about Y as vanilla's does. Progress^4, so the
     * item winds up slowly and snaps through, swung further across (0.55) and lifted on the way.
     */
    public static void legacySwing(Sink out, int side, float progress) {
        float t = progress * progress * progress * progress;
        float arc = sin(sqrt(t) * PI), lift = sin(sqrt(t) * PI * 2), depth = sin(t * PI);
        translate(out, side, -arc * 0.55f, lift * 0.25f, -depth * 0.2f);
        rotate(out, side, 45 - arc * 20, 0, 1, 0);
        rotate(out, side, arc * -20, 0, 0, 1);
        rotate(out, side, arc * -80, 1, 0, 0);
        rotate(out, side, -45, 0, 1, 0);
    }

    /** {@link #hand} for an idle hand while Legacy Swing is on: arm origin to legacy hand frame with Legacy Swing's motion. */
    public static void legacySwingHand(Sink out, int side, float equip, float swing) {
        translate(out, side, 0.56f, -0.52f - equip * 0.6f, -0.72f);   // vanilla's (and 1.7's) hand place
        legacySwing(out, side, swing);
        fromModernHand(out, side);
    }

    /** 1.7.10 renderItem for a flat item in first person: legacy hand frame to item mesh. */
    public static void item(Sink out, int side, boolean rod) {
        if (rod) rotate(out, side, 180, 0, 1, 0);                     // 1.7.10 shouldRotateAroundWhenRendering: rods turned round
        icon(out, side);
    }

    /** 1.7.10 and 1.8.9: arm pivot to the hand, after the arm's postRender (modern translateToHand). */
    public static void thirdPersonHand(Sink out, int side) {
        translate(out, side, -0.0625f, 0.4375f, 0.0625f);             // 1.7.10 RenderPlayer: (-1, 7, 1) pixels
    }

    /** 1.7.10 RenderPlayer held item: hand to item mesh. {@code blocking}: a sword block (TOOL) in use. */
    public static void thirdPersonItem(Sink out, int side, Held held, boolean blocking) {
        switch (held) {
            case BOW:
                translate(out, side, 0, 0.125f, 0.3125f);             // 1.7.10 bow: (0, 2, 5) pixels, -20° Y,
                rotate(out, side, -20, 0, 1, 0);                      // 0.625 flipped in Y, -100° X, 45° Y
                out.scale(0.625f, -0.625f, 0.625f);
                rotate(out, side, -100, 1, 0, 0);
                rotate(out, side, 45, 0, 1, 0);
                break;
            case TOOL:
            case ROD:
                if (held == Held.ROD) {
                    rotate(out, side, 180, 0, 0, 1);                  // 1.7.10 rods: turned 180° about Z, 2 pixels down
                    translate(out, side, 0, -0.125f, 0);
                }
                if (blocking) {
                    translate(out, side, 0.05f, 0, -0.1f);            // 1.7.10 sword block: (0.05, 0, -0.1), -50° Y, -10° X, -60° Z
                    rotate(out, side, -50, 0, 1, 0);
                    rotate(out, side, -10, 1, 0, 0);
                    rotate(out, side, -60, 0, 0, 1);
                }
                translate(out, side, 0, 0.1875f, 0);                  // 1.7.10 tools: 3 pixels up, 0.625 flipped in Y, -100° X, 45° Y
                out.scale(0.625f, -0.625f, 0.625f);
                rotate(out, side, -100, 1, 0, 0);
                rotate(out, side, 45, 0, 1, 0);
                break;
            default:
                translate(out, side, 0.25f, 0.1875f, -0.1875f);       // 1.7.10 other items: (4, 3, -3) pixels, 0.375, 60° Z, -90° X, 20° Z
                out.scale(0.375f, 0.375f, 0.375f);
                rotate(out, side, 60, 0, 0, 1);
                rotate(out, side, -90, 1, 0, 0);
                rotate(out, side, 20, 0, 0, 1);
        }
        icon(out, side);
        // The flipped scale mirrors the icon. Mirroring the flat mesh through its own mid-plane draws the same surface
        // and makes the transform proper again, so back-face culling on modern renderers keeps the right faces.
        if (held != Held.ITEM) out.scale(1, 1, -1);
    }

    /**
     * 1.7.10 Fast-graphics dropped item: entity origin to item mesh, a flat icon turned about Y to face the camera.
     *
     * @param age       ticks alive plus the partial tick
     * @param bobOffset the entity's hover offset (1.8.9 hoverStart, modern bobOffs)
     * @param cameraYaw camera yaw in degrees (1.8.9 playerViewY, modern Camera.yRot())
     */
    public static void droppedItem(Sink out, float age, float bobOffset, float cameraYaw) {
        out.translate(0, sin(age / 10 + bobOffset) * 0.1f + 0.1f, 0);  // 1.7.10 hover: 0.1 ± 0.1 (unchanged since)
        out.scale(0.5f, 0.5f, 0.5f);                                  // 1.7.10: dropped icons at half size
        out.rotate(180 - cameraYaw, 0, 1, 0);                         // 1.7.10: 180 - playerViewY, facing the camera
        out.translate(0, 0.25f, 0);                                   // 1.7.10 quad: x -0.5..0.5, y -0.25..0.75
    }

    /**
     * 1.7.10 sneak camera, one tick: the eye reaches a lower target at once and closes 60% of the gap to a higher one
     * (EntityPlayerSP holds ySize at 0.2 while sneaking; Entity.moveEntity multiplies it by 0.4 each tick).
     * Interpolate the previous and current results with the partial tick.
     */
    public static float sneakEyeHeight(float current, float target) {
        return target <= current ? target : target - (target - current) * 0.4f;
    }

    private static void eat(Sink out, int side, float left, int max) {
        float done = 1 - left / max;                                  // 1.7.10: share of the use already done
        out.translate(0, done > 0.2f ? Math.abs(cos(left / 4 * PI) * 0.1f) : 0, 0);  // 1.7.10: 0.1 jiggle per 4 ticks after 20%
        float lift = 1 - (float) Math.pow(left / max, 27);            // 1.7.10: 1 - (1 - done)^27, cubed three times
        translate(out, side, lift * 0.6f, lift * -0.5f, 0);           // 1.7.10: 0.6 right and 0.5 down,
        rotate(out, side, lift * 90, 0, 1, 0);                        // 90° Y, 10° X, 30° Z at full lift
        rotate(out, side, lift * 10, 1, 0, 0);
        rotate(out, side, lift * 30, 0, 0, 1);
    }

    private static void swingOffset(Sink out, int side, float swing) {
        float root = sqrt(swing);                                     // 1.7.10: -0.4 sin(√s π) across, 0.2 sin(2√s π) up, -0.2 sin(s π) forward
        translate(out, side, -0.4f * sin(root * PI), 0.2f * sin(root * PI * 2), -0.2f * sin(swing * PI));
    }

    private static void block(Sink out, int side) {
        translate(out, side, -0.5f, 0.2f, 0);                         // 1.7.10 sword block: (-0.5, 0.2, 0), 30° Y, -80° X, 60° Y
        rotate(out, side, 30, 0, 1, 0);
        rotate(out, side, -80, 1, 0, 0);
        rotate(out, side, 60, 0, 1, 0);
    }

    private static void bow(Sink out, int side, float drawn) {
        rotate(out, side, -18, 0, 0, 1);                              // 1.7.10 bow: -18° Z, -12° Y, -8° X, then (-0.9, 0.2, 0)
        rotate(out, side, -12, 0, 1, 0);
        rotate(out, side, -8, 1, 0, 0);
        translate(out, side, -0.9f, 0.2f, 0);
        float t = drawn / 20, pull = Math.min(1, (t * t + t * 2) / 3); // 1.7.10 ItemBow pull: (t² + 2t) / 3, t = ticks / 20
        if (pull > 0.1f) out.translate(0, sin((drawn - 0.1f) * 1.3f) * 0.01f * (pull - 0.1f), 0);  // 1.7.10: drawn-bow shake
        out.translate(0, 0, pull * 0.1f);                             // 1.7.10: pulled back 0.1
        rotate(out, side, -335, 0, 0, 1);                             // 1.7.10: stretched 20% along the icon's own frame,
        rotate(out, side, -50, 0, 1, 0);                              // about its y = 0.5
        out.translate(0, 0.5f, 0);
        out.scale(1, 1, 1 + pull * 0.2f);
        out.translate(0, -0.5f, 0);
        rotate(out, side, 50, 0, 1, 0);
        rotate(out, side, 335, 0, 0, 1);
    }

    private static void icon(Sink out, int side) {
        translate(out, side, 0, -0.3f, 0);                            // 1.7.10 renderItem: 0.3 down, 1.5 scale, 50° Y, 335° Z,
        out.scale(1.5f, 1.5f, 1.5f);                                  // then (-15, -1, 0) sixteenths to the icon corner
        rotate(out, side, 50, 0, 1, 0);
        rotate(out, side, 335, 0, 0, 1);
        translate(out, side, -0.9375f, -0.0625f, 0);
        // 1.7.10 draws the icon over [0,1]² at z -1/16..0, mirrored in x; the centred 1.8+ item mesh lands there.
        translate(out, side, 0.5f, 0.5f, -0.03125f);
        rotate(out, side, 180, 0, 1, 0);
    }

    private static void translate(Sink out, int side, float x, float y, float z) {
        out.translate(side * x, y, z);
    }

    private static void rotate(Sink out, int side, float degrees, float x, float y, float z) {
        out.rotate(x != 0 ? degrees : side * degrees, x, y, z);
    }

    private static float sin(float radians) { return (float) Math.sin(radians); }

    private static float cos(float radians) { return (float) Math.cos(radians); }

    private static float sqrt(float value) { return (float) Math.sqrt(value); }
}
