package com.thelads.core.v1_8_9.cit;

import com.thelads.core.client.cem.CemLoader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Custom Entity Models (CEM) for Minecraft 1.8.9 on Ionium (without OptiFine).
 * Reads .jem entity model files and provides custom entity models to living entity renderers.
 */
public final class Cem189 implements IResourceManagerReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    private static final Map<String, CemLoader.Model> MODELS = new HashMap<String, CemLoader.Model>();
    private static boolean registered = false;

    private static final String[] ENTITY_TYPES = {
            "creeper", "zombie", "skeleton", "spider", "enderman", "pig", "cow", "sheep", "chicken"
    };

    private Cem189() {}

    public static void register() {
        if (registered) return;
        registered = true;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getResourceManager() instanceof net.minecraft.client.resources.IReloadableResourceManager) {
            ((net.minecraft.client.resources.IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(new Cem189());
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager rm) {
        reload(rm);
    }

    public static void reload(IResourceManager rm) {
        MODELS.clear();

        for (String entity : ENTITY_TYPES) {
            for (String folder : CemLoader.FOLDERS) {
                ResourceLocation loc = new ResourceLocation("minecraft", folder + entity + ".jem");
                InputStream in = null;
                try {
                    IResource res = rm.getResource(loc);
                    in = res.getInputStream();
                    String json = IOUtils.toString(new InputStreamReader(in, StandardCharsets.UTF_8));
                    CemLoader.Model model = CemLoader.parseJem(entity, json);
                    if (model != null) {
                        MODELS.put(entity, model);
                        break;
                    }
                } catch (Exception ignored) {
                } finally {
                    IOUtils.closeQuietly(in);
                }
            }
        }

        if (!MODELS.isEmpty()) {
            LOGGER.info("Cem189: loaded {} custom entity models", MODELS.size());
        }
    }

    public static CemLoader.Model getModel(String entityName) {
        if (entityName == null) return null;
        return MODELS.get(entityName.toLowerCase());
    }

    public static int getLoadedModelCount() {
        return MODELS.size();
    }
}
