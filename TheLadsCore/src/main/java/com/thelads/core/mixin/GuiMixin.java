package com.thelads.core.mixin;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.ScoreboardHudElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.modules.killbanner.KillBannerRenderer;

import java.util.ArrayList;
import java.util.List;

@Mixin(Hud.class)
public class GuiMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void onExtractRenderState(GuiGraphicsExtractor g, DeltaTracker tickDelta, CallbackInfo ci) {
        HudManager.getInstance().render(g, tickDelta);
        KillBannerRenderer.extract(g);
    }

    // ── Scoreboard: cancel vanilla, render at the element's exact position ──

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsScoreboardRender(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("Scoreboard");
        if (m == null || !m.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Objective objective = mc.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if (objective == null) return;

        ci.cancel(); // always take over when the module is on
        ScoreboardHudElement sbEl = HudManager.getInstance().getScoreboardElement();
        if (sbEl == null) return;
        ladsRenderScoreboard(g, objective, m, sbEl);
    }

    @Unique
    private void ladsRenderScoreboard(GuiGraphicsExtractor g, Objective objective, Module m, ScoreboardHudElement sbEl) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        Scoreboard sb = objective.getScoreboard();

        // Collect & sort scores (highest first, matches most server sidebars)
        var raw = new ArrayList<>(sb.listPlayerScores(objective));
        raw.removeIf(e -> e.owner() == null || e.owner().startsWith("#"));
        raw.sort((a, b) -> {
            int c = Integer.compare(b.value(), a.value());
            return c != 0 ? c : a.owner().compareToIgnoreCase(b.owner());
        });
        int count = Math.min(raw.size(), 15);

        int lineH = mc.font.lineHeight + 1;

        // Build display components with team colours
        List<Component> names = new ArrayList<>(count);
        List<String>    vals  = new ArrayList<>(count);
        Component title = objective.getDisplayName();

        int maxW = mc.font.width(title);
        for (int i = 0; i < count; i++) {
            var e = raw.get(i);
            PlayerTeam team = sb.getPlayersTeam(e.owner());
            Component nameComp = PlayerTeam.formatNameForTeam(team, Component.literal(e.owner()));
            String valStr = String.valueOf(e.value());
            names.add(nameComp);
            vals.add(valStr);
            int rw = mc.font.width(nameComp) + 4 + mc.font.width(valStr);
            if (rw > maxW) maxW = rw;
        }

        int sbW = maxW + 8;
        int sbH = count == 0 ? lineH + 4 : lineH * (count + 1) + 6;

        // Update element size so the HUD editor preview is accurate
        sbEl.setSize(sbW, sbH);

        // Options
        boolean shadow = ladsGetBool(m, "Text Shadow", false);
        boolean hideNumbers = ladsGetBool(m, "Hide Red Numbers", false);
        int[] bg = ladsResolveBg(m);
        int bgFill  = bg[0];
        int hdrFill = bg[1];

        // Recalculate maxW if we are hiding numbers
        if (hideNumbers) {
            maxW = mc.font.width(title);
            for (Component name : names) {
                int rw = mc.font.width(name);
                if (rw > maxW) maxW = rw;
            }
            sbW = maxW + 8;
            sbEl.setSize(sbW, sbH);
        }

        float scale = sbEl.getScale();
        // Clamp so the scoreboard never renders off-screen, regardless of element position
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int ex = Mth.clamp(sbEl.getX(), 0, Math.max(0, screenW - (int) (sbW * scale)));
        int ey = Mth.clamp(sbEl.getY(), 0, Math.max(0, screenH - (int) (sbH * scale)));

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) ex, (float) ey);
        if (scale != 1.0f) pose.scale(scale, scale);

        // Background panel
        if ((bgFill & 0xFF000000) != 0) {
            g.fill(0, 0, sbW, sbH, bgFill);
        }
        if ((hdrFill & 0xFF000000) != 0) {
            g.fill(0, 0, sbW, lineH + 2, hdrFill);
        }

        // Title centred (no shadow API on centeredText, so manual centering)
        int titleW = mc.font.width(title);
        g.text(mc.font, title, (sbW - titleW) / 2, 2, 0xFFFFFF55, shadow);

        // Score rows
        int ly = lineH + 4;
        for (int i = 0; i < count; i++) {
            g.text(mc.font, names.get(i), 3, ly, 0xFFFFFFFF, shadow);
            if (!hideNumbers) {
                String valStr = vals.get(i);
                g.text(mc.font, valStr, sbW - mc.font.width(valStr) - 3, ly, 0xFFFF5555, shadow);
            }
            ly += lineH;
        }

        pose.popMatrix();
    }

    @Unique
    private boolean ladsGetBool(Module m, String name, boolean def) {
        Option o = m.getOption(name);
        return (o instanceof BoolOption) ? ((BoolOption) o).get() : def;
    }

    @Unique
    private int[] ladsResolveBg(Module m) {
        // returns [background fill, header fill]
        Option o = m.getOption("Background");
        if (o instanceof DropdownOption) {
            switch (((DropdownOption) o).getIndex()) {
                case 1: return new int[]{ 0xCC000000, 0xFF1A1A1A }; // Dark
                case 2: return new int[]{ 0x33FFFFFF,  0x55FFFFFF }; // Light
                case 3: return new int[]{ 0, 0 };                    // Off
            }
        }
        // Default: vanilla-like semi-transparent
        return new int[]{ 0x80000000, 0xCC000000 };
    }

    // ── Smooth Hotbar ──

    @Unique private float ladsInterpolatedSlot = -1;

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), require = 0)
    private void ladsUpdateHotbar(GuiGraphicsExtractor g, DeltaTracker tickDelta, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("SmoothHotbar");
        if (m == null || !m.isEnabled()) {
            ladsInterpolatedSlot = -1;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int selected = mc.player.getInventory().getSelectedSlot();
        if (ladsInterpolatedSlot < 0) {
            ladsInterpolatedSlot = selected;
        } else {
            float speed = 0.5f;
            Option speedOpt = m.getOption("Speed");
            if (speedOpt instanceof DropdownOption) {
                int idx = ((DropdownOption) speedOpt).getIndex();
                if (idx == 0) speed = 0.2f;
                else if (idx == 1) speed = 0.4f;
                else speed = 0.7f;
            }
            float dt = Math.min(1.0f, tickDelta.getRealtimeDeltaTicks());
            float lerpAmt = 1.0f - (float) Math.exp(-speed * 5.0f * dt);
            ladsInterpolatedSlot = net.minecraft.util.Mth.lerp(lerpAmt, ladsInterpolatedSlot, selected);
        }
    }

}
