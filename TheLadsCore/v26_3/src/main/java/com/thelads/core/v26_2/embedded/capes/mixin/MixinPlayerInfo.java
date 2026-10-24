// Adapted from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes.mixin;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.cosmetics.wearables.CosmeticItem;
import com.thelads.core.client.cosmetics.wearables.CosmeticLibrary;
import com.thelads.core.client.cosmetics.wearables.CosmeticSlot;
import com.thelads.core.client.cosmetics.wearables.CosmeticsManager;
import com.thelads.core.v26_2.cosmetics.wearables.CosmeticsWearablesLayer26;
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
        if (profile != null && profile.id() != null) {
            CosmeticsManager manager = CosmeticsManager.get();
            if (manager.shouldRender(profile.id(), CosmeticSlot.CAPE)) {
                String capeId = manager.getEquippedCape(profile.id());
                if (capeId != null && !capeId.isEmpty() && !"none".equals(capeId)) {
                    CosmeticItem item = CosmeticLibrary.get(capeId);
                    if (item != null && item.texturePath() != null) {
                        PlayerSkin oldTextures = cir.getReturnValue();
                        Identifier capeIdentifier = CosmeticsWearablesLayer26.getIdentifier(item.texturePath());
                        // The texture path is what CapeLayer draws: null crashed every frame that drew the player (26.3 QA).
                        ClientAsset.Texture capeTexture = new ClientAsset.ResourceTexture(capeIdentifier, capeIdentifier);
                        PlayerSkin newTextures = new PlayerSkin(
                                oldTextures.body(),
                                capeTexture, oldTextures.elytra(),
                                oldTextures.model(), oldTextures.secure());
                        cir.setReturnValue(newTextures);
                        return;
                    }
                }
            }
        }
        if (!Utils.isValidProfile(profile)) return;
        PlayerHandler handler = PlayerHandler.fromProfile(profile);
        if (handler.getHasCape()) {
            CapeConfig config = Capes.getConfig();
            PlayerSkin oldTextures = cir.getReturnValue();
            ClientAsset.Texture capeTexture = handler.getCape();
            // Vanilla's elytra when the cape has none: its texture path is what the wings layer draws (null crashed it, as the cape did).
            Identifier elytra = Identifier.parse("textures/entity/equipment/wings/elytra.png");
            ClientAsset.Texture elytraTexture = handler.getHasElytraTexture() && config.getEnableElytraTexture() ? capeTexture : new ClientAsset.ResourceTexture(elytra, elytra);
            PlayerSkin newTextures = new PlayerSkin(
                    oldTextures.body(),
                    capeTexture, elytraTexture,
                    oldTextures.model(), oldTextures.secure());
            cir.setReturnValue(newTextures);
        }
    }

}
