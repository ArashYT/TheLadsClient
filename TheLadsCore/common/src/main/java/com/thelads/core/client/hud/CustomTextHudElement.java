package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.StringOption;

/**
 * OneClient EvergreenHUD parity Custom Text HUD element.
 * Supports configurable user-defined template strings with dynamic runtime placeholders.
 * Visual-only.
 */
public class CustomTextHudElement extends TextHudElement {
    public static final String MODULE_NAME = "CustomText";

    public CustomTextHudElement() {
        super(80);
        useOrganizedDefaults();
    }

    public static String resolveTemplate(String template, LadsGameBridge bridge) {
        if (template == null || template.isEmpty()) template = "{player} | FPS: {fps}";
        if (bridge == null) return template;

        String result = template;
        result = result.replace("{player}", bridge.getPlayerName() != null ? bridge.getPlayerName() : "Player");
        result = result.replace("{fps}", String.valueOf(bridge.getFps()));
        result = result.replace("{ping}", String.valueOf(bridge.getPing()));
        result = result.replace("{time}", bridge.getGameTime() != null ? bridge.getGameTime() : "12:00");
        result = result.replace("{server}", bridge.getServerAddress() != null ? bridge.getServerAddress() : "Singleplayer");
        result = result.replace("{x}", String.valueOf(bridge.getPlayerX()));
        result = result.replace("{y}", String.valueOf(bridge.getPlayerY()));
        result = result.replace("{z}", String.valueOf(bridge.getPlayerZ()));
        result = result.replace("{biome}", bridge.getBiomeName() != null ? bridge.getBiomeName() : "Plains");
        result = result.replace("{speed}", String.format(java.util.Locale.ROOT, "%.1f", bridge.getSpeed()));
        result = result.replace("{memory}", String.valueOf(bridge.getUsedMemoryMb()) + "MB");
        result = result.replace("{entities}", String.valueOf(bridge.getLoadedEntityCount()));
        return result;
    }

    @Override
    protected String updateText(LadsGraphics graphics) {
        LadsGameBridge bridge = LadsGameBridge.get();
        var mod = ModuleManager.getInstance().getModule(MODULE_NAME);
        String template = "{player} | FPS: {fps} | Ping: {ping}ms";
        if (mod != null) {
            Option opt = mod.getOption("Template");
            if (opt instanceof StringOption so && so.get() != null && !so.get().isEmpty()) {
                template = so.get();
            }
        }
        return resolveTemplate(template, bridge);
    }
}
