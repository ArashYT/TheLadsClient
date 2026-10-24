package com.thelads.core.v26_2.mixin.visuals;

import com.thelads.core.modules.UhcOverlayModule;
import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * UHC Overlay on 26.3: every GUI item (inventories, the hotbar) is recorded with a copy of the current 2D pose and blitted as a 16x16
 * quad through it. A UHC item is recorded under a pose scaled about its slot's center; the count and durability bar (drawn by
 * itemDecorations) keep their size.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiItemUhcMixin26 {
    @Shadow @Final private Matrix3x2fStack pose;
    @Unique private boolean ladsUhcPushed;

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void ladsUhc(LivingEntity owner, Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        float scale = UhcOverlayModule.scale(PvpVisuals26.uhcGroup(stack));
        ladsUhcPushed = scale != 1;
        if (!ladsUhcPushed) return;
        pose.pushMatrix();
        pose.translate(x + 8, y + 8);
        pose.scale(scale, scale);
        pose.translate(-(x + 8), -(y + 8));
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("RETURN"))
    private void ladsUhcDone(LivingEntity owner, Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (!ladsUhcPushed) return;
        ladsUhcPushed = false;
        pose.popMatrix();
    }
}
