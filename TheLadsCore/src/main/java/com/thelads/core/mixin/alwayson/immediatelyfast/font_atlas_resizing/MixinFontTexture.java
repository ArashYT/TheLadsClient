/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.font.FontTexture
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Constant
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.ModifyConstant
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.font_atlas_resizing;

import net.minecraft.client.gui.font.FontTexture;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={FontTexture.class})
public abstract class MixinFontTexture {
    @Unique
    private boolean immediatelyFast$shouldResizeFontAtlas;
    @Unique
    private int immediatelyFast$fontAtlasSize;

    @Inject(method={"<init>"}, at={@At(value="CTOR_HEAD", unsafe=true)})
    private void cacheConfigState(CallbackInfo ci) {
        this.immediatelyFast$shouldResizeFontAtlas = ImmediatelyFast.runtimeConfig.font_atlas_resizing;
        this.immediatelyFast$fontAtlasSize = ImmediatelyFast.config.font_atlas_size;
    }

    @ModifyConstant(method={"*"}, constant={@Constant(intValue=256)})
    private int modifyTextureSize(int original) {
        return this.immediatelyFast$shouldResizeFontAtlas ? this.immediatelyFast$fontAtlasSize : 256;
    }

    @ModifyConstant(method={"*"}, constant={@Constant(floatValue=256.0f)})
    private float modifyTextureSize(float original) {
        return this.immediatelyFast$shouldResizeFontAtlas ? (float)this.immediatelyFast$fontAtlasSize : 256.0f;
    }
}

