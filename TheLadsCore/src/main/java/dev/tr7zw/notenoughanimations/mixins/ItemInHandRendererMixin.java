/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  net.minecraft.client.renderer.ItemInHandRenderer
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.world.entity.HumanoidArm
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.tr7zw.notenoughanimations.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tr7zw.notenoughanimations.NEAnimationsLoader;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ItemInHandRenderer.class})
public class ItemInHandRendererMixin {
    @Inject(method={"renderPlayerArm"}, at={@At(value="HEAD")})
    private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f, float g, HumanoidArm humanoidArm, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        NEAnimationsLoader.INSTANCE.playerTransformer.renderingFirstPersonArm(true);
    }

    @Inject(method={"renderPlayerArm"}, at={@At(value="RETURN")})
    private void renderPlayerArmEnd(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f, float g, HumanoidArm humanoidArm, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        NEAnimationsLoader.INSTANCE.playerTransformer.renderingFirstPersonArm(false);
    }
}

