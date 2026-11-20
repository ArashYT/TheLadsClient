package com.thelads.core.v1_8_9.ladsapi.minigame.world;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.modules.FreelookModule;
import com.thelads.core.v1_8_9.ladsapi.minigame.world.mixin.MgMinecraftAccessor189;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Camera shots. Only the render view moves: for the length of one renderWorldPass the render view entity is a dummy player standing
 * where the shot puts the camera (and first-person view is forced, FOV comes from the shot). getMouseOver runs before renderWorldPass,
 * so picks, attacks and every packet still use the real player, and the real player is left alone (it is drawn like any other
 * entity while the view is the dummy). Freelook wins: while it is detached the shot is not drawn.
 */
public final class CameraRender189 {
    private static CameraState.Pose pose;
    private static Entity savedView;
    private static int savedThirdPerson;
    private static boolean swapped;
    private static EntityOtherPlayerMP dummy;
    private static World dummyWorld;

    private CameraRender189() {}

    /** The shot's pose for this frame, or null; valid inside renderWorldPass only. */
    public static CameraState.Pose current() {
        return pose;
    }

    /** renderWorldPass HEAD. */
    public static void begin(Minecraft mc, float partial) {
        if (swapped) return;
        pose = null;
        long now = Minigame.now();
        if (!Minigame.camera.active(now) || mc.thePlayer == null || mc.theWorld == null || FreelookModule.get().detached()) return;
        Entity p = mc.thePlayer;
        double eye = p.getEyeHeight();
        CameraState.Own own = new CameraState.Own(p.prevPosX + (p.posX - p.prevPosX) * partial,
            p.prevPosY + (p.posY - p.prevPosY) * partial + eye, p.prevPosZ + (p.posZ - p.prevPosZ) * partial,
            p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * partial,
            p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * partial, mc.gameSettings.fovSetting);
        pose = Minigame.camera.poseAt(now, own, Hooks.entities);
        if (pose == null) return;
        if (dummy == null || dummyWorld != mc.theWorld) {
            dummyWorld = mc.theWorld;
            dummy = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(new UUID(0x9057CA3EL, 1L), "LadsCamera"));
        }
        double y = pose.y() - dummy.getEyeHeight();
        dummy.posX = dummy.prevPosX = dummy.lastTickPosX = pose.x();
        dummy.posY = dummy.prevPosY = dummy.lastTickPosY = y;
        dummy.posZ = dummy.prevPosZ = dummy.lastTickPosZ = pose.z();
        dummy.rotationYaw = dummy.prevRotationYaw = (float) pose.yaw();
        dummy.rotationPitch = dummy.prevRotationPitch = (float) pose.pitch();
        MgMinecraftAccessor189 acc = (MgMinecraftAccessor189) mc;
        savedView = acc.ladsMgView();
        savedThirdPerson = mc.gameSettings.thirdPersonView;
        acc.ladsMgSetView(dummy);
        mc.gameSettings.thirdPersonView = 0;
        swapped = true;
    }

    /** renderWorldPass RETURN (and the start of the next frame, should a pass have thrown). */
    public static void end(Minecraft mc) {
        if (!swapped) {
            pose = null;
            return;
        }
        swapped = false;
        ((MgMinecraftAccessor189) mc).ladsMgSetView(savedView);
        mc.gameSettings.thirdPersonView = savedThirdPerson;
        savedView = null;
        pose = null;
    }

    public static void clear() {
        dummy = null;
        dummyWorld = null;
    }
}
