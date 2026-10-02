package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Borderless189;
import com.thelads.core.v1_8_9.feature.Reconnect189;
import com.thelads.core.v1_8_9.feature.WorldBackup189;
import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F11 and the fullscreen option: borderless with the BorderlessFullscreen module, and a window that stays resizable after fullscreen.
 * Opening a world a newer version saved asks for a backup first (WorldBackup189); a world that opens is AutoReconnect's target. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow private boolean fullscreen;

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBorderless(CallbackInfo ci) {
        if (!Borderless189.active() && (fullscreen || !Borderless189.enabled())) return;
        fullscreen = Borderless189.toggle((Minecraft) (Object) this);
        ci.cancel();
    }

    @Inject(method = "toggleFullscreen", at = @At("TAIL"), require = 1)
    private void ladsResizable(CallbackInfo ci) {
        if (!fullscreen) Borderless189.resizable();
    }

    @Inject(method = "launchIntegratedServer", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsWorldBackup(String folder, String name, WorldSettings settings, CallbackInfo ci) {
        if (WorldBackup189.intercept((Minecraft) (Object) this, folder, name, settings)) ci.cancel();
        else Reconnect189.world(folder, name);
    }
}
