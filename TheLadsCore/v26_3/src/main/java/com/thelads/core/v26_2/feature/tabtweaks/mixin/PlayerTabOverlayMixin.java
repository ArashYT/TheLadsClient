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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
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
    @Shadow @Final private Minecraft minecraft;
    @SuppressWarnings("InstantiationOfUtilityClass")
    @Unique PlayerFaceExtractor ladsTab$playerFaceRenderer = new PlayerFaceExtractor();

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(I)I"))
    private int modifyTabPlayerWidgetColor(int color) {
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().tabPlayerListColor : color;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0), index = 4)
    private int modifyTabHeaderColor(int color) {
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().tabHeaderColor : color;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1), index = 4)
    private int modifyTabBodyColor(int color) {
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().tabBodyColor : color;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 3), index = 4)
    private int modifyTabFooterColor(int color) {
        return TabTweaksConfig.current().layout ? TabTweaksConfig.current().tabFooterColor : color;
    }

    @WrapMethod(method = "extractPingIcon")
    private void textLatency(GuiGraphicsExtractor graphics, int slotWidth, int xo, int yo, PlayerInfo info, Operation<Void> original) {
        if (!TabTweaksConfig.current().showPingInTab) {
            original.call(graphics, slotWidth, xo, yo, info);
            return;
        }
        int ping = info.getLatency();
        int color = -5636096;
        if (ping >= 0 && ping < 75) color = TabTweaksConfig.current().pingColorOne;
        else if (ping >= 75 && ping < 145) color = TabTweaksConfig.current().pingColorTwo;
        else if (ping >= 145 && ping < 200) color = TabTweaksConfig.current().pingColorThree;
        else if (ping >= 200 && ping < 300) color = TabTweaksConfig.current().pingColorFour;
        else if (ping >= 300 && ping < 400) color = TabTweaksConfig.current().pingColorFive;
        else if (ping >= 400) color = TabTweaksConfig.current().pingColorSix;
        String pingString = String.valueOf(ping);
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
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(distanceX, distanceY);
            original.call(graphics, (int) (screenWidth / scale), scoreboard, displayObjective);
        } finally { graphics.pose().popMatrix(); }
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
        if (TabTweaksConfig.current().improvedHeads) {
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

    @WrapOperation(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;", ordinal = 0, opcode = Opcodes.GETFIELD))
    private Component removeHeader(PlayerTabOverlay instance, Operation<Component> original) {
        if (TabTweaksConfig.current().removeHeader) return null;
        else return original.call(instance);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;", ordinal = 0, opcode = Opcodes.GETFIELD))
    private Component removeFooter(PlayerTabOverlay instance, Operation<Component> original) {
        if (TabTweaksConfig.current().removeFooter) return null;
        else return original.call(instance);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V", ordinal = 0))
    private void removeHeaderShadow(GuiGraphicsExtractor instance, Font font, FormattedCharSequence str, int x, int y, int color, Operation<Void> original) {
        if (TabTweaksConfig.current().removeHeaderShadow) instance.text(font, str, x, y, color, false);
        else original.call(instance, font, str, x, y, color);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", ordinal = 0))
    private void removeBodyShadow(GuiGraphicsExtractor instance, Font font, Component str, int x, int y, int color, Operation<Void> original) {
        if (TabTweaksConfig.current().removeBodyShadow) instance.text(font, str, x, y, color, false);
        else original.call(instance, font, str, x, y, color);
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
