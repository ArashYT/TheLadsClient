package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.LocalSkins189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The skin picked in the Skin changer replaces the signed-in account's own skin on this client only (LocalSkinMixin on 1.20.1). */
@Mixin(AbstractClientPlayer.class)
public abstract class LocalSkinMixin189 {
    @Inject(method = "getLocationSkin()Lnet/minecraft/util/ResourceLocation;", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsLocalSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        boolean isLocal = ladsIsLocalAccount();
        if (com.thelads.core.client.NickHider.shouldHideSkin(player.getUniqueID(), isLocal)) {
            cir.setReturnValue(net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(player.getUniqueID()));
            return;
        }
        LocalSkins189.Skin skin = LocalSkins189.current();
        if (skin != null && isLocal) cir.setReturnValue(skin.texture);
    }

    @Inject(method = "getSkinType", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsLocalModel(CallbackInfoReturnable<String> cir) {
        LocalSkins189.Skin skin = LocalSkins189.current();
        if (skin != null && ladsIsLocalAccount()) cir.setReturnValue(skin.slim ? "slim" : "default");
    }

    @Inject(method = "hasSkin", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsLocalLoaded(CallbackInfoReturnable<Boolean> cir) {
        if (LocalSkins189.current() != null && ladsIsLocalAccount()) cir.setReturnValue(true);
    }

    @Unique private static net.minecraft.util.Session ladsLastSession;
    @Unique private static java.util.UUID ladsSessionId;

    @Unique
    private boolean ladsIsLocalAccount() {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        Minecraft mc = Minecraft.getMinecraft();
        if (player == mc.thePlayer) return true;
        net.minecraft.util.Session session = mc.getSession();
        if (session != ladsLastSession) {
            ladsLastSession = session;
            try {
                ladsSessionId = session != null && session.getProfile() != null ? session.getProfile().getId() : null;
            } catch (Throwable ignored) {
                ladsSessionId = null;
            }
        }
        return ladsSessionId != null && ladsSessionId.equals(player.getUniqueID());
    }
}
