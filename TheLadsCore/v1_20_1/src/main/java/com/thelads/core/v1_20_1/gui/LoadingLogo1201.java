package com.thelads.core.v1_20_1.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.gui.GuiGraphics;
import com.thelads.core.client.title.LoadingScreenTheme;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

/** The loading screen's wordmark, read from the jar on first use (before resource packs exist) and sampled linearly. */
public final class LoadingLogo1201 {
    public static final ResourceLocation ID = new ResourceLocation("theladscore", "loading_logo");
    private static boolean tried, ready;

    private LoadingLogo1201() {}

    public static boolean ready() {
        if (tried) return ready;
        tried = true;
        try (InputStream in = LoadingScreenTheme.class.getResourceAsStream(LoadingScreenTheme.LOGO)) {
            if (in == null) throw new IllegalStateException(LoadingScreenTheme.LOGO + " is missing");
            DynamicTexture texture = new DynamicTexture(NativeImage.read(in));
            Minecraft.getInstance().getTextureManager().register(ID, texture);
            texture.setFilter(true, false); // linear, no mipmaps: the 1024 px wordmark is drawn small
            ready = true;
        } catch (Exception failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads loading screen logo unavailable", failure);
        }
        return ready;
    }

    /** One frame of the Lads loading screen (vanilla's overlay and Forge's both draw it). */
    public static void draw(GuiGraphics graphics, float progress, float alpha, float barAlpha) {
        LoadingScreenTheme.render(new GuiGraphicsLadsAdapter1201(graphics, Minecraft.getInstance().font), graphics.guiWidth(), graphics.guiHeight(),
            progress, alpha, barAlpha, (x, y, width, height, a) -> {
                if (ready()) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    graphics.setColor(1f, 1f, 1f, a);
                    graphics.blit(ID, x, y, width, height, 0, 0, LoadingScreenTheme.LOGO_WIDTH, LoadingScreenTheme.LOGO_HEIGHT,
                        LoadingScreenTheme.LOGO_WIDTH, LoadingScreenTheme.LOGO_HEIGHT);
                    graphics.setColor(1f, 1f, 1f, 1f);
                }
            });
    }
}
