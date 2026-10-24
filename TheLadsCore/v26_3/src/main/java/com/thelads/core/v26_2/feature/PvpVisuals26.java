package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.ModuleSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** PvP visuals on 26.x (common PvpVisuals): module statuses and the name tag's ping and health. The hooks are the mixins in mixin.pvp. */
public final class PvpVisuals26 {
    private static final FormattedCharSequence MS = FormattedCharSequence.forward("ms", Style.EMPTY), HEART = FormattedCharSequence.forward("\u2764", Style.EMPTY);
    /** "0".."999" as text to submit, built once each (PvpVisuals.number). */
    private static final FormattedCharSequence[] NUMBERS = new FormattedCharSequence[1000];

    private PvpVisuals26() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(PvpVisuals.RENDER_TWEAKS, PvpVisuals.OVERLAYS, PvpVisuals.TOTEM, PvpVisuals.DARK_MODE,
            PvpVisuals.NAMETAG_HEALTH);
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
        float x = font.width(NativeNicknames.rename(state.nameTag)) / 2f + 3; // the tag shows the renamed name (NametagBackgroundMixin)
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
}
