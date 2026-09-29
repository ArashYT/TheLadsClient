package com.thelads.core.v26_2.mixin;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.network.chat.Component;
import com.thelads.core.v26_2.feature.GraphicsCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PreferredGraphicsApi.class)
public class GraphicsCompatibilityMixin {
    @Inject(method="getBackendsToTry",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsShaderCompatibility(CallbackInfoReturnable<com.mojang.renderpearl.api.device.GpuBackend[]> ci){
        if(GraphicsCompatibility.requiresOpenGl()){
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Using OpenGL for Iris compatibility; Vulkan remains the default without Iris.");
            ci.setReturnValue(new com.mojang.renderpearl.api.device.GpuBackend[]{new com.mojang.renderpearl.backend.opengl.GlBackend()});
        }
    }
    @Inject(method="caption",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsExplainFallback(CallbackInfoReturnable<Component> ci){
        if((Object)this==PreferredGraphicsApi.VULKAN&&GraphicsCompatibility.requiresOpenGl())
            ci.setReturnValue(Component.literal("Vulkan (OpenGL fallback: Iris)"));
    }
}
