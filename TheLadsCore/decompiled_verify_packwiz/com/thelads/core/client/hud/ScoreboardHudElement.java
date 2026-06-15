/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.world.scores.DisplaySlot
 *  net.minecraft.world.scores.Objective
 *  net.minecraft.world.scores.PlayerScoreEntry
 *  net.minecraft.world.scores.Scoreboard
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

public class ScoreboardHudElement
extends HudElement {
    public ScoreboardHudElement() {
        this.x = 10;
        this.y = 10;
        this.width = 96;
        this.height = 80;
    }

    public void setSize(int w, int h) {
        this.width = w;
        this.height = h;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
    }

    public void renderEditorPreview(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        Scoreboard sb = mc.level != null ? mc.level.getScoreboard() : null;
        Objective obj = sb != null ? sb.getDisplayObjective(DisplaySlot.SIDEBAR) : null;
        int bgCol = -1155917266;
        int headerCol = -1154867136;
        int textCol = this.resolveColor();
        if (obj != null) {
            ArrayList scores = new ArrayList(sb.listPlayerScores(obj));
            int maxLines = Math.min(scores.size(), 15);
            Objects.requireNonNull(mc.font);
            int lineH = 9 + 2;
            this.width = 96;
            this.height = lineH * (maxLines + 1) + 4;
            g.fill(this.x, this.y, this.x + this.width, this.y + this.height, bgCol);
            g.fill(this.x, this.y, this.x + this.width, this.y + lineH + 2, headerCol);
            g.centeredText(mc.font, obj.getDisplayName(), this.x + this.width / 2, this.y + 2, -171);
            int ly = this.y + lineH + 4;
            int shown = 0;
            for (int i = scores.size() - 1; i >= 0 && shown < maxLines; --i, ++shown) {
                PlayerScoreEntry entry = (PlayerScoreEntry)scores.get(i);
                String name = entry.owner();
                String score = String.valueOf(entry.value());
                g.text(mc.font, name, this.x + 3, ly, textCol, false);
                g.text(mc.font, score, this.x + this.width - mc.font.width(score) - 3, ly, -43691, false);
                ly += lineH;
            }
        } else {
            this.width = 88;
            this.height = 56;
            g.fill(this.x, this.y, this.x + this.width, this.y + this.height, bgCol);
            g.fill(this.x, this.y, this.x + this.width, this.y + 12, headerCol);
            g.centeredText(mc.font, "Scoreboard", this.x + this.width / 2, this.y + 2, -171);
            g.text(mc.font, "Player A", this.x + 3, this.y + 16, textCol, false);
            g.text(mc.font, "42", this.x + this.width - mc.font.width("42") - 3, this.y + 16, -43691, false);
            g.text(mc.font, "Player B", this.x + 3, this.y + 28, textCol, false);
            g.text(mc.font, "37", this.x + this.width - mc.font.width("37") - 3, this.y + 28, -43691, false);
            g.text(mc.font, "Player C", this.x + 3, this.y + 40, textCol, false);
            g.text(mc.font, "21", this.x + this.width - mc.font.width("21") - 3, this.y + 40, -43691, false);
        }
    }
}

