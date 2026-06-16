/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.resources.sounds.AbstractSoundInstance
 *  net.minecraft.util.RandomSource
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.ModifyVariable
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit;

import com.thelads.core.features.alwayson.raisesoundlimit.common.WorldRandomHolder;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={AbstractSoundInstance.class})
public class MixinAbstractSoundInstance {
    @ModifyVariable(method={"<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/sounds/SoundSource;Lnet/minecraft/util/RandomSource;)V"}, at=@At(value="HEAD"), argsOnly=true)
    private static RandomSource onInit(RandomSource value) {
        if (WorldRandomHolder.isWorldRandom(value)) {
            return RandomSource.create();
        }
        return value;
    }
}

