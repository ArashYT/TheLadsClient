/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.server.packs.repository.Pack
 *  net.minecraft.server.packs.repository.PackRepository
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

public class TexturePackHudElement
extends HudElement {
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int PADDING = 4;

    public TexturePackHudElement() {
        this.x = 5;
        this.y = 285;
        this.width = 120;
        this.height = 24;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Module m = ModuleManager.getInstance().getModule("TexturePacks");
        boolean showAll = this.optBool("Show All", false);
        int maxPacks = this.optCycle("Max Packs", 3) + 1;
        boolean showHidden = this.optBool("Show Hidden", false);
        List<String> packNames = this.getActivePacks(mc, showHidden);
        int total = packNames.size();
        int show = showAll ? total : Math.min(total, maxPacks);
        int rowH = 20;
        this.width = 24 + mc.font.width("00 packs") + 4;
        this.height = Math.max(rowH, show * 18 + 8);
        this.drawBackground(g);
        if (show == 0) {
            this.drawCenteredText(g, "No packs");
            return;
        }
        int ix = this.x + 4;
        int iy = this.y + 4;
        int rendered = 0;
        for (int i = packNames.size() - 1; i >= 0 && rendered < show; ++rendered, --i) {
            String packId = packNames.get(i);
            Identifier iconId = Identifier.fromNamespaceAndPath("theladscore", "packicon/" + TexturePackHudElement.safeId(packId));
            this.drawPackIcon(g, iconId, ix, iy, 16);
            String label = TexturePackHudElement.truncate(TexturePackHudElement.displayName(packId), 14);
            Objects.requireNonNull(mc.font);
            int ly = iy + (16 - 9) / 2 + 1;
            boolean shadow = this.resolveTextShadow();
            g.text(mc.font, label, ix + 16 + 3, ly, this.resolveColor(), shadow);
            iy += 18;
        }
        if (!showAll && total > show) {
            Objects.requireNonNull(mc.font);
            this.height = iy - this.y + 9 + 4;
            this.drawBackground(g);
            g.text(mc.font, "+" + (total - show) + " more", ix, iy, -5592406, false);
        }
    }

    private List<String> getActivePacks(Minecraft mc, boolean showHidden) {
        ArrayList<String> result;
        block4: {
            result = new ArrayList<String>();
            try {
                PackRepository repo = mc.getResourcePackRepository();
                for (Pack pack : repo.getSelectedPacks()) {
                    String id = pack.getId();
                    if (id.equals("vanilla") || !showHidden && !id.startsWith("file/")) continue;
                    result.add(id);
                }
            }
            catch (Exception ignored) {
                if (mc.options == null || mc.options.resourcePacks == null) break block4;
                for (String name : mc.options.resourcePacks) {
                    result.add("file/" + name);
                }
            }
        }
        return result;
    }

    private static String displayName(String packId) {
        String s;
        String string = s = packId.startsWith("file/") ? packId.substring(5) : packId;
        if (s.endsWith(".zip")) {
            s = s.substring(0, s.length() - 4);
        }
        return s;
    }

    private void drawPackIcon(GuiGraphicsExtractor g, Identifier icon, int x, int y, int size) {
        try {
            g.pose().pushMatrix();
            g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0f, 0.0f, size, size, size, size);
            g.pose().popMatrix();
        }
        catch (Exception ignored) {
            g.fill(x, y, x + size, y + size, -9673729);
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, -15066578);
        }
    }

    private boolean resolveTextShadow() {
        return HudSettings.getInstance().isTextShadow();
    }

    private static String safeId(String raw) {
        return raw.replaceAll("[^a-z0-9_\\-./]", "_").toLowerCase();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }
}

