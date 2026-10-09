package com.thelads.core.v1_20_1.mixin.menus;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.gui.SmoothScrollTarget1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wheel scrolling in every list (worlds, servers, controls, options, mod lists) eases to its target instead of jumping. */
@Mixin(AbstractSelectionList.class)
public abstract class SmoothScrollMixin implements SmoothScrollTarget1201 {
    @Shadow public abstract double getScrollAmount();
    @Shadow public abstract void setScrollAmount(double amount);
    @Shadow public abstract int getMaxScroll();
    @Unique private double ladsTarget;
    @Unique private long ladsFrame;
    @Unique private boolean ladsAnimating, ladsUpdating;

    @WrapOperation(method = "mouseScrolled(DDD)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractSelectionList;setScrollAmount(D)V"), require = 1)
    private void ladsWheel(AbstractSelectionList<?> list, double amount, Operation<Void> original) {
        if (!ladsAnimating) ladsTarget = getScrollAmount();
        ladsTarget = Math.max(0, Math.min(getMaxScroll(), ladsTarget + amount - getScrollAmount()));
        ladsAnimating = true;
        if (ladsFrame == 0) ladsFrame = System.nanoTime();
    }

    @Inject(method = "setScrollAmount(D)V", at = @At("HEAD"), require = 1)
    private void ladsDirectScroll(double amount, CallbackInfo ci) {
        if (!ladsUpdating) {
            ladsAnimating = false;
            ladsTarget = amount;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), require = 1)
    private void ladsAdvanceOnRender(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ladsAdvanceScroll();
    }

    @Override
    public void ladsAdvanceScroll() {
        long now = System.nanoTime();
        double dt = Math.max(0, Math.min((now - ladsFrame) / 1e9, .1));
        ladsFrame = now;
        if (!ladsAnimating) return;
        ladsTarget = Math.max(0, Math.min(getMaxScroll(), ladsTarget));
        double next = getScrollAmount() + (ladsTarget - getScrollAmount()) * (1 - Math.exp(-dt * 18));
        if (net.minecraft.client.Minecraft.getInstance().options.screenEffectScale().get() <= 0 || Math.abs(next - ladsTarget) < .2) {
            next = ladsTarget;
            ladsAnimating = false;
        }
        ladsUpdating = true;
        try {
            setScrollAmount(next);
        } finally {
            ladsUpdating = false;
        }
    }
}
