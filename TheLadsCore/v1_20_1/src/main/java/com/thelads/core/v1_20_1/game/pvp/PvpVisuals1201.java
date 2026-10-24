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
import com.thelads.core.client.LightOverlay;
import com.thelads.core.modules.ItemDespawnModule;
import com.thelads.core.modules.LootBeamsModule;
import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.modules.MobSizeModule;
import com.thelads.core.modules.OofModule;
import com.thelads.core.modules.UhcOverlayModule;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The PvP visual modules on 1.20.1 (common PvpVisuals): Block Overlay (LevelRenderer.renderHitOutline), Hitboxes and Hide Mob
 * Deaths (EntityRenderDispatcher), Mount Opacity (LivingEntityRenderer), Glint Colorizer and Shiny Potions (the glint texturing
 * state, RenderSystem and ItemStack.hasFoil) and Hit Color, which repaints the entity overlay texture's hurt row when it changes.
 * Damage Tint is drawn by the common HUD. Mixins: theladscore.v1_20_1.pvp.mixins.json; Render Tweaks, Overlays, Totem Animation,
 * Dark Mode and the 1.7 extras: theladscore.v1_20_1.pvp2.mixins.json (mixin.pvp); the Nametags extras: Nametags1201. Rows 31-38
 * (theladscore.v1_20_1.pvp3.mixins.json, mixin.visuals): Oof Mod (ClientLevelOofMixin1201), Light Overlay (scanned each tick, drawn before
 * the debug renderers), Loot Beams and Item Despawn (ItemEntityVisualsMixin1201), Mob Overlay and shulker bullets (the glowing outline:
 * MinecraftGlowMixin1201, EntityTeamColorMixin1201), Mob Size (LivingEntityScaleMixin1201) and UHC Overlay (GuiGraphicsUhcMixin1201).
 */
public final class PvpVisuals1201 {
    /** OverlayTexture's own hurt pixel (ABGR): red at 70% alpha, a 30% red flash. */
    static final int VANILLA_HURT = 0xB20000FF;
    private static final Map<String, Boolean> POTION_SHINES = new ConcurrentHashMap<>();
    private static final Edges EDGES = new Edges();
    private static int hurtPixel;
    private static int lastHurtKey;
    private static RenderType outlineLines, boxLines;
    private static float outlineWidth, boxWidth;
    private static boolean boxWalls, glintTinted;

    /** The built-in oof (common sounds.json: Minecraft's own random/classic_hurt). */
    public static final SoundEvent OOF = SoundEvent.createVariableRangeEvent(new ResourceLocation("theladscore", "oof"));
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    /** One reused vector for the vertex transforms (the render thread only): no allocation per vertex. */
    private static final Vector3f VEC = new Vector3f();
    /** Light Overlay looks this many blocks above and below your feet. */
    private static final int UP = 4, DOWN = 8;

    private PvpVisuals1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(PvpVisuals.MODULES);
        ModuleSupport.registerBuiltIn(PvpVisuals.TOTEM);
        ClientTickEvents.END_CLIENT_TICK.register(PvpVisuals1201::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(PvpVisuals1201::drawLightOverlay);
    }

    private static void tick(Minecraft mc) {
        PvpVisuals.tick();
        if (LightOverlay.active() && mc.level != null && mc.player != null) scan(mc.level, mc.player);
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        int def = hit.on ? HitColorModule.abgr(HitColorModule.overlayPixel(hit.argb)) : VANILLA_HURT;
        int fire = hit.on ? HitColorModule.abgr(HitColorModule.overlayPixel(hit.fireArgb)) : VANILLA_HURT;
        int fall = hit.on ? HitColorModule.abgr(HitColorModule.overlayPixel(hit.fallArgb)) : VANILLA_HURT;
        int drown = hit.on ? HitColorModule.abgr(HitColorModule.overlayPixel(hit.drownArgb)) : VANILLA_HURT;
        int key = def ^ (fire << 1) ^ (fall << 2) ^ (drown << 3);
        if (key != lastHurtKey) {
            paintHurt(mc, def, fire, fall, drown);
            lastHurtKey = key;
        }
    }

