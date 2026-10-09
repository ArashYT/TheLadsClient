package com.thelads.core.v1_20_1.hud.items.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v1_20_1.hud.items.Food1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge + Connector (Zero SMP without AppleSkin): Forge's HUD draws the hunger row in ForgeGui.renderFood(width, height, graphics)
 * at (width / 2 + 91, height - rightHeight), then adds 10 to rightHeight, and leaves blending off. Optional: the class only exists
 * on Forge (plain Fabric skips this mixin), and a changed Forge method leaves the row without previews instead of failing.
 * Hearts on Forge still go through Gui.renderHearts (FoodGuiMixin).
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.client.gui.overlay.ForgeGui")
abstract class FoodForgeGuiMixin {
    @Shadow(remap = false) public int rightHeight;
    @Unique private int lads$top, lads$right;

    @Unique
    private static Player lads$player() {
        var minecraft = Minecraft.getInstance();
        return minecraft.getCameraEntity() instanceof Player player ? player : minecraft.player;
    }

    @Inject(method = "renderFood", at = @At("HEAD"), remap = false, require = 0)
    private void lads$beforeFood(int width, int height, GuiGraphics graphics, CallbackInfo callback) {
        lads$top = height - rightHeight;
        lads$right = width / 2 + 91;
        Player player = lads$player();
        if (player != null) Food1201.beforeFood(graphics, player, lads$top, lads$right);
    }

    @WrapOperation(method = "renderFood", remap = false, require = 0, at = @At(value = "INVOKE", remap = true,
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void lads$foodIcon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height, Operation<Void> original) {
        original.call(graphics, texture, x, y, u, v, width, height);
        Food1201.foodIcon(x, y);
    }

    @Inject(method = "renderFood", at = @At("RETURN"), remap = false, require = 0)
    private void lads$afterFood(int width, int height, GuiGraphics graphics, CallbackInfo callback) {
        Player player = lads$player();
        if (player == null) return;
        Food1201.afterFood(graphics, player, lads$top, lads$right);
        RenderSystem.disableBlend(); // as renderFood leaves it
    }
}
