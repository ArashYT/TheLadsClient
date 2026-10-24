package com.thelads.core.v1_20_1.mixin.social;

import com.mojang.authlib.GameProfile;
import com.thelads.core.v1_20_1.social.SocialBridge1201;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to enforce the host's login filter, rejecting uninvited guests with "You weren't invited to this world".
 * Server-safe: uses the PlayerList's MinecraftServer, with zero net.minecraft.client dependencies.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListLoginFilterMixin {
    @Shadow
    public abstract MinecraftServer getServer();

    @Inject(method = "canPlayerLogin(Ljava/net/SocketAddress;Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"), cancellable = true)
    private void ladsCheckLoginFilter(SocketAddress socketAddress, GameProfile gameProfile, CallbackInfoReturnable<Component> cir) {
        Predicate<UUID> filter = SocialBridge1201.getLoginFilter();
        if (filter != null) {
            MinecraftServer server = getServer();
            if (server != null && gameProfile != null && server.isSingleplayerOwner(gameProfile)) {
                return; // Singleplayer owner is always allowed
            }
            if (gameProfile == null || gameProfile.getId() == null || !filter.test(gameProfile.getId())) {
                cir.setReturnValue(Component.literal("You weren't invited to this world"));
            }
        }
    }
}
