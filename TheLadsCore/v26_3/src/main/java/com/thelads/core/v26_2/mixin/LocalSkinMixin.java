package com.thelads.core.v26_2.mixin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerSkin;
import com.thelads.core.v26_2.feature.LocalSkins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayer.class)
public class LocalSkinMixin {
    @Inject(method="getSkin",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsLocalSkin(CallbackInfoReturnable<PlayerSkin> ci){
        var player=(AbstractClientPlayer)(Object)this;
        if(com.thelads.core.client.NickHider.isEnabled()&&com.thelads.core.client.NickHider.shouldHideSkin(player.getUUID(),player.getUUID().equals(Minecraft.getInstance().getUser().getProfileId()))){
            ci.setReturnValue(net.minecraft.client.resources.DefaultPlayerSkin.get(player.getUUID()));
            return;
        }
        var showcase=com.thelads.core.v26_2.cosmetics.anim.ShowcaseEmote26.skin(player);if(showcase!=null){ci.setReturnValue(showcase);return;}
    }
    /** The local skin swaps only the body and model: the account's cape and elytra stay (they were dropped before). */
    @Inject(method="getSkin",at=@At("RETURN"),cancellable=true,require=1)
    private void ladsLocalSkinKeepCape(CallbackInfoReturnable<PlayerSkin> ci){
        var local=LocalSkins.current();var account=ci.getReturnValue();
        if(local==null||account==null||account==local||!((AbstractClientPlayer)(Object)this).getUUID().equals(Minecraft.getInstance().getUser().getProfileId()))return;
        ci.setReturnValue(new PlayerSkin(local.body(),account.cape(),account.elytra(),local.model(),local.secure()));
    }
}
