/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.text2speech.Narrator
 *  net.minecraft.client.GameNarrator
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.disablenarrator;

import com.mojang.text2speech.Narrator;
import net.minecraft.client.GameNarrator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={GameNarrator.class})
public class NoNarratorToastMixin {
    @Overwrite
    public void narrateMessage(String message, boolean interrupt) {
    }

    @Redirect(method={"<init>"}, at=@At(value="INVOKE", target="Lcom/mojang/text2speech/Narrator;getNarrator()Lcom/mojang/text2speech/Narrator;", remap=false))
    private static Narrator getOS() {
        return Narrator.EMPTY;
    }
}

