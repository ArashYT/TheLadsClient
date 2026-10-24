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
        var showcase=com.thelads.core.v26_2.cosmetics.anim.ShowcaseEmote26.skin(player);if(showcase!=null){ci.setReturnValue(showcase);return;}
        boolean isLocal = player.getUUID().equals(Minecraft.getInstance().getUser().getProfileId());
        if(com.thelads.core.client.NickHider.shouldHideSkin(player.getUUID(), isLocal)){
            ci.setReturnValue(net.minecraft.client.resources.DefaultPlayerSkin.get(player.getGameProfile()));
            return;
        }
        if(LocalSkins.current()!=null&&isLocal)ci.setReturnValue(LocalSkins.current());
    }
}
