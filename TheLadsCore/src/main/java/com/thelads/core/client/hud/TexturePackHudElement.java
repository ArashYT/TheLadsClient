package com.thelads.core.client.hud;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;

public class TexturePackHudElement extends HudElement {

    private static final int ICON_SIZE  = 16;
    private static final int ICON_GAP   = 2;
    private static final int PADDING    = 4;

    public TexturePackHudElement() {
        this.x = 5;
        this.y = 285;
        this.width  = 120;
        this.height = 24;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Module m = ModuleManager.getInstance().getModule("TexturePacks");
        boolean showAll  = optBool("Show All", false);
        int maxPacks     = optCycle("Max Packs", 3) + 1; // 1 – 8
        boolean showHidden = optBool("Show Hidden", false);

        // Gather enabled packs from the resource pack repository
        List<String> packNames = getActivePacks(mc, showHidden);
        int total = packNames.size();
        int show  = showAll ? total : Math.min(total, maxPacks);

        // Measure layout
        int rowH   = ICON_SIZE + PADDING;
        this.width  = ICON_SIZE + PADDING * 2 + mc.font.width("00 packs") + 4;
        this.height = Math.max(rowH, show * (ICON_SIZE + ICON_GAP) + PADDING * 2);

        drawBackground(g);

        if (show == 0) {
            drawCenteredText(g, "No packs");
            return;
        }

        // Show icons stacked vertically
        int ix = x + PADDING;
        int iy = y + PADDING;
        int rendered = 0;
        for (int i = packNames.size() - 1; i >= 0 && rendered < show; i--) {
            String packId = packNames.get(i);
            Identifier iconId = Identifier.fromNamespaceAndPath("theladscore", "packicon/" + safeId(packId));
            // Try to draw the pack icon; fall back to a colour square
            drawPackIcon(g, iconId, ix, iy, ICON_SIZE);
            // Pack name beside icon (truncated, no "file/" prefix or .zip extension)
            String label = truncate(displayName(packId), 14);
            int ly = iy + (ICON_SIZE - mc.font.lineHeight) / 2 + 1;
            boolean shadow = resolveTextShadow();
            g.text(mc.font, label, ix + ICON_SIZE + 3, ly, resolveColor(), shadow);
            iy += ICON_SIZE + ICON_GAP;
            rendered++;
        }

        // Pack count label at the bottom when not showing all
        if (!showAll && total > show) {
            this.height = iy - y + mc.font.lineHeight + PADDING;
            drawBackground(g); // re-draw to cover expanded area
            g.text(mc.font, "+" + (total - show) + " more", ix, iy, 0xFFAAAAAA, false);
        }
    }

    private List<String> getActivePacks(Minecraft mc, boolean showHidden) {
        List<String> result = new ArrayList<>();
        try {
            PackRepository repo = mc.getResourcePackRepository();
            for (Pack pack : repo.getSelectedPacks()) {
                String id = pack.getId();
                // "vanilla" is always filtered; non-file packs (mod built-ins, library packs)
                // are only shown when Show Hidden is on.
                if (id.equals("vanilla")) continue;
                if (!showHidden && !id.startsWith("file/")) continue;
                result.add(id);
            }
        } catch (Exception ignored) {
            if (mc.options != null && mc.options.resourcePacks != null) {
                for (String name : mc.options.resourcePacks) {
                    result.add("file/" + name);
                }
            }
        }
        return result;
    }

    private static String displayName(String packId) {
        String s = packId.startsWith("file/") ? packId.substring(5) : packId;
        if (s.endsWith(".zip")) s = s.substring(0, s.length() - 4);
        return s;
    }

    private void drawPackIcon(GuiGraphicsExtractor g, Identifier icon, int x, int y, int size) {
        // Attempt to draw the icon texture; if it's missing MC renders the missing-texture
        try {
            g.pose().pushMatrix();
            g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, size, size, size, size);
            g.pose().popMatrix();
        } catch (Exception ignored) {
            // Draw a coloured placeholder square
            g.fill(x, y, x + size, y + size, 0xFF6C63FF);
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF1A1A2E);
        }
    }

    private boolean resolveTextShadow() {
        return com.thelads.core.config.HudSettings.getInstance().isTextShadow();
    }

    private static String safeId(String raw) {
        return raw.replaceAll("[^a-z0-9_\\-./]", "_").toLowerCase();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
