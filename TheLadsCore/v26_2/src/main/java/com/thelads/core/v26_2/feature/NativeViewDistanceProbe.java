package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import java.lang.reflect.Method;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Isolated QA of actual transformed camera and block-entity render paths. */
final class NativeViewDistanceProbe {
    private NativeViewDistanceProbe() {}

    static int run() throws ReflectiveOperationException {
        Minecraft minecraft = Minecraft.getInstance();
        Module vertical = NativeQualityOfLife.module("VerticalBobbing");
        Module far = NativeQualityOfLife.module("FarBlockEntities");
        SliderOption distance = (SliderOption) far.getOption("Distance");
        boolean verticalBefore = vertical.isEnabled(), farBefore = far.isEnabled();
        long verticalModifiedBefore = vertical.getLastModified(), farModifiedBefore = far.getLastModified();
        long verticalModified = vertical.getLastModified(), farModified = far.getLastModified();
        boolean bobBefore = minecraft.options.bobView().get();
        double screenBefore = minecraft.options.screenEffectScale().get();
        double fovBefore = minecraft.options.fovEffectScale().get();
        double distanceBefore = distance.getValue();
        CameraType cameraBefore = minecraft.options.getCameraType();
        int passed = 0;
        try {
            var camera = new CameraRenderState();
            require(camera instanceof VerticalBobState, "camera render snapshot implements vertical motion state"); passed++;
            camera.entityRenderState.isPlayer = true;
            VerticalBobState bob = (VerticalBobState) camera;
            Method transform = GameRenderer.class.getDeclaredMethod("bobView", CameraRenderState.class, PoseStack.class);
            transform.setAccessible(true);
            PoseStack neutral = new PoseStack();
            transform.invoke(minecraft.gameRenderer, camera, neutral);
            require(Math.abs(neutral.last().pose().m31()) < .000001, "zero vertical offset preserves vanilla pose"); passed++;
            bob.lads$verticalBob(.025f);
            PoseStack world = new PoseStack();
            transform.invoke(minecraft.gameRenderer, camera, world);
            require(Math.abs(world.last().pose().m12() - Math.sin(Math.toRadians(.025))) < .000001 && Math.abs(world.last().pose().m31()) < .000001, "real game-renderer pose contains legacy pitch without vertical translation"); passed++;
            PoseStack hand = new PoseStack();
            transform.invoke(minecraft.gameRenderer, camera, hand);
            require(world.last().pose().equals(hand.last().pose()), "world and held-item passes consume the same motion snapshot"); passed++;

            vertical.setEnabled(false);
            minecraft.gameRenderer.mainCamera().extractRenderState(camera, 0);
            require(bob.lads$verticalBob() == 0, "disabled module extracts no camera displacement"); passed++;
            vertical.setEnabled(true);
            minecraft.options.bobView().set(false);
            minecraft.gameRenderer.mainCamera().extractRenderState(camera, 0);
            require(bob.lads$verticalBob() == 0, "vanilla view-bobbing off suppresses extraction"); passed++;
            minecraft.options.bobView().set(true);
            minecraft.options.screenEffectScale().set(0d);
            minecraft.gameRenderer.mainCamera().extractRenderState(camera, 0);
            require(bob.lads$verticalBob() == 0, "zero screen-effect strength suppresses extraction"); passed++;
            minecraft.options.screenEffectScale().set(1d);
            minecraft.options.fovEffectScale().set(0d);
            minecraft.gameRenderer.mainCamera().extractRenderState(camera, 0);
            require(bob.lads$verticalBob() == 0, "zero FOV-effect strength suppresses extraction"); passed++;
            minecraft.options.fovEffectScale().set(1d);
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            minecraft.gameRenderer.mainCamera().extractRenderState(camera, 0);
            require(bob.lads$verticalBob() == 0, "third-person camera does not receive first-person bob"); passed++;

            ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            var renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(chest);
            Vec3 center = Vec3.atCenterOf(BlockPos.ZERO);
            far.setEnabled(false);
            require(!renderer.shouldRender(chest, center.add(100, 0, 0)), "disabled module keeps vanilla chest distance"); passed++;
            far.setEnabled(true);
            distance.setValue(128);
            require(renderer.shouldRender(chest, center.add(100, 0, 0)), "actual chest renderer accepts extended distance"); passed++;
            distance.setValue(64);
            require(!renderer.shouldRender(chest, center.add(100, 0, 0)), "distance setting applies immediately"); passed++;
            distance.setValue(256);
            require(renderer.shouldRender(chest, center.add(255, 0, 0)), "maximum configured distance"); passed++;
            require(!renderer.shouldRender(chest, center.add(257, 0, 0)), "entities beyond configured distance stay culled"); passed++;
            require(chest.getLevel() == null, "distance checks do not require attaching or loading a chunk"); passed++;
            LoggerFactory.getLogger("TheLadsCore").info("Lads camera and block-distance probe END: {} passed, 0 failed", passed);
            return passed;
        } finally {
            vertical.setEnabled(verticalBefore);
            far.setEnabled(farBefore);
            vertical.setLastModified(verticalModified);
            far.setLastModified(farModified);
            distance.setValue(distanceBefore);
            minecraft.options.bobView().set(bobBefore);
            minecraft.options.screenEffectScale().set(screenBefore);
            minecraft.options.fovEffectScale().set(fovBefore);
            minecraft.options.setCameraType(cameraBefore);
            vertical.setLastModified(verticalModifiedBefore);
            far.setLastModified(farModifiedBefore);
        }
    }

    private static void require(boolean value, String name) {
        if (!value) throw new IllegalStateException(name);
    }
}
