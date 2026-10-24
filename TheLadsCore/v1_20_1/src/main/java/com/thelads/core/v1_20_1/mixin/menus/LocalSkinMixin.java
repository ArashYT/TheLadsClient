package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.v1_20_1.feature.LocalSkins1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The skin picked in the Skin changer replaces the signed-in account's own skin on this client only. */
@Mixin(AbstractClientPlayer.class)
public class LocalSkinMixin {
    @Inject(method = "getSkinTextureLocation()Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void ladsLocalSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        var player = (AbstractClientPlayer) (Object) this;
        boolean isLocal = ladsIsLocalAccount();
        if (com.thelads.core.client.NickHider.shouldHideSkin(player.getUUID(), isLocal)) {
            cir.setReturnValue(net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(player.getUUID()));
            return;
        }
        var skin = LocalSkins1201.current();
        if (skin != null && isLocal) cir.setReturnValue(skin.texture());
    }

    @Inject(method = "getModelName()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void ladsLocalModel(CallbackInfoReturnable<String> cir) {
        var skin = LocalSkins1201.current();
        if (skin != null && ladsIsLocalAccount()) cir.setReturnValue(skin.slim() ? "slim" : "default");
    }

    @Inject(method = "isSkinLoaded()Z", at = @At("HEAD"), cancellable = true)
    private void ladsLocalLoaded(CallbackInfoReturnable<Boolean> cir) {
        if (LocalSkins1201.current() != null && ladsIsLocalAccount()) cir.setReturnValue(true);
    }

    @Unique
    private boolean ladsIsLocalAccount() {
        var player = (AbstractClientPlayer) (Object) this;
        return player.getUUID().equals(Minecraft.getInstance().getUser().getProfileId());
    }
}
