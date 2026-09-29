package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeClientTools;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Modify display components only. Signed packets, signatures and report logs stay intact. */
@Mixin(ChatComponent.class)
public abstract class ClientToolsChatMixin {
    @ModifyVariable(method = {"addClientSystemMessage", "addServerSystemMessage", "addPlayerMessage"},
        at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 3)
    private Component ladsTimestamp(Component content) { return NativeClientTools.timestamp(content); }
}
