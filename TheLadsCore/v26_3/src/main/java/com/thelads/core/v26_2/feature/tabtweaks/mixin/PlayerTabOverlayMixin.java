// Adapted from TabTweaks 1.5.11 by MicrocontrollersDev, LGPL-3.0-only.
// Source faa19c704c3c967e1cf0f0355791f9d90d47a1c5; corresponding source in META-INF/lads-sources/tabtweaks.
package com.thelads.core.v26_2.feature.tabtweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import com.thelads.core.client.hud.HudCover;
import com.thelads.core.v26_2.feature.HudCapture;
import com.thelads.core.v26_2.feature.tabtweaks.Head;
import com.thelads.core.v26_2.feature.tabtweaks.Shifter;
import com.thelads.core.v26_2.feature.tabtweaks.config.TabTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.thelads.core.client.ladsapi.LadsBadge;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.level.GameType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Choosing a priority of 900 to take effect before any mods that may modify or even cancel tab rendering
// Example of this is SkyCubed, which cancels at head the render to render its own tab
// Since we may still want some settings to apply, specifically move tab height, let's apply first
@Mixin(value = PlayerTabOverlay.class, priority = 900)
public class PlayerTabOverlayMixin {
    // Static fields stay above the initialised instance fields: JDK 25 gives the implicit constructor's return the line of the last
    // static initialiser, so Mixin read the instance initialisers below as constructor body and dropped them (26.3 tab list NPE).
    @Unique
    private static final Identifier LADS$BADGE_TEXTURE = Identifier.fromNamespaceAndPath(
        LadsBadge.TEXTURE_NAMESPACE,
        LadsBadge.TEXTURE_PATH
    );
    @Shadow @Final private Minecraft minecraft;
    @SuppressWarnings("InstantiationOfUtilityClass")
    @Unique PlayerFaceExtractor ladsTab$playerFaceRenderer = new PlayerFaceExtractor();
    /** Where the list is drawn this frame (its move and scale on the GUI's pose); null outside its render. */
    @Unique private Matrix3x2f lads$pose;
    /** A listed player shows the Lads badge this frame: every row gets the badge column left of the head (heads stay aligned). */
    @Unique private boolean lads$badgeColumn;
    /** The left edge of the row being drawn: the badge column. */
    @Unique private int lads$rowX;

