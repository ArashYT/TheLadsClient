/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.MoverType
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.vmp.entity.move_zero_velocity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Entity.class})
public class MixinEntity {
    @Shadow
    private AABB bb;
    @Unique
    private boolean vmp$boundingBoxChanged = false;

    @Inject(method={"move"}, at={@At(value="HEAD")}, cancellable=true)
    private void onMove(MoverType movementType, Vec3 movement, CallbackInfo ci) {
        if (this.vmp$boundingBoxChanged && movement.equals((Object)Vec3.ZERO)) {
            ci.cancel();
            this.vmp$boundingBoxChanged = false;
        }
    }

    @Inject(method={"setBoundingBox"}, at={@At(value="HEAD")})
    private void onBoundingBoxChanged(AABB boundingBox, CallbackInfo ci) {
        if (!this.bb.equals((Object)boundingBox)) {
            this.vmp$boundingBoxChanged = true;
        }
    }
}

