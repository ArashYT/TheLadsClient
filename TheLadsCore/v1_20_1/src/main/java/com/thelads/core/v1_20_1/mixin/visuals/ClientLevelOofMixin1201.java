package com.thelads.core.v1_20_1.mixin.visuals;

import com.thelads.core.modules.OofModule;
import com.thelads.core.v1_20_1.feature.PvpVisuals1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oof Mod on 1.20.1: every positioned sound the server sends, players' hurt and death sounds among them, ends in this private
 * playSound. A replaced one plays the chosen file, or the built-in theladscore:oof at the same spot, volume and pitch.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelOofMixin1201 {
    @Shadow
    private void playSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean delay, long seed) {}

    @Inject(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V", at = @At("HEAD"), cancellable = true)
    private void ladsOof(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean delay, long seed, CallbackInfo ci) {
        int kind = PvpVisuals1201.oofKind(sound);
        if (kind == 0 || !OofModule.replaces(kind)) return;
        ci.cancel();
        Vec3 ear = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (!OofModule.playFile(Math.sqrt(ear.distanceToSqr(x, y, z))))
            playSound(x, y, z, PvpVisuals1201.OOF, source, volume * OofModule.volume(), pitch, delay, seed);
    }
}
