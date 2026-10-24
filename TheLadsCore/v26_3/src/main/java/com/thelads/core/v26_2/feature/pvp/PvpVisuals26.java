package com.thelads.core.v26_2.feature.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.BlockOverlayModule;
import com.thelads.core.modules.GlintColorizerModule;
import com.thelads.core.modules.HitColorModule;
import com.thelads.core.modules.HitboxesModule;
import com.thelads.core.v26_2.feature.pvp.mixin.OverlayTextureAccessor26;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
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

/**
 * The PvP visual modules on 26.3 (common PvpVisuals): Block Overlay (LevelRenderer.submitBlockOutline), Hitboxes (the F3+B
 * EntityHitboxDebugRenderer's gizmos, also run while F3+B is off), Hide Mob Deaths (EntityRenderDispatcher.shouldRender), Mount
 * Opacity (LivingEntityRenderer: a flag on the render state), Glint Colorizer (the glint render types' colour modulator and scroll)
 * with Shiny Potions (ItemStack.hasFoil) and Hit Color, which repaints the entity overlay texture's hurt row when it changes. Damage
 * Tint is drawn by the common HUD. Mixins: theladscore.v26_3.pvp.mixins.json.
 */
public final class PvpVisuals26 {
    /** OverlayTexture's own hurt pixel (ARGB): red at 70% alpha, a 30% red flash. */
    static final int VANILLA_HURT = 0xB2FF0000; // ldc -1291911168
    /** The glint shader's colour modulator for Glint Colorizer, and the model offset DynamicGpuData's other transforms use. */
    public static final Vector4f GLINT_COLOR = new Vector4f(1, 1, 1, 1);
    public static final Vector3f NO_OFFSET = new Vector3f();
    private static final Fill FILL = new Fill();
    private static int hurtPixel;
    private static int styledBox, styledHurt;
    private static float styledWidth = -1;
    private static GizmoStyle boxStyle, hurtStyle, eyeStyle;
    private static EntityHitboxDebugRenderer hitboxes;
    /** The render type RenderType.writeDynamicTransforms writes now is a glint (its texture transform said so). */
    public static boolean glintNow;

    /** Mount Opacity's mark on a LivingEntityRenderState (PvpMountStateMixin26): the player's mount, drawn see-through. */
    public interface MountState {
        boolean lads$mount();

        void lads$mount(boolean mount);
    }

    private PvpVisuals26() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(PvpVisuals.MODULES);
        ClientTickEvents.END_CLIENT_TICK.register(PvpVisuals26::tick);
    }

    private static void tick(Minecraft mc) {
        PvpVisuals.tick();
        GlintColorizerModule glint = PvpVisuals.GLINT;
        GLINT_COLOR.set(glint.red, glint.green, glint.blue, 1);
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        int pixel = hit.on ? HitColorModule.overlayPixel(hit.argb) : VANILLA_HURT;
        if (pixel != hurtPixel) {
            // Hit Color: the overlay texture's hurt rows (v 0 to 7; models sample v 3) in the new colour.
            DynamicTexture texture = ((OverlayTextureAccessor26) mc.gameRenderer.overlayTexture()).lads$texture();
            NativeImage image = texture.getPixels();
            if (image == null) return;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) image.setPixel(x, y, pixel);
            texture.upload();
            hurtPixel = pixel;
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
}
