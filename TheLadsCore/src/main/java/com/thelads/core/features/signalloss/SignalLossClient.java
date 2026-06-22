/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
 *  net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
 *  net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
 *  net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.resources.Identifier
 *  net.minecraft.util.Mth
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.thelads.core.features.signalloss;

import com.thelads.core.features.signalloss.SignalLossCommands;
import com.thelads.core.features.signalloss.SignalLossConfig;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Objects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignalLossClient
implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"signalloss");
    private static final Identifier HUD_LAYER = Identifier.fromNamespaceAndPath((String)"signalloss", (String)"toast_layer");
    public static volatile long lastPacketTime = System.nanoTime();
    private static boolean isSignalLost = false;
    private static long toastStartTime = 0L;
    private static long lingerStartTime = 0L;
    private static double displayedLagTime = 0.0;
    private static long joinTime = 0L;
    private static float animationProgress = 0.0f;

    public void onInitializeClient() {
        LOGGER.info("Initializing SignalLoss...");
        SignalLossConfig.load();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> SignalLossCommands.register((CommandDispatcher<FabricClientCommandSource>)dispatcher));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SignalLossClient.resetAll());
        HudElementRegistry.attachElementAfter((Identifier)VanillaHudElements.CHAT, (Identifier)HUD_LAYER, SignalLossClient::render);
    }

    private static void render(GuiGraphicsExtractor drawContext, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        if (!SignalLossConfig.INSTANCE.enabled) {
            if (isSignalLost || animationProgress > 0.0f) {
                SignalLossClient.resetAll();
            }
            return;
        }
        if (client.isLocalServer() && !SignalLossConfig.INSTANCE.showInSingleplayer) {
            SignalLossClient.resetLogicState();
            return;
        }
        if (client.isPaused()) {
            lastPacketTime = System.nanoTime();
            SignalLossClient.resetLogicState();
            return;
        }
        long currentNanoTime = System.nanoTime();
        float deltaSeconds = tickCounter.getGameTimeDeltaTicks() / 20.0f;
        long nanoDiff = currentNanoTime - lastPacketTime;
        long msSinceLastPacket = nanoDiff / 1000000L;
        double lagSeconds = (double)nanoDiff / 1.0E9;
        boolean inGracePeriod = (currentNanoTime - joinTime) / 1000000L < 5000L;
        int thresholdMs = SignalLossConfig.INSTANCE.timeoutThreshold;
        int minWarningMs = SignalLossConfig.INSTANCE.minWarningTime;
        int lingerMs = SignalLossConfig.INSTANCE.lingerTime;
        boolean isOverThreshold = msSinceLastPacket > (long)thresholdMs;
        boolean shouldShowToast = false;
        if (isOverThreshold) {
            if (!inGracePeriod) {
                if (toastStartTime == 0L) {
                    toastStartTime = currentNanoTime;
                }
                isSignalLost = true;
                lingerStartTime = 0L;
                displayedLagTime = lagSeconds;
                shouldShowToast = true;
            }
        } else if (isSignalLost) {
            if (lingerStartTime == 0L) {
                lingerStartTime = currentNanoTime;
            }
            long msShownTotal = (currentNanoTime - toastStartTime) / 1000000L;
            long msLingered = (currentNanoTime - lingerStartTime) / 1000000L;
            if (msShownTotal < (long)minWarningMs || msLingered < (long)lingerMs) {
                shouldShowToast = true;
            } else {
                SignalLossClient.resetLogicState();
                shouldShowToast = false;
            }
        }
        float animationSpeed = 4.0f;
        animationProgress = shouldShowToast ? (animationProgress += deltaSeconds * animationSpeed) : (animationProgress -= deltaSeconds * animationSpeed);
        animationProgress = Mth.clamp((float)animationProgress, (float)0.0f, (float)1.0f);
        if (animationProgress > 0.0f) {
            double displayTime = isOverThreshold ? lagSeconds : displayedLagTime;
            String timeString = String.format("%.1f", displayTime);
            MutableComponent text = Component.translatable((String)"signalloss.toast.lost", (Object[])new Object[]{timeString});
            SignalLossClient.renderToast(drawContext, client.font, client.getWindow().getGuiScaledWidth(), (Component)text, animationProgress);
        }
    }

    private static void resetAll() {
        lastPacketTime = System.nanoTime();
        joinTime = System.nanoTime();
        SignalLossClient.resetLogicState();
        animationProgress = 0.0f;
    }

    private static void resetLogicState() {
        isSignalLost = false;
        toastStartTime = 0L;
        lingerStartTime = 0L;
        displayedLagTime = 0.0;
    }

    private static void renderToast(GuiGraphicsExtractor context, Font textRenderer, int screenWidth, Component text, float progress) {
        float easedProgress = 1.0f - (1.0f - progress) * (1.0f - progress);
        int textWidth = textRenderer.width((FormattedText)text);
        Objects.requireNonNull(textRenderer);
        int textHeight = 9;
        int padding = 6;
        int totalHeight = textHeight + padding * 2;
        SignalLossConfig.ToastPosition pos = SignalLossConfig.INSTANCE.toastPosition;
        int x = switch (pos) {
            default -> throw new MatchException(null, null);
            case SignalLossConfig.ToastPosition.LEFT -> 10;
            case SignalLossConfig.ToastPosition.CENTER -> (screenWidth - textWidth) / 2;
            case SignalLossConfig.ToastPosition.RIGHT -> screenWidth - textWidth - 10;
        };
        int hiddenY = -totalHeight - 5;
        int visibleY = 10;
        int y = Mth.lerpInt((float)easedProgress, (int)hiddenY, (int)visibleY);
        if (SignalLossConfig.INSTANCE.drawBackground) {
            context.fill(x - padding, y - padding, x + textWidth + padding, y + textHeight + padding, SignalLossConfig.INSTANCE.backgroundColor);
        }
        context.text(textRenderer, text, x, y, SignalLossConfig.INSTANCE.textColor, true);
    }
}

