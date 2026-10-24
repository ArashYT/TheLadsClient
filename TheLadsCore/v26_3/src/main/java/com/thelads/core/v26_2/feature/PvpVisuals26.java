package com.thelads.core.v26_2.feature;

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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
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
import org.joml.Vector3f;

/**
 * The PvP visuals of PARITY-1023 rows 31-38 on 26.3: Oof Mod (ClientLevelOofMixin26), Light Overlay (scanned here each tick), Loot
 * Beams and Item Despawn (ItemEntityVisualsMixin26 queues the beams, the despawn blink skips the item), Mob Overlay and shulker
 * bullets (the glowing outline: MinecraftGlowMixin26, EntityTeamColorMixin26), Mob Size (LivingEntityScaleMixin26) and UHC Overlay
 * (GuiItemUhcMixin26). The overlay's lines and the frame's beams are two custom-geometry submits once the level's features are in
 * (Fabric's COLLECT_SUBMITS), drawn from fixed arrays by non-capturing method references: nothing is allocated per frame.
 */
public final class PvpVisuals26 {
    /** The built-in oof (common sounds.json: Minecraft's own random/classic_hurt). */
    public static final SoundEvent OOF = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("theladscore", "oof"));
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    /** One reused vector for the vertex transforms (the render thread only). */
    private static final Vector3f VEC = new Vector3f();
    /** Light Overlay looks this many blocks above and below your feet. */
    private static final int UP = 4, DOWN = 8, MAX_BEAMS = 512;
    /** Packed light 15/15 (what the beacon beam itself uses). */
    private static final int FULL_BRIGHT = 15728880;
    /** This frame's beams: world position and rarity, queued by the item renderer (the render thread only). */
    private static final double[] BEAM_X = new double[MAX_BEAMS], BEAM_Y = new double[MAX_BEAMS], BEAM_Z = new double[MAX_BEAMS];
    private static final int[] BEAM_RARITY = new int[MAX_BEAMS];
    private static int beams, drawn;
    private static double camX, camY, camZ;

    /** The extra fields Lads keeps on an entity's render state: Mob Overlay/Size's kind, and a dropped item's rarity and age. */
    public interface Visual {
        int lads$kind();
        int lads$rarity();
        int lads$age();
        void lads$visual(int kind, int rarity, int age);
    }

    private PvpVisuals26() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(OofModule.NAME, LightOverlay.NAME, LootBeamsModule.NAME, MobOverlayModule.NAME, MobSizeModule.NAME,
            ItemDespawnModule.NAME, UhcOverlayModule.NAME);
        ClientTickEvents.END_CLIENT_TICK.register(PvpVisuals26::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(PvpVisuals26::submit);
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
                if (hereOpen && aboveOpen && below.isValidSpawn(level, POS, EntityTypes.ZOMBIE)) {
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
        return NaturalSpawner.isValidEmptySpawnBlock(level, POS, state, state.getFluidState(), EntityTypes.ZOMBIE);
    }

    /** The item renderer's hook: this item gets a beam this frame (its world position from the render state). */
    public static void queueBeam(double x, double y, double z, int rarity) {
        if (beams >= MAX_BEAMS || !LootBeamsModule.beams(rarity)) return;
        BEAM_X[beams] = x; BEAM_Y[beams] = y; BEAM_Z[beams] = z; BEAM_RARITY[beams] = rarity;
        beams++;
    }

    private static void submit(LevelRenderContext context) {
        Vec3 camera = context.levelState().cameraRenderState.pos;
        camX = camera.x; camY = camera.y; camZ = camera.z;
        if (LightOverlay.active()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), PvpVisuals26::drawLightOverlay);
        drawn = beams;
        beams = 0; // the next frame queues its own
        if (drawn > 0) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
            PvpVisuals26::drawBeams);
    }

    private static void drawLightOverlay(PoseStack.Pose pose, VertexConsumer lines) {
        for (int column = 0, columns = LightOverlay.columns(); column < columns; column++) {
            for (int n = 0, count = LightOverlay.count(column); n < count; n++) {
                int slot = column * LightOverlay.PER_COLUMN + n, color = LightOverlay.color(LightOverlay.KIND[slot]);
                float x0 = (float) (LightOverlay.X[slot] - camX) + 0.1f, x1 = x0 + 0.8f, y = (float) (LightOverlay.Y[slot] - camY) + 0.02f;
                float z0 = (float) (LightOverlay.Z[slot] - camZ) + 0.1f, z1 = z0 + 0.8f;
                line(lines, pose, x0, y, z0, x1, y, z1, color);
                line(lines, pose, x1, y, z0, x0, y, z1, color);
            }
        }
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        pose.transformNormal(dx, dy, dz, VEC);
        float nx = VEC.x, ny = VEC.y, nz = VEC.z;
        pose.pose().transformPosition(x0, y0, z0, VEC);
        lines.addVertex(VEC.x, VEC.y, VEC.z).setColor(color).setNormal(nx, ny, nz).setLineWidth(2);
        pose.pose().transformPosition(x1, y1, z1, VEC);
        lines.addVertex(VEC.x, VEC.y, VEC.z).setColor(color).setNormal(nx, ny, nz).setLineWidth(2);
    }

    /**
     * Every queued beam: four faces of a thin column, both sides, in the beacon beam's texture and the rarity's color, fading out to the
     * top. The translucent beacon type writes no depth, so the faint top hides nothing behind it.
     */
    private static void drawBeams(PoseStack.Pose pose, VertexConsumer buffer) {
        int alpha = (int) (LootBeamsModule.alpha() * 255);
        float s = 0.06f, height = LootBeamsModule.height();
        pose.transformNormal(0, 1, 0, VEC);
        float nx = VEC.x, ny = VEC.y, nz = VEC.z;
        for (int i = 0; i < drawn; i++) {
            int rgb = LootBeamsModule.color(BEAM_RARITY[i]);
            float x = (float) (BEAM_X[i] - camX), y = (float) (BEAM_Y[i] - camY), z = (float) (BEAM_Z[i] - camZ), top = y + height;
            for (int side = 0; side < 2; side++) { // the beam type culls back faces: each face both ways round
                face(buffer, pose, x - s, z - s, x + s, z - s, y, top, rgb, alpha, side, nx, ny, nz);
                face(buffer, pose, x + s, z - s, x + s, z + s, y, top, rgb, alpha, side, nx, ny, nz);
                face(buffer, pose, x + s, z + s, x - s, z + s, y, top, rgb, alpha, side, nx, ny, nz);
                face(buffer, pose, x - s, z + s, x - s, z - s, y, top, rgb, alpha, side, nx, ny, nz);
            }
        }
    }

    private static void face(VertexConsumer buffer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y0, float y1, int rgb, int alpha,
                             int side, float nx, float ny, float nz) {
        if (side == 1) { float t = x0; x0 = x1; x1 = t; t = z0; z0 = z1; z1 = t; }
        int bottom = alpha << 24 | rgb, top = rgb;
        beamVertex(buffer, pose, x0, y0, z0, bottom, 0, 0, nx, ny, nz);
        beamVertex(buffer, pose, x1, y0, z1, bottom, 1, 0, nx, ny, nz);
        beamVertex(buffer, pose, x1, y1, z1, top, 1, 1, nx, ny, nz);
        beamVertex(buffer, pose, x0, y1, z0, top, 0, 1, nx, ny, nz);
    }

    private static void beamVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v, float nx, float ny, float nz) {
        pose.pose().transformPosition(x, y, z, VEC);
        buffer.addVertex(VEC.x, VEC.y, VEC.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(nx, ny, nz);
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
