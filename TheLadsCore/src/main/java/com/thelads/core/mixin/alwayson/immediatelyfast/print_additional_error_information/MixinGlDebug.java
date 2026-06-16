/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.opengl.GlDebug
 *  org.slf4j.Logger
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.ModifyVariable
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.print_additional_error_information;

import com.mojang.blaze3d.opengl.GlDebug;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={GlDebug.class})
public abstract class MixinGlDebug {
    @Unique
    private static long immediatelyFast$lastTime;

    @ModifyVariable(method={"enableDebugCallback"}, at=@At(value="HEAD"), index=1, argsOnly=true)
    private static boolean enableSyncDebug(boolean sync) {
        return true;
    }

    @Redirect(method={"printDebugLog"}, at=@At(value="INVOKE", target="Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V"))
    private void appendStackTrace(Logger instance, String message, Object argument) {
        if (System.currentTimeMillis() - immediatelyFast$lastTime > 1000L) {
            immediatelyFast$lastTime = System.currentTimeMillis();
            instance.info(message, argument, (Object)new Exception());
        } else {
            instance.info(message, argument);
        }
    }
}

