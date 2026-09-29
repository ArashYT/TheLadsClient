package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeKeyBindings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

@Mixin(Options.class)
public class OptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="<init>",at=@At(value="FIELD",target="Lnet/minecraft/client/PreferredGraphicsApi;DEFAULT:Lnet/minecraft/client/PreferredGraphicsApi;"),require=1)
    private net.minecraft.client.PreferredGraphicsApi ladsVulkanDefault(net.minecraft.client.PreferredGraphicsApi original){return com.thelads.core.v26_2.feature.GraphicsCompatibility.firstLaunchApi();}

    @Shadow @Final private java.io.File optionsFile;
    @Shadow private net.minecraft.client.PreferredGraphicsApi preferredGraphicsBackendFromStartup;
    @org.spongepowered.asm.mixin.Unique private boolean ladsMissingBackend;
    @Inject(method="load()V",at=@At("HEAD"),require=1)
    private void ladsReadBackendPreference(CallbackInfo ci){
        ladsMissingBackend=!optionsFile.isFile();
        if(optionsFile.isFile())try(var lines=java.nio.file.Files.lines(optionsFile.toPath())){
            ladsMissingBackend=lines.noneMatch(line->line.startsWith("preferredGraphicsBackend:"));
        }catch(java.io.IOException failure){ladsMissingBackend=false;}
    }
    @Inject(method="load()V",at=@At("RETURN"),require=1)
    private void ladsDefaultBackendAfterMigration(CallbackInfo ci){
        // Vanilla data fixers insert DEFAULT into old/missing version options. Preserve explicit choices.
        if(ladsMissingBackend){
            ((Options)(Object)this).preferredGraphicsBackend().set(com.thelads.core.v26_2.feature.GraphicsCompatibility.firstLaunchApi());
            preferredGraphicsBackendFromStartup=com.thelads.core.v26_2.feature.GraphicsCompatibility.firstLaunchApi();
        }
    }

    @Inject(method = "load()V", at = @At("HEAD"), require = 1)
    private void ladsRegisterControls(CallbackInfo ci) {
        // Register before native options parsing, so saved bindings load on the first launch.
        // Reloads and upgrades add only missing entries, retaining every other mod's binding.
        boolean zoom = false, modules = false;
        for (KeyMapping binding : keyMappings) {
            zoom |= binding == NativeKeyBindings.ZOOM;
            modules |= binding == NativeKeyBindings.MODULES;
        }
        if (zoom && modules) return;
        int next = keyMappings.length;
        keyMappings = Arrays.copyOf(keyMappings, next + (zoom ? 0 : 1) + (modules ? 0 : 1));
        if (!zoom) keyMappings[next++] = NativeKeyBindings.ZOOM;
        if (!modules) keyMappings[next] = NativeKeyBindings.MODULES;
    }
}
