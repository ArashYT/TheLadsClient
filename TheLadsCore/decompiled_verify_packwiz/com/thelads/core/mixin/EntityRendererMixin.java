/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.entity.EntityRenderer
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin;

import com.thelads.core.modules.ToggleNametagsModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={EntityRenderer.class})
public class EntityRendererMixin {
    @Inject(method={"shouldShowName"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void ladsToggleNametags(Entity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (ToggleNametagsModule.shouldHide(entity)) {
            cir.setReturnValue((Object)false);
        }
    }
}