    /** Hit Color: the overlay texture's hurt rows (v 0 to 7) in the new colours, uploaded as OverlayTexture does. */
    private static void paintHurt(Minecraft mc, int def, int fire, int fall, int drown) {
        DynamicTexture texture = ((OverlayTextureAccessor1201) mc.gameRenderer.overlayTexture()).lads$texture();
        NativeImage image = texture.getPixels();
        if (image == null) return;
        for (int x = 0; x < 16; x++) {
            image.setPixelRGBA(x, 0, def);
            image.setPixelRGBA(x, 1, fire);
            image.setPixelRGBA(x, 2, fall);
            image.setPixelRGBA(x, 3, def);
            image.setPixelRGBA(x, 4, drown);
            image.setPixelRGBA(x, 5, def);
            image.setPixelRGBA(x, 6, def);
            image.setPixelRGBA(x, 7, def);
        }
        RenderSystem.activeTexture(33985);
        texture.bind();
        image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false, true, false, false);
        RenderSystem.activeTexture(33984);
        hurtPixel = def;
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

    /** Rescans this tick's columns with vanilla's own spawn tests for a zombie: the block below, then the two the mob stands in. */
    private static void scan(ClientLevel level, Player player) {
        int r = LightOverlay.radius(), side = 2 * r + 1, limit = level.dimensionType().monsterSpawnBlockLightLimit();
        if (limit >= 15) limit = 7; // the Nether: no block light limit, but the raw light must still be 7 or less
        int px = player.getBlockX(), py = player.getBlockY(), pz = player.getBlockZ();
        for (int i = LightOverlay.columnsThisTick(); i > 0; i--) {
            int column = LightOverlay.nextColumn(), x = px + column % side - r, z = pz + column / side - r;
            boolean aboveOpen = open(level, level.getBlockState(POS.set(x, py + UP + 1, z)));
            boolean hereOpen = open(level, level.getBlockState(POS.set(x, py + UP, z)));
            for (int y = py + UP; y >= py - DOWN; y--) {
                BlockState below = level.getBlockState(POS.set(x, y - 1, z));
                if (hereOpen && aboveOpen && below.isValidSpawn(level, POS, EntityType.ZOMBIE)) {
                    POS.set(x, y, z);
                    LightOverlay.add(column, x, y, z, LightOverlay.kind(level.getBrightness(LightLayer.BLOCK, POS), level.getBrightness(LightLayer.SKY, POS), limit));
                }
                aboveOpen = hereOpen;
                POS.set(x, y - 1, z);
                hereOpen = open(level, below);
            }
        }
    }

    /** A block a zombie may stand in (vanilla's empty-spawn-block test); POS holds its position. */
    private static boolean open(ClientLevel level, BlockState state) {
        return NaturalSpawner.isValidEmptySpawnBlock(level, POS, state, state.getFluidState(), EntityType.ZOMBIE);
    }

    private static void drawLightOverlay(WorldRenderContext context) {
        MlgClutch1201.renderWorld(context);
        MultiBufferSource buffers = context.consumers();
        if (!LightOverlay.active() || buffers == null) return;
        Vec3 camera = context.camera().getPosition();
        PoseStack.Pose pose = context.matrixStack().last();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (int column = 0, columns = LightOverlay.columns(); column < columns; column++) {
            for (int n = 0, count = LightOverlay.count(column); n < count; n++) {
                int slot = column * LightOverlay.PER_COLUMN + n, color = LightOverlay.color(LightOverlay.KIND[slot]);
                float x0 = (float) (LightOverlay.X[slot] - camera.x) + 0.1f, x1 = x0 + 0.8f, y = (float) (LightOverlay.Y[slot] - camera.y) + 0.02f;
                float z0 = (float) (LightOverlay.Z[slot] - camera.z) + 0.1f, z1 = z0 + 0.8f;
                line(lines, pose, x0, y, z0, x1, y, z1, color);
                line(lines, pose, x1, y, z0, x0, y, z1, color);
            }
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        Matrix3f normal = pose.normal();
        normal.transform(dx / length, dy / length, dz / length, VEC);
        float nx = VEC.x, ny = VEC.y, nz = VEC.z;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        vertex(lines, pose.pose(), x0, y0, z0).color(r, g, b, 255).normal(nx, ny, nz).endVertex();
        vertex(lines, pose.pose(), x1, y1, z1).color(r, g, b, 255).normal(nx, ny, nz).endVertex();
    }

    private static VertexConsumer vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z) {
        pose.transformPosition(x, y, z, VEC);
        return buffer.vertex(VEC.x, VEC.y, VEC.z);
    }

