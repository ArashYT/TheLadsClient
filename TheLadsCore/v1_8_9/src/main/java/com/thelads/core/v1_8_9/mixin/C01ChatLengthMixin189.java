package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ChatTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C01PacketChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unlimited Command Length (gated) on 1.8.9: the chat packet cuts every line to 100 characters as it is made; a command keeps its
 * whole length only in singleplayer, where the packet goes to the game's own server object by object (never encoded, so no length
 * limit applies). Multiplayer always keeps vanilla's 100.
 */
@Mixin(C01PacketChatMessage.class)
public abstract class C01ChatLengthMixin189 {
    @Shadow private String message;

    @Inject(method = "<init>(Ljava/lang/String;)V", at = @At("RETURN"), require = 1)
    private void ladsLongCommand(String typed, CallbackInfo ci) {
        if (typed.length() > message.length() && typed.length() <= ChatTweaks.LONG_COMMAND_LIMIT && typed.startsWith("/")
            && ChatTweaks.longCommands(Minecraft.getMinecraft().isIntegratedServerRunning())) message = typed;
    }
}
