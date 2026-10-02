// Adapted from TabTweaks 1.5.11 by MicrocontrollersDev, LGPL-3.0-only (26.x PlayerTabOverlayMixin).
// Corresponding source in META-INF/lads-sources/tabtweaks.
package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.TabTweaks189;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.NetworkManager;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PingView and TabList (TabTweaks189), as 26.x PlayerTabOverlayMixin on 1.8.9's renderPlayerlist. Its rectangles in bytecode
 * order: header, body, player row, footer; its texts: header, spectator name, name, footer; its face blits: face, hat. Hidden
 * heads use 1.8.9's own no-heads layout (offline servers).
 */
@Mixin(value = GuiPlayerTabOverlay.class, priority = 900)
public abstract class GuiPlayerTabOverlayMixin {
    @Unique private static final String RENDER = "renderPlayerlist";
    @Unique private static final String RECT = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;drawRect(IIIII)V";
    @Unique private static final String TEXT = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I";
    @Unique private static final String FACE = "Lnet/minecraft/client/gui/Gui;drawScaledCustomSizeModalRect(IIFFIIIIFF)V";
    @Shadow @Final private Minecraft mc;
    @Shadow private IChatComponent header, footer;
    @Unique private boolean ladsHeads, ladsNpc;

    @Inject(method = RENDER, at = @At("HEAD"), require = 1)
    private void ladsMoveTab(int width, Scoreboard scoreboard, ScoreObjective objective, CallbackInfo ci) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(TabTweaks189.tabScale, TabTweaks189.tabScale, 1.0F);
        GlStateManager.translate(TabTweaks189.moveTabHorizontal, TabTweaks189.down(), 0.0F);
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 1)
    private void ladsMoveTabDone(int width, Scoreboard scoreboard, ScoreObjective objective, CallbackInfo ci) {
        GlStateManager.popMatrix();
    }

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int ladsScaledWidth(int width) {
        return (int) (width / TabTweaks189.tabScale);
    }

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, require = 1)
    private ScoreObjective ladsObjective(ScoreObjective objective) {
        return TabTweaks189.removeObjectives ? null : objective;
    }

    @ModifyConstant(method = RENDER, constant = @Constant(intValue = 80), require = 1)
    private int ladsMaxPlayers(int max) {
        return TabTweaks189.layout ? TabTweaks189.maxTabPlayers : max;
    }

    @ModifyConstant(method = RENDER, constant = @Constant(intValue = 20), require = 1)
    private int ladsPlayersPerColumn(int rows) {
        return TabTweaks189.layout ? TabTweaks189.playersPerColumn : rows;
    }

    /** The slot's room for the ping bars (13) fits the number instead. */
    @ModifyConstant(method = RENDER, constant = @Constant(intValue = 13), require = 1)
    private int ladsPingWidth(int width) {
        return TabTweaks189.showPingInTab ? 30 : width;
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = RECT, ordinal = 0), index = 4, require = 1)
    private int ladsHeaderColor(int color) { return TabTweaks189.layout ? TabTweaks189.tabHeaderColor : color; }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = RECT, ordinal = 1), index = 4, require = 1)
    private int ladsBodyColor(int color) { return TabTweaks189.layout ? TabTweaks189.tabBodyColor : color; }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = RECT, ordinal = 2), index = 4, require = 1)
    private int ladsRowColor(int color) { return TabTweaks189.layout ? TabTweaks189.tabPlayerListColor : color; }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = RECT, ordinal = 3), index = 4, require = 1)
    private int ladsFooterColor(int color) { return TabTweaks189.layout ? TabTweaks189.tabFooterColor : color; }

    /** Heads are drawn when the integrated server runs or the connection is encrypted; Hide Heads takes both away. */
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isIntegratedServerRunning()Z"), require = 1)
    private boolean ladsHeadsLocal(Minecraft minecraft) {
        return ladsHeads = !TabTweaks189.removeHeads && minecraft.isIntegratedServerRunning();
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkManager;getIsencrypted()Z"), require = 1)
    private boolean ladsHeadsOnline(NetworkManager connection) {
        return ladsHeads = !TabTweaks189.removeHeads && connection.getIsencrypted();
    }

    /** Hide NPC Heads: Citizens-style NPCs have version 2 UUIDs; their row keeps the head's room. */
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/NetworkPlayerInfo;getLocationSkin()Lnet/minecraft/util/ResourceLocation;"), require = 1)
    private ResourceLocation ladsSkin(NetworkPlayerInfo info) {
        UUID id = info.getGameProfile().getId();
        ladsNpc = TabTweaks189.removeNpcHeads && id != null && id.version() == 2;
        return info.getLocationSkin();
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = FACE, ordinal = 0), require = 1)
    private void ladsFace(int x, int y, float u, float v, int uWidth, int vHeight, int width, int height, float tileWidth, float tileHeight) {
        if (!ladsNpc) Gui.drawScaledCustomSizeModalRect(x, y, u, v, uWidth, vHeight, width, height, tileWidth, tileHeight);
    }

    /** Improved Hats (VanillaHUD's idea): the hat layer a pixel larger than the face, half a pixel out on each side. */
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = FACE, ordinal = 1), require = 1)
    private void ladsHat(int x, int y, float u, float v, int uWidth, int vHeight, int width, int height, float tileWidth, float tileHeight) {
        if (ladsNpc) return;
        if (!TabTweaks189.improvedHeads) {
            Gui.drawScaledCustomSizeModalRect(x, y, u, v, uWidth, vHeight, width, height, tileWidth, tileHeight);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(-0.5F, -0.5F, 0.0F);
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, uWidth, vHeight, width + 1, height + 1, tileWidth, tileHeight);
        GlStateManager.popMatrix();
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = TEXT, ordinal = 0), require = 1)
    private int ladsHeaderText(FontRenderer font, String text, float x, float y, int color) {
        return font.drawString(text, x, y, color, !TabTweaks189.removeHeaderShadow);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = TEXT, ordinal = 1), require = 1)
    private int ladsSpectatorName(FontRenderer font, String text, float x, float y, int color) {
        return ladsName(font, text, x, y, color);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = TEXT, ordinal = 2), require = 1)
    private int ladsPlayerName(FontRenderer font, String text, float x, float y, int color) {
        return ladsName(font, text, x, y, color);
    }

    @Unique
    private int ladsName(FontRenderer font, String text, float x, float y, int color) {
        return font.drawString(text, x + (ladsHeads && TabTweaks189.improvedHeads ? 1 : 0), y, color, !TabTweaks189.removeBodyShadow);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = TEXT, ordinal = 3), require = 1)
    private int ladsFooterText(FontRenderer font, String text, float x, float y, int color) {
        return font.drawString(text, x, y, color, !TabTweaks189.removeFooterShadow);
    }

    @Redirect(method = RENDER, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;header:Lnet/minecraft/util/IChatComponent;"), require = 1)
    private IChatComponent ladsHeader(GuiPlayerTabOverlay overlay) {
        return TabTweaks189.removeHeader ? null : header;
    }

    /** Hide Footer, and Show Player Count's line below it (the listed players). */
    @Redirect(method = RENDER, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;footer:Lnet/minecraft/util/IChatComponent;"), require = 1)
    private IChatComponent ladsFooter(GuiPlayerTabOverlay overlay) {
        IChatComponent shown = TabTweaks189.removeFooter ? null : footer;
        if (!TabTweaks189.showPlayerCount || mc.getNetHandler() == null) return shown;
        IChatComponent count = new ChatComponentText(TabTweaks189.playerCountFormat.replace("{count}", String.valueOf(mc.getNetHandler().getPlayerInfoMap().size())));
        return shown == null ? count : new ChatComponentText("").appendSibling(shown.createCopy()).appendText("\n").appendSibling(count);
    }

    /** PingView: the number instead of the bars, or nothing (Hide Ping). */
    @Inject(method = "drawPing", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsPing(int slotWidth, int x, int y, NetworkPlayerInfo info, CallbackInfo ci) {
        if (TabTweaks189.removePing) ci.cancel();
        else if (TabTweaks189.showPingInTab) {
            TabTweaks189.drawPing(mc.fontRendererObj, slotWidth, x, y, info.getResponseTime());
            ci.cancel();
        }
    }
}
