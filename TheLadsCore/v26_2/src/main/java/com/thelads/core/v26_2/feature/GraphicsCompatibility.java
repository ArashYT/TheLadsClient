package com.thelads.core.v26_2.feature;
import net.minecraft.client.PreferredGraphicsApi;
import net.fabricmc.loader.api.FabricLoader;
/** The bundled Iris renderer invokes GL during startup even when shaders are disabled. */
public final class GraphicsCompatibility {
    private GraphicsCompatibility() {}
    public static boolean requiresOpenGl(){return FabricLoader.getInstance().isModLoaded("iris");}
    public static PreferredGraphicsApi firstLaunchApi(){return requiresOpenGl()?PreferredGraphicsApi.OPENGL:PreferredGraphicsApi.VULKAN;}
}