    /**
     * HudCover: the header, body and footer backgrounds are the list's box on screen; the Lads HUD widgets under it step aside. A
     * capped HUD build (HudCapture) replays the box with the list.
     */
    @Unique
    private void lads$cover(int left, int top, int right, int bottom) {
        if (lads$pose == null) return;
        Vector2f a = lads$pose.transformPosition(new Vector2f(left, top)), b = lads$pose.transformPosition(new Vector2f(right, bottom));
        HudCapture.record(state -> HudCover.add(HudCover.TAB, a.x, a.y, b.x, b.y));
        HudCover.add(HudCover.TAB, a.x, a.y, b.x, b.y);
    }

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(I)I"))
    private int modifyTabPlayerWidgetColor(int color) {
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().tabPlayerListColor : color;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0), index = 4)
    private int modifyTabHeaderColor(int left, int top, int right, int bottom, int color) {
        lads$cover(left, top, right, bottom);
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().tabHeaderColor : color;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1), index = 4)
    private int modifyTabBodyColor(int left, int top, int right, int bottom, int color) {
        lads$cover(left, top, right, bottom);
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().tabBodyColor : color;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 2), index = 0)
    private int lads$rowLeft(int left) {
        lads$rowX = left;
        return left;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 3), index = 4)
    private int modifyTabFooterColor(int left, int top, int right, int bottom, int color) {
        lads$cover(left, top, right, bottom);
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().tabFooterColor : color;
    }

    @WrapMethod(method = "extractPingIcon")
    private void textLatency(GuiGraphicsExtractor graphics, int slotWidth, int xo, int yo, PlayerInfo info, Operation<Void> original) {
        if (!TabTweaksConfig.current().showPingInTab) {
            original.call(graphics, slotWidth, xo, yo, info);
            return;
        }
        // 1.9.1: Hypixel lists everyone at 1 ms: your own row shows the measured ping, the others no number (RealPing).
        int ping = com.thelads.core.client.hypixel.RealPing.row(info.getLatency(), minecraft.player != null && minecraft.player.getUUID().equals(info.getProfile().id()));
        int color = ping == com.thelads.core.client.hypixel.RealPing.NONE ? 0xFF808080 : -5636096;
        if (ping >= 0 && ping < 75) color = TabTweaksConfig.current().pingColorOne;
        else if (ping >= 75 && ping < 145) color = TabTweaksConfig.current().pingColorTwo;
        else if (ping >= 145 && ping < 200) color = TabTweaksConfig.current().pingColorThree;
        else if (ping >= 200 && ping < 300) color = TabTweaksConfig.current().pingColorFour;
        else if (ping >= 300 && ping < 400) color = TabTweaksConfig.current().pingColorFive;
        else if (ping >= 400) color = TabTweaksConfig.current().pingColorSix;
        String pingString = ping == com.thelads.core.client.hypixel.RealPing.NONE ? "-" : String.valueOf(ping);
        if (TabTweaksConfig.current().hideFalsePing && (ping <= 1 || ping >= 999)) pingString = "";
        int pingStringLength = minecraft.font.width(pingString);
        boolean shadow = !TabTweaksConfig.current().removePingShadow;
        if (TabTweaksConfig.current().scalePingDisplay) {
            graphics.pose().pushMatrix();
            try {
                graphics.pose().scale(0.5F, 0.5F);
                graphics.text(minecraft.font, pingString, 2 * (xo + slotWidth) - pingStringLength - 4, 2 * yo + 4, color, shadow);
            } finally { graphics.pose().popMatrix(); }
        } else graphics.text(minecraft.font, pingString, xo + slotWidth - pingStringLength, yo, color, shadow);
    }

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "CONSTANT", args = "intValue=13"))
    private int changeWidgetWidth(int original) {
        return TabTweaksConfig.current().showPingInTab ? 30 : original;
    }

    @WrapMethod(method = "extractRenderState")
    private void moveTab(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, Objective displayObjective, Operation<Void> original) {
        TabTweaksConfig.refresh();
        float distanceY = TabTweaksConfig.current().moveTabBelowBossBars
                ? ((Shifter) minecraft.gui.hud.getBossOverlay()).ladsTab$getShift() > TabTweaksConfig.current().moveTabDown
                    ? ((Shifter) minecraft.gui.hud.getBossOverlay()).ladsTab$getShift()
                    : TabTweaksConfig.current().moveTabDown
                : TabTweaksConfig.current().moveTabDown;
        float distanceX = TabTweaksConfig.current().moveTabHorizontal;
        float scale = TabTweaksConfig.current().tabScale;
        lads$badgeColumn = false;
        if (minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getListedOnlinePlayers()) {
                if (LadsBadge.showInTab(info.getProfile().id())) { lads$badgeColumn = true; break; }
            }
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(distanceX, distanceY);
            lads$pose = new Matrix3x2f(graphics.pose());
            original.call(graphics, (int) (screenWidth / scale), scoreboard, displayObjective);
        } finally { lads$pose = null; graphics.pose().popMatrix(); }
    }

    @ModifyArg(method = "getPlayerInfos", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;limit(J)Ljava/util/stream/Stream;"))
    private long changePlayerCount(long maxSize) {
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().maxTabPlayers : maxSize;
    }

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "CONSTANT", args = "intValue=20"))
    private int modifyHeightCount(int original) {
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().playersPerColumn : original;
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V"))
    private boolean removeHeadRendering(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size, boolean hat, boolean flip, int color) {
        return !TabTweaksConfig.current().removeHeads;
    }

    // This is heavily inspired by VanillaHUD shout-out to ImToggle because I basically stole his idea
    // https://github.com/Polyfrost/VanillaHUD
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V"))
    private void betterHatLayer(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size, boolean hat, boolean flip, int color, Operation<Void> original) {
        if (lads$badgeColumn) x += LadsBadge.TAB_SIZE + LadsBadge.GAP;
        if (TabTweaksConfig.current().improvedHeads) {
            // Null seen once in a showcase run (crash 2026-10-24 10:35, hold Tab with client NPC entries): an overlay built without the initializer
            if (ladsTab$playerFaceRenderer == null) ladsTab$playerFaceRenderer = new PlayerFaceExtractor();
            ((Head) ladsTab$playerFaceRenderer).ladsTab$draw(graphics, texture, x, y, size, hat, flip, color);
        } else {
            original.call(graphics, texture, x, y, size, hat, flip, color);
        }
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V"))
    private boolean removeLatencyRendering(PlayerTabOverlay instance, GuiGraphicsExtractor graphics, int slotWidth, int xo, int yo, PlayerInfo info) {
        return !TabTweaksConfig.current().removePing;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 2)
    private int modifyNamePosition(int original) {
        if (TabTweaksConfig.current().removeHeads) return original - 8;
        else if (TabTweaksConfig.current().improvedHeads) return original + 1;
        else return original;
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;", opcode = Opcodes.GETFIELD))
    private Component removeHeader(PlayerTabOverlay instance, Operation<Component> original) {
        if (TabTweaksConfig.current().removeHeader) return null;
        else return original.call(instance);
    }

    /** Both reads (the null check and the line split) see the same footer, with the player count line when asked. */
    @WrapOperation(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;", opcode = Opcodes.GETFIELD))
    private Component removeFooter(PlayerTabOverlay instance, Operation<Component> original) {
        Component orig = TabTweaksConfig.current().removeFooter ? null : original.call(instance);
        if (TabTweaksConfig.current().showPlayerCount && minecraft.getConnection() != null) {
            int count = minecraft.getConnection().getListedOnlinePlayers().size();
            String fmt = TabTweaksConfig.current().formatPlayerCount(count);
            Component countComp = Component.literal(fmt);
            if (orig == null || orig.getString().isEmpty()) return countComp;
            return Component.empty().append(orig).append("\n").append(countComp);
        }
        return orig;
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V", ordinal = 0))
    private void removeHeaderShadow(GuiGraphicsExtractor instance, Font font, FormattedCharSequence str, int x, int y, int color, Operation<Void> original) {
        if (TabTweaksConfig.current().removeHeaderShadow) instance.text(font, str, x, y, color, false);
        else original.call(instance, font, str, x, y, color);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I", ordinal = 0))
    private int lads$badgeNameWidth(Font font, FormattedText text, Operation<Integer> original) {
        int width = original.call(font, text);
        return lads$badgeColumn ? width + LadsBadge.TAB_SIZE + LadsBadge.GAP : width;
    }

    /** The badge sits in its column at the row's left edge, left of the head; the name moves right with the head. */
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", ordinal = 0))
    private void removeBodyShadow(GuiGraphicsExtractor instance, Font font, Component str, int x, int y, int color, Operation<Void> original, @Local PlayerInfo info) {
        int textX = x;
        if (lads$badgeColumn) {
            textX += LadsBadge.TAB_SIZE + LadsBadge.GAP;
            if (info != null && LadsBadge.showInTab(info.getProfile().id())) {
                int badgeColor = info.getGameMode() == GameType.SPECTATOR ? 0x90FFFFFF : -1;
                // The whole 64x64 texture scaled to 8x8 (the 11-argument blit drew only its top-left 8x8 pixels)
                instance.blit(RenderPipelines.GUI_TEXTURED, LADS$BADGE_TEXTURE, lads$rowX, y, 0.0F, 0.0F, LadsBadge.TAB_SIZE, LadsBadge.TAB_SIZE,
                    LadsBadge.TEXTURE_SIZE, LadsBadge.TEXTURE_SIZE, LadsBadge.TEXTURE_SIZE, LadsBadge.TEXTURE_SIZE, badgeColor);
            }
        }
        if (TabTweaksConfig.current().removeBodyShadow) instance.text(font, str, textX, y, color, false);
        else original.call(instance, font, str, textX, y, color);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V", ordinal = 1))
    private void removeFooterShadow(GuiGraphicsExtractor instance, Font font, FormattedCharSequence str, int x, int y, int color, Operation<Void> original) {
        if (TabTweaksConfig.current().removeFooterShadow) instance.text(font, str, x, y, color, false);
        else original.call(instance, font, str, x, y, color);
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V"))
    private boolean removeNpcHeads(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size, boolean hat, boolean flip, int color, @Local(name = "profile") GameProfile profile) {
        return !(TabTweaksConfig.current().removeNpcHeads && profile.id().version() == 2);
    }

    @ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, name = "displayObjective")
    private Objective removeObjectives(Objective displayObjective) {
        return TabTweaksConfig.current().removeObjectives ? null : displayObjective;
    }
}
