package com.thelads.core.v1_20_1.mixin.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.modules.UhcOverlayModule;
import com.thelads.core.v1_20_1.game.pvp.PvpVisuals1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * UHC Overlay on 1.20.1: every GUI item (inventories, the hotbar) is drawn by this renderItem, about the slot's center after its
 * scale(16). A UHC item is scaled right there; the count and durability bar (renderItemDecorations) keep their size.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsUhcMixin1201 {
    @Shadow @Final private PoseStack pose;

    // require = 0: a modpack mod that wraps this same call (Zero SMP, the SMP pack) leaves the hook out instead of crashing the game.
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER), require = 0)
    private void ladsUhc(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed, int guiOffset, CallbackInfo ci) {
        float scale = UhcOverlayModule.scale(PvpVisuals1201.uhcGroup(stack));
        if (scale != 1) pose.scale(scale, scale, scale);
    }
}
