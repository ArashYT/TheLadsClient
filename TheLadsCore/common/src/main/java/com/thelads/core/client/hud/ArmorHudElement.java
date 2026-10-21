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
    /** Hotbar Slots: four 20 px slots plus the frame's 1 px edges. */
    private static final int SLOTS_WIDTH = 82;

    public record StripPiece(int xOffset, int u, int width) {}

    /**
     * Pure function calculating the hotbar texture pieces for an armor strip.
     * Takes left slots (helmet, chestplate) from the left end of the hotbar (including left outer border)
     * and right slots (leggings, boots) from the right end of the hotbar (including right outer border),
     * joining them seamlessly without a duplicate inner divider.
     */
    public static List<StripPiece> calculateStripPieces() {
        return calculateStripPieces(4);
    }

    public static List<StripPiece> calculateStripPieces(int slots) {
        if (slots != 4) {
            return List.of(
                    new StripPiece(0, 0, 1 + slots * 20),
                    new StripPiece(1 + slots * 20, 181, 1)
            );
        }
        int leftSlots = slots / 2;
        int rightSlots = slots - leftSlots;
        int leftWidth = 1 + leftSlots * 20;
        int rightWidth = rightSlots * 20 + 1;
        int rightU = 182 - rightWidth;
        return List.of(
                new StripPiece(0, 0, leftWidth),
                new StripPiece(leftWidth, rightU, rightWidth)
        );
    }
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
        if (slotsStyle()) renderSlots(g, false); else renderArmor(g, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        if (slotsStyle()) renderSlots(g, true); else renderArmor(g, true);
    }

    /** The default style since 1.7.0: helmet to boots in four vanilla hotbar slots, each empty one a faint silhouette. */
    private boolean slotsStyle() {
        return optCycle("Style", 0) == 0;
    }

    private void renderSlots(LadsGraphics g, boolean editor) {
        boolean preview = editor && g.getGame().getArmor().isEmpty();
        if (!preview && g.getGame().getArmor().isEmpty()) return;
        g.drawHotbarSlots(x, y, 4);
        for (int slot = 0; slot < 4; slot++) g.drawArmorSlot(slot, x + 3 + slot * 20, y + 3, preview);
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
        if (slotsStyle()) {
            width = SLOTS_WIDTH;
            height = 22;
            return;
        }
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

    /**
     * Hotbar Slots attached to the hotbar: its row, as far left of the offhand slot as that slot is from the hotbar (its 22 px frame
     * sits 7 px off, 29 px in all), or in the offhand's own place while Minecraft draws no offhand slot there (always on 1.8.9).
     */
    @Override
    public int getDisplayX(LadsGraphics g) {
        if (!optBool("Attach to hotbar", true)) return super.getDisplayX(g);
        HudGroupLayout.Rect hotbar = HudAnchors.hotbar(g);
        int gap = g.getGame().offhandSlotLeft() ? 29 + 7 : 7;
        return slotsStyle() ? hotbar.x() - (int) Math.round(gap * g.hudUnit()) - getRenderWidth() : hotbar.right() + 5;
    }

    @Override
    public int getDisplayY(LadsGraphics g) {
        if (!optBool("Attach to hotbar", true)) return super.getDisplayY(g);
        return HudAnchors.hotbar(g).bottom() - getRenderHeight() - (slotsStyle() ? 0 : 4);
    }

    @Override
    public boolean usesSavedPosition() {
        return !optBool("Attach to hotbar", true);
    }

    @Override
    protected boolean followsOrganizedDefault() {
        return !(slotsStyle() && optBool("Attach to hotbar", true));
    }

    @Override
    public void beginPositionEdit() {
        super.beginPositionEdit();
        var module = ModuleManager.getInstance().getModule(moduleName);
        if (module != null && module.getOption("Attach to hotbar") instanceof BoolOption attach) attach.set(false);
    }
}
