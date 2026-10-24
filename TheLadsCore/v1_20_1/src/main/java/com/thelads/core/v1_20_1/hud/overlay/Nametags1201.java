package com.thelads.core.v1_20_1.hud.overlay;

import com.thelads.core.client.Nicknames;
import com.thelads.core.v1_20_1.hud.Hud1201;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/** Nametags (26.3 OwnNametagMixin, NametagBackgroundMixin, NativeNicknames): own tag in third person, background, shadow, renames. */
public final class Nametags1201 {
    private static final String MODULE = "Nametags";
    /** A name tag is drawing (render thread only): its shadowed text goes in front of the shadow (NametagShadowDepthMixin). */
    public static boolean drawing;
    private static com.thelads.core.modules.ToggleNametagsModule cachedModule;
    private static java.util.Map<String, String> lastNames = java.util.Collections.emptyMap();
    private static final java.util.Map<Component, Component> RENAME_CACHE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private Nametags1201() {}

    private static com.thelads.core.modules.ToggleNametagsModule module() {
        com.thelads.core.modules.ToggleNametagsModule m = cachedModule;
        if (m == null && Hud1201.module(MODULE) instanceof com.thelads.core.modules.ToggleNametagsModule found) {
            cachedModule = m = found;
        }
        return m;
    }

    /**
     * LivingEntityRenderer.shouldShowName asks for the camera entity only to hide your own tag: with the option on, in third person,
     * it gets none, so every other vanilla rule (distance, team, invisibility, F1, passengers) still decides. Never over the paper doll.
     */
    public static Entity cameraForNameRule(Minecraft minecraft, LivingEntity entity, Entity camera) {
        var mod = module();
        if (entity == minecraft.player && camera == minecraft.player && !minecraft.options.getCameraType().isFirstPerson()
            && !PaperDoll1201.drawing() && mod != null && mod.isEnabled() && mod.showOwnNametag.get())
            return null;
        return camera;
    }

    public static int background(int vanilla) {
        var mod = module();
        return mod != null && mod.isEnabled() && !mod.renderBackground.get() ? 0 : vanilla;
    }

    /** Nametags' Show Ping and Nametag Health after a player's name (EntityRenderer.renderNameTag, before its pop). */
    public static void extras(Entity entity, Component name, Matrix4f pose, MultiBufferSource buffers, int light) {
        if (!(entity instanceof Player player) || !PvpVisuals.nametagPing && !PvpVisuals.nametagHealth) return;
        Font font = Minecraft.getInstance().font;
        float x = font.width(name) / 2f + 3;
        if (PvpVisuals.nametagPing) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
            if (info != null) x = extra(font, PvpVisuals.number(info.getLatency()), "ms", PvpVisuals.pingColor(info.getLatency()), x, pose, buffers, light) + 3;
        }
        if (PvpVisuals.nametagHealth) extra(font, PvpVisuals.number((int) Math.ceil(player.getHealth())), "\u2764", 0xFFFF5555, x, pose, buffers, light);
    }

    private static float extra(Font font, String number, String unit, int color, float x, Matrix4f pose, MultiBufferSource buffers, int light) {
        int faded = PvpVisuals.alpha(color, PvpVisuals.tagAlpha);
        font.drawInBatch(number, x, 0, faded, false, pose, buffers, Font.DisplayMode.NORMAL, 0, light);
        x += font.width(number);
        font.drawInBatch(unit, x, 0, faded, false, pose, buffers, Font.DisplayMode.NORMAL, 0, light);
        return x + font.width(unit);
    }

    /** Shadow on the normal pass only; the see-through pass behind walls stays flat and readable. */
    public static boolean shadow(boolean vanilla, boolean normalPass) {
        var mod = module();
        return vanilla || normalPass && mod != null && mod.isEnabled() && mod.textShadow.get();
    }

    /** Renamed players (Your Display Name, Nicknames), each piece keeping its style; the same instance when nothing is renamed. */
    public static Component rename(Component text) {
        if (text == null) return text;
        var names = Nicknames.active(Minecraft.getInstance().getUser().getName());
        if (names.isEmpty()) return text;
        if (names != lastNames) {
            lastNames = names;
            RENAME_CACHE.clear();
        }
        Component cached = RENAME_CACHE.get(text);
        if (cached != null) return cached;
        List<String> parts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        text.visit((style, part) -> { parts.add(part); styles.add(style); return Optional.empty(); }, Style.EMPTY);
        String[] renamed = Nicknames.rename(parts.toArray(new String[0]), names);
        if (renamed == null) {
            RENAME_CACHE.put(text, text);
            return text;
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < renamed.length; i++)
            if (!renamed[i].isEmpty()) out.append(Component.literal(renamed[i]).setStyle(styles.get(i)));
        RENAME_CACHE.put(text, out);
        return out;
    }
}
