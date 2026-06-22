/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.text2speech.Narrator
 *  com.mojang.text2speech.Narrator$InitializeException
 *  com.mojang.text2speech.NarratorWindows
 *  com.sun.jna.Pointer
 *  com.sun.jna.platform.win32.COM.Unknown
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 */
package com.thelads.core.mixin.alwayson.disablenarrator;

import com.mojang.text2speech.Narrator;
import com.mojang.text2speech.NarratorWindows;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.Unknown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value={NarratorWindows.class})
public abstract class NoWindowsNarratorMixin
extends Unknown
implements Narrator {
    @Overwrite
    private static Pointer initSAPI() throws Narrator.InitializeException {
        return new Pointer(0L);
    }

    @Overwrite
    public void say(String msg, boolean interrupt, float volume) {
    }

    public void clear() {
    }

    public void destroy() {
    }
}

