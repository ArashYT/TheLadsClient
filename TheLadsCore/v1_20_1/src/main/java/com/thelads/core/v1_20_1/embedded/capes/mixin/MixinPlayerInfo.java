// Adapted from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only); modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.mixin;

import com.mojang.authlib.GameProfile;
import com.thelads.core.v1_20_1.embedded.capes.CapeConfig;
import com.thelads.core.v1_20_1.embedded.capes.Capes;
import com.thelads.core.v1_20_1.embedded.capes.handler.PlayerHandler;
import com.thelads.core.v1_20_1.embedded.capes.util.Utils;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1 keeps the textures of a player in {@code PlayerInfo} ({@code getCapeLocation}/{@code getElytraLocation}, which is what
 * {@code AbstractClientPlayer.getCloakTextureLocation()} and the cape/elytra layers read); 26.x has them in a {@code PlayerSkin}
 * record. The texture is swapped on read, like 26.3 does in {@code getSkin}.
 *
 * <p>The cape lookup starts the first time the profile is seen to be valid (26.3 starts it in {@code createSkinLookup}). That has to
 * be lazy here: in a singleplayer world the local profile has no texture property when the info is created, 1.20.1 fills it in
 * from the skin loader afterwards.
 */
@Mixin(PlayerInfo.class)
public abstract class MixinPlayerInfo {
    @Shadow @Final private GameProfile profile;

    @Unique
    private static final ResourceLocation capes$VANILLA_ELYTRA = new ResourceLocation("textures/entity/elytra.png");

    @Unique
    private boolean capes$valid;
    @Unique
    private long capes$recheckAt;

    /** Validity of the profile (it parses the texture property, so it is cached; an invalid one is looked at again once a second). */
    @Unique
    private boolean capes$isValid() {
        if (capes$valid) return true;
        long now = System.currentTimeMillis();
        if (now < capes$recheckAt) return false;
        if (Utils.isValidProfile(profile)) {
            capes$valid = true;
            PlayerHandler.onLoadTexture(profile);
            return true;
        }
        capes$recheckAt = now + 1000L;
        return false;
    }

    @Inject(method = "getCapeLocation", at = @At("RETURN"), cancellable = true)
    private void getCapeTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        if (!capes$isValid()) return;
        PlayerHandler handler = PlayerHandler.fromProfile(profile);
        if (handler.getHasCape()) {
            cir.setReturnValue(handler.getCape());
        }
    }

    /**
     * Without an own elytra texture the 1.20.1 elytra layer falls back to the cape texture, so the "cape texture on elytra" switch
     * (and capes that are not elytra-shaped) have to answer with the vanilla elytra explicitly.
     */
    @Inject(method = "getElytraLocation", at = @At("RETURN"), cancellable = true)
    private void getElytraTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        if (!capes$isValid()) return;
        PlayerHandler handler = PlayerHandler.fromProfile(profile);
        if (handler.getHasCape()) {
            CapeConfig config = Capes.getConfig();
            cir.setReturnValue(handler.getHasElytraTexture() && config.getEnableElytraTexture() ? handler.getCape() : capes$VANILLA_ELYTRA);
        }
    }
}
