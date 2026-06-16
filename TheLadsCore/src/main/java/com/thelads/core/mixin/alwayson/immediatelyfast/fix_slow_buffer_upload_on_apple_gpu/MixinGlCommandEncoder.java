/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.sugar.Local
 *  com.mojang.blaze3d.buffers.GpuBufferSlice
 *  com.mojang.blaze3d.opengl.DirectStateAccess
 *  com.mojang.blaze3d.opengl.GlCommandEncoder
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.fix_slow_buffer_upload_on_apple_gpu;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import java.nio.ByteBuffer;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={GlCommandEncoder.class})
public abstract class MixinGlCommandEncoder {
    @Redirect(method={"writeToBuffer"}, at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/opengl/DirectStateAccess;bufferSubData(IJLjava/nio/ByteBuffer;I)V"))
    private void fixSlowBufferUploadOnAppleGpu(DirectStateAccess instance, int buffer, long offset, ByteBuffer data, int usage, @Local(argsOnly=true) GpuBufferSlice gpuBufferSlice) {
        if (ImmediatelyFast.runtimeConfig.disable_fast_buffer_upload && offset == 0L && gpuBufferSlice.length() == gpuBufferSlice.buffer().size()) {
            instance.bufferData(buffer, data, usage);
        } else {
            instance.bufferSubData(buffer, offset, data, usage);
        }
    }
}

