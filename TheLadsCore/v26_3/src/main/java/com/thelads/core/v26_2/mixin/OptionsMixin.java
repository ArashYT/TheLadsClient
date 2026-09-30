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
    @org.spongepowered.asm.mixin.Unique private net.minecraft.client.PreferredGraphicsApi ladsSavedBackend;
    @Inject(method="load()V",at=@At("HEAD"),require=1)
    private void ladsReadBackendPreference(CallbackInfo ci){
        ladsSavedBackend=com.thelads.core.v26_2.feature.GraphicsCompatibility.firstLaunchApi();
        if(optionsFile.isFile())try(var lines=java.nio.file.Files.lines(optionsFile.toPath())){
            var saved=lines.filter(line->line.startsWith("preferredGraphicsBackend:")).reduce((a,b)->b).orElse(null);
            if(saved!=null) ladsSavedBackend=switch(saved.substring(saved.indexOf(':')+1).trim()){
                case "\"vulkan\"" -> net.minecraft.client.PreferredGraphicsApi.VULKAN;
                case "\"opengl\"" -> net.minecraft.client.PreferredGraphicsApi.OPENGL;
                case "\"default\"" -> net.minecraft.client.PreferredGraphicsApi.DEFAULT;
                default -> null;
            };
        }catch(java.io.IOException failure){ladsSavedBackend=null;}
    }
    @Inject(method="load()V",at=@At("RETURN"),require=1)
    private void ladsDefaultBackendAfterMigration(CallbackInfo ci){
        // Old/versionless options migrate this field to DEFAULT. Preserve an explicit choice,
        // including vanilla's saved DEFAULT/OpenGL crash fallback; default only when absent.
        if(ladsSavedBackend!=null){
            ((Options)(Object)this).preferredGraphicsBackend().set(ladsSavedBackend);
            preferredGraphicsBackendFromStartup=ladsSavedBackend;
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
