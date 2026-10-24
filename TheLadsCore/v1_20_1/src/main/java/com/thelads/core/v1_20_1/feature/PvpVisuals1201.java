package com.thelads.core.v1_20_1.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.LightOverlay;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.ItemDespawnModule;
import com.thelads.core.modules.LootBeamsModule;
import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.modules.MobSizeModule;
import com.thelads.core.modules.OofModule;
import com.thelads.core.modules.UhcOverlayModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The PvP visuals of PARITY-1023 rows 31-38 on 1.20.1: Oof Mod (ClientLevelOofMixin1201), Light Overlay (scanned here each tick,
 * drawn before the debug renderers), Loot Beams and Item Despawn (ItemEntityVisualsMixin1201), Mob Overlay and shulker bullets
 * (the glowing outline: MinecraftGlowMixin1201, EntityTeamColorMixin1201), Mob Size (LivingEntityScaleMixin1201) and UHC Overlay
 * (GuiGraphicsUhcMixin1201).
 */
public final class PvpVisuals1201 {
    /** The built-in oof (common sounds.json: Minecraft's own random/classic_hurt). */
    public static final SoundEvent OOF = SoundEvent.createVariableRangeEvent(new ResourceLocation("theladscore", "oof"));
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    /** One reused vector for the vertex transforms (the render thread only): no allocation per vertex. */
    private static final Vector3f VEC = new Vector3f();
    /** Light Overlay looks this many blocks above and below your feet. */
    private static final int UP = 4, DOWN = 8;

    private PvpVisuals1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(OofModule.NAME, LightOverlay.NAME, LootBeamsModule.NAME, MobOverlayModule.NAME, MobSizeModule.NAME,
            ItemDespawnModule.NAME, UhcOverlayModule.NAME);
        ClientTickEvents.END_CLIENT_TICK.register(PvpVisuals1201::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(PvpVisuals1201::drawLightOverlay);
    }

    private static void tick(Minecraft mc) {
        LightOverlay.refresh();
        LootBeamsModule.refresh();
        MobOverlayModule.refresh();
        MobSizeModule.refresh();
        ItemDespawnModule.refresh();
        UhcOverlayModule.refresh();
        if (LightOverlay.active() && mc.level != null && mc.player != null) scan(mc.level, mc.player);
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
    public static int kind(Entity entity) {
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
