package com.thelads.core.v26_2.feature.paperdoll;

import com.thelads.core.client.hud.PaperDoll;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v26_2.feature.NativeAutohide;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Lads paper doll on 26.x: feeds {@link PaperDoll} every client tick and draws the actual player (skin, armour, held items,
 * pose) in the Paperdoll HUD element's box with the doll's own body and head angles. Stands down while the Paper Doll mod runs.
 */
public final class PaperDoll26 {
    private static final boolean MOD = FabricLoader.getInstance().isModLoaded("paperdoll");
    private static float lastYaw = Float.NaN;
    private PaperDoll26() {}

    public static boolean active() { return !MOD; }

    /** Built in; with the mod installed ExternalModSettings has already listed that instead. */
    public static void register() {
        if (!MOD) ModuleSupport.registerBuiltIn("Paperdoll");
    }

    /** Every client tick: the triggers happening now and how far the view turned. Paused worlds keep the doll as it is. */
    public static void tick(Minecraft mc) {
        var player = mc.player;
        if (MOD || player == null) { lastYaw = Float.NaN; return; }
        if (mc.isPaused()) return;
        float yaw = player.getYRot(), turned = Float.isNaN(lastYaw) ? 0 : Mth.wrapDegrees(yaw - lastYaw);
        lastYaw = yaw;
        PaperDoll.INSTANCE.tick(name -> PlayerActions.happening(player, name), turned);
        PaperDollProbe26.tick(mc);
    }

    /** PaperdollHudElement's box; the HUD editor shows it whatever the triggers say, at full opacity. */
    public static void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean editor) {
        var mc = Minecraft.getInstance();
        if (MOD || mc.player == null) return;
        if (!editor && !PaperDoll.INSTANCE.visible(mc.options.getCameraType().isFirstPerson())) return;
        EntityRenderState state = state(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), x + width / 2 < graphics.guiWidth() / 2);
        // A picture blitted at this opacity (AutohideElementsMixin): the whole doll fades, inside any Autohide fade.
        float scope = NativeAutohide.scopeOpacity;
        NativeAutohide.scopeOpacity = scope * (editor ? 1 : PaperDoll.INSTANCE.opacity());
        try {
            // As vanilla's inventory doll: flipped upright, centred on its bounding box; about 0.43 of the box per block.
            graphics.entity(state, height * 0.43f, new Vector3f(0, state.boundingBoxHeight / 2 + 0.0625f, 0),
                new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), x, y, x + width, y + height);
        } finally {
            NativeAutohide.scopeOpacity = scope;
        }
    }

    /** The player's own render state, turned to the doll's angles; the player itself is never touched. */
    static EntityRenderState state(float partialTick, boolean leftHalfOfScreen) {
        var player = Minecraft.getInstance().player;
        var state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player).createRenderState(player, partialTick);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = PaperDoll.INSTANCE.bodyYaw(leftHalfOfScreen);
            living.yRot = PaperDoll.INSTANCE.headYaw(partialTick);
            living.xRot = living.pose == Pose.FALL_FLYING ? 0 : PaperDoll.INSTANCE.headPitch(player.getXRot(partialTick));
            // HUD size does not follow the player's scale attribute.
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1;
        }
        return state;
    }
}
