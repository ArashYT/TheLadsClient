package com.thelads.core.v1_20_1.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/** Camera shots: the Camera mixin asks for the pose once per frame; null (no shot) leaves the vanilla view untouched. */
public final class CameraRender1201 {
    private static CameraState.Pose pose;
    /** The field of view the last world frame was drawn with (degrees), for projecting waypoint markers. */
    public static double fov = 70;

    private CameraRender1201() {}

    /** The shot's pose for this frame from the vanilla view, or null. */
    public static CameraState.Pose evaluate(Camera camera, long now) {
        if (!Minigame.camera.active(now)) return pose = null;
        double vanillaFov = Minecraft.getInstance().options.fov().get();
        CameraState.Own own = new CameraState.Own(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z,
            camera.getYRot(), camera.getXRot(), vanillaFov);
        return pose = Minigame.camera.poseAt(now, own, Hooks.entities);
    }

    /** The pose evaluated this frame, or null. */
    public static CameraState.Pose current() {
        return pose;
    }

    /** Leaving the world: no shot carries over. */
    public static void clear() {
        pose = null;
    }
}
