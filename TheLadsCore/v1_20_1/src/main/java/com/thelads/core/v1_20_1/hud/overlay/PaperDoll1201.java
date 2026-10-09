package com.thelads.core.v1_20_1.hud.overlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thelads.core.client.hud.PaperDoll;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.Mods1201;
import java.io.File;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * The Lads paper doll on 1.20.1 (26.3 PaperDoll26): feeds {@link PaperDoll} every client tick and draws the real local player
 * (skin, armour, held items, pose) in the Paperdoll HUD element's box with the doll's own body and head angles. Immediate mode,
 * like vanilla's inventory doll; Model Opacity draws it into an offscreen copy of the screen first and blends that over.
 * Stands down while the Paper Doll mod is installed.
 */
public final class PaperDoll1201 {
    private static boolean mod, drawing, registered;
    private static float lastYaw = Float.NaN;
    private static TextureTarget faded;

    private PaperDoll1201() {}

    /** VanillaGameBridge1201.hasPaperDollRenderer. */
    public static boolean active() { return !mod; }

    /** True while the doll is being drawn (Nametags never shows your own tag over it). */
    static boolean drawing() { return drawing; }

    /** Built in (with its triggers and per-tick feed) unless the Paper Doll mod is installed. Called by OverlayFeatures1201; repeat calls do nothing. */
    public static void register() {
        if (registered) return;
        registered = true;
        mod = Mods1201.loaded("paperdoll");
        if (mod) {
            ModuleSupport.registerExternal("Paperdoll", "Paper Doll", "paperdoll", true);
            return;
        }
        ModuleSupport.registerBuiltIn("Paperdoll");
        if (PlayerActions1201.initialize()) reloadSavedTriggers();
        ClientTickEvents.END_CLIENT_TICK.register(PaperDoll1201::tick);
    }

    /**
     * The pose/state triggers are added after TheLadsCoreClient1201 read the config, so their saved values were skipped then:
     * apply the saved Paperdoll section again (only that section).
     */
    private static void reloadSavedTriggers() {
        try {
            File file = ClientPaths.getConfigFile();
            if (!file.isFile()) return;
            JsonObject saved = JsonParser.parseString(Files.readString(file.toPath())).getAsJsonObject();
            if (!(saved.get("modules") instanceof JsonObject modules) || !(modules.get("Paperdoll") instanceof JsonObject doll)) return;
            JsonObject only = new JsonObject(), section = new JsonObject();
            section.add("Paperdoll", doll);
            only.add("modules", section);
            ConfigManager.applyJson(only);
        } catch (Exception failure) {
            Hud1201.LOG.warn("Paperdoll triggers keep their defaults; saved values could not be read", failure);
        }
    }

