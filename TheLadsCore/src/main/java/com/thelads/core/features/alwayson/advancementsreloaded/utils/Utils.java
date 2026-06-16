package com.thelads.core.features.alwayson.advancementsreloaded.utils;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Utils {
    public static final Style SUCCESS_STYLE = Style.EMPTY.withColor(TextColor.fromRgb(0x55FF55));
    public static final Style ERROR_STYLE = Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555));
    public static final Logger LOGGER = LoggerFactory.getLogger("AdvancementsReloaded");
    public static final ClientAsset.ResourceTexture INTENTIONAL_MISSING_TEXTURE = new ClientAsset.ResourceTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE);
    private static String modVersion;

    private Utils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String modVersion() {
        return modVersion;
    }

    public static void modVersion(String version) {
        modVersion = version;
    }
}
