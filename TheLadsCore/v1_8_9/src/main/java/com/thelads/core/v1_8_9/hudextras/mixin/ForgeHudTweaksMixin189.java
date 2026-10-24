package com.thelads.core.v1_8_9.hudextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.TabListModule;
import com.thelads.core.modules.TitleTweakerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla HUD (hardcore hearts, XP bar and level), Title Tweaker's move and scale and the tab list's open/close animation in Forge's
 * 1.8.9 HUD (GuiIngameForge draws these itself).
 */
@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class ForgeHudTweaksMixin189 {
    @ModifyExpressionValue(method = "renderHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/storage/WorldInfo;isHardcoreModeEnabled()Z", remap = true))
    private boolean lads$hardcore(boolean hardcore) {
        return hardcore || HudExtras.VANILLA.hardcoreHearts;
    }

    @WrapOperation(method = "renderExperience", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/GuiIngameForge;drawTexturedModalRect(IIIIII)V", remap = true))
    private void lads$xpBar(GuiIngameForge gui, int x, int y, int u, int v, int w, int h, Operation<Void> original) {
        if (!HudExtras.VANILLA.hideXpBar) original.call(gui, x, y, u, v, w, h);
    }

    @WrapOperation(method = "renderExperience", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", remap = true))
    private int lads$xpLevel(FontRenderer font, String text, int x, int y, int color, Operation<Integer> original) {
        return HudExtras.VANILLA.hideXpLevel ? 0 : original.call(font, text, x, y, color);
    }

    /** Title Tweaker: right after the title's translate to the screen centre, its move and scale. */
    @Inject(method = "renderTitle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V", ordinal = 0, shift = At.Shift.AFTER, remap = true))
    private void lads$title(int width, int height, float partialTicks, CallbackInfo ci) {
        TitleTweakerModule titles = HudExtras.TITLES;
        if (!titles.on) return;
        GuiTitleAccessor189 gui = (GuiTitleAccessor189) (Object) this;
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        String title = gui.lads$title(), subtitle = gui.lads$subtitle();
        float scale = TitleTweakerModule.drawScale(titles.size, titles.fits, title == null ? 0 : font.getStringWidth(title),
            subtitle == null ? 0 : font.getStringWidth(subtitle), width);
        GlStateManager.translate(titles.dx, titles.dy, 0);
        GlStateManager.scale(scale, scale, 1);
    }

    /** Animated Open/Close: the list scaled from its top while it opens or closes. */
    @WrapOperation(method = "renderPlayerList", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;renderPlayerlist(ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreObjective;)V", remap = true))
    private void lads$tabAnimation(GuiPlayerTabOverlay tab, int width, Scoreboard scoreboard, ScoreObjective objective, Operation<Void> original) {
        TabListModule module = HudExtras.TAB;
        float open = module.animated ? module.openness() : 1;
        if (open >= 1) {
            original.call(tab, width, scoreboard, objective);
            return;
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(width / 2f, 10, 0);
            GlStateManager.scale(open, open, 1);
            GlStateManager.translate(-width / 2f, -10, 0);
            original.call(tab, width, scoreboard, objective);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
