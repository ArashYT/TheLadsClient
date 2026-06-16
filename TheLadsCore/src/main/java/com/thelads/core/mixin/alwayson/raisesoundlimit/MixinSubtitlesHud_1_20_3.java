/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.components.SubtitleOverlay
 *  net.minecraft.client.resources.sounds.SoundInstance
 *  net.minecraft.client.sounds.WeighedSoundEvents
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={SubtitleOverlay.class})
public abstract class MixinSubtitlesHud_1_20_3 {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract void onPlaySound(SoundInstance var1, WeighedSoundEvents var2, float var3);

    @Inject(method={"onPlaySound"}, at={@At(value="HEAD")}, cancellable=true)
    private void onSoundPlayedHandler(SoundInstance sound, WeighedSoundEvents soundSet, float range, CallbackInfo ci) {
        if (!this.minecraft.isSameThread()) {
            ci.cancel();
            this.minecraft.execute(() -> this.onPlaySound(sound, soundSet, range));
        }
    }
}

