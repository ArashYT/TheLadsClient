// Adapted from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes.mixin;

import com.mojang.authlib.GameProfile;
import com.thelads.core.v26_2.embedded.capes.CapeConfig;
import com.thelads.core.v26_2.embedded.capes.Capes;
import com.thelads.core.v26_2.embedded.capes.handler.PlayerHandler;
import com.thelads.core.v26_2.embedded.capes.util.Utils;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(PlayerInfo.class)
public abstract class MixinPlayerInfo {
    @Shadow @Final private GameProfile profile;

    @Inject(method = "createSkinLookup", at = @At("HEAD"))
    private static void loadTextures(GameProfile profile, CallbackInfoReturnable<Supplier<PlayerSkin>> cir) {
        if (Utils.isValidProfile(profile)) {
            PlayerHandler.onLoadTexture(profile);
        }
    }

    @Inject(method = "getSkin", at = @At("TAIL"), cancellable = true)
    private void getCapeTexture(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!Utils.isValidProfile(profile)) return;
        PlayerHandler handler = PlayerHandler.fromProfile(profile);
        if (handler.getHasCape()) {
            CapeConfig config = Capes.getConfig();
            PlayerSkin oldTextures = cir.getReturnValue();
            ClientAsset.Texture capeTexture = handler.getCape();
            ClientAsset.Texture elytraTexture = handler.getHasElytraTexture() && config.getEnableElytraTexture() ? capeTexture : new ClientAsset.ResourceTexture(Identifier.parse("textures/entity/equipment/wings/elytra.png"),null);
            PlayerSkin newTextures = new PlayerSkin(
                    oldTextures.body(),
                    capeTexture, elytraTexture,
                    oldTextures.model(), oldTextures.secure());
            cir.setReturnValue(newTextures);
        }
    }

}
