package com.thelads.core.v1_20_1.game.skin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.QaSkin;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.SkinLayersModule;
import com.thelads.core.v1_20_1.game.Gameplay1201;
import com.thelads.core.v1_20_1.game.ModPresence1201;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native 3D Skin Layers (module "SkinLayers"): players' hat, jacket, sleeves and trousers drawn as pixel cuboids (VoxelMesh) instead
 * of vanilla's flat outer boxes. PlayerRenderer.setModelProperties decides per player and hides the flat parts it replaces; a render
 * layer then draws the cuboids with each part's own pose, so every animation (sneak, swim, elytra, ride, sleep) carries over.
 * Mixins: theladscore.v1_20_1.skinlayers.mixins.json. With the real 3D Skin Layers mod installed every hook stays inert.
 */
public final class SkinLayers1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    /** Players farther from the camera than this (blocks) keep vanilla's flat layers. */
    private static final double DETAIL_DISTANCE_SQ = 14 * 14;
    /** Where a player model is being drawn: in the world, as a GUI preview (InventoryScreen), or as the first-person arm. */
    private enum Context { WORLD, GUI, HAND }

    private static boolean standDown, broken, qaSkin, warnedSize;
    private static Context context = Context.WORLD;
    /** The player setModelProperties just prepared, its meshes and which parts (bit per VoxelMesh index) are drawn as cuboids. */
    private static AbstractClientPlayer pending;
    private static float[][] pendingMesh;
    private static int pendingParts;
    private static ResourceLocation qaTexture;
    private static final Vector3f POS = new Vector3f(), NORMAL = new Vector3f();

    private record Key(ResourceLocation skin, boolean slim) {}
    /** Per skin texture: the texture object it was read from (a replaced or released texture rebuilds) and the mesh. */
    private record Built(AbstractTexture source, CompletableFuture<float[][]> mesh, long started) {}
    private static final Map<Key, Built> MESHES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Built> eldest) { return size() > 64; }
    };

    private SkinLayers1201() {}

    /** Called once from Gameplay1201 (after the client entrypoint registered the module list): the truthful module status. */
    public static void register() {
        standDown = ModPresence1201.loaded("skinlayers3d");
        qaSkin = Gameplay1201.qaActive() && (Boolean.getBoolean("thelads.qaSkin") || qaScriptAsksSkin()
            || Files.isRegularFile(FabricLoader.getInstance().getGameDir().resolve(".lads-qa-skin")));
        SkinLayersQa1201.register();
        if (standDown) ModuleSupport.registerExternal(SkinLayersModule.NAME, "3D Skin Layers", "skinlayers3d", true);
        else ModuleSupport.registerBuiltIn(SkinLayersModule.NAME);
        LOGGER.info(standDown ? "Lads SkinLayers stands down: 3D Skin Layers (skinlayers3d) is installed and draws the layers{}"
            : "Lads SkinLayers: native 3D skin layers active{}", qaSkin ? " (QA checkerboard skin on every player)" : "");
    }

    /** QA only: the harness script carries a "# lads-qa-skin" line. */
    private static boolean qaScriptAsksSkin() {
        try {
            String script = System.getProperty("thelads.qaScript");
            return script != null && Files.readString(java.nio.file.Path.of(script)).contains("# lads-qa-skin");
        } catch (Exception unreadable) {
            return false;
        }
    }

    /** The layer every PlayerRenderer gets, or null while the real mod is installed. */
    public static RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        return standDown ? null : new Layer(renderer);
    }

    public static void beginGui() { context = Context.GUI; }

    public static void beginHand() { context = Context.HAND; }

    public static void endGui() { context = Context.WORLD; }

    /**
     * PlayerRenderer.setModelProperties just set the outer parts' visibility from the player's skin customization: parts that get
     * cuboids are hidden here and remembered for the layer (or for the first-person arm).
     */
    public static void prepare(PlayerRenderer renderer, AbstractClientPlayer player) {
        pending = null;
        if (standDown || broken || !SkinLayersModule.layersOn()) return;
        try {
            if (player.isSpectator() || player.isInvisible()) return;
            if (context == Context.GUI && !previewOn()) return;
            if (context == Context.WORLD
                && Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(player.position()) > DETAIL_DISTANCE_SQ) return;
            boolean hand = context == Context.HAND;
            float[][] mesh = mesh(hand ? player.getSkinTextureLocation() : renderer.getTextureLocation(player), "slim".equals(player.getModelName()));
            if (mesh == null) return;
            // Under worn armour vanilla's flat layer stays: the armour covers it as before, cuboids would poke through.
            boolean head = hand || player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
            boolean chest = hand || !(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem);
            boolean legs = hand || !(player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof ArmorItem);
            ModelPart[] parts = parts(renderer.getModel());
            boolean[] allowed = {head, chest, chest, chest, legs, legs};
            int drawn = 0;
            for (int i = 0; i < VoxelMesh.PARTS; i++)
                if (allowed[i] && parts[i].visible) { parts[i].visible = false; drawn |= 1 << i; }
            pending = player;
            pendingMesh = mesh;
            pendingParts = drawn;
        } catch (RuntimeException | LinkageError failure) {
            fail(failure);
        }
    }

    /** End of PlayerRenderer.renderHand: the first-person sleeve as cuboids, where vanilla's flat one was hidden. */
    public static void endHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart sleeve, PlayerModel<?> model) {
        context = Context.WORLD;
        if (pending != player) return;
        pending = null;
        int part = sleeve == model.rightSleeve ? VoxelMesh.RIGHT_SLEEVE : sleeve == model.leftSleeve ? VoxelMesh.LEFT_SLEEVE : -1;
        if (part < 0 || (pendingParts & 1 << part) == 0) return;
        try {
            VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentCull(player.getSkinTextureLocation()));
            draw(pendingMesh[part], sleeve, pose, out, light, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException | LinkageError failure) {
            fail(failure);
        }
    }

    /** Draws the prepared player's cuboids after the model, in the model's pose; nothing for anyone setModelProperties skipped. */
    private static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
            if (pending != player) return;
            pending = null;
            try {
                VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentCull(getTextureLocation(player)));
                int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0f); // the red hurt tint, as the body gets
                ModelPart[] parts = parts(getParentModel());
                for (int i = 0; i < VoxelMesh.PARTS; i++)
                    if ((pendingParts & 1 << i) != 0) draw(pendingMesh[i], parts[i], pose, out, light, overlay);
            } catch (RuntimeException | LinkageError failure) {
                fail(failure);
            }
        }
    }

    private static ModelPart[] parts(PlayerModel<?> model) {
        return new ModelPart[] {model.hat, model.jacket, model.rightSleeve, model.leftSleeve, model.rightPants, model.leftPants};
    }

    /** One part's quads in that part's pose, written as vanilla's ModelPart.Cube writes its own. */
    private static void draw(float[] quads, ModelPart part, PoseStack pose, VertexConsumer out, int light, int overlay) {
        if (quads.length == 0) return;
        pose.pushPose();
        part.translateAndRotate(pose);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        for (int q = 0; q < quads.length; q += VoxelMesh.FLOATS) {
            normals.transform(quads[q + 14], quads[q + 15], quads[q + 16], NORMAL);
            float u = quads[q + 12], v = quads[q + 13];
            for (int c = 0; c < 12; c += 3) {
                matrix.transformPosition(quads[q + c], quads[q + c + 1], quads[q + c + 2], POS);
                out.vertex(POS.x(), POS.y(), POS.z(), 1.0f, 1.0f, 1.0f, 1.0f, u, v, overlay, light, NORMAL.x(), NORMAL.y(), NORMAL.z());
            }
        }
        pose.popPose();
    }

    /**
     * The skin's meshes, or null while they are being built (vanilla's flat layers show meanwhile). The pixels are read back from
     * the GL texture on the render thread (it is whatever vanilla draws, from any source); the cuboids are built off-thread.
     */
    private static float[][] mesh(ResourceLocation skin, boolean slim) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skin, null);
        Key key = new Key(skin, slim);
        if (texture == null) {
            MESHES.remove(key);
            return null;
        }
        Built built = MESHES.get(key);
        long now = System.currentTimeMillis();
        if (built == null || built.source != texture || (built.mesh.isDone() && built.mesh.getNow(null) == null && now - built.started > 2000)) {
            built = new Built(texture, build(texture, skin, slim), now);
            MESHES.put(key, built);
        }
        return built.mesh.getNow(null);
    }

    private static CompletableFuture<float[][]> build(AbstractTexture texture, ResourceLocation skin, boolean slim) {
        int[] size = new int[2];
        int[] pixels = readPixels(texture, size);
        if (pixels == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            float[][] mesh = VoxelMesh.build(pixels, size[0], size[1], slim);
            if (Gameplay1201.qaActive()) {
                StringBuilder quads = new StringBuilder();
                for (float[] part : mesh) quads.append(' ').append(part.length / VoxelMesh.FLOATS);
                LOGGER.info("Lads SkinLayers built {} ({}, {}x{}): quads hat/jacket/rsleeve/lsleeve/rpants/lpants{}",
                    skin, slim ? "slim" : "wide", size[0], size[1], quads);
            }
            return mesh;
        }, Util.backgroundExecutor()).exceptionally(failure -> {
            LOGGER.error("Lads SkinLayers could not build the layers of {}", skin, failure);
            return null;
        });
    }

    /** The texture's pixels (render thread only); null if it is not a 64-wide skin or holds nothing yet. */
    private static int[] readPixels(AbstractTexture texture, int[] size) {
        RenderSystem.assertOnRenderThread();
        int previous = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(texture.getId());
            int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width != 64 || (height != 64 && height != 32)) {
                if (width > 0 && !warnedSize) LOGGER.warn("Lads SkinLayers: a {}x{} skin keeps flat layers (only 64x64 and 64x32 skins)", width, height);
                warnedSize |= width > 0;
                return null;
            }
            try (NativeImage image = new NativeImage(width, height, false)) {
                image.downloadTexture(0, false);
                int[] pixels = image.getPixelsRGBA();
                for (int pixel : pixels)
                    if (pixel >>> 24 != 0) { size[0] = width; size[1] = height; return pixels; }
                return null; // not uploaded yet: tried again shortly
            }
        } finally {
            GlStateManager._bindTexture(previous);
        }
    }

    private static boolean previewOn() {
        Module module = ModuleManager.getInstance().getModule(SkinLayersModule.NAME);
        return module == null || !(module.getOption("3D Preview") instanceof BoolOption option) || option.get();
    }

    /** Anything unexpected (another mod's odd player model, a broken texture): vanilla's flat layers from now on, logged once. */
    private static void fail(Throwable failure) {
        broken = true;
        pending = null;
        LOGGER.error("Lads SkinLayers stopped: vanilla's flat skin layers are shown from now on", failure);
    }

    /**
     * QA only (QA run plus -Dthelads.qaSkin=true, a "# lads-qa-skin" script line or a ".lads-qa-skin" file): every player (the QA
     * world has the local one and SkinLayersQa1201's dummy) wears QaSkin's checkerboard.
     */
    public static ResourceLocation qaSkin() {
        if (!qaSkin || !RenderSystem.isOnRenderThread()) return null;
        if (qaTexture == null) {
            BufferedImage skin = QaSkin.image();
            NativeImage image = new NativeImage(64, 64, true);
            for (int y = 0; y < 64; y++)
                for (int x = 0; x < 64; x++) {
                    int argb = skin.getRGB(x, y);
                    image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16); // ARGB -> ABGR
                }
            qaTexture = Minecraft.getInstance().getTextureManager().register("lads_qa_skin", new DynamicTexture(image));
        }
        return qaTexture;
    }

    /** QA only: the QA skin is a wide-armed (Steve) layout. */
    public static boolean qaWide() {
        return qaSkin;
    }
}
