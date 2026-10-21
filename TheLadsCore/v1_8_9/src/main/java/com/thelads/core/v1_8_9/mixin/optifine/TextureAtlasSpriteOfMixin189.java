package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OptiFine Animations texture gating:
 * Categorizes sprites during atlas stitch/init so updateAnimation hot paths check
 * an integer switch and static volatile booleans with ZERO String operations or allocations.
 */
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteOfMixin189 {
    @Shadow public abstract String getIconName();

    @Unique private int lads$animKind = -1; // 0=terrain/generic, 1=water, 2=lava, 3=fire, 4=portal, 5=redstone

    @Inject(method = "initSprite", at = @At("RETURN"))
    private void lads$classifySprite(int inX, int inY, int originInX, int originInY, boolean rotatedIn, CallbackInfo ci) {
        String name = getIconName();
        if (name != null) {
            if (name.contains("water")) lads$animKind = 1;
            else if (name.contains("lava")) lads$animKind = 2;
            else if (name.contains("fire")) lads$animKind = 3;
            else if (name.contains("portal")) lads$animKind = 4;
            else if (name.contains("redstone")) lads$animKind = 5;
            else lads$animKind = 0;
        } else {
            lads$animKind = 0;
        }
    }

    @Inject(method = "updateAnimation", at = @At("HEAD"), cancellable = true)
    private void lads$gateAnimation(CallbackInfo ci) {
        if (lads$animKind < 0) {
            String name = getIconName();
            if (name != null) {
                if (name.contains("water")) lads$animKind = 1;
                else if (name.contains("lava")) lads$animKind = 2;
                else if (name.contains("fire")) lads$animKind = 3;
                else if (name.contains("portal")) lads$animKind = 4;
                else if (name.contains("redstone")) lads$animKind = 5;
                else lads$animKind = 0;
            } else {
                lads$animKind = 0;
            }
        }

        switch (lads$animKind) {
            case 1:
                if (!OptiFine189Module.sAnimWater) ci.cancel();
                break;
            case 2:
                if (!OptiFine189Module.sAnimLava) ci.cancel();
                break;
            case 3:
                if (!OptiFine189Module.sAnimFire) ci.cancel();
                break;
            case 4:
                if (!OptiFine189Module.sAnimPortal) ci.cancel();
                break;
            case 5:
                if (!OptiFine189Module.sAnimRedstone) ci.cancel();
                break;
            default:
                if (!OptiFine189Module.sAnimTerrain) ci.cancel();
                break;
        }
    }
}
