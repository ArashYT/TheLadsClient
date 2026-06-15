package com.thelads.core.mixin;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.thelads.core.modules.PingViewModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Unique private boolean ladsTabPushed = false;

    // TabList module: scale + reposition the player list (anchored to screen centre).
    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void ladsTabPre(GuiGraphicsExtractor graphics, int width, net.minecraft.world.scores.Scoreboard scoreboard, net.minecraft.world.scores.Objective objective, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("TabList");
        if (m == null || !m.isEnabled()) {
            return;
        }
        float s = ladsScale(m);
        int ox = ladsOffset(m, "X Offset");
        int oy = ladsOffset(m, "Y Offset");
        int cx = graphics.guiWidth() / 2;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx + ox, oy);
        pose.scale(s, s);
        pose.translate(-cx, 0);
        ladsTabPushed = true;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void ladsTabPost(GuiGraphicsExtractor graphics, int width, net.minecraft.world.scores.Scoreboard scoreboard, net.minecraft.world.scores.Objective objective, CallbackInfo ci) {
        if (ladsTabPushed) {
            graphics.pose().popMatrix();
            ladsTabPushed = false;
        }
    }

    @Unique
    private float ladsScale(Module m) {
        Option o = m.getOption("Size");
        int idx = (o instanceof CycleOption) ? ((CycleOption) o).getIndex() : 2;
        float[] scales = { 0.5f, 0.75f, 1.0f, 1.25f, 1.5f };
        return scales[Math.max(0, Math.min(scales.length - 1, idx))];
    }

    @Unique
    private int ladsOffset(Module m, String name) {
        Option o = m.getOption(name);
        int idx = (o instanceof CycleOption) ? ((CycleOption) o).getIndex() : 2;
        return (idx - 2) * 20;
    }



    @Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true, require = 0)
    protected void extractPingIcon(GuiGraphicsExtractor guiGraphics, int width, int x, int y, PlayerInfo playerInfo, CallbackInfo ci) {
        Module module = ModuleManager.getInstance().getModule("PingView");
        if (module instanceof PingViewModule pingViewModule && pingViewModule.isEnabled()) {
            ci.cancel();

            if (playerInfo == null) {
                return;
            }

            int ping = playerInfo.getLatency();
            String pingText = pingViewModule.getPingText(ping);
            int color = pingViewModule.getPingColor(ping);

            Minecraft mc = Minecraft.getInstance();
            int textWidth = mc.font.width(pingText);
            int textX = x + width - textWidth - 1;
            
            guiGraphics.text(mc.font, pingText, textX, y, color, true);
        }
    }

    // Suppress or recolor tab-list background fill() calls based on Background option.
    @Redirect(method = "extractRenderState",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"),
              require = 0)
    private void ladsTabFill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        Module m = ModuleManager.getInstance().getModule("TabList");
        if (m != null && m.isEnabled()) {
            Option bgOpt = m.getOption("Background");
            if (bgOpt instanceof CycleOption) {
                int idx = ((CycleOption) bgOpt).getIndex();
                if (idx == 3) return;                         // Off
                if (idx == 1) color = (0xCC000000) | (color & 0x00FFFFFF); // Dark
                if (idx == 2) color = (0x33FFFFFF) | (color & 0x00FFFFFF); // Light
            }
        }
        g.fill(x1, y1, x2, y2, color);
    }

    @ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = 11), require = 0)
    private int modifyPingWidth11(int original) {
        Module module = ModuleManager.getInstance().getModule("PingView");
        if (module != null && module.isEnabled()) {
            return 45;
        }
        return original;
    }

    @ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = 13), require = 0)
    private int modifyPingWidth13(int original) {
        Module module = ModuleManager.getInstance().getModule("PingView");
        if (module != null && module.isEnabled()) {
            return 45; // slightly larger just in case
        }
        return original;
    }
}
