package com.thelads.core.v1_20_1.hud.mixin;

import com.thelads.core.v1_20_1.hud.Hud1201;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The BossBar module's world effects: Darken sky, Boss fog and Boss music each switch what a boss event asks the game to do. */
@Mixin(BossHealthOverlay.class)
public abstract class BossBarSettingsMixin {
    @Inject(method = "shouldDarkenScreen()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$sky(CallbackInfoReturnable<Boolean> callback) {
        if (Hud1201.enabled("BossBar") && !Hud1201.bool("BossBar", "Darken sky", true)) callback.setReturnValue(false);
    }

    @Inject(method = "shouldCreateWorldFog()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$fog(CallbackInfoReturnable<Boolean> callback) {
        if (Hud1201.enabled("BossBar") && !Hud1201.bool("BossBar", "Boss fog", true)) callback.setReturnValue(false);
    }

    @Inject(method = "shouldPlayMusic()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$music(CallbackInfoReturnable<Boolean> callback) {
        if (Hud1201.enabled("BossBar") && !Hud1201.bool("BossBar", "Boss music", true)) callback.setReturnValue(false);
    }
}
