package com.thelads.core.v26_2.mixin;
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
}
