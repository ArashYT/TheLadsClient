package com.thelads.core.mixin.capes;

import com.mojang.authlib.GameProfile;
import java.util.function.Supplier;
import com.thelads.core.client.capes.CapeConfig;
import com.thelads.core.client.capes.Capes;
import com.thelads.core.client.capes.PlayerHandler;
import com.thelads.core.client.capes.util.CapesUtils;
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

@Mixin(PlayerInfo.class)
public abstract class MixinPlayerInfo {
    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "createSkinLookup", at = @At("HEAD"))
    private static void loadTextures(GameProfile profile, CallbackInfoReturnable<Supplier<PlayerSkin>> cir) {
        if (CapesUtils.isValidProfile(profile)) {
            PlayerHandler.onLoadTexture(profile);
        }
    }

    @Inject(method = "getSkin", at = @At("TAIL"), cancellable = true)
    private void getCapeTexture(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!CapesUtils.isValidProfile(this.profile)) {
            return;
        }
        com.thelads.core.config.Module capesMod = com.thelads.core.config.ModuleManager.getInstance().getModule("Capes");
        if (capesMod != null && !capesMod.isEnabled()) {
            return;
        }
        PlayerHandler handler = PlayerHandler.fromProfile(this.profile);
        if (handler.getHasCape()) {
            boolean enableElytra = true;
            if (capesMod != null) {
                var opt = capesMod.getOption("Elytra Texture");
                if (opt instanceof com.thelads.core.config.BoolOption) {
                    enableElytra = ((com.thelads.core.config.BoolOption) opt).get();
                }
            }
            PlayerSkin oldTextures = cir.getReturnValue();
            ClientAsset.Texture capeTexture = handler.getCape();
            ClientAsset.Texture elytraTexture = (handler.getHasElytraTexture() && enableElytra)
                    ? capeTexture
                    : new ClientAsset.ResourceTexture(Identifier.parse("textures/entity/equipment/wings/elytra.png"), null);
            PlayerSkin newTextures = new PlayerSkin(
                    oldTextures.body(),
                    capeTexture,
                    elytraTexture,
                    oldTextures.model(),
                    oldTextures.secure()
            );
            cir.setReturnValue(newTextures);
        }
    }
}
