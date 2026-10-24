package com.thelads.core.v1_8_9.pvp;

import com.thelads.core.client.CustomSky;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.CustomSkyModule;
import com.thelads.core.v1_8_9.feature.GlState189;
import com.thelads.core.v1_8_9.feature.Options189;
import com.thelads.core.v1_8_9.feature.RenderScale189;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;

/**
 * Custom Skies on 1.8.9 without OptiFine (Ionium): the resource packs' OptiFine sky layers (client.CustomSky), loaded on every
 * resource reload and drawn by RenderGlobal.renderSky over the overworld's sky colour and sunrise, before the sun, moon and stars
 * (CustomSkyMixin189), turned with the sun. With OptiFine nothing is loaded or drawn: OptiFine's own Custom Sky draws these packs.
 */
public final class CustomSky189 {
    private static final boolean OPTIFINE = FMLClientHandler.instance().hasOptifine();
    private static final float[] COLOR = new float[4];
    private static volatile List<CustomSky.Layer> layers = Collections.emptyList();
    private static volatile ResourceLocation[] textures = new ResourceLocation[0];

    private CustomSky189() {}

    static void register() {
        if (OPTIFINE) {
            ModuleSupport.registerBuiltInLimited(CustomSkyModule.NAME, "With OptiFine, OptiFine's own Custom Sky setting draws the packs' skies; "
                + "this module draws them when Lads runs 1.8.9 without OptiFine (Ionium).");
            return;
        }
        ModuleSupport.registerBuiltIn(CustomSkyModule.NAME);
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(CustomSky189::load);
    }

    private static void load(IResourceManager resources) {
        List<CustomSky.Layer> found = CustomSky.load(name -> read(resources, name), name -> exists(resources, name));
        ResourceLocation[] ids = new ResourceLocation[found.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = new ResourceLocation(found.get(i).texture);
        textures = ids;
        layers = found;
    }

    /** A properties file's text, or null when no pack has it. */
    private static String read(IResourceManager resources, String name) {
        try (InputStream in = resources.getResource(new ResourceLocation(name)).getInputStream()) {
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException missing) {
            return null;
        }
    }

    private static boolean exists(IResourceManager resources, String name) {
        try (InputStream in = resources.getResource(new ResourceLocation(name)).getInputStream()) {
            return in != null;
        } catch (IOException | RuntimeException missing) {
            return false;
        }
    }

    /** RenderGlobal.renderSky: the matrix is the camera turned -90 degrees about Y, as the sun is drawn from. */
    public static void render(Minecraft mc, WorldClient world, float partialTicks) {
        List<CustomSky.Layer> sky = layers;
        if (OPTIFINE || sky.isEmpty() || world.provider.getDimensionId() != 0 || !Options189.enabled(CustomSkyModule.NAME) || RenderScale189.shaders()) return;
        ResourceLocation[] ids = textures;
        float celestial = world.getCelestialAngle(partialTicks), rain = world.getRainStrength(partialTicks), thunder = world.getThunderStrength(partialTicks);
        long time = world.getWorldTime();
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG), alpha = GlState189.alpha(), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        float[] faces = CustomSky.FACES;
        for (int i = 0; i < sky.size() && i < ids.length; i++) {
            CustomSky.Layer layer = sky.get(i);
            float brightness = layer.brightness(time, rain, thunder);
            if (brightness < 1e-4F) continue;
            layer.blend.color(brightness, COLOR);
            GlStateManager.color(COLOR[0], COLOR[1], COLOR[2], COLOR[3]);
            GlStateManager.blendFunc(layer.blend.source, layer.blend.destination);
            mc.getTextureManager().bindTexture(ids[i]);
            GlStateManager.pushMatrix();
            if (layer.rotate) GlStateManager.rotate(layer.angle(celestial), layer.axisX, layer.axisY, layer.axisZ);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            for (int v = 0; v < faces.length; v += 5) buffer.pos(faces[v], faces[v + 1], faces[v + 2]).tex(faces[v + 3], faces[v + 4]).endVertex();
            tessellator.draw();
            GlStateManager.popMatrix();
        }
        // vanilla's state for the sun that comes next
        if (fog) GlStateManager.enableFog();
        if (alpha) GlStateManager.enableAlpha();
        if (cull) GlStateManager.enableCull();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1, 1, 1, 1 - rain);
    }
}
