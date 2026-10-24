package com.thelads.core.v26_2.pvp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.thelads.core.client.CustomSky;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.CustomSkyModule;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Custom Skies on 26.x: the resource packs' OptiFine sky layers (client.CustomSky), loaded (textures included) on every resource
 * reload and drawn in the sky's render pass over the sky colour and sunrise, before the sun, moon and stars (CustomSkyMixin26), turned
 * with the sun. One pipeline per blend mode on vanilla's position_tex_color shader, the brightness as its colour modulator; works on
 * Vulkan and OpenGL. Stands aside while an Iris shader pack renders the world (it draws its own sky).
 */
public final class CustomSky26 {
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
    private static final RenderPipeline[] PIPELINES = new RenderPipeline[CustomSky.Blend.values().length];
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Vector4f COLOR = new Vector4f();
    private static final float[] RGBA = new float[4];
    private static volatile List<CustomSky.Layer> layers = List.of();
    private static volatile Identifier[] textures = new Identifier[0];
    private static GpuBuffer cube;
    private static GpuBufferSlice cubeSlice;
    private static CustomSkyModule module;

    static {
        for (CustomSky.Blend blend : CustomSky.Blend.values()) {
            PIPELINES[blend.ordinal()] = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("theladscore", "pipeline/custom_sky_" + blend.name().toLowerCase(Locale.ROOT)))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(new BlendFunction(factor(blend.source), factor(blend.destination))))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build();
        }
    }

    private CustomSky26() {}

    static void register() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Identifier.fromNamespaceAndPath("theladscore", "custom_sky"),
            (ResourceManagerReloadListener) CustomSky26::load);
    }

    private static BlendFactor factor(int gl) {
        return switch (gl) {
            case 0 -> BlendFactor.ZERO;
            case 1 -> BlendFactor.ONE;
            case 0x300 -> BlendFactor.SRC_COLOR;
            case 0x301 -> BlendFactor.ONE_MINUS_SRC_COLOR;
            case 0x302 -> BlendFactor.SRC_ALPHA;
            case 0x303 -> BlendFactor.ONE_MINUS_SRC_ALPHA;
            case 0x306 -> BlendFactor.DST_COLOR;
            case 0x307 -> BlendFactor.ONE_MINUS_DST_COLOR;
            default -> throw new IllegalArgumentException("blend factor " + gl);
        };
    }

    /** Resource reload, on the render thread: the layers, their textures (loaded here, outside any render pass) and the cube. */
    private static void load(ResourceManager resources) {
        List<CustomSky.Layer> found = CustomSky.load(name -> read(resources, name), name -> {
            Identifier id = Identifier.tryParse(name);
            return id != null && resources.getResource(id).isPresent();
        });
        Identifier[] ids = new Identifier[found.size()];
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Identifier.parse(found.get(i).texture);
            textureManager.getTexture(ids[i]);
        }
        if (!found.isEmpty() && cube == null) buildCube();
        textures = ids;
        layers = found;
    }

    private static String read(ResourceManager resources, String name) {
        Identifier id = Identifier.tryParse(name);
        if (id == null || resources.getResource(id).isEmpty()) return null;
        try (BufferedReader reader = resources.openAsReader(id)) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static void buildCube() {
        float[] faces = CustomSky.FACES;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(faces.length / 5 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int v = 0; v < faces.length; v += 5) builder.addVertex(faces[v], faces[v + 1], faces[v + 2]).setUv(faces[v + 3], faces[v + 4]).setColor(-1);
            try (MeshData mesh = builder.buildOrThrow()) {
                cube = RenderSystem.getDevice().createBuffer(() -> "Lads custom sky", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
            }
        }
        cubeSlice = cube.slice();
    }

    /** SkyRenderer.render, in its pass, just before the sun: {@code sunAngle} is the sun's turn in radians. */
    public static void render(RenderPass pass, float sunAngle) {
        List<CustomSky.Layer> sky = layers;
        if (sky.isEmpty() || cubeSlice == null) return;
        if (module == null) module = (CustomSkyModule) ModuleManager.getInstance().getModule(CustomSkyModule.NAME);
        ClientLevel level = Minecraft.getInstance().level;
        if (!module.isEnabled() || level == null || level.dimension() != Level.OVERWORLD
            || IRIS && net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()) return;
        Identifier[] ids = textures;
        float celestial = (float) (sunAngle / (2 * Math.PI)), rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
        long time = level.getOverworldClockTime();
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (int i = 0; i < sky.size() && i < ids.length; i++) {
            CustomSky.Layer layer = sky.get(i);
            float brightness = layer.brightness(time, rain, thunder);
            if (brightness < 1e-4F) continue;
            layer.blend.color(brightness, RGBA);
            MATRIX.set(RenderSystem.getModelViewStack()).rotateY((float) (-Math.PI / 2));
            if (layer.rotate) MATRIX.rotate((float) Math.toRadians(layer.angle(celestial)), layer.axisX, layer.axisY, layer.axisZ);
            GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(MATRIX, COLOR.set(RGBA[0], RGBA[1], RGBA[2], RGBA[3]));
            AbstractTexture texture = textureManager.getTexture(ids[i]);
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINES[layer.blend.ordinal()]));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            pass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
            pass.setVertexBuffer(0, cubeSlice);
            pass.setIndexBuffer(indices.getBuffer(36), indices.type());
            pass.drawIndexed(36, 1, 0, 0, 0);
        }
    }
}
