package com.thelads.core.v1_20_1.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.WavyCape;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.TntTimerModule;
import com.thelads.core.modules.WavyCapesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Wavy Capes and TNT Timer on 1.20.1 (plain Fabric and Zero SMP). The cape mesh (common WavyCape) is drawn instead of
 * PlayerModel.renderCloak inside vanilla's cape layer, so vanilla's own rules still decide whether a cape shows (elytra, invisible,
 * the skin option, a cape texture) and the Capes render type stays (CapeLayerWavyMixin). The timer is drawn after the TNT and
 * minecart renderers like a name tag (TntTimerMixin).
 */
public final class World1201 {
    private static final Vector3f POS = new Vector3f(), NORMAL = new Vector3f();
    /** tr7zw's Wavey Capes installed by the player (the Lads packs retire it): it draws the capes, Wavy Capes steps aside. */
    private static boolean external;

    private World1201() {}

    public static void register() {
        external = com.thelads.core.v1_20_1.compat.ModPresence1201.loaded("waveycapes");
        if (external) ModuleSupport.registerExternal(WavyCapesModule.NAME, "Wavey Capes", "waveycapes", true);
        else ModuleSupport.registerBuiltIn(WavyCapesModule.NAME);
        ModuleSupport.registerBuiltIn(TntTimerModule.NAME);
        WorldQa1201.register();
    }

    /**
     * In place of renderCloak: false leaves the cape to vanilla. The pose arrives turned by vanilla's cape angles; it is put back to
     * the layer's pose (pop and push, vanilla pops after the call) and turned by the cape's own physics instead.
     */
    public static boolean cape(AbstractClientPlayer player, float partial, PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        WavyCapesModule settings = external ? null : WavyCapesModule.active(player == Minecraft.getInstance().player);
        if (settings == null) return false;
        boolean crouching = player.isCrouching();
        WavyCape cape = WavyCape.of(player);
        cape.update(settings, player.tickCount, player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo,
            player.yBodyRotO, crouching ? 25 : 0);
        int quads = cape.build(settings, partial);
        pose.popPose();
        pose.pushPose();
        pose.translate(0, 0, 0.125f);
        // the cloak's pivot as PlayerModel.setupAnim moves it for a chest item and crouching (box pixels; the frame's z is the box's -z)
        boolean armour = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        float y = armour ? crouching ? 0.8f : -0.85f : crouching ? 1.85f : 0, z = armour ? crouching ? 0.3f : -1.1f : crouching ? 1.4f : 0;
        pose.translate(0, y / 16, -z / 16);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        float[] mesh = cape.mesh;
        for (int at = 0, end = quads * 4 * WavyCape.STRIDE; at < end; at += WavyCape.STRIDE) {
            matrix.transformPosition(mesh[at], mesh[at + 1], mesh[at + 2], POS);
            normals.transform(mesh[at + 5], mesh[at + 6], mesh[at + 7], NORMAL);
            buffer.vertex(POS.x, POS.y, POS.z, 1, 1, 1, 1, mesh[at + 3], mesh[at + 4], overlay, light, NORMAL.x, NORMAL.y, NORMAL.z);
        }
        return true;
    }

    /** The time left above a primed TNT or lit TNT minecart, after its renderer (the pose at the entity's feet). */
    public static void tnt(Entity entity, float ticksLeft, boolean minecart, PoseStack pose, MultiBufferSource buffers) {
        TntTimerModule module = TntTimerModule.active();
        Minecraft mc = Minecraft.getInstance();
        if (module == null || minecart && !module.minecarts.get() || mc.options.hideGui
            || !module.shows(mc.getEntityRenderDispatcher().distanceToSqr(entity))) return;
        String text = TntTimerModule.label(ticksLeft);
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() + 0.5f, 0); // where its name tag would be
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        // As vanilla's text displays face the camera (not as name tags, which have no shadow): the text's shadow then lies behind it.
        float scale = module.scale();
        pose.scale(-scale, -scale, -scale);
        Matrix4f matrix = pose.last().pose();
        Font font = mc.font;
        boolean through = module.throughBlocks.get();
        float x = -font.width(text) / 2f, right = -x;
        if (module.background.get()) { // drawn apart from the text, as text displays do, so it never covers the shadow
            int color = (int) (mc.options.getBackgroundOpacity(0.25f) * 255) << 24;
            VertexConsumer background = buffers.getBuffer(through ? RenderType.textBackgroundSeeThrough() : RenderType.textBackground());
            background.vertex(matrix, x - 1, -1, 0).color(color).uv2(LightTexture.FULL_BRIGHT).endVertex();
            background.vertex(matrix, x - 1, 9, 0).color(color).uv2(LightTexture.FULL_BRIGHT).endVertex();
            background.vertex(matrix, right, 9, 0).color(color).uv2(LightTexture.FULL_BRIGHT).endVertex();
            background.vertex(matrix, right, -1, 0).color(color).uv2(LightTexture.FULL_BRIGHT).endVertex();
        }
        font.drawInBatch(text, x, 0, TntTimerModule.color(ticksLeft), module.shadow.get(), matrix, buffers,
            through ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
