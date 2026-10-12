package com.thelads.core.v26_2.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thelads.core.client.WavyCape;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.TntTimerModule;
import com.thelads.core.modules.WavyCapesModule;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Wavy Capes and TNT Timer on 26.3. The cape mesh (common WavyCape) is submitted as custom geometry instead of the cape model, once
 * vanilla's cape layer has decided the cape shows (invisible, the skin option, wings, a cape texture) and moved it for chest armour;
 * the render type is the layer's own, so the Capes cutout stays (CapeLayerWavyMixin). The timer is submitted after the TNT and
 * minecart renderers like a name tag (TntTimerMixin, MinecartTntTimerMixin).
 */
public final class World26 {
    private static final Vector3f POS = new Vector3f(), NORMAL = new Vector3f();
    private static final Map<Entity, Emitter> CAPES = new WeakHashMap<>();
    /** QA: capes and timers submitted so far. */
    public static int capesDrawn, timersDrawn;
    /** tr7zw's Wavey Capes installed by the player (the Lads pack retires it): it draws the capes, Wavy Capes steps aside. */
    private static boolean external;

    private World26() {}

    public static void register() {
        external = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("waveycapes");
        if (external) ModuleSupport.registerExternal(WavyCapesModule.NAME, "Wavey Capes", "waveycapes", true);
        else ModuleSupport.registerBuiltIn(WavyCapesModule.NAME);
        ModuleSupport.registerBuiltIn(TntTimerModule.NAME);
    }

    /** This entity's cape (QA logs). */
    public static WavyCape capeOf(Entity entity) { return CAPES.computeIfAbsent(entity, key -> new Emitter(new WavyCape())).cape; }

    /** In place of the cape model's submitModel: false leaves the cape to vanilla. */
    public static boolean cape(SubmitNodeCollector collector, AvatarRenderState state, PoseStack pose, RenderType type, int light, int overlay, int outline) {
        Minecraft mc = Minecraft.getInstance();
        if (external || mc.level == null || !(mc.level.getEntity(state.id) instanceof Avatar avatar)) return false; // previews of made-up players
        WavyCapesModule settings = WavyCapesModule.active(avatar == mc.player);
        if (settings == null) return false;
        Emitter emitter = CAPES.computeIfAbsent(avatar, key -> new Emitter(new WavyCape()));
        emitter.cape.update(settings, avatar.tickCount, avatar.getX() - avatar.xo, avatar.getY() - avatar.yo, avatar.getZ() - avatar.zo,
            avatar.yBodyRotO, 0); // sneaking tilts the body, and the cape with it, below
        emitter.quads = emitter.cape.build(settings, Math.max(0, Math.min(1, state.ageInTicks - avatar.tickCount)));
        emitter.light = light;
        emitter.overlay = overlay;
        pose.pushPose();
        if (state.isCrouching) { // the body as HumanoidModel.setupAnim tilts it; the cape hangs from the body (PlayerCapeModel)
            pose.translate(0, 3.2f / 16, 0);
            pose.rotate(Axis.XP, 0.5f);
        }
        pose.translate(0, 0, 0.125f); // the cape's pivot, 2 pixels behind the body's centre
        collector.submitCustomGeometry(pose, type, emitter);
        if (outline != 0 && type.outline().isPresent()) { // glowing: the same mesh in the outline pass, as submitModel does for models
            if (emitter.outline == null) emitter.outline = new Emitter(emitter.cape);
            emitter.outline.quads = emitter.quads;
            emitter.outline.light = LightCoordsUtil.FULL_BRIGHT;
            emitter.outline.overlay = overlay;
            emitter.outline.color = outline;
            collector.submitCustomGeometry(pose, type.outline().get(), emitter.outline);
        }
        pose.popPose();
        capesDrawn++;
        return true;
    }

    /** The time left above a primed TNT or lit TNT minecart, after its renderer (the pose at the entity's feet). */
    public static void tnt(EntityRenderState state, float ticksLeft, boolean minecart, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TntTimerModule module = TntTimerModule.active();
        Minecraft mc = Minecraft.getInstance();
        if (module == null || minecart && !module.minecarts.get() || mc.gui.hud.isHidden() || !module.shows(state.distanceToCameraSq)) return;
        String text = TntTimerModule.label(ticksLeft);
        pose.pushPose();
        pose.translate(0, state.boundingBoxHeight + 0.5f, 0); // where its name tag would be
        pose.rotate(camera.orientation);
        float scale = module.scale();
        pose.scale(scale, -scale, scale);
        // background and text submitted as vanilla's text displays do (a background inside the text submit hid the text)
        Font.DisplayMode mode = module.seeThrough() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET;
        float x = -mc.font.width(text) / 2f;
        if (module.background.get())
            collector.submitTextBackground(pose, x - 1, -1, -x, 9, mc.options.getBackgroundColor(0.25f), mode, LightCoordsUtil.FULL_BRIGHT);
        collector.submitText(pose, x, 0, FormattedCharSequence.forward(text, Style.EMPTY), module.shadow.get(), mode, LightCoordsUtil.FULL_BRIGHT,
            TntTimerModule.color(ticksLeft), 0, 0);
        pose.popPose();
        timersDrawn++;
    }

    /** Writes a cape's mesh when the submitted geometry is drawn (later in the frame); its outline twin draws the glowing outline. */
    private static final class Emitter implements SubmitNodeCollector.CustomGeometryRenderer {
        final WavyCape cape;
        int quads, light, overlay, color = -1;
        Emitter outline;

        Emitter(WavyCape cape) { this.cape = cape; }

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer buffer) {
            Matrix4f matrix = pose.pose();
            float[] mesh = cape.mesh;
            for (int at = 0, end = quads * 4 * WavyCape.STRIDE; at < end; at += WavyCape.STRIDE) {
                matrix.transformPosition(mesh[at], mesh[at + 1], mesh[at + 2], POS);
                pose.transformNormal(mesh[at + 5], mesh[at + 6], mesh[at + 7], NORMAL);
                buffer.addVertex(POS.x, POS.y, POS.z, color, mesh[at + 3], mesh[at + 4], overlay, light, NORMAL.x, NORMAL.y, NORMAL.z);
            }
        }
    }
}
