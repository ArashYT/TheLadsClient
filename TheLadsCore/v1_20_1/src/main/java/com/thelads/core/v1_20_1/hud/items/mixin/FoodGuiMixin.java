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
 * Food1201's heart hooks in vanilla's HUD (plain Fabric and Forge): hearts are renderHearts, each one a renderHeart blit;
 * container blits record where each heart sits. The hunger row is FoodRowMixin (plain Fabric) or FoodForgeGuiMixin (Forge).
 */
@Mixin(Gui.class)
abstract class FoodGuiMixin {
    @Inject(method = "renderHearts", at = @At("HEAD"))
    private void lads$beforeHearts(GuiGraphics graphics, Player player, int left, int top, int rowHeight, int regenerating, float maxHealth,
                                   int health, int displayHealth, int absorption, boolean blinking, CallbackInfo callback) {
        Food1201.beforeHearts(top, rowHeight, maxHealth, absorption);
    }

    /** Gui.HeartType is package-private: a container is told apart by its icons.png column (u 16, or 25 blinking; hearts start at 52). */
    @WrapOperation(method = "renderHeart", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void lads$heart(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height, Operation<Void> original) {
        original.call(graphics, texture, x, y, u, v, width, height);
        Food1201.heart(u < 34, x, y);
    }

    @Inject(method = "renderHearts", at = @At("RETURN"))
    private void lads$afterHearts(GuiGraphics graphics, Player player, int left, int top, int rowHeight, int regenerating, float maxHealth,
                                  int health, int displayHealth, int absorption, boolean blinking, CallbackInfo callback) {
        Food1201.afterHearts(graphics, player, maxHealth, health);
    }
}
