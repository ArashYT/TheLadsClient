package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.DisplayEntityBoundingBoxCalculator;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Display.class)
public abstract class DisplayEntityCullingMixin {
    @Shadow
    private AABB cullingBoundingBox;

    @Shadow
    private boolean noCulling;

    @Shadow
    protected abstract float getWidth();

    @Shadow
    protected abstract float getHeight();

    @WrapMethod(method = "updateCulling")
    private void lads$modifyDisplayCullingBoundingBox(Operation<Void> original) {
        if (this.getWidth() != 0.0f && this.getHeight() != 0.0f) {
            original.call();
            return;
        }
        Display display = (Display) (Object) this;
        this.noCulling = false;
        this.cullingBoundingBox = DisplayEntityBoundingBoxCalculator.getWithTransform(
                display,
                DisplayEntityAccessor.invokeCreateTransformation(display.getEntityData()).getMatrix()
        );
    }
}
