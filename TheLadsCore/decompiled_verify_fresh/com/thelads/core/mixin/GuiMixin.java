/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Gui
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.util.Mth
 *  net.minecraft.world.scores.Objective
 *  net.minecraft.world.scores.PlayerScoreEntry
 *  net.minecraft.world.scores.PlayerTeam
 *  net.minecraft.world.scores.Scoreboard
 *  net.minecraft.world.scores.Team
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.ModifyArg
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.ScoreboardHudElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.killbanner.KillBannerRenderer;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Gui.class})
public class GuiMixin {
    @Unique
    private float ladsInterpolatedSlot = -1.0f;

    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")}, require=0)
    private void onExtractRenderState(GuiGraphicsExtractor g, DeltaTracker tickDelta, CallbackInfo ci) {
        HudManager.getInstance().render(g, tickDelta);
        KillBannerRenderer.extract(g);
    }

    @Inject(method={"displayScoreboardSidebar"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void ladsScoreboardRender(GuiGraphicsExtractor g, Objective objective, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("Scoreboard");
        if (m == null || !m.isEnabled()) {
            return;
        }
        ci.cancel();
        ScoreboardHudElement sbEl = HudManager.getInstance().getScoreboardElement();
        if (sbEl == null) {
            return;
        }
        this.ladsRenderScoreboard(g, objective, m, sbEl);
    }

    @Unique
    private void ladsRenderScoreboard(GuiGraphicsExtractor g, Objective objective, Module m, ScoreboardHudElement sbEl) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return;
        }
        Scoreboard sb = objective.getScoreboard();
        ArrayList<PlayerScoreEntry> raw = new ArrayList<PlayerScoreEntry>(sb.listPlayerScores(objective));
        raw.removeIf(e -> e.owner() == null || e.owner().startsWith("#"));
        raw.sort((a, b) -> {
            int c = Integer.compare(b.value(), a.value());
            return c != 0 ? c : a.owner().compareToIgnoreCase(b.owner());
        });
        int count = Math.min(raw.size(), 15);
        Objects.requireNonNull(mc.font);
        int lineH = 9 + 1;
        ArrayList<MutableComponent> names = new ArrayList<MutableComponent>(count);
        ArrayList<String> vals = new ArrayList<String>(count);
        Component title = objective.getDisplayName();
        int maxW = mc.font.width((FormattedText)title);
        for (int i = 0; i < count; ++i) {
            PlayerScoreEntry e2 = (PlayerScoreEntry)raw.get(i);
            PlayerTeam team = sb.getPlayersTeam(e2.owner());
            MutableComponent nameComp = PlayerTeam.formatNameForTeam((Team)team, (Component)Component.literal((String)e2.owner()));
            String valStr = String.valueOf(e2.value());
            names.add(nameComp);
            vals.add(valStr);
            int rw = mc.font.width((FormattedText)nameComp) + 4 + mc.font.width(valStr);
            if (rw <= maxW) continue;
            maxW = rw;
        }
        int sbW = maxW + 8;
        int sbH = count == 0 ? lineH + 4 : lineH * (count + 1) + 6;
        sbEl.setSize(sbW, sbH);
        boolean shadow = this.ladsGetBool(m, "Text Shadow", false);
        int[] bg = this.ladsResolveBg(m);
        int bgFill = bg[0];
        int hdrFill = bg[1];
        float scale = sbEl.getScale();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int ex = Mth.clamp((int)sbEl.getX(), (int)0, (int)Math.max(0, screenW - sbW));
        int ey = Mth.clamp((int)sbEl.getY(), (int)0, (int)Math.max(0, screenH - sbH));
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(ex, ey);
        if (scale != 1.0f) {
            pose.scale(scale, scale);
        }
        if ((bgFill & 0xFF000000) != 0) {
            g.fill(0, 0, sbW, sbH, bgFill);
        }
        if ((hdrFill & 0xFF000000) != 0) {
            g.fill(0, 0, sbW, lineH + 2, hdrFill);
        }
        int titleW = mc.font.width((FormattedText)title);
        g.text(mc.font, title, (sbW - titleW) / 2, 2, -171, shadow);
        int ly = lineH + 4;
        for (int i = 0; i < count; ++i) {
            String valStr = (String)vals.get(i);
            g.text(mc.font, (Component)names.get(i), 3, ly, -1, shadow);
            g.text(mc.font, valStr, sbW - mc.font.width(valStr) - 3, ly, -43691, shadow);
            ly += lineH;
        }
        pose.popMatrix();
    }

    @Unique
    private boolean ladsGetBool(Module m, String name, boolean def) {
        Option o = m.getOption(name);
        return o instanceof BoolOption ? ((BoolOption)o).get() : def;
    }

    @Unique
    private int[] ladsResolveBg(Module m) {
        Option o = m.getOption("Background");
        if (o instanceof CycleOption) {
            switch (((CycleOption)o).getIndex()) {
                case 1: {
                    return new int[]{-872415232, -15066598};
                }
                case 2: {
                    return new int[]{0x33FFFFFF, 0x55FFFFFF};
                }
                case 3: {
                    return new int[]{0, 0};
                }
            }
        }
        return new int[]{Integer.MIN_VALUE, -872415232};
    }

    @Inject(method={"extractItemHotbar"}, at={@At(value="HEAD")}, require=0)
    private void ladsUpdateHotbar(GuiGraphicsExtractor g, DeltaTracker tickDelta, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("SmoothHotbar");
        if (m == null || !m.isEnabled()) {
            this.ladsInterpolatedSlot = -1.0f;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int selected = mc.player.getInventory().getSelectedSlot();
        if (this.ladsInterpolatedSlot < 0.0f) {
            this.ladsInterpolatedSlot = selected;
        } else {
            float speed = 0.5f;
            Option speedOpt = m.getOption("Speed");
            if (speedOpt instanceof CycleOption) {
                int idx = ((CycleOption)speedOpt).getIndex();
                speed = idx == 0 ? 0.2f : (idx == 1 ? 0.4f : 0.7f);
            }
            float dt = tickDelta.getRealtimeDeltaTicks();
            float lerpAmt = 1.0f - (float)Math.exp(-speed * 5.0f * dt);
            this.ladsInterpolatedSlot = Mth.lerp((float)lerpAmt, (float)this.ladsInterpolatedSlot, (float)selected);
        }
    }

    @ModifyArg(method={"extractItemHotbar"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lnet/minecraft/resources/Identifier;IIII)V", ordinal=1), index=1, require=0)
    private int ladsModifyHotbarSelectionX(int originalX) {
        Module m = ModuleManager.getInstance().getModule("SmoothHotbar");
        if (m == null || !m.isEnabled() || this.ladsInterpolatedSlot < 0.0f) {
            return originalX;
        }
        Minecraft mc = Minecraft.getInstance();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        return guiWidth / 2 - 91 - 1 + (int)(this.ladsInterpolatedSlot * 20.0f);
    }
}

