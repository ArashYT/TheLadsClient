/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.PlayerInfo
 *  net.minecraft.world.scores.Objective
 *  net.minecraft.world.scores.Scoreboard
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Constant
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.ModifyConstant
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.PingViewModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PlayerTabOverlay.class})
public class PlayerTabOverlayMixin {
    @Unique
    private boolean ladsTabPushed = false;

    @Inject(method={"extractRenderState"}, at={@At(value="HEAD")}, require=0)
    private void ladsTabPre(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("TabList");
        if (m == null || !m.isEnabled()) {
            return;
        }
        float s = this.ladsScale(m);
        int ox = this.ladsOffset(m, "X Offset");
        int oy = this.ladsOffset(m, "Y Offset");
        int cx = graphics.guiWidth() / 2;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx + ox, oy);
        pose.scale(s, s);
        pose.translate(-cx, 0.0f);
        this.ladsTabPushed = true;
    }

    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")}, require=0)
    private void ladsTabPost(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (this.ladsTabPushed) {
            graphics.pose().popMatrix();
            this.ladsTabPushed = false;
        }
    }

    @Unique
    private float ladsScale(Module m) {
        Option o = m.getOption("Size");
        int idx = o instanceof CycleOption ? ((CycleOption)o).getIndex() : 2;
        float[] scales = new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f};
        return scales[Math.max(0, Math.min(scales.length - 1, idx))];
    }

    @Unique
    private int ladsOffset(Module m, String name) {
        Option o = m.getOption(name);
        int idx = o instanceof CycleOption ? ((CycleOption)o).getIndex() : 2;
        return (idx - 2) * 20;
    }

    @Inject(method={"extractPingIcon"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    protected void extractPingIcon(GuiGraphicsExtractor guiGraphics, int width, int x, int y, PlayerInfo playerInfo, CallbackInfo ci) {
        PingViewModule pingViewModule;
        Module module = ModuleManager.getInstance().getModule("PingView");
        if (module instanceof PingViewModule && (pingViewModule = (PingViewModule)module).isEnabled()) {
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

    @Redirect(method={"extractRenderState"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), require=0)
    private void ladsTabFill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        Option bgOpt;
        Module m = ModuleManager.getInstance().getModule("TabList");
        if (m != null && m.isEnabled() && (bgOpt = m.getOption("Background")) instanceof CycleOption) {
            int idx = ((CycleOption)bgOpt).getIndex();
            if (idx == 3) {
                return;
            }
            if (idx == 1) {
                color = 0xCC000000 | color & 0xFFFFFF;
            }
            if (idx == 2) {
                color = 0x33FFFFFF | color & 0xFFFFFF;
            }
        }
        g.fill(x1, y1, x2, y2, color);
    }

    @ModifyConstant(method={"extractRenderState"}, constant={@Constant(intValue=11)}, require=0)
    private int modifyPingWidth11(int original) {
        Module module = ModuleManager.getInstance().getModule("PingView");
        if (module != null && module.isEnabled()) {
            return 45;
        }
        return original;
    }

    @ModifyConstant(method={"extractRenderState"}, constant={@Constant(intValue=13)}, require=0)
    private int modifyPingWidth13(int original) {
        Module module = ModuleManager.getInstance().getModule("PingView");
        if (module != null && module.isEnabled()) {
            return 45;
        }
        return original;
    }
}

