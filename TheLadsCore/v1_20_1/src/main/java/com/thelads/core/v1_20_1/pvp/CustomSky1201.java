package com.thelads.core.v1_20_1.pvp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thelads.core.client.CustomSky;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.CustomSkyModule;
import com.thelads.core.v1_20_1.game.RenderScale1201;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Custom Skies on 1.20.1: the resource packs' OptiFine sky layers (client.CustomSky), loaded on every resource reload and drawn by
 * LevelRenderer.renderSky over the overworld's sky colour and sunrise, before the sun, moon and stars (CustomSkyMixin1201), turned
 * with the sun. Stands aside while an Iris shader pack renders the world (it draws its own sky).
 */
public final class CustomSky1201 {
    private static final Quaternionf TURN = new Quaternionf();
    private static final float[] COLOR = new float[4], SAVED = new float[4];
    private static volatile List<CustomSky.Layer> layers = List.of();
    private static volatile ResourceLocation[] textures = new ResourceLocation[0];
    private static CustomSkyModule module;

    private CustomSky1201() {}

    static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return new ResourceLocation("theladscore", "custom_sky"); }
            @Override public void onResourceManagerReload(ResourceManager resources) { load(resources); }
        });
    }

    private static void load(ResourceManager resources) {
        List<CustomSky.Layer> found = CustomSky.load(name -> read(resources, name), name -> {
            ResourceLocation id = ResourceLocation.tryParse(name);
            return id != null && resources.getResource(id).isPresent();
        });
        ResourceLocation[] ids = new ResourceLocation[found.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = new ResourceLocation(found.get(i).texture);
        textures = ids;
        layers = found;
    }

    private static String read(ResourceManager resources, String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || resources.getResource(id).isEmpty()) return null;
        try (BufferedReader reader = resources.openAsReader(id)) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException unreadable) {
            return null;
        }
    }

    /** LevelRenderer.renderSky: {@code pose} is the camera turned -90 degrees about Y, as the sun is drawn from. */
    public static void render(ClientLevel level, PoseStack pose, float partialTick) {
        List<CustomSky.Layer> sky = layers;
        if (sky.isEmpty()) return;
        if (module == null) module = (CustomSkyModule) ModuleManager.getInstance().getModule(CustomSkyModule.NAME);
        if (!module.isEnabled() || level.dimension() != Level.OVERWORLD || RenderScale1201.shadersInUse()) return;
        ResourceLocation[] ids = textures;
        float celestial = level.getTimeOfDay(partialTick), rain = level.getRainLevel(partialTick), thunder = level.getThunderLevel(partialTick);
        long time = level.getDayTime();
        System.arraycopy(RenderSystem.getShaderColor(), 0, SAVED, 0, 4);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        float[] faces = CustomSky.FACES;
        for (int i = 0; i < sky.size() && i < ids.length; i++) {
            CustomSky.Layer layer = sky.get(i);
            float brightness = layer.brightness(time, rain, thunder);
            if (brightness < 1e-4F) continue;
            layer.blend.color(brightness, COLOR);
            RenderSystem.setShaderColor(COLOR[0], COLOR[1], COLOR[2], COLOR[3]);
            RenderSystem.blendFunc(layer.blend.source, layer.blend.destination);
            RenderSystem.setShaderTexture(0, ids[i]);
            pose.pushPose();
            if (layer.rotate) pose.mulPose(TURN.rotationAxis((float) Math.toRadians(layer.angle(celestial)), layer.axisX, layer.axisY, layer.axisZ));
            Matrix4f matrix = pose.last().pose();
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int v = 0; v < faces.length; v += 5) buffer.vertex(matrix, faces[v], faces[v + 1], faces[v + 2]).uv(faces[v + 3], faces[v + 4]).endVertex();
            BufferUploader.drawWithShader(buffer.end());
            pose.popPose();
        }
        // vanilla's state for the sun that comes next
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(SAVED[0], SAVED[1], SAVED[2], SAVED[3]);
        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
            com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
    }
}
