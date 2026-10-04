package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.WorldCheats;
import com.thelads.core.v26_2.feature.NativeCheats;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cheats stay as the host set them (WorldCheats, NativeCheats). */
@Mixin(IntegratedServer.class)
public abstract class CheatsMixin {
    @Unique private static boolean ladsPublishing;

    /** World Options' Allow Commands (and NativeCheats): Essential's switch first, so the permissions sent next already use it. */
    @Inject(method = "setWorldAllowCommands", at = @At("HEAD"), require = 1)
    private void ladsEssentialFollows(boolean allowCommands, CallbackInfo ci) {
        WorldCheats.essential(allowCommands);
    }

    /** /publish and Essential's world hosting choose joined players' commands for that session only. */
    @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;Lnet/minecraft/world/level/GameType;ZI)Z",
        at = @At("HEAD"), require = 1)
    private void ladsPublishing(MinecraftServer.MultiplayerScope scope, GameType gameMode, boolean allowCommands, int port,
                                CallbackInfoReturnable<Boolean> cir) {
        ladsPublishing = true;
    }

    @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;Lnet/minecraft/world/level/GameType;ZI)Z",
        at = @At("RETURN"), require = 1)
    private void ladsPublished(MinecraftServer.MultiplayerScope scope, GameType gameMode, boolean allowCommands, int port,
                               CallbackInfoReturnable<Boolean> cir) {
        ladsPublishing = false;
    }

    /** Multiplayer Options' Allow Commands for other players: the host's choice, kept with the world. */
    @Inject(method = "setCommandsAllowedForOtherPlayers", at = @At("HEAD"), require = 1)
    private void ladsKeepGuests(boolean allowCommands, CallbackInfo ci) {
        if (!ladsPublishing) WorldCheats.guests(NativeCheats.folder((IntegratedServer) (Object) this), allowCommands);
    }
}
