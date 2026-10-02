package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.thelads.core.v1_21_1.embedded.emf.utils.EMFEntity;

import java.util.Map;


@Mixin(EntityRenderer.class)
public class MixinLeashOffsets {

    private static final String METHOD = "renderLeash";


    @ModifyExpressionValue(method = METHOD, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getLeashOffset(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 emf$leash(Vec3 original, @Local(ordinal = 0, argsOnly = true) Entity leashable) {
        return leashModify(original, "", ((EMFEntity) leashable).emf$getVariableMap(), false);
    }

    @ModifyExpressionValue(method = METHOD, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getRopeHoldPosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 emf$leashHold(Vec3 original, @Local(ordinal = 1, argsOnly = true) Entity roper) {
        return leashModify(original, "", ((EMFEntity) roper).emf$getVariableMap(), true);
    }

    @Unique
    private Vec3 leashModify(Vec3 original, String variant, Map<String, Float> variable, boolean holder) {
        String type = holder ? "_holder" : "";
        var modifiedX = variable.getOrDefault("render.leash" + type + "_offset_x" + variant, 0f);
        var modifiedY = variable.getOrDefault("render.leash" + type + "_offset_y" + variant, 0f);
        var modifiedZ = variable.getOrDefault("render.leash" + type + "_offset_z" + variant, 0f);
        if (modifiedX != 0 || modifiedY != 0 || modifiedZ != 0) {
            foundQuadLeashHolderOffsets = true;
            return original.add(modifiedX, modifiedY, modifiedZ);
        }
        return original;
    }

    @Unique private boolean foundQuadLeashHolderOffsets = false;

}
