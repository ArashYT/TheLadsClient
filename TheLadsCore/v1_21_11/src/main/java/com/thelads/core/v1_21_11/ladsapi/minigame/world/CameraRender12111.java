package com.thelads.core.v1_21_11.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/** Camera shots: the Camera mixin asks for the pose once per frame; null (no shot) leaves the vanilla view untouched. */
public final class CameraRender12111 {
    private static CameraState.Pose pose;

    private CameraRender12111() {}

    /** The shot's pose for this frame from the vanilla view, or null. */
    public static CameraState.Pose evaluate(Camera camera, long now) {
        if (!Minigame.camera.active(now)) return pose = null;
        double fov = Minecraft.getInstance().options.fov().get();
        CameraState.Own own = new CameraState.Own(camera.position().x, camera.position().y, camera.position().z,
            camera.yRot(), camera.xRot(), fov);
        return pose = Minigame.camera.poseAt(now, own, Hooks.entities);
    }

    /** The pose evaluated this frame, or null. */
    public static CameraState.Pose current() {
        return pose;
    }
}
