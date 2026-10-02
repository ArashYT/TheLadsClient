package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.OldAnimations;
import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.modules.OldAnimationsModule.Platform;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * 1.7 Animations on 1.8.9 (OldAnimationsModule, built in through TheLadsCore189.GAMEPLAY_MODULES). The mixins ask this class and
 * replay the shared recipes (OldAnimations) through GlStateManager; with the module or an option off each hook leaves 1.8.9's
 * call as it was. 1.8.9 blocks with swords for real, so Blockhitting and the block pose follow its blocking; it has no attack
 * cooldown or shields, so the menu hides those two options.
 */
public final class OldAnimations189 {
    public static final Platform PLATFORM = Platform.V1_8_9;
    public static final OldAnimationsModule MODULE = (OldAnimationsModule) ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
    /** Recipe ops as GL calls: GlStateManager post-multiplies the current matrix, as the recipes expect. */
    public static final OldAnimations.Sink GL = new OldAnimations.Sink() {
        @Override public void translate(float x, float y, float z) { GlStateManager.translate(x, y, z); }
        @Override public void rotate(float degrees, float x, float y, float z) { GlStateManager.rotate(degrees, x, y, z); }
        @Override public void scale(float x, float y, float z) { GlStateManager.scale(x, y, z); }
    };
    /** 1.7.10 RenderFish: the first-person line leaves the rod here (camera space); 1.8 moved it to (-0.36, 0.03, 0.35) for its rod. */
    private static final Vec3 ROD_LINE = new Vec3(-0.5, 0.03, 0.8);

    /** QA only (Probe151): each place a hook changed 1.8.9's result, counted per draw. */
    public enum Hook { FP_HAND, FP_ICON, TP_ITEM, TP_ARM, ARMOUR, HEARTS, SNEAK, DROP, FIRE, LINE, SWING }
    private static final long[] HITS = new long[Hook.values().length];
    /** QA only: the largest swing the first-person hand showed while using an item since the last reset. */
    public static float usedSwing;
    private static Entity eyeEntity;
    private static float eyeBefore, eye;

    private OldAnimations189() {}

    public static void register() {
        MODULE.setPlatform(PLATFORM);
    }

    public static boolean active(Feature feature) {
        return MODULE.active(feature, PLATFORM);
    }

    public static void hit(Hook hook) {
        HITS[hook.ordinal()]++;
    }

    public static long hits(Hook hook) {
        return HITS[hook.ordinal()];
    }

    public static void resetHits() {
        Arrays.fill(HITS, 0);
        usedSwing = 0;
    }

    /** The flat item kind 1.7 drew differently, or null for block and built-in models, which keep 1.8.9's rendering. */
    public static Held held(ItemStack stack) {
        IBakedModel model = Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getItemModel(stack);
        if (model == null || model.isGui3d() || model.isBuiltInRenderer()) return null;
        Item item = stack.getItem();
        if (item == Items.bow) return Held.BOW;
        return !item.isFull3D() ? Held.ITEM : item.shouldRotateAroundWhenRendering() ? Held.ROD : Held.TOOL;
    }

    /** What the player does with the stack as 1.7 saw it: its use action while the use count is above 0; null keeps vanilla. */
    public static Use use(EntityPlayer player, ItemStack stack) {
        if (player.getItemInUseCount() <= 0) return Use.NONE;
        switch (stack.getItemUseAction()) {
            case EAT: case DRINK: return Use.EAT_DRINK;
            case BLOCK: return Use.BLOCK;
            case BOW: return Use.BOW;
            default: return null;
        }
    }

    /** Before a TransformType.NONE draw: RenderItem.renderItem draws models at half size, the recipes place the full-size mesh. */
    public static void fullSizeMesh() {
        GlStateManager.scale(2, 2, 2);
    }

    /**
     * Every client tick (END). Instant sneak camera: 1.7's per-tick eye height for the view entity. Blockhitting and Swing while
     * using items: 1.7 swung the arm while the attack key was held on a block during a use (1.8 dropped that), so the swing shows
     * on the blocking sword, the food or the bow. Drawn only: no packet, no block damage, so servers see 1.8.9 play.
     */
    public static void tick(Minecraft mc) {
        Entity view = mc.getRenderViewEntity();
        if (view != eyeEntity) eyeBefore = eye = view != null ? view.getEyeHeight() : 0;
        else if (view != null) {
            eyeBefore = eye;
            eye = OldAnimations.sneakEyeHeight(eye, view.getEyeHeight());
        }
        eyeEntity = view;

        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.currentScreen != null || !player.isUsingItem() || !mc.gameSettings.keyBindAttack.isKeyDown()
            || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        Use use = use(player, player.getItemInUse());
        if (use == null || MODULE.swingShown(PLATFORM, use, 1) == 0) return;
        // EntityLivingBase.swingItem without EntityPlayerSP's packet; 3 is its restart at half a swing (6 ticks, without haste).
        if (!player.isSwingInProgress || player.swingProgressInt >= 3 || player.swingProgressInt < 0) {
            player.swingProgressInt = -1;
            player.isSwingInProgress = true;
            hit(Hook.SWING);
        }
    }

    /** The camera's eye height (EntityRenderer.orientCamera): with Instant sneak camera, 1.7's per-tick height, interpolated. */
    public static float cameraEyeHeight(Entity entity, float partialTicks) {
        float vanilla = entity.getEyeHeight();
        if (entity != eyeEntity || !active(Feature.INSTANT_SNEAK)) return vanilla;
        float height = trackedEyeHeight(partialTicks);
        if (height != vanilla) hit(Hook.SNEAK);
        return height;
    }

    /** QA reads it directly: the eye height the camera gets at a partial tick while Instant sneak camera is on. */
    public static float trackedEyeHeight(float partialTicks) {
        return eyeBefore + (eye - eyeBefore) * partialTicks;
    }

    /** Low Fire: the fire overlay's quads 0.3 lower, as 26.x LowFireMixin. */
    public static float fireY(float y) {
        if (!active(Feature.LOW_FIRE)) return y;
        hit(Hook.FIRE);
        return y - 0.3f;
    }

    /** RenderFish's line origin for the angler: 1.7's in first person with the 1.7 rod position (it draws the rod tip there). */
    public static Vec3 rodLine(Vec3 vanilla, EntityPlayer angler) {
        Minecraft mc = Minecraft.getMinecraft();
        if (angler != mc.thePlayer || mc.gameSettings.thirdPersonView != 0 || !active(Feature.ROD)) return vanilla;
        hit(Hook.LINE);
        return ROD_LINE;
    }
}
