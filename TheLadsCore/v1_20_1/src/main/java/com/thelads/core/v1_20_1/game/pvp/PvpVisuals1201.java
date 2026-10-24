package com.thelads.core.v1_20_1.game.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BlockOverlayModule;
import com.thelads.core.modules.GlintColorizerModule;
import com.thelads.core.modules.HitColorModule;
import com.thelads.core.modules.HitboxesModule;
import com.thelads.core.v1_20_1.game.pvp.mixin.OverlayTextureAccessor1201;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The PvP visual modules on 1.20.1 (common PvpVisuals): Block Overlay (LevelRenderer.renderHitOutline), Hitboxes and Hide Mob
 * Deaths (EntityRenderDispatcher), Mount Opacity (LivingEntityRenderer), Glint Colorizer and Shiny Potions (the glint texturing
 * state, RenderSystem and ItemStack.hasFoil) and Hit Color, which repaints the entity overlay texture's hurt row when it changes.
 * Damage Tint is drawn by the common HUD. Mixins: theladscore.v1_20_1.pvp.mixins.json.
 */
public final class PvpVisuals1201 {
    /** OverlayTexture's own hurt pixel (ABGR): red at 70% alpha, a 30% red flash. */
    static final int VANILLA_HURT = 0xB20000FF;
    private static final Map<String, Boolean> POTION_SHINES = new ConcurrentHashMap<>();
    private static final Edges EDGES = new Edges();
    private static int hurtPixel;
    private static RenderType outlineLines, boxLines;
    private static float outlineWidth, boxWidth;
    private static boolean boxWalls, glintTinted;
    /** Mount Opacity: the entity LivingEntityRenderer.render draws now is the player's mount (its alpha argument follows). */
    public static boolean mountNow;

