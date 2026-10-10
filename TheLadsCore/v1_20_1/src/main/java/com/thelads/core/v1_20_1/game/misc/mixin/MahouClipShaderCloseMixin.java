package com.thelads.core.v1_20_1.game.misc.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mahou Tsukai 1.20.1-v1.34.74 (Zero SMP) frees the "ModelMat" uniform of its clip shader twice: ClipShaderInstance.close() closes
 * MODEL_MATRIX, which is the uniform the shader itself registered (getUniform("ModelMat")), and then calls ShaderInstance.close(),
 * which closes every registered uniform again. Each resource reload (F3+T, a resource pack toggle) closes the old shader, so the
 * native buffer is freed twice and the allocator's heap is corrupted: the game dies a few reloads later inside jemalloc, at whatever
 * shader is created next (Supplementaries, Alex's Caves, Creeper Overhaul, ...). The first close call is dropped; the base class
 * still frees the uniform once. Absent from a game without the mod: require 0.
 */
@Pseudo
@Mixin(targets = "stepsword.mahoutsukai.render.shader.ClipShaderInstance", remap = false)
public abstract class MahouClipShaderCloseMixin {
    @Redirect(method = "close()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;close()V", ordinal = 0),
        remap = false, require = 0)
    private void lads$closeModelMatOnce(Uniform modelMatrix) {
    }
}
