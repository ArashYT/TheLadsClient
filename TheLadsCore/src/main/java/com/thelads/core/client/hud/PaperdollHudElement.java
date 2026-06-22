package com.thelads.core.client.hud;

import com.thelads.core.config.HudSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class PaperdollHudElement extends HudElement {
    private float yRotOffset;
    private float yRotOffsetO;
    private int remainingDisplayTicks = 0;

    public PaperdollHudElement() {
        this.x = 5;
        this.y = 5;
        this.width = 50;
        this.height = 70;
    }

    @Override
    public String getModuleName() {
        return "Paperdoll";
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        boolean showFirstPerson = optBool("Show in First Person", false);
        if (mc.options.getCameraType().isFirstPerson() && !showFirstPerson && !(mc.gui.screen() instanceof com.thelads.core.client.gui.DraggableHudScreen)) {
            return; // Hide in first person if configured
        }

        boolean alwaysDisplay = optBool("Always Display", true);
        if (!alwaysDisplay && remainingDisplayTicks <= 0 && !(mc.gui.screen() instanceof com.thelads.core.client.gui.DraggableHudScreen)) {
            return;
        }
        
        drawBackground(g);

        // Tick logic for rotation
        if (!mc.isPaused()) {
            yRotOffsetO = yRotOffset;
            yRotOffset = Mth.clamp(yRotOffset + (mc.player.yHeadRot - mc.player.yHeadRotO) * 0.5f, -30.0f, 30.0f);
            float nextYRotOffset = yRotOffset - yRotOffset / 10.0f;
            yRotOffset = yRotOffset < 0.0f ? Math.min(0.0f, nextYRotOffset) : Math.max(0.0f, nextYRotOffset);
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int scale = 30; // Could be mapped to an option if wanted
        
        renderEntityInInventory(g, this.x, this.y, this.x + this.width, this.y + this.height, scale, 0.0f, mc.player, partialTick);
    }

    private void renderEntityInInventory(GuiGraphicsExtractor guiGraphics, int x1, int y1, int x2, int y2, int scale, float yOffset, LivingEntity livingEntity, float partialTick) {
        Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI);
        Quaternionf overrideCameraAngle = new Quaternionf().rotateX(0.2617994f);
        rotation.mul(overrideCameraAngle);
        LivingEntityRenderState state = extractRenderState(livingEntity, partialTick);
        state.bodyRot = 180.0f + 15.0f;
        state.xRot = 0.0f; // Yaw only for paperdoll
        state.yRot = Mth.rotLerp(partialTick, yRotOffsetO, yRotOffset);
        state.boundingBoxWidth /= state.scale;
        state.boundingBoxHeight /= state.scale;
        state.scale = 1.0f;
        Vector3f vector3f = new Vector3f(0.0f, state.boundingBoxHeight / 2.0f + yOffset, 0.0f);
        guiGraphics.entity(state, (float)scale, vector3f, rotation, overrideCameraAngle, x1, y1, x2, y2);
    }

    private LivingEntityRenderState extractRenderState(LivingEntity livingEntity, float partialTick) {
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer entityRenderer = entityRenderDispatcher.getRenderer(livingEntity);
        LivingEntityRenderState livingEntityRenderState = (LivingEntityRenderState)entityRenderer.createRenderState(livingEntity, partialTick);
        livingEntityRenderState.lightCoords = 0xF000F0;
        livingEntityRenderState.shadowPieces.clear();
        livingEntityRenderState.outlineColor = 0;
        return livingEntityRenderState;
    }
}
