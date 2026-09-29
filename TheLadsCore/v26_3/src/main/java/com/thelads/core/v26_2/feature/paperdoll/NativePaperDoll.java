/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * See assets/theladscore/licenses/PaperDoll-MPL-2.0.txt in the distributed Core JAR.
 * Adapted from Fuzss Paper Doll 26.2.3, commit 5968f6f523a2ddc46e5890bd47dc6a5d9bf48b29.
 */
package com.thelads.core.v26_2.feature.paperdoll;

import com.thelads.core.client.paperdoll.PaperDollMotion;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/** Extracts the real local player, with no synthetic model or temporary mutation of player fields. */
public final class NativePaperDoll {
    private static final PaperDollMotion MOTION = new PaperDollMotion();
    private static boolean active;
    private static Player lastPlayer;
    private NativePaperDoll() {}
    public static boolean active() { return active; }

    public static void register() {
        if (active || FabricLoader.getInstance().isModLoaded("paperdoll")) return;
        active = true;
        ModuleSupport.registerBuiltIn("Paperdoll");
        ClientTickEvents.END_CLIENT_TICK.register(NativePaperDoll::tick);
    }

    static boolean option(String name, boolean fallback) { return NativeQualityOfLife.bool("Paperdoll", name, fallback); }
    static float number(String name, float fallback) { return (float) NativeQualityOfLife.number("Paperdoll", name, fallback); }
    static int choice(String name, int fallback) { return NativeQualityOfLife.choice("Paperdoll", name, fallback); }

    private static void tick(Minecraft mc) {
        if (mc.player != lastPlayer) { MOTION.reset(); lastPlayer = mc.player; }
        if (mc.player == null) return;
        int duration = (int) number("Display Time (ticks)", 40);
        MOTION.tick(NativeQualityOfLife.enabled("Paperdoll"), mc.isPaused(), option("Always Display", true), duration,
                performingAction(mc.player, MOTION.recentlyRiding(duration)), mc.player.isPassenger(),
                mc.player.yHeadRot - mc.player.yHeadRotO, number("Maximum Yaw", 30));
        NativePaperDollProbe.tick();
    }

    static boolean performingAction(Player player, boolean recentlyRiding) {
        return option("Sprinting", true) && player.canSpawnSprintParticle()
                || option("Swimming", true) && !player.isVisuallyCrawling() && player.isVisuallySwimming() && player.getSwimAmount(1) > 0
                || option("Crawling", true) && player.isVisuallyCrawling()
                || option("Crouching", true) && !recentlyRiding && player.isCrouching()
                || option("Creative Flying", true) && player.getAbilities().flying
                || option("Elytra Gliding", true) && player.isFallFlying()
                || option("Riding", false) && player.isPassenger()
                || option("Spin Attacking", false) && player.isAutoSpinAttack()
                || option("Using Items", false) && player.isUsingItem();
    }

    static boolean visible(Minecraft mc, boolean editor) {
        if (!active || mc.player == null) return false;
        if (editor) return true;
        return NativeQualityOfLife.enabled("Paperdoll") && !mc.gui.hud.isHidden() && !mc.player.isInvisible()
                && !mc.player.isSpectator() && (mc.options.getCameraType().isFirstPerson()
                ? option("Show in First Person", false) : option("Show in Third Person", true))
                && MOTION.visible(option("Always Display", true), (int) number("Display Time (ticks)", 40));
    }

    public static void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean editor) {
        var mc = Minecraft.getInstance();
        if (!visible(mc, editor)) return;
        // entity() queues a picture-in-picture state and ignores the GUI pose. Apply the
        // HUD editor's translation/scale ourselves, preserving its actual viewport bounds.
        var topLeft = graphics.pose().transformPosition(x, y, new Vector2f());
        var bottomRight = graphics.pose().transformPosition(x + width, y + height, new Vector2f());
        int x1 = Math.round(topLeft.x), y1 = Math.round(topLeft.y);
        int x2 = Math.round(bottomRight.x), y2 = Math.round(bottomRight.y);
        if (x2 <= x1 || y2 <= y1) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean right = choice("Anchor", 0) != 0 ? (choice("Anchor", 0) - 1) % 3 == 2 : x1 + (x2 - x1) / 2 > graphics.guiWidth() / 2;
        var state = extractState(partial, right);
        var camera = new Quaternionf().rotateX((float) Math.toRadians(15));
        var rotation = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
        float pixelScale = number("Model Scale", 4) * 5 * Math.min((x2 - x1) / (float) width, (y2 - y1) / (float) height);
        graphics.entity(state, pixelScale, new Vector3f(0, state.boundingBoxHeight / 2, 0), rotation, camera, x1, y1, x2, y2);
    }

    static LivingEntityRenderState extractState(float partial, boolean right) {
        var mc = Minecraft.getInstance();
        var renderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
        var state = (LivingEntityRenderState) renderer.createRenderState(mc.player, partial);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        state.bodyRot = 180 + number("Default Rotation", 15) * (right ? 1 : -1);
        int axis = choice("Head Movement", 0);
        boolean pitch = axis == 0 || axis == 3;
        float maximumPitch = number("Maximum Pitch", 30);
        state.xRot = !pitch || state.pose == Pose.FALL_FLYING ? 7.5f : Math.clamp(state.xRot, -maximumPitch, maximumPitch);
        state.yRot = axis == 0 || axis == 1 ? MOTION.yaw(partial) : 0;
        float scale = Float.isFinite(state.scale) && state.scale > 0 ? state.scale : 1;
        state.boundingBoxWidth /= scale;
        state.boundingBoxHeight /= scale;
        state.scale = 1;
        ((PaperDollRenderState) state).ladsSetPaperDollAlpha(Math.round(number("Model Opacity", 100) * 2.55f));
        return state;
    }
}