    /**
     * A loot beam over a dropped item, at the item renderer's pose (the entity's origin): four faces of a thin column, both sides, in
     * the beacon beam's texture and the rarity's color, fading out to the top. The translucent beacon type writes no depth, so the
     * faint top hides nothing behind it.
     */
    public static void beam(ItemStack stack, PoseStack poseStack, MultiBufferSource buffers) {
        int rarity = stack.getRarity().ordinal();
        if (!LootBeamsModule.beams(rarity)) return;
        int color = LootBeamsModule.color(rarity), r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        int a = (int) (LootBeamsModule.alpha() * 255);
        float s = 0.06f, top = LootBeamsModule.height();
        PoseStack.Pose pose = poseStack.last();
        pose.normal().transform(0, 1, 0, VEC);
        float nx = VEC.x, ny = VEC.y, nz = VEC.z;
        VertexConsumer buffer = buffers.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
        for (int side = 0; side < 2; side++) { // the beam type culls back faces: each face both ways round
            face(buffer, pose.pose(), -s, -s, s, -s, top, r, g, b, a, side, nx, ny, nz);
            face(buffer, pose.pose(), s, -s, s, s, top, r, g, b, a, side, nx, ny, nz);
            face(buffer, pose.pose(), s, s, -s, s, top, r, g, b, a, side, nx, ny, nz);
            face(buffer, pose.pose(), -s, s, -s, -s, top, r, g, b, a, side, nx, ny, nz);
        }
    }

    private static void face(VertexConsumer buffer, Matrix4f pose, float x0, float z0, float x1, float z1, float top, int r, int g, int b, int a,
                             int side, float nx, float ny, float nz) {
        if (side == 1) { float t = x0; x0 = x1; x1 = t; t = z0; z0 = z1; z1 = t; }
        beamVertex(buffer, pose, x0, 0, z0, r, g, b, a, 0, 0, nx, ny, nz);
        beamVertex(buffer, pose, x1, 0, z1, r, g, b, a, 1, 0, nx, ny, nz);
        beamVertex(buffer, pose, x1, top, z1, r, g, b, 0, 1, 1, nx, ny, nz);
        beamVertex(buffer, pose, x0, top, z0, r, g, b, 0, 0, 1, nx, ny, nz);
    }

    private static void beamVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int r, int g, int b, int a, float u, float v,
                                   float nx, float ny, float nz) {
        vertex(buffer, pose, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
            .normal(nx, ny, nz).endVertex();
    }

    /** What Mob Overlay and Mob Size call this entity: a hostile or a passive mob, a shulker bullet, or nothing of theirs. */
    public static int mobKind(Entity entity) {
        if (entity instanceof Mob) return entity instanceof Enemy ? MobOverlayModule.HOSTILE : MobOverlayModule.PASSIVE;
        return entity instanceof ShulkerBullet ? MobOverlayModule.BULLET : MobOverlayModule.OTHER;
    }

    /** Oof Mod: which player sound this is (any player hurt variant, or the death sound), or 0. */
    public static int oofKind(SoundEvent sound) {
        if (sound == SoundEvents.PLAYER_HURT || sound == SoundEvents.PLAYER_HURT_ON_FIRE || sound == SoundEvents.PLAYER_HURT_DROWN
            || sound == SoundEvents.PLAYER_HURT_FREEZE || sound == SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH) return OofModule.HURT;
        return sound == SoundEvents.PLAYER_DEATH ? OofModule.DEATH : 0;
    }

    /** UHC Overlay's group of an item: golden apples, heads, ores with their raw metal, ingots and diamonds, or none. */
    public static int uhcGroup(ItemStack stack) {
        if (!UhcOverlayModule.active()) return UhcOverlayModule.NONE;
        Item item = stack.getItem();
        if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) return UhcOverlayModule.APPLE;
        if (item == Items.PLAYER_HEAD || item == Items.ZOMBIE_HEAD || item == Items.SKELETON_SKULL || item == Items.WITHER_SKELETON_SKULL
            || item == Items.CREEPER_HEAD || item == Items.DRAGON_HEAD || item == Items.PIGLIN_HEAD) return UhcOverlayModule.HEAD;
        if (item == Items.GOLD_ORE || item == Items.DEEPSLATE_GOLD_ORE || item == Items.NETHER_GOLD_ORE || item == Items.IRON_ORE
            || item == Items.DEEPSLATE_IRON_ORE || item == Items.DIAMOND_ORE || item == Items.DEEPSLATE_DIAMOND_ORE || item == Items.RAW_GOLD
            || item == Items.RAW_IRON || item == Items.GOLD_INGOT || item == Items.IRON_INGOT || item == Items.DIAMOND) return UhcOverlayModule.ORE;
        return UhcOverlayModule.NONE;
    }
}
