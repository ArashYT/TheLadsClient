package com.thelads.core.v26_2.feature.raised;

import com.thelads.core.config.ActionOption;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.raised.client.gui.screens.SelectScreen;
import com.thelads.core.v26_2.feature.raised.config.Config;
import com.thelads.core.v26_2.feature.raised.mixin.minecraft.client.gui.GuiGraphicsExtractorInvoker;
import com.thelads.core.v26_2.feature.raised.option.AdditionalSettings.HotbarSelectionFix;
import com.thelads.core.v26_2.feature.raised.util.Pack;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.nio.file.Files;

/** Integrates the LGPL Raised layout engine and editor with the Lads module and smooth selector. */
public final class NativeRaised {
    private static boolean active;
    public static boolean active() { return active; }
    public static boolean enabled() { return active && NativeQualityOfLife.enabled("Raised"); }
    public static void initialize() {
        if (active) return;
        if (FabricLoader.getInstance().isModLoaded("raised")) {
            com.thelads.core.config.ModuleSupport.registerExternal("Raised", "Raised", "raised", true);
            return;
        }
        var directory = FabricLoader.getInstance().getConfigDir();
        var nativeFile = directory.resolve("lads-raised.json");
        try {
            // Adopt the actual previous layout once; retain the original upstream file.
            Files.createDirectories(directory);
            if (!Files.exists(nativeFile) && Files.isRegularFile(directory.resolve("raised.json")))
                Files.copy(directory.resolve("raised.json"), nativeFile);
            Config.file = nativeFile.toFile();
            Raised.loadConfiguration();
            RaisedClient.registerLayers();
            RaisedClient.registerCommands();
            active = true;
            var module = NativeQualityOfLife.module("Raised");
            if (!(module.getOption("HUD Layout") instanceof ActionOption))
                module.addOption(new ActionOption("HUD Layout", "Edit layout"));
            ((ActionOption) module.getOption("HUD Layout")).setAction(() -> {
                var mc = Minecraft.getInstance();
                mc.gui.setScreen(new SelectScreen(mc.gui.screen()));
            });
            Pack.checkResources();
        } catch (Exception failure) {
            active = false;
            com.thelads.core.config.ModuleSupport.registerUnavailable("Raised", "HUD layout could not load. See the game log; previous layout files are preserved.");
            Raised.LOGGER.error("Could not initialize the built-in HUD layout; saved files were preserved", failure);
        }
    }

    // Called inside the existing SmoothHotbar pose, so repaired pixels move with the selection.
    public static void selection(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                                 int x, int y, int width, int height) {
        var mode = enabled() ? Config.getOptions().getAdditionalSettings().getHotbarSelectionFix() : HotbarSelectionFix.NONE;
        boolean replace = mode == HotbarSelectionFix.REPLACE || (mode == HotbarSelectionFix.AUTO && Pack.getPack());
        graphics.blitSprite(pipeline, replace ? Identifier.fromNamespaceAndPath("raised", "hud/hotbar_selection") : sprite,
                x, y, width, replace ? 24 : height);
        if (mode == HotbarSelectionFix.PATCH || (mode == HotbarSelectionFix.AUTO && !Pack.getPack()))
            ((GuiGraphicsExtractorInvoker) graphics).invokeInnerBlit(RenderPipelines.GUI_TEXTURED,
                    Identifier.withDefaultNamespace("textures/gui/sprites/hud/hotbar_selection.png"),
                    x, x + width, y + height, y + height + 1, 0, 1, 1 / 23.0f, 0, -1);
    }
}
