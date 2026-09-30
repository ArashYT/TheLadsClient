package com.thelads.core.v1_21_1.mixin;
import com.thelads.core.v1_21_1.feature.NativeFeatures;
import com.thelads.core.v1_21_1.feature.NativeMenuKey;
import com.thelads.core.v1_21_1.feature.NativeQualityOfLife;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ClientTickMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), require = 1)
    private void ladsTickFeatures(CallbackInfo ci) {
        NativeMenuKey.tick();
        NativeFeatures.tick();
        NativeQualityOfLife.tick();
        // The launcher lists Lads modules from this catalog; a later registration bumps the revision and rewrites it.
        com.thelads.core.mods.CoreCatalogExporter.exportIfChanged();
    }
}