    private PvpVisuals1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(PvpVisuals.MODULES);
        ClientTickEvents.END_CLIENT_TICK.register(PvpVisuals1201::tick);
    }

    private static void tick(Minecraft mc) {
        PvpVisuals.tick();
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        int pixel = hit.on ? HitColorModule.abgr(HitColorModule.overlayPixel(hit.argb)) : VANILLA_HURT;
        if (pixel != hurtPixel) paintHurt(mc, pixel);
    }

    /** Hit Color: the overlay texture's hurt rows (v 0 to 7; models sample v 3) in the new colour, uploaded as OverlayTexture does. */
    private static void paintHurt(Minecraft mc, int pixel) {
        DynamicTexture texture = ((OverlayTextureAccessor1201) mc.gameRenderer.overlayTexture()).lads$texture();
        NativeImage image = texture.getPixels();
        if (image == null) return;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) image.setPixelRGBA(x, y, pixel);
        RenderSystem.activeTexture(33985);
        texture.bind();
        image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false, true, false, false);
        RenderSystem.activeTexture(33984);
        hurtPixel = pixel;
    }

    /** Block Overlay in place of LevelRenderer.renderHitOutline; false while the module is off (vanilla draws). */
    public static boolean blockOverlay(PoseStack pose, Entity camera, double camX, double camY, double camZ, BlockPos pos, BlockState state) {
        BlockOverlayModule overlay = PvpVisuals.BLOCK_OVERLAY;
        Minecraft mc = Minecraft.getInstance();
        if (!overlay.on || mc.level == null) return false;
        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(camera));
        double x = pos.getX() - camX, y = pos.getY() - camY, z = pos.getZ() - camZ;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int fill = overlay.fillArgb;
        if (fill >>> 24 != 0 && !shape.isEmpty()) {
            LevelRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                x + shape.min(Direction.Axis.X) - 0.002, y + shape.min(Direction.Axis.Y) - 0.002, z + shape.min(Direction.Axis.Z) - 0.002,
                x + shape.max(Direction.Axis.X) + 0.002, y + shape.max(Direction.Axis.Y) + 0.002, z + shape.max(Direction.Axis.Z) + 0.002,
                (fill >> 16 & 0xFF) / 255F, (fill >> 8 & 0xFF) / 255F, (fill & 0xFF) / 255F, (fill >>> 24) / 255F);
        }
        if (overlay.outlineArgb >>> 24 != 0) {
            if (outlineLines == null || outlineWidth != overlay.lineWidth) {
                outlineWidth = overlay.lineWidth;
                outlineLines = LadsLines1201.of(outlineWidth, false);
            }
            EDGES.draw(shape, pose, buffers.getBuffer(outlineLines), x, y, z, overlay.outlineArgb);
        }
        return true;
    }

    /** Hitboxes in place of EntityRenderDispatcher.renderHitbox; false while the module is off (vanilla draws, with F3+B). */
    public static boolean hitbox(PoseStack pose, MultiBufferSource buffers, Entity entity, float partial) {
        HitboxesModule hitboxes = PvpVisuals.HITBOXES;
        if (!hitboxes.on) return false;
        if (!hitboxes.shows(kind(entity))) return true;
        if (boxLines == null || boxWidth != hitboxes.lineWidth || boxWalls != hitboxes.walls) {
            boxWidth = hitboxes.lineWidth;
            boxWalls = hitboxes.walls;
            boxLines = LadsLines1201.of(boxWidth, boxWalls);
        }
        VertexConsumer lines = buffers.getBuffer(boxLines);
        AABB box = entity.getBoundingBox();
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        int color = hitboxes.color(entity instanceof LivingEntity living && living.hurtTime > 0);
        LevelRenderer.renderLineBox(pose, lines, box.minX - x, box.minY - y, box.minZ - z, box.maxX - x, box.maxY - y, box.maxZ - z,
            (color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
        float eye = entity.getEyeHeight();
        if (hitboxes.eyes && entity instanceof LivingEntity)
            LevelRenderer.renderLineBox(pose, lines, box.minX - x, eye - 0.01, box.minZ - z, box.maxX - x, eye + 0.01, box.maxZ - z, 1, 0, 0, 1);
        if (hitboxes.look) {
            // Entity.getViewVector without its Vec3: the look direction from the interpolated rotations.
            float pitch = entity.getViewXRot(partial) * Mth.DEG_TO_RAD, yaw = -entity.getViewYRot(partial) * Mth.DEG_TO_RAD;
            float cos = Mth.cos(pitch), dx = Mth.sin(yaw) * cos, dy = -Mth.sin(pitch), dz = Mth.cos(yaw) * cos;
            PoseStack.Pose last = pose.last();
            lines.vertex(last.pose(), 0, eye, 0).color(0, 0, 255, 255).normal(last.normal(), dx, dy, dz).endVertex();
            lines.vertex(last.pose(), dx * 2, eye + dy * 2, dz * 2).color(0, 0, 255, 255).normal(last.normal(), dx, dy, dz).endVertex();
        }
        return true;
    }

    static int kind(Entity entity) {
        if (entity instanceof Player) return HitboxesModule.PLAYER;
        if (entity instanceof LivingEntity) return HitboxesModule.MOB;
        if (entity instanceof ItemEntity) return HitboxesModule.ITEM;
        return entity instanceof Projectile ? HitboxesModule.PROJECTILE : HitboxesModule.OTHER;
    }

    /** Hide Mob Deaths: a dying mob (not a player) is not drawn. */
    public static boolean hidden(Entity entity) {
        return PvpVisuals.hideMobDeaths && entity instanceof LivingEntity living && living.deathTime > 0 && !(entity instanceof Player);
    }

    /** Mount Opacity: the player's mount is drawn see-through. */
    public static boolean isMount(Entity entity) {
        if (!PvpVisuals.MOUNT_OPACITY.see) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entity == mc.player.getVehicle();
    }

    /** Glint Colorizer: the glint texturing state was just set up; tint its draw (RenderSystem.resetTextureMatrix undoes it). */
    public static void tintGlint() {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        if (!glint.on) return;
        RenderSystem.setShaderColor(glint.red, glint.green, glint.blue, 1);
        glintTinted = true;
    }

    /** The glint texturing state is cleared (it resets the texture matrix): the shader colour back to white. */
    public static void untintGlint() {
        if (!glintTinted) return;
        glintTinted = false;
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** Glint Colorizer's speed: the clock the glint scrolls by. */
    public static long glintMillis(long millis) {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        return glint.on ? (long) (millis * (double) glint.speedFactor) : millis;
    }

    /** Shiny Potions: a potion with effects glints (1.19.4 took the glint away). Its potion's answer is kept, so no lookup per frame. */
    public static boolean shiny(ItemStack stack) {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        if (!glint.on || !glint.shiny || !(stack.getItem() instanceof PotionItem)) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        if (tag.contains("CustomPotionEffects", Tag.TAG_LIST)) return true;
        String potion = tag.getString("Potion");
        return !potion.isEmpty() && POTION_SHINES.computeIfAbsent(potion, id -> !Potion.byName(id).getEffects().isEmpty());
    }

    /** A shape's edges as lines, without a lambda per frame (LevelRenderer.renderShape's drawing). */
    private static final class Edges implements Shapes.DoubleLineConsumer {
        private PoseStack.Pose pose;
        private VertexConsumer lines;
        private double x, y, z;
        private int r, g, b, a;

        void draw(VoxelShape shape, PoseStack stack, VertexConsumer lines, double x, double y, double z, int argb) {
            this.pose = stack.last();
            this.lines = lines;
            this.x = x;
            this.y = y;
            this.z = z;
            r = argb >> 16 & 0xFF;
            g = argb >> 8 & 0xFF;
            b = argb & 0xFF;
            a = argb >>> 24;
            shape.forAllEdges(this);
        }

        @Override
        public void consume(double x1, double y1, double z1, double x2, double y2, double z2) {
            float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1), length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            lines.vertex(pose.pose(), (float) (x1 + x), (float) (y1 + y), (float) (z1 + z)).color(r, g, b, a).normal(pose.normal(), dx, dy, dz).endVertex();
            lines.vertex(pose.pose(), (float) (x2 + x), (float) (y2 + y), (float) (z2 + z)).color(r, g, b, a).normal(pose.normal(), dx, dy, dz).endVertex();
        }
    }
}
