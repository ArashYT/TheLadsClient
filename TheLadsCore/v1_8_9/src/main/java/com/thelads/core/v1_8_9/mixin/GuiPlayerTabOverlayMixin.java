// Adapted from TabTweaks 1.5.11 by MicrocontrollersDev, LGPL-3.0-only (26.x PlayerTabOverlayMixin).
// Corresponding source in META-INF/lads-sources/tabtweaks.
package com.thelads.core.v1_8_9.mixin;

import com.google.common.collect.Ordering;
import com.thelads.core.client.hud.HudCover;
import com.thelads.core.client.ladsapi.LadsBadge;
import com.thelads.core.v1_8_9.feature.TabListCache189;
import com.thelads.core.v1_8_9.feature.TabTweaks189;
import java.util.Collection;
import java.util.List;
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
import org.lwjgl.opengl.GL11;
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
    @Unique private static final ResourceLocation LADS_BADGE = new ResourceLocation(LadsBadge.TEXTURE_NAMESPACE, LadsBadge.TEXTURE_PATH);
    @Shadow @Final private Minecraft mc;
    @Shadow private IChatComponent header, footer;
    @Unique private boolean ladsHeads, ladsNpc;
    /** The list's move and scale this frame: a point p of the list is drawn at scale * (p + move). */
    @Unique private float ladsScale = 1, ladsMoveX, ladsMoveY;
    @Unique private NetworkPlayerInfo ladsWidthInfo;
    @Unique private NetworkPlayerInfo ladsRowInfo;
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Ordering;sortedCopy(Ljava/lang/Iterable;)Ljava/util/List;", remap = false), require = 1)
    private List<NetworkPlayerInfo> ladsSortedCopy(Ordering<NetworkPlayerInfo> ordering, Iterable<NetworkPlayerInfo> elements) {
        int currentVersion = TabListCache189.getVersion();
        int currentSize = (elements instanceof Collection) ? ((Collection<?>) elements).size() : -1;
        if (TabListCache189.cachedSortedPlayers == null
                || currentVersion != TabListCache189.cachedSortedVersion
                || currentSize != TabListCache189.cachedMapSize
                || elements != TabListCache189.cachedElementsRef) {
            TabListCache189.cachedSortedPlayers = ordering.sortedCopy(elements);
            TabListCache189.cachedSortedVersion = currentVersion;
            TabListCache189.cachedMapSize = currentSize;
            TabListCache189.cachedElementsRef = elements;
        }
        return TabListCache189.cachedSortedPlayers;
    }

    @Inject(method = RENDER, at = @At("HEAD"), require = 1)
    private void ladsMoveTab(int width, Scoreboard scoreboard, ScoreObjective objective, CallbackInfo ci) {
        ladsScale = TabTweaks189.tabScale;
        ladsMoveX = TabTweaks189.moveTabHorizontal;
        ladsMoveY = TabTweaks189.down();
        GlStateManager.pushMatrix();
        GlStateManager.scale(ladsScale, ladsScale, 1.0F);
        GlStateManager.translate(ladsMoveX, ladsMoveY, 0.0F);
    }

    /** HudCover: the header, body and footer backgrounds are the list's box on screen; the Lads HUD widgets under it step aside. */
    @Unique
    private void ladsCover(int left, int top, int right, int bottom) {
        HudCover.add(HudCover.TAB, ladsScale * (left + ladsMoveX), ladsScale * (top + ladsMoveY), ladsScale * (right + ladsMoveX), ladsScale * (bottom + ladsMoveY));
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 1)
    private void ladsMoveTabDone(int width, Scoreboard scoreboard, ScoreObjective objective, CallbackInfo ci) {
        ladsWidthInfo = null;
        ladsRowInfo = null;
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
    private int ladsHeaderColor(int left, int top, int right, int bottom, int color) {
        ladsCover(left, top, right, bottom);
        return TabTweaks189.layout ? TabTweaks189.tabHeaderColor : color;
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = RECT, ordinal = 1), index = 4, require = 1)
    private int ladsBodyColor(int left, int top, int right, int bottom, int color) {
        ladsCover(left, top, right, bottom);
        return TabTweaks189.layout ? TabTweaks189.tabBodyColor : color;
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = RECT, ordinal = 2), index = 4, require = 1)
    private int ladsRowColor(int color) { return TabTweaks189.layout ? TabTweaks189.tabPlayerListColor : color; }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = RECT, ordinal = 3), index = 4, require = 1)
    private int ladsFooterColor(int left, int top, int right, int bottom, int color) {
        ladsCover(left, top, right, bottom);
        return TabTweaks189.layout ? TabTweaks189.tabFooterColor : color;
    }

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

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;", ordinal = 0), require = 1)
    private String ladsCaptureWidthInfo(GuiPlayerTabOverlay overlay, NetworkPlayerInfo info) {
        ladsWidthInfo = info;
        return overlay.getPlayerName(info);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 0), require = 1)
    private int ladsRowWidth(FontRenderer font, String text) {
        int w = font.getStringWidth(text);
        if (ladsWidthInfo != null) {
            if (ladsWidthInfo.getGameProfile() != null && ladsWidthInfo.getGameProfile().getId() != null) {
                if (LadsBadge.showInTab(ladsWidthInfo.getGameProfile().getId())) {
                    w += LadsBadge.TAB_SIZE + LadsBadge.GAP;
                }
            }
            ladsWidthInfo = null;
        }
        return w;
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;", ordinal = 1), require = 1)
    private String ladsCaptureRowInfo(GuiPlayerTabOverlay overlay, NetworkPlayerInfo info) {
        ladsRowInfo = info;
        return overlay.getPlayerName(info);
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
        float drawX = x + (ladsHeads && TabTweaks189.improvedHeads ? 1 : 0);
        NetworkPlayerInfo info = ladsRowInfo;
        ladsRowInfo = null;
        if (info != null && info.getGameProfile() != null && info.getGameProfile().getId() != null) {
            UUID id = info.getGameProfile().getId();
            if (LadsBadge.showInTab(id)) {
                mc.getTextureManager().bindTexture(LADS_BADGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                float alpha = ((color >> 24) & 0xFF) / 255.0F;
                if (alpha <= 0.0F) alpha = 1.0F;
                GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                Gui.drawScaledCustomSizeModalRect((int) drawX, (int) y, 0.0F, 0.0F,
                        LadsBadge.TEXTURE_SIZE, LadsBadge.TEXTURE_SIZE,
                        LadsBadge.TAB_SIZE, LadsBadge.TAB_SIZE,
                        (float) LadsBadge.TEXTURE_SIZE, (float) LadsBadge.TEXTURE_SIZE);
                drawX += LadsBadge.TAB_SIZE + LadsBadge.GAP;
            }
        }
        return font.drawString(text, drawX, y, color, !TabTweaks189.removeBodyShadow);
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
        IChatComponent count = new ChatComponentText(TabTweaks189.formatPlayerCount(mc.getNetHandler().getPlayerInfoMap().size()));
        return (shown == null || shown.getUnformattedText().isEmpty()) ? count : new ChatComponentText("").appendSibling(shown.createCopy()).appendText("\n").appendSibling(count);
    }

    /** PingView: the number instead of the bars, or nothing (Hide Ping). */
    @Inject(method = "drawPing", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsPing(int slotWidth, int x, int y, NetworkPlayerInfo info, CallbackInfo ci) {
        if (TabTweaks189.removePing) ci.cancel();
        else if (TabTweaks189.showPingInTab) {
            // 1.9.1: Hypixel lists everyone at 1 ms: your own row shows the measured ping, the others no number (RealPing).
            boolean self = mc.thePlayer != null && mc.thePlayer.getUniqueID().equals(info.getGameProfile().getId());
            TabTweaks189.drawPing(mc.fontRendererObj, slotWidth, x, y, com.thelads.core.client.hypixel.RealPing.row(info.getResponseTime(), self));
            ci.cancel();
        }
    }
}
