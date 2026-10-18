package com.thelads.core.v1_8_9.mixin;

import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * With Ionium (the opt-in 1.8.9 renderer) Forge's loading splash crashes: Ionium batches all font drawing through Minecraft's
 * texture manager, which does not exist yet on the splash thread. Forge already skips its splash when OptiFine is installed;
 * this skips it with Ionium too (vanilla's loading screen shows instead).
 */
@Mixin(value = SplashProgress.class, remap = false)
public abstract class SplashProgressIoniumMixin {
    private static final boolean IONIUM = SplashProgressIoniumMixin.class.getClassLoader().getResource("org/taumc/ionium/launch/IoniumTweaker.class") != null;

    @Shadow private static boolean getBool(String name, boolean def) { throw new AssertionError(); }

    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/client/SplashProgress;getBool(Ljava/lang/String;Z)Z"), require = 0)
    private static boolean lads$noSplashWithIonium(String name, boolean def) {
        return !(IONIUM && "enabled".equals(name)) && getBool(name, def);
    }
}
