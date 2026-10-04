package com.thelads.core.v26_2.feature.food.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.thelads.core.v26_2.feature.food.NativeFood;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NativeFood's hooks on the hunger bar and hearts: draw around vanilla's own icons, at the places vanilla drew them. */
@Mixin(Hud.class)
abstract class FoodHudMixin {
    @Inject(method = "extractFood", at = @At("HEAD"))
    private void lads$beforeFood(GuiGraphicsExtractor graphics, Player player, int top, int right, CallbackInfo callback) {
        NativeFood.beforeFood(graphics, player, top, right);
    }

    @WrapOperation(method = "extractFood", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void lads$foodIcon(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original) {
        original.call(graphics, pipeline, sprite, x, y, width, height);
        NativeFood.foodIcon(x, y);
    }

    @Inject(method = "extractFood", at = @At("RETURN"))
    private void lads$afterFood(GuiGraphicsExtractor graphics, Player player, int top, int right, CallbackInfo callback) {
        NativeFood.afterFood(graphics, player, top, right);
    }

    @Inject(method = "extractHearts", at = @At("HEAD"))
    private void lads$beforeHearts(GuiGraphicsExtractor graphics, Player player, int left, int top, int rowHeight, int regenerating, float maxHealth,
                                   int health, int displayHealth, int absorption, boolean blinking, CallbackInfo callback) {
        NativeFood.beforeHearts(top, rowHeight, maxHealth, absorption);
    }

    @WrapOperation(method = "extractHearts", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Hud;extractHeart(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Hud$HeartType;IIZZZ)V"))
    private void lads$heart(Hud hud, GuiGraphicsExtractor graphics, Hud.HeartType type, int x, int y, boolean hardcore, boolean blinking, boolean half,
                            Operation<Void> original) {
        original.call(hud, graphics, type, x, y, hardcore, blinking, half);
        NativeFood.heart(type, x, y);
    }

    @Inject(method = "extractHearts", at = @At("RETURN"))
    private void lads$afterHearts(GuiGraphicsExtractor graphics, Player player, int left, int top, int rowHeight, int regenerating, float maxHealth,
                                  int health, int displayHealth, int absorption, boolean blinking, CallbackInfo callback) {
        NativeFood.afterHearts(graphics, player, maxHealth, health);
    }
}
