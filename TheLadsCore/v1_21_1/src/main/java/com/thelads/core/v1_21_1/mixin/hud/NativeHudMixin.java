package com.thelads.core.v1_21_1.mixin.hud;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.FrameAnimation;
import com.thelads.core.v1_21_1.feature.NativeAutohide;
import com.thelads.core.v1_21_1.feature.NativeQualityOfLife;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SmoothHotbar and the Autohide scope of the hotbar, status bars and XP level (26.x NativeHudMixin on 1.21.1's layers). */
@Mixin(Gui.class)
public class NativeHudMixin {
    @Unique private final FrameAnimation lads$selection = new FrameAnimation();

    /** The second sprite of renderItemHotbar is the selected-slot highlight; a wrap keeps other mods' changes to that call (HoveringHotbar). */
    @WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void lads$selection(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height, Operation<Void> original) {
        double speed = switch (NativeQualityOfLife.choice("SmoothHotbar", "Speed", 1)) { case 0 -> 10; case 2 -> 28; default -> 18; };
        float offset = (float) (lads$selection.update(x, speed, System.nanoTime(), NativeQualityOfLife.enabled("SmoothHotbar")) - x);
        graphics.pose().pushPose();
        graphics.pose().translate(offset, 0, 0);
        original.call(graphics, sprite, x, y, width, height);
        graphics.pose().popPose();
    }
    /** A hidden HUD skips the whole layer, other mods' draws in it included (Xaero's minimap on 1.21.1); the wrap keeps their push/pop pairs whole. */
    @WrapMethod(method = "renderHotbarAndDecorations")
    private void lads$fade(GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        if (NativeAutohide.update() <= 0) return;
        try { original.call(graphics, delta); } finally { NativeAutohide.end(graphics); }
    }
    /** The fade starts at the first vanilla call, after HEAD injections such as Xaero's, whose map renders its own textures. */
    @Inject(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private void lads$fadeVanilla(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) { NativeAutohide.begin(graphics, NativeAutohide.opacity()); }
    /** 1.21.1 draws the XP level in its own layer, right after the hotbar's; 26.x draws it with the hotbar. */
    @WrapMethod(method = "renderExperienceLevel")
    private void lads$fadeLevel(GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        float alpha = NativeAutohide.opacity();
        if (alpha <= 0) return;
        NativeAutohide.begin(graphics, alpha);
        try { original.call(graphics, delta); } finally { NativeAutohide.end(graphics); }
    }
}