    /** Every client tick (registered by {@link #register}; do not call it again): the triggers now and how far the view turned. */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (mod || player == null) { lastYaw = Float.NaN; return; }
        if (mc.isPaused()) return;
        float yaw = player.getYRot(), turned = Float.isNaN(lastYaw) ? 0 : Mth.wrapDegrees(yaw - lastYaw);
        lastYaw = yaw;
        PaperDoll.INSTANCE.tick(name -> PlayerActions1201.happening(player, name), turned);
    }

    /** PaperdollHudElement's box (GuiGraphicsLadsAdapter1201.drawPlayerModel); the HUD editor shows it whatever the triggers say, opaque. */
    public static void render(GuiGraphics graphics, int x, int y, int width, int height, boolean editor) {
        Minecraft mc = Minecraft.getInstance();
        if (mod || mc.player == null || width <= 0 || height <= 0) return;
        if (!editor && !PaperDoll.INSTANCE.visible(mc.options.getCameraType().isFirstPerson())) return;
        // The HUD element is moved and scaled through the pose: clip to where the box really is on screen.
        Matrix4f pose = graphics.pose().last().pose();
        Vector4f a = pose.transform(new Vector4f(x, y, 0, 1)), b = pose.transform(new Vector4f(x + width, y + height, 0, 1));
        int left = Math.round(Math.min(a.x, b.x)), top = Math.round(Math.min(a.y, b.y)), right = Math.round(Math.max(a.x, b.x)), bottom = Math.round(Math.max(a.y, b.y));
        boolean leftHalf = (left + right) / 2 < graphics.guiWidth() / 2;
        // Its own Model Opacity, and Auto Hide's while the HUD is faded.
        float opacity = (editor ? 1 : PaperDoll.INSTANCE.opacity()) * com.thelads.core.v1_20_1.feature.NativeAutohide1201.scopeOpacity;
        if (opacity >= 1) {
            draw(graphics, x, y, width, height, left, top, right, bottom, leftHalf);
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (faded == null) faded = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        else if (faded.width != main.width || faded.height != main.height) faded.resize(main.width, main.height, Minecraft.ON_OSX);
        faded.setClearColor(0, 0, 0, 0);
        faded.clear(Minecraft.ON_OSX);
        faded.bindWrite(true);
        try {
            draw(graphics, x, y, width, height, left, top, right, bottom, leftHalf);
        } finally {
            main.bindWrite(true);
        }
        blendOver(faded, opacity, (float) (main.width / mc.getWindow().getGuiScale()), (float) (main.height / mc.getWindow().getGuiScale()));
    }

    /** The player at the doll's angles in the box (vanilla InventoryScreen.renderEntityInInventory's setup); the player is restored after. */
    private static void draw(GuiGraphics graphics, int x, int y, int width, int height, int left, int top, int right, int bottom, boolean leftHalf) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        float partial = mc.getFrameTime();
        float pitch = player.isFallFlying() ? 0 : PaperDoll.INSTANCE.headPitch(player.getViewXRot(partial));
        float body = PaperDoll.INSTANCE.bodyYaw(leftHalf), head = body + PaperDoll.INSTANCE.headYaw(partial);
        float bodyRot = player.yBodyRot, bodyRotO = player.yBodyRotO, headRot = player.yHeadRot, headRotO = player.yHeadRotO;
        float xRot = player.getXRot(), xRotO = player.xRotO;
        // Riding a living mount, vanilla turns the body with the mount: turn the mount's body for this one draw too.
        LivingEntity mount = player.getVehicle() instanceof LivingEntity living ? living : null;
        float mountRot = mount == null ? 0 : mount.yBodyRot, mountRotO = mount == null ? 0 : mount.yBodyRotO;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        PoseStack pose = graphics.pose();
        player.yBodyRot = player.yBodyRotO = body;
        player.yHeadRot = player.yHeadRotO = head;
        player.setXRot(pitch);
        player.xRotO = pitch;
        if (mount != null) mount.yBodyRot = mount.yBodyRotO = body;
        drawing = true;
        graphics.enableScissor(left, top, right, bottom);
        pose.pushPose();
        try {
            // As vanilla's inventory doll: flipped upright, centred on its bounding box; about 0.43 of the box per block.
            float scale = height * 0.43f;
            pose.translate(x + width / 2f, y + height / 2f, 50);
            pose.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
            pose.translate(0, player.getBbHeight() / 2 + 0.0625f, 0);
            pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(player, 0, 0, 0, 0, partial, pose, graphics.bufferSource(), 15728880));
            graphics.flush();
        } finally {
            dispatcher.setRenderShadow(true);
            pose.popPose();
            Lighting.setupFor3DItems();
            graphics.disableScissor();
            drawing = false;
            player.yBodyRot = bodyRot;
            player.yBodyRotO = bodyRotO;
            player.yHeadRot = headRot;
            player.yHeadRotO = headRotO;
            player.setXRot(xRot);
            player.xRotO = xRotO;
            if (mount != null) { mount.yBodyRot = mountRot; mount.yBodyRotO = mountRotO; }
        }
    }

    /** The offscreen picture (transparent but for the doll) over the whole screen at the doll's opacity. */
    private static void blendOver(RenderTarget picture, float opacity, float width, float height) {
        RenderSystem.setShaderTexture(0, picture.getColorTextureId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, opacity);
        Matrix4f identity = new Matrix4f();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(identity, 0, 0, 0).uv(0, 1).endVertex();
        buffer.vertex(identity, 0, height, 0).uv(0, 0).endVertex();
        buffer.vertex(identity, width, height, 0).uv(1, 0).endVertex();
        buffer.vertex(identity, width, 0, 0).uv(1, 1).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableDepthTest();
    }
}
