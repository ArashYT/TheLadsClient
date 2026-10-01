package com.thelads.core.v26_2.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.thelads.core.client.title.LoadingScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/** The loading screen's wordmark, read from the jar on first use (before resource packs exist) and sampled linearly. */
public final class LoadingLogo {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("theladscore", "loading_logo");
    private static boolean tried, ready;
    private LoadingLogo() {}

    public static boolean ready() {
        if (tried) return ready;
        tried = true;
        try (InputStream in = LoadingScreenTheme.class.getResourceAsStream(LoadingScreenTheme.LOGO)) {
            if (in == null) throw new IllegalStateException(LoadingScreenTheme.LOGO + " is missing");
            NativeImage image = NativeImage.read(in);
            Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "Lads loading logo", image) {
                { sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR); }
            });
            ready = true;
        } catch (Exception failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads loading screen logo unavailable", failure);
        }
        return ready;
    }
}
