package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.gui.SmoothScrollTarget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractScrollArea.class)
public abstract class SmoothScrollMixin implements SmoothScrollTarget {
    @Shadow public abstract double scrollAmount();
    @Shadow public abstract void setScrollAmount(double amount);
    @Shadow public abstract int maxScrollAmount();
    @Unique private double ladsTarget;
    @Unique private long ladsFrame;
    @Unique private boolean ladsAnimating, ladsUpdating;

    @Redirect(method="mouseScrolled", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/components/AbstractScrollArea;setScrollAmount(D)V"), require=1)
    private void ladsWheel(AbstractScrollArea area, double amount) {
        if (net.minecraft.client.Minecraft.getInstance().options.screenEffectScale().get() <= 0) {
            setScrollAmount(amount);
            return;
        }
        if (!ladsAnimating) ladsTarget = scrollAmount();
        ladsTarget = Math.clamp(ladsTarget + (amount - scrollAmount()) * com.thelads.core.client.gui.SmoothScroll.FACTOR, 0, maxScrollAmount());
        ladsAnimating = true;
        if (ladsFrame == 0) ladsFrame = System.nanoTime();
    }

    @Inject(method="setScrollAmount", at=@At("HEAD"), require=1)
    private void ladsDirectScroll(double amount, CallbackInfo ci) {
        if (!ladsUpdating) {
            ladsAnimating = false;
            ladsTarget = amount;
        }
    }

    @Inject(method = "extractScrollbar", at = @At("HEAD"))
    private void ladsAdvanceOnScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!((Object) this instanceof AbstractSelectionList)) {
            ladsAdvanceScroll();
        }
    }

    @Override
    public void ladsAdvanceScroll() {
        long now = System.nanoTime();
        double dt = Math.clamp((now - ladsFrame) / 1e9, 0, .1);
        ladsFrame = now;
        if (!ladsAnimating) return;
        ladsTarget = Math.clamp(ladsTarget, 0, maxScrollAmount());
        double next = com.thelads.core.client.gui.SmoothScroll.step(scrollAmount(), ladsTarget, dt);
        if (net.minecraft.client.Minecraft.getInstance().options.screenEffectScale().get() <= 0 || next == ladsTarget) {
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
