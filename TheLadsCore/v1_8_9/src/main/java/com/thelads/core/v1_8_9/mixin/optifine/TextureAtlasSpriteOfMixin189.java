package com.thelads.core.v1_8_9.mixin.optifine;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * OptiFine Animations texture gating:
 * Categorizes sprites during atlas stitch/init so updateAnimation hot paths check
 * an integer switch and static volatile booleans with ZERO String operations or allocations.
 * Uses MixinExtras WrapMethod to avoid CallbackInfo allocations in hot paths.
 */
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteOfMixin189 {
    @Shadow public abstract String getIconName();

    @Unique private int lads$animKind = -1; // 0=terrain/generic, 1=water, 2=lava, 3=fire, 4=portal, 5=redstone

    @WrapMethod(method = "updateAnimation")
    private void lads$gateAnimation(Operation<Void> original) {
        // Fast path: if no texture animations are disabled (default), proceed immediately with zero overhead
        if (!OptiFine189Module.sAnimAnyTextureDisabled) {
            original.call();
            return;
        }

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
            case 1: if (!OptiFine189Module.sAnimWater) return; break;
            case 2: if (!OptiFine189Module.sAnimLava) return; break;
            case 3: if (!OptiFine189Module.sAnimFire) return; break;
            case 4: if (!OptiFine189Module.sAnimPortal) return; break;
            case 5: if (!OptiFine189Module.sAnimRedstone) return; break;
            default:
                if (!OptiFine189Module.sAnimTerrain) return;
                break;
        }

        original.call();
    }
}
