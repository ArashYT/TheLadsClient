package com.thelads.core.v26_2.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.repository.Pack;

/**
 * Resource pack icons for the Pack HUD, loaded as the pack screen does: the pack's own pack.png, else the vanilla pack's, else the
 * pack screen's "unknown pack" picture. Uploaded when a pack is first drawn and kept per pack id; a pack that left the selection is
 * freed ({@link #retain}), a pack the repository re-created (resource reload) is read again.
 */
public final class PackIcons {
    private record Icon(Identifier texture, Pack pack, int width, int height, boolean owned) {}

    private static final Identifier UNKNOWN = Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");
    private static final Map<String, Icon> ICONS = new HashMap<>();

    private PackIcons() {}

    public static void draw(GuiGraphicsExtractor g, String id, int x, int y, int size) {
        Pack pack = pack(id);
        Icon icon = ICONS.get(id);
        if (icon == null || icon.pack != pack) {
            if (icon != null) release(icon);
            ICONS.put(id, icon = load(id, pack));
        }
        g.blit(RenderPipelines.GUI_TEXTURED, icon.texture, x, y, 0, 0, size, size, icon.width, icon.height, icon.width, icon.height);
    }

    /** Frees the icons of packs that are no longer selected. */
    public static void retain(Collection<String> ids) {
        ICONS.entrySet().removeIf(entry -> {
            if (ids.contains(entry.getKey())) return false;
            release(entry.getValue());
            return true;
        });
    }

    private static Pack pack(String id) {
        var repository = Minecraft.getInstance().getResourcePackRepository();
        return repository == null ? null : repository.getPack(id);
    }

    private static void release(Icon icon) {
        if (icon.owned) Minecraft.getInstance().getTextureManager().release(icon.texture);
    }

    private static Icon load(String id, Pack pack) {
        NativeImage image = read(pack);
        if (image == null) image = read(pack("vanilla"));
        if (image == null) return new Icon(UNKNOWN, pack, 64, 64, false);
        var texture = Identifier.fromNamespaceAndPath("theladscore", "pack_icons/" + Integer.toHexString(id.hashCode()) + "_" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "Pack icon " + id, image));
        return new Icon(texture, pack, image.getWidth(), image.getHeight(), true);
    }

    /** The pack's pack.png, or null when it has none or cannot be opened (a mod-provided pack may not allow it). */
    private static NativeImage read(Pack pack) {
        if (pack == null) return null;
        try (PackMetadataResources resources = pack.openMetadata()) {
            var icon = resources.getRootResource("pack.png");
            if (icon == null) return null;
            try (InputStream stream = icon.get()) {
                return NativeImage.read(stream);
            }
        } catch (Exception | LinkageError failure) {
            return null;
        }
    }
}
