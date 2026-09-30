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
        if(LocalSkins.current()!=null&&player.getUUID().equals(Minecraft.getInstance().getUser().getProfileId()))ci.setReturnValue(LocalSkins.current());
    }
}
