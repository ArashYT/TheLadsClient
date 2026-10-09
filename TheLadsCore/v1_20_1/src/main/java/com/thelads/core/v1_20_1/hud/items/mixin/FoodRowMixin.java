package com.thelads.core.v1_20_1.hud.items.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.hud.items.Food1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Food1201's hunger row on plain Fabric. The row has no method of its own in 1.20.1: it is the part of renderPlayerHealth
 * between the profiler's "food" and "air" sections (skipped while riding a mob with health), drawn at
 * (screenWidth / 2 + 91, screenHeight - 39). Forge + Connector draws it in ForgeGui.renderFood instead (FoodForgeGuiMixin), and
 * Connector rewrites Fabric mixins that reach into renderPlayerHealth, so under Forge this one is not registered at all
 * (HudMixinPlugin.getMixins) and its handlers keep no state of their own (Food1201.row).
 */
@Mixin(Gui.class)
abstract class FoodRowMixin {
    @Inject(method = "renderPlayerHealth", at = @At(value = "INVOKE_STRING",
        target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=food"))
    private void lads$beforeFood(GuiGraphics graphics, CallbackInfo callback) {
        Player player = Food1201.cameraPlayer();
        if (player == null) return;
        Food1201.row = true;
        Food1201.beforeFood(graphics, player, graphics.guiHeight() - 39, graphics.guiWidth() / 2 + 91);
    }

    @WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void lads$foodIcon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height, Operation<Void> original) {
        original.call(graphics, texture, x, y, u, v, width, height);
        if (Food1201.row && v == 27) Food1201.foodIcon(x, y);
    }

    @Inject(method = "renderPlayerHealth", at = @At(value = "INVOKE_STRING",
        target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=air"))
    private void lads$afterFood(GuiGraphics graphics, CallbackInfo callback) {
        if (!Food1201.row) return;
        Food1201.row = false;
        Player player = Food1201.cameraPlayer();
        if (player != null) Food1201.afterFood(graphics, player, graphics.guiHeight() - 39, graphics.guiWidth() / 2 + 91);
    }
}
