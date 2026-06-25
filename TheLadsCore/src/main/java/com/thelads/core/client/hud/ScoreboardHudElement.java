package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import com.thelads.core.config.Module;

public class ScoreboardHudElement extends HudElement {

    public ScoreboardHudElement() {
        // Default position: right side of screen, roughly where vanilla puts it
        this.x = 10;
        this.y = 10;
        this.width  = 96;
        this.height = 80;
    }

    /** Called by GuiMixin after each real render to keep bounds accurate for the HUD editor. */
    public void setSize(int w, int h) {
        this.width  = w;
        this.height = h;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        // In-game rendering is handled by vanilla's displayScoreboardSidebar (GuiMixin
        // repositions it using this element's x/y). This render() is called only in the
        // HUD editor to show a draggable proxy.
    }

    /**
     * Draws the editor preview — a fake scoreboard so the user can see and drag it.
     * Called by DraggableHudScreen instead of render() when in edit mode.
     */
    public void renderEditorPreview(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        Scoreboard sb = mc.level != null ? mc.level.getScoreboard() : null;
        Objective obj = sb != null ? sb.getDisplayObjective(
                net.minecraft.world.scores.DisplaySlot.SIDEBAR) : null;

        int bgCol    = 0xBB1A1A2E;
        int headerCol = 0xBB2A2040;
        int textCol  = resolveColor();

        Module m = com.thelads.core.config.ModuleManager.getInstance().getModule("Scoreboard");
        boolean hideNumbers = m != null && m.getOption("Hide Red Numbers") instanceof com.thelads.core.config.BoolOption bo && bo.get();

        if (obj != null) {
            // Real scoreboard data — use sorted list via ArrayList
            var scores = new java.util.ArrayList<>(sb.listPlayerScores(obj));
            int maxLines = Math.min(scores.size(), 15);
            int lineH    = mc.font.lineHeight + 2;

            int maxW = mc.font.width(obj.getDisplayName());
            java.util.List<net.minecraft.network.chat.Component> names = new java.util.ArrayList<>();
            java.util.List<String> vals = new java.util.ArrayList<>();
            for (int i = 0; i < maxLines && i < scores.size(); i++) {
                var entry = scores.get(scores.size() - 1 - i);
                var team = sb.getPlayersTeam(entry.owner());
                var nameComp = net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team, net.minecraft.network.chat.Component.literal(entry.owner()));
                String valStr = String.valueOf(entry.value());
                names.add(nameComp);
                vals.add(valStr);
                
                int rw;
                if (hideNumbers) {
                    rw = mc.font.width(nameComp);
                } else {
                    rw = mc.font.width(nameComp) + 4 + mc.font.width(valStr);
                }
                if (rw > maxW) maxW = rw;
            }

            int sbW = maxW + 8;
            int sbH = maxLines == 0 ? lineH + 4 : lineH * (maxLines + 1) + 6;
            this.width = sbW;
            this.height = sbH;

            g.fill(x, y, x + width, y + height, bgCol);
            g.fill(x, y, x + width, y + lineH + 2, headerCol);
            g.centeredText(mc.font, obj.getDisplayName(), x + width / 2, y + 2, 0xFFFFFF55);

            int ly = y + lineH + 4;
            int shown = 0;
            for (int i = scores.size() - 1; i >= 0 && shown < maxLines; i--, shown++) {
                var entry = scores.get(i);
                String name  = entry.owner();
                g.text(mc.font, name,  x + 3,            ly, textCol, false);
                if (!hideNumbers) {
                    String score = String.valueOf(entry.value());
                    g.text(mc.font, score, x + width - mc.font.width(score) - 3, ly, 0xFFFF5555, false);
                }
                ly += lineH;
            }
        } else {
            // No active scoreboard — show placeholder with dynamically computed size
            int lineH = mc.font.lineHeight + 2;
            int maxW = mc.font.width("Scoreboard");
            
            java.util.List<String> pNames = java.util.List.of("Player A", "Player B", "Player C");
            java.util.List<String> pScores = java.util.List.of("42", "37", "21");
            for (int i = 0; i < pNames.size(); i++) {
                int rw;
                if (hideNumbers) {
                    rw = mc.font.width(pNames.get(i));
                } else {
                    rw = mc.font.width(pNames.get(i)) + 4 + mc.font.width(pScores.get(i));
                }
                if (rw > maxW) maxW = rw;
            }
            int sbW = maxW + 8;
            int sbH = lineH * (pNames.size() + 1) + 6;
            this.width = sbW;
            this.height = sbH;

            g.fill(x, y, x + width, y + height, bgCol);
            g.fill(x, y, x + width, y + lineH + 2, headerCol);
            g.centeredText(mc.font, "Scoreboard", x + width / 2, y + 2, 0xFFFFFF55);
            g.text(mc.font, "Player A",  x + 3, y + lineH + 4, textCol, false);
            if (!hideNumbers) g.text(mc.font, "42",        x + width - mc.font.width("42") - 3, y + lineH + 4, 0xFFFF5555, false);
            g.text(mc.font, "Player B",  x + 3, y + lineH * 2 + 4, textCol, false);
            if (!hideNumbers) g.text(mc.font, "37",        x + width - mc.font.width("37") - 3, y + lineH * 2 + 4, 0xFFFF5555, false);
            g.text(mc.font, "Player C",  x + 3, y + lineH * 3 + 4, textCol, false);
            if (!hideNumbers) g.text(mc.font, "21",        x + width - mc.font.width("21") - 3, y + lineH * 3 + 4, 0xFFFF5555, false);
        }
    }
}
