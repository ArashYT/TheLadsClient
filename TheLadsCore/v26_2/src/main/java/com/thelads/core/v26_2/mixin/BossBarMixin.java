package com.thelads.core.v26_2.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(BossHealthOverlay.class)
public class BossBarMixin {
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsCustomBars(GuiGraphicsExtractor g,CallbackInfo ci){if(NativeQualityOfLife.enabled("BossBar"))ci.cancel();}
    @Inject(method="shouldDarkenScreen",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsSky(CallbackInfoReturnable<Boolean> ci){if(NativeQualityOfLife.enabled("BossBar")&&!NativeQualityOfLife.bool("BossBar","Darken sky",true))ci.setReturnValue(false);}
    @Inject(method="shouldCreateWorldFog",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsFog(CallbackInfoReturnable<Boolean> ci){if(NativeQualityOfLife.enabled("BossBar")&&!NativeQualityOfLife.bool("BossBar","Boss fog",true))ci.setReturnValue(false);}
    @Inject(method="shouldPlayMusic",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsMusic(CallbackInfoReturnable<Boolean> ci){if(NativeQualityOfLife.enabled("BossBar")&&!NativeQualityOfLife.bool("BossBar","Boss music",true))ci.setReturnValue(false);}
    /** While the Lads API notification is up the bars start below it (slides with it). */
    @WrapMethod(method="extractRenderState",require=1)
    private void ladsBelowToast(GuiGraphicsExtractor g,Operation<Void> original){
        int shift=com.thelads.core.client.hud.ServerRulesNotice.clearOf(g.guiWidth()/2-91,g.guiWidth()/2+91,12)-12;
        if(shift<=0){original.call(g);return;}
        g.pose().pushMatrix();g.pose().translate(0,shift);
        try{original.call(g);}finally{g.pose().popMatrix();}
    }
}
