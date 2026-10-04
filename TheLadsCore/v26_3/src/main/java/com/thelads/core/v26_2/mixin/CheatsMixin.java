package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.WorldCheats;
import com.thelads.core.v26_2.feature.NativeCheats;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
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

    /** /publish and Essential's world hosting choose guests' commands for that session only. */
    @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;ZI)Z", at = @At("HEAD"), require = 1)
    private void ladsPublishing(MinecraftServer.MultiplayerScope scope, boolean allowCommands, int port, CallbackInfoReturnable<Boolean> cir) {
        ladsPublishing = true;
    }

    @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;ZI)Z", at = @At("RETURN"), require = 1)
    private void ladsPublished(MinecraftServer.MultiplayerScope scope, boolean allowCommands, int port, CallbackInfoReturnable<Boolean> cir) {
        ladsPublishing = false;
    }

    /** World Options' Guest Command Access: the host's choice, kept with the world. */
    @Inject(method = "setGuestCommandAccess", at = @At("HEAD"), require = 1)
    private void ladsKeepGuests(boolean commandAccess, CallbackInfo ci) {
        if (!ladsPublishing) WorldCheats.guests(NativeCheats.folder((IntegratedServer) (Object) this), commandAccess);
    }

    /**
     * A guest the host made an operator (/op, or Essential's per-player operator switch when it hosts) gets operator
     * commands; 26.3 answered every guest from Guest Command Access alone while commands were on, so opped guests had none.
     */
    @Inject(method = "getProfilePermissions", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsOppedGuests(NameAndId player, CallbackInfoReturnable<LevelBasedPermissionSet> cir) {
        IntegratedServer server = (IntegratedServer) (Object) this;
        if (server.isSingleplayerOwner(player) || server.getPlayerList() == null) return;
        ServerOpListEntry op = server.getPlayerList().getOps().get(player);
        if (op != null) cir.setReturnValue(op.permissions());
    }
}
