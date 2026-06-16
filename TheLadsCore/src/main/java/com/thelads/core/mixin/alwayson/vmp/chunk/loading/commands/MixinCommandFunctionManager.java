/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.commands.CommandSource
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.server.ServerFunctionManager
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin.alwayson.vmp.chunk.loading.commands;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ServerFunctionManager.class})
public class MixinCommandFunctionManager {
    @Inject(method={"getGameLoopSender"}, at={@At(value="RETURN")}, cancellable=true)
    private void onGetScheduledCommandSource(CallbackInfoReturnable<CommandSourceStack> cir) {
        cir.setReturnValue(cir.getReturnValue().withSource(CommandSource.NULL));
    }
}

