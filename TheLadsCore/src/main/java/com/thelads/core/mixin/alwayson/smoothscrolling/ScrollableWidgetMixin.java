/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
 *  com.mojang.blaze3d.pipeline.RenderPipeline
 *  net.minecraft.client.gui.components.AbstractScrollArea
 *  net.minecraft.util.Mth
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 */
package com.thelads.core.mixin.alwayson.smoothscrolling;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.thelads.core.features.alwayson.smoothscrolling.ScrollMath;
import com.thelads.core.features.alwayson.smoothscrolling.ScrollableWidgetManipulator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value={AbstractScrollArea.class})
public abstract class ScrollableWidgetMixin
implements ScrollableWidgetManipulator {
    @Shadow
    private double scrollAmount;
    @Unique
    private double animationTimer = 0.0;
    @Unique
    private double scrollStartVelocity = 0.0;
    @Unique
    private boolean renderSmooth = false;

    @Shadow
    public abstract int maxScrollAmount();

    @Shadow
    public abstract void setScrollAmount(double var1);

    @Override
    @Unique
    public void smoothScrollingRefurbished$manipulateScrollAmount(float delta) {
        this.renderSmooth = true;
        this.checkOutOfBounds(delta);
        if (Math.abs(ScrollMath.scrollbarVelocity(this.animationTimer, this.scrollStartVelocity)) < 1.0) {
            return;
        }
        this.applyMotion(delta);
    }

    @Unique
    private void applyMotion(float delta) {
        this.setScrollAmount(this.scrollAmount + ScrollMath.scrollbarVelocity(this.animationTimer, this.scrollStartVelocity) * (double)delta);
        this.animationTimer += (double)(delta * 10.0f) / ScrollMath.animationDuration;
    }

    @Unique
    private void checkOutOfBounds(float delta) {
        if (this.scrollAmount < 0.0) {
            this.setScrollAmount(this.scrollAmount + ScrollMath.pushBackStrength(Math.abs(this.scrollAmount), delta));
            if (this.scrollAmount > -0.2) {
                this.scrollAmount = 0.0;
            }
        }
        if (this.scrollAmount > (double)this.maxScrollAmount()) {
            this.setScrollAmount(this.scrollAmount - ScrollMath.pushBackStrength(this.scrollAmount - (double)this.maxScrollAmount(), delta));
            if (this.scrollAmount < (double)this.maxScrollAmount() + 0.2) {
                this.scrollAmount = this.maxScrollAmount();
            }
        }
    }

    @WrapOperation(method={"mouseScrolled"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/gui/components/AbstractScrollArea;setScrollAmount(D)V")})
    private void setVelocity(AbstractScrollArea instance, double scrollY, Operation<Void> original) {
        if (!this.renderSmooth) {
            original.call(new Object[]{instance, scrollY});
            return;
        }
        double diff = scrollY - this.scrollAmount;
        diff = Math.signum(diff) * Math.min(Math.abs(diff), 10.0);
        if (Math.signum(diff *= ScrollMath.scrollSpeed) != Math.signum(this.scrollStartVelocity)) {
            diff *= 2.5;
        }
        this.animationTimer *= 0.5;
        this.scrollStartVelocity = ScrollMath.scrollbarVelocity(this.animationTimer, this.scrollStartVelocity) + diff;
        this.animationTimer = 0.0;
    }

    @WrapOperation(method={"extractScrollbar"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal=3)})
    private void modifyScrollbar(GuiGraphicsExtractor instance, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original) {
        int bottom;
        if (!this.renderSmooth) {
            original.call(new Object[]{instance, pipeline, sprite, x, y, width, height});
            return;
        }
        if (this.scrollAmount < 0.0) {
            height -= ScrollMath.dampenSquish(Math.abs(this.scrollAmount), height);
        }
        if (y + height > (bottom = ((AbstractScrollArea)(Object)this).getBottom())) {
            y = bottom - height;
        }
        if (this.scrollAmount > (double)this.maxScrollAmount()) {
            int squish = ScrollMath.dampenSquish(this.scrollAmount - (double)this.maxScrollAmount(), height);
            y += squish;
            height -= squish;
        }
        original.call(new Object[]{instance, pipeline, sprite, x, y, width, height});
    }

    @WrapOperation(method={"mouseDragged"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/gui/components/AbstractScrollArea;setScrollAmount(D)V", ordinal=2)})
    private void clampDraggedScrollY(AbstractScrollArea instance, double scrollY, Operation<Void> original) {
        original.call(new Object[]{instance, Mth.clamp((double)scrollY, (double)0.0, (double)this.maxScrollAmount())});
    }

    @WrapMethod(method={"setScrollAmount"})
    private void setScrollYUnclamped(double scrollY, Operation<Void> original) {
        if (scrollY > (double)this.maxScrollAmount() + 100000.0 || scrollY < -100000.0) {
            original.call(new Object[]{scrollY});
        } else {
            this.scrollAmount = scrollY;
        }
    }
}

