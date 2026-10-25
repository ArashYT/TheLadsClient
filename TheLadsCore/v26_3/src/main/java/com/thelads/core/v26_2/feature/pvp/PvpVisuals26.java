package com.thelads.core.v26_2.feature.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.client.ladsapi.LadsBadge;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BlockOverlayModule;
import com.thelads.core.modules.GlintColorizerModule;
import com.thelads.core.modules.HitColorModule;
import com.thelads.core.modules.HitboxesModule;
import com.thelads.core.v26_2.feature.pvp.mixin.OverlayTextureAccessor26;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.joml.Vector4f;
import com.thelads.core.client.LightOverlay;
import com.thelads.core.modules.ItemDespawnModule;
import com.thelads.core.modules.LootBeamsModule;
import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.modules.MobSizeModule;
import com.thelads.core.modules.OofModule;
import com.thelads.core.modules.UhcOverlayModule;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The PvP visual modules on 26.3 (common PvpVisuals): Block Overlay (LevelRenderer.submitBlockOutline), Hitboxes (the F3+B
 * EntityHitboxDebugRenderer's gizmos, also run while F3+B is off), Hide Mob Deaths (EntityRenderDispatcher.shouldRender), Mount
 * Opacity (LivingEntityRenderer: a flag on the render state), Glint Colorizer (the glint render types' colour modulator and scroll)
 * with Shiny Potions (ItemStack.hasFoil) and Hit Color, which repaints the entity overlay texture's hurt row when it changes. Damage
 * Tint is drawn by the common HUD. Mixins: theladscore.v26_3.pvp.mixins.json; Render Tweaks, Overlays, Totem Animation, Dark Mode,
 * the 1.7 extras and the Nametags extras: theladscore.v26_3.pvp2.mixins.json (mixin.pvp), which draws ping and health through
 * {@link #tagExtras}. Rows 31-38 (theladscore.v26_3.pvp3.mixins.json, mixin.visuals): Oof Mod (ClientLevelOofMixin26), Light Overlay
 * (scanned each tick), Loot Beams and Item Despawn (ItemEntityVisualsMixin26 queues the beams, the despawn blink skips the item), Mob
 * Overlay and shulker bullets (the glowing outline: MinecraftGlowMixin26, EntityTeamColorMixin26), Mob Size (LivingEntityScaleMixin26)
 * and UHC Overlay (GuiItemUhcMixin26). The overlay's lines and the frame's beams are two custom-geometry submits once the level's
 * features are in (Fabric's COLLECT_SUBMITS), drawn from fixed arrays by non-capturing method references: nothing is allocated per frame.
 */
public final class PvpVisuals26 {
    /** OverlayTexture's own hurt pixel (ARGB): red at 70% alpha, a 30% red flash. */
    static final int VANILLA_HURT = 0xB2FF0000; // ldc -1291911168
    /** The glint shader's colour modulator for Glint Colorizer, and the model offset DynamicGpuData's other transforms use. */
    public static final Vector4f GLINT_COLOR = new Vector4f(1, 1, 1, 1);
    public static final Vector3f NO_OFFSET = new Vector3f();
    private static final Fill FILL = new Fill();
    private static int hurtPixel;
    private static int lastHurtKey;
    private static int styledBox, styledHurt;
    private static float styledWidth = -1;
    private static GizmoStyle boxStyle, hurtStyle, eyeStyle;
    private static EntityHitboxDebugRenderer hitboxes;
    /** The render type RenderType.writeDynamicTransforms writes now is a glint (its texture transform said so). */
    public static boolean glintNow;
    private static final FormattedCharSequence MS = FormattedCharSequence.forward("ms", Style.EMPTY), HEART = FormattedCharSequence.forward("\u2764", Style.EMPTY);
    /** "0".."999" as text to submit, built once each (PvpVisuals.number). */
    private static final FormattedCharSequence[] NUMBERS = new FormattedCharSequence[1000];

    /** Mount Opacity's mark on a LivingEntityRenderState (PvpMountStateMixin26): the player's mount, drawn see-through. */
    public interface MountState {
        boolean lads$mount();

        void lads$mount(boolean mount);
    }

    public interface DamageState {
        int lads$damageV();
        void lads$damageV(int v);
    }

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
        ModuleSupport.registerBuiltIn(PvpVisuals.MODULES);
        ModuleSupport.registerBuiltIn(PvpVisuals.TOTEM);
        ClientTickEvents.END_CLIENT_TICK.register(PvpVisuals26::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(PvpVisuals26::submit);
    }

    /**
     * Nametags' Show Ping and Nametag Health after a player's name, in the name tag's own place: SubmitNodeCollection.submitNameTag's
     * transform (attachment, half a block up, facing the camera, text scale and Nametags' Scale), drawn with the normal pass.
     */
    public static void tagExtras(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, int offset) {
        if (!(state instanceof AvatarRenderState avatar) || !PvpVisuals.nametagPing && !PvpVisuals.nametagHealth) return;
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null || state.nameTagAttachment == null ? null : mc.level.getEntity(avatar.id);
        if (!(entity instanceof Player player)) return;
        Font font = mc.font;
        Vec3 at = state.nameTagAttachment;
        pose.pushPose();
        pose.translate(at.x, at.y + 0.5, at.z);
        pose.rotate(camera.orientation);
        float scale = 0.025f * PvpVisuals.tagScale;
        pose.scale(scale, -scale, scale);
        // the tag shows the renamed name (NametagBackgroundMixin), moved right by the Lads badge: Ping and Health follow it
        var tag = com.thelads.core.v26_2.feature.NativeNicknames.rename(state.nameTag);
        float x = font.width(tag) / 2f + 3;
        if (com.thelads.core.v26_2.feature.NametagBadgeContext.isBadgeActive(tag)) x += (LadsBadge.NAMETAG_SIZE + LadsBadge.GAP) / 2f;
        if (PvpVisuals.nametagPing && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) x = extra(collector, pose, font, info.getLatency(), MS, PvpVisuals.pingColor(info.getLatency()), x, offset, state.lightCoords) + 3;
        }
        if (PvpVisuals.nametagHealth) extra(collector, pose, font, (int) Math.ceil(player.getHealth()), HEART, 0xFFFF5555, x, offset, state.lightCoords);
        pose.popPose();
    }

    private static float extra(SubmitNodeCollector collector, PoseStack pose, Font font, int value, FormattedCharSequence unit, int color, float x, int y,
                               int light) {
        int v = Math.max(0, Math.min(999, value));
        FormattedCharSequence number = NUMBERS[v];
        if (number == null) NUMBERS[v] = number = FormattedCharSequence.forward(PvpVisuals.number(v), Style.EMPTY);
        int faded = PvpVisuals.alpha(color, PvpVisuals.tagAlpha);
        collector.submitText(pose, x, y, number, false, Font.DisplayMode.NORMAL, light, faded, 0, 0);
        x += font.width(number);
        collector.submitText(pose, x, y, unit, false, Font.DisplayMode.NORMAL, light, faded, 0, 0);
        return x + font.width(unit);
    }

    private static void tick(Minecraft mc) {
        PvpVisuals.tick();
        if (LightOverlay.active() && mc.level != null && mc.player != null) scan(mc.level, mc.player);
        GlintColorizerModule glint = PvpVisuals.GLINT;
        GLINT_COLOR.set(glint.red, glint.green, glint.blue, 1);
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        int def = hit.on ? HitColorModule.overlayPixel(hit.argb) : VANILLA_HURT;
        int fire = hit.on ? HitColorModule.overlayPixel(hit.fireArgb) : VANILLA_HURT;
        int fall = hit.on ? HitColorModule.overlayPixel(hit.fallArgb) : VANILLA_HURT;
        int drown = hit.on ? HitColorModule.overlayPixel(hit.drownArgb) : VANILLA_HURT;
        int key = def ^ (fire << 1) ^ (fall << 2) ^ (drown << 3);
        if (key != lastHurtKey) {
            // Hit Color: the overlay texture's hurt rows (v 0 to 7) in the new colours.
            DynamicTexture texture = ((OverlayTextureAccessor26) mc.gameRenderer.overlayTexture()).lads$texture();
            NativeImage image = texture.getPixels();
            if (image == null) return;
            for (int x = 0; x < 16; x++) {
                image.setPixel(x, 0, def);
                image.setPixel(x, 1, fire);
                image.setPixel(x, 2, fall);
                image.setPixel(x, 3, def);
                image.setPixel(x, 4, drown);
                image.setPixel(x, 5, def);
                image.setPixel(x, 6, def);
                image.setPixel(x, 7, def);
            }
            texture.upload();
            lastHurtKey = key;
            hurtPixel = def;
        }
    }

    /** Block Overlay: the outline's colour (ARGB) for submitHitOutline; alpha 0 hides it. */
    public static int outlineColor(int vanilla) {
        BlockOverlayModule overlay = PvpVisuals.BLOCK_OVERLAY;
        return overlay.on ? overlay.outlineArgb : vanilla;
    }

    /** Block Overlay: the outline's width in pixels; 0 (no line) when the outline is off. */
    public static float outlineWidth(float vanilla) {
        BlockOverlayModule overlay = PvpVisuals.BLOCK_OVERLAY;
        return !overlay.on ? vanilla : overlay.outlineArgb >>> 24 == 0 ? 0 : overlay.lineWidth;
    }

    /** Block Overlay's fill over the targeted block's shape, after the outline was submitted. */
    public static void fill(PoseStack pose, SubmitNodeCollector collector, LevelRenderState level) {
        BlockOverlayModule overlay = PvpVisuals.BLOCK_OVERLAY;
        BlockOutlineRenderState state = level.blockOutlineRenderState;
        if (!overlay.on || overlay.fillArgb >>> 24 == 0 || state == null || state.shape().isEmpty()) return;
        Vec3 camera = level.cameraRenderState.pos;
        BlockPos pos = state.pos();
        FILL.set(state.shape(), overlay.fillArgb);
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        collector.submitCustomGeometry(pose, RenderTypes.debugFilledBox(), FILL);
        pose.popPose();
    }

    /** Hitboxes in place of EntityHitboxDebugRenderer.showHitboxes; false while the module is off (vanilla draws, with F3+B). */
    public static boolean hitbox(Entity entity, float partial) {
        HitboxesModule module = PvpVisuals.HITBOXES;
        if (!module.on) return false;
        if (!module.shows(kind(entity))) return true;
        if (styledWidth != module.lineWidth || styledBox != module.boxArgb || styledHurt != module.hurtArgb) {
            styledWidth = module.lineWidth;
            styledBox = module.boxArgb;
            styledHurt = module.hurtArgb;
            boxStyle = GizmoStyle.stroke(styledBox, styledWidth);
            hurtStyle = GizmoStyle.stroke(styledHurt, styledWidth);
            eyeStyle = GizmoStyle.stroke(0xFFFF0000, styledWidth);
        }
        // The gizmo API takes boxes and points as objects, as vanilla's own F3+B drawing does.
        Vec3 latest = entity.position(), now = entity.getPosition(partial);
        AABB box = entity.getBoundingBox().move(now.x - latest.x, now.y - latest.y, now.z - latest.z);
        boolean hurt = entity instanceof LivingEntity living && living.hurtTime > 0;
        top(Gizmos.cuboid(box, hurt ? hurtStyle : boxStyle), module);
        float eye = entity.getEyeHeight();
        if (module.eyes && entity instanceof LivingEntity)
            top(Gizmos.cuboid(new AABB(box.minX, box.minY + eye - 0.01, box.minZ, box.maxX, box.minY + eye + 0.01, box.maxZ), eyeStyle), module);
        if (module.look) {
            Vec3 view = entity.getViewVector(partial);
            Vec3 from = now.add(0, eye, 0);
            top(Gizmos.arrow(from, from.add(view.x * 2, view.y * 2, view.z * 2), 0xFF0000FF, styledWidth), module);
        }
        return true;
    }

    private static void top(GizmoProperties gizmo, HitboxesModule module) {
        if (module.walls) gizmo.setAlwaysOnTop();
    }

    /**
     * Hitboxes while F3+B is off: vanilla's hitbox renderer runs anyway (it skips invisible entities and yourself in first person).
     * Reduced debug info hides them, as it hides F3+B.
     */
    public static void moreHitboxes(double camX, double camY, double camZ, Frustum frustum, float partial) {
        if (!PvpVisuals.HITBOXES.on) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES) || mc.showOnlyReducedInfo()) return;
        if (hitboxes == null) hitboxes = new EntityHitboxDebugRenderer(mc);
        hitboxes.emitGizmos(camX, camY, camZ, null, frustum, partial); // it never reads the debug values
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

    /** Mount Opacity: the entity is the player's mount and the module draws it see-through. */
    public static boolean isMount(Entity entity) {
        if (!PvpVisuals.MOUNT_OPACITY.see) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entity == mc.player.getVehicle();
    }

    /** Mount Opacity's model colour: white at the module's opacity. */
    public static int mountColor() {
        return Math.round(PvpVisuals.MOUNT_OPACITY.alpha * 255) << 24 | 0xFFFFFF;
    }

    /** Glint Colorizer: the render type about to be drawn scrolls a glint texture (items, entities, armour). */
    public static boolean isGlint(TextureTransform transform) {
        return PvpVisuals.GLINT.on && (transform == TextureTransform.GLINT_TEXTURING || transform == TextureTransform.ENTITY_GLINT_TEXTURING
            || transform == TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING);
    }

    /** Glint Colorizer's speed: the clock the glint scrolls by. */
    public static long glintMillis(long millis) {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        return glint.on ? (long) (millis * (double) glint.speedFactor) : millis;
    }

    /** Shiny Potions: a potion with effects glints, unless its item data says otherwise (the glint override component). */
    public static boolean shiny(ItemStack stack) {
        GlintColorizerModule glint = PvpVisuals.GLINT;
        if (!glint.on || !glint.shiny || !(stack.getItem() instanceof PotionItem) || stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != null)
            return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.hasEffects();
    }

    /** Block Overlay's fill: one box over the shape's bounds, a hair outside it, in quads (debugFilledBox). Reused every frame. */
    private static final class Fill implements SubmitNodeCollector.CustomGeometryRenderer {
        private float x0, y0, z0, x1, y1, z1;
        private int color;

        void set(VoxelShape shape, int argb) {
            x0 = (float) shape.min(Direction.Axis.X) - 0.002F;
            y0 = (float) shape.min(Direction.Axis.Y) - 0.002F;
            z0 = (float) shape.min(Direction.Axis.Z) - 0.002F;
            x1 = (float) shape.max(Direction.Axis.X) + 0.002F;
            y1 = (float) shape.max(Direction.Axis.Y) + 0.002F;
            z1 = (float) shape.max(Direction.Axis.Z) + 0.002F;
            color = argb;
        }

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer quads) {
            quad(pose, quads, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // bottom
            quad(pose, quads, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // top
            quad(pose, quads, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // north
            quad(pose, quads, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // south
            quad(pose, quads, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // west
            quad(pose, quads, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // east
        }

        private void quad(PoseStack.Pose pose, VertexConsumer quads, float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz) {
            quads.addVertex(pose, ax, ay, az).setColor(color);
            quads.addVertex(pose, bx, by, bz).setColor(color);
            quads.addVertex(pose, cx, cy, cz).setColor(color);
            quads.addVertex(pose, dx, dy, dz).setColor(color);
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
        MlgClutch26.renderWorld(context);
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
