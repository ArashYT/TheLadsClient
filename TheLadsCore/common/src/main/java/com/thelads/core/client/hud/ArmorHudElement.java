package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.bridge.LadsGameBridge.ArmorPiece;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.List;

public class ArmorHudElement extends HudElement {
    private static final List<ArmorPiece> EDITOR_SAMPLE = List.of(new ArmorPiece("Helmet", 120, 165),
            new ArmorPiece("Chestplate", 200, 240));
    private List<ArmorPiece> cachedArmor;
    private List<String> cachedLines = List.of();
    private int cachedMode = -1;
    private boolean cachedPreview;
    private LadsGraphics preparedGraphics;
    public ArmorHudElement() {
        this.x = 5;
        this.y = 85;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        renderArmor(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        renderArmor(g, true);
    }

    private void renderArmor(LadsGraphics g, boolean editor) {
        List<ArmorPiece> armor = g.getGame().getArmor();
        boolean preview = editor && armor.isEmpty();
        List<String> previousLines = cachedLines;
        updateLines(preview ? EDITOR_SAMPLE : armor, preview);
        if (preparedGraphics != g || previousLines != cachedLines)
            measureArmor(g);
        // Recheck live data, but reuse an unchanged preparation for exactly one draw.
        preparedGraphics = null;
        List<String> lines = cachedLines;
        if (lines.isEmpty()) return;
        drawBackground(g);
        int color = resolveColor();
        int lineHeight = Math.max(20, g.fontHeight() + 7);
        for (int i = 0; i < lines.size(); i++) {
            // Bridges list armor feet first (inventory order); stack it head first. The editor sample is head first already.
            int row = preview ? i : lines.size() - 1 - i;
            g.drawArmorItem(i, x + 3, y + 2 + row * lineHeight, preview);
            g.drawText(lines.get(i), x + 23, y + 6 + row * lineHeight, color,
                    HudSettings.getInstance().isTextShadow());
        }
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        List<ArmorPiece> armor = g.getGame().getArmor();
        boolean preview = editor && armor.isEmpty();
        updateLines(preview ? EDITOR_SAMPLE : armor, preview);
        measureArmor(g);
        preparedGraphics = g;
    }

    private void updateLines(List<ArmorPiece> armor, boolean preview) {
        int mode = optCycle("Durability", 1);
        if (mode == cachedMode && preview == cachedPreview && armor.equals(cachedArmor)) return;
        // The bridge may return a mutable list; retain our own snapshot for change detection.
        cachedArmor = new ArrayList<>(armor);
        cachedMode = mode;
        cachedPreview = preview;
        List<String> lines = new ArrayList<>();

        for (ArmorPiece piece : armor) {
            if (piece == null || piece.name() == null || piece.name().isBlank()) continue;
            String line = piece.name() + (preview ? " (sample)" : "");
            if (mode != 0 && piece.maximum() > 0 && piece.remaining() >= 0) {
                int remaining = Math.min(piece.remaining(), piece.maximum());
                line += mode == 2 ? " " + (remaining * 100L / piece.maximum()) + "%"
                        : " " + remaining + "/" + piece.maximum();
            }
            lines.add(line);
        }
        cachedLines = lines;
    }

    private void measureArmor(LadsGraphics g) {
        List<String> lines = cachedLines;
        if (lines.isEmpty()) {
            width = 80;
            height = 16;
            return;
        }
        int lineHeight = Math.max(20, g.fontHeight() + 7);
        width = 80;
        height = Math.max(16, lines.size() * lineHeight + 4);
        for (String line : lines) width = Math.max(width, g.textWidth(line) + 28);
    }

    @Override
    public int getDisplayX(LadsGraphics g) {
        return optBool("Attach to hotbar", true) ? g.getScaledWidth() / 2 + 96 : super.getDisplayX(g);
    }

    @Override
    public int getDisplayY(LadsGraphics g) {
        return optBool("Attach to hotbar", true) ? g.getScaledHeight() - getRenderHeight() - 4 - g.hotbarLift() : super.getDisplayY(g);
    }

    @Override
    public void beginPositionEdit() {
        super.beginPositionEdit();
        var module = ModuleManager.getInstance().getModule(moduleName);
        if (module != null && module.getOption("Attach to hotbar") instanceof BoolOption attach) attach.set(false);
    }
}
