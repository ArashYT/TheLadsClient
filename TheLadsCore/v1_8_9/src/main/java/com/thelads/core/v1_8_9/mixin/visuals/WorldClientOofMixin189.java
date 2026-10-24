package com.thelads.core.v1_8_9.mixin.visuals;

import com.thelads.core.modules.OofModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oof Mod on 1.8.9: every sound the server sends (S29PacketSoundEffect) plays through WorldClient.playSound, players' hurt and death
 * sounds among them ("game.player.hurt", "game.player.die"). A replaced one plays the chosen file, or the built-in theladscore:oof
 * at the same spot, volume and pitch.
 */
@Mixin(WorldClient.class)
public abstract class WorldClientOofMixin189 {
    @Inject(method = "playSound(DDDLjava/lang/String;FFZ)V", at = @At("HEAD"), cancellable = true)
    private void ladsOof(double x, double y, double z, String name, float volume, float pitch, boolean distanceDelay, CallbackInfo ci) {
        int kind = "game.player.hurt".equals(name) ? OofModule.HURT : "game.player.die".equals(name) ? OofModule.DEATH : 0;
        if (kind == 0 || !OofModule.replaces(kind)) return;
        ci.cancel();
        Entity listener = Minecraft.getMinecraft().getRenderViewEntity();
        double distance = listener == null ? 0 : Math.sqrt(listener.getDistanceSq(x, y, z));
        if (!OofModule.playFile(distance)) ((WorldClient) (Object) this).playSound(x, y, z, "theladscore:oof", volume * OofModule.volume(), pitch, distanceDelay);
    }
}
