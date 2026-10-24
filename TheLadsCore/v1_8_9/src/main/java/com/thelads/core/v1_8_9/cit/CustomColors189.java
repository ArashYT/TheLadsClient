package com.thelads.core.v1_8_9.cit;

import com.thelads.core.client.colors.CustomColors;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Custom Colors for Minecraft 1.8.9 on Ionium (without OptiFine).
 * Reads color.properties from resource packs and supplies custom palette tints.
 */
public final class CustomColors189 implements IResourceManagerReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    private static boolean registered = false;

    private CustomColors189() {}

    public static void register() {
        if (registered) return;
        registered = true;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getResourceManager() instanceof net.minecraft.client.resources.IReloadableResourceManager) {
            ((net.minecraft.client.resources.IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(new CustomColors189());
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager rm) {
        reload(rm);
    }

    public static void reload(IResourceManager rm) {
        CustomColors.clear();

        String[] paths = {"optifine/color.properties", "mcpatcher/color.properties"};
        for (String path : paths) {
            ResourceLocation loc = new ResourceLocation("minecraft", path);
            InputStream in = null;
            try {
                IResource res = rm.getResource(loc);
                in = res.getInputStream();
                String text = IOUtils.toString(new InputStreamReader(in, StandardCharsets.UTF_8));
                CustomColors.loadProperties(text);
            } catch (Exception ignored) {
            } finally {
                IOUtils.closeQuietly(in);
            }
        }
        LOGGER.info("CustomColors189: reloaded custom color properties");
    }
}
