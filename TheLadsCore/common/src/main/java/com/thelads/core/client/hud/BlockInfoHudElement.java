package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

/**
 * Feather & Badlion parity Block Info HUD element.
 * Displays targeted block name, required tool, harvestability, mining progress, and light level.
 * GATED: Info advantage.
 */
public class BlockInfoHudElement extends HudElement {
    public static final String MODULE_NAME = "BlockInfo";

    public BlockInfoHudElement() {
        useOrganizedDefaults();
    }

    @Override
    public void prepareRender(LadsGraphics graphics, boolean editor) {
        width = 125;
        height = 36;
    }

    @Override
    public void render(LadsGraphics graphics) {
        if (graphics == null) return;
        LadsGameBridge bridge = LadsGameBridge.get();
        LadsGameBridge.BlockTargetInfo info = bridge != null ? bridge.getTargetedBlockInfo() : null;

        String name, tool;
        boolean harvestable;
        int blockLight, skyLight;
        float progress;

        if (info != null && info.name() != null && !info.name().isEmpty()) {
            name = info.name();
            tool = info.requiredTool();
            harvestable = info.harvestable();
            blockLight = info.blockLight();
            skyLight = info.skyLight();
            progress = info.miningProgress();
        } else {
            // Preview / editor default
            name = "Diamond Ore";
            tool = "Iron Pickaxe+";
            harvestable = true;
            blockLight = 4;
            skyLight = 12;
            progress = 0f;
        }

        drawBackground(graphics);

        boolean shadow = HudSettings.getInstance().isTextShadow();
        int textCol = resolveColor();
        graphics.drawText(name, x + 4, y + 3, textCol, shadow);

        int harvCol = harvestable ? 0xFF55FF55 : 0xFFFF5555;
        String harvStr = harvestable ? "✓ " + tool : "✗ " + tool;
        graphics.drawText(harvStr, x + 4, y + 14, harvCol, shadow);

        String lightStr = "Light: " + blockLight + " (Sky " + skyLight + ")";
        graphics.drawText(lightStr, x + 4, y + 25, 0xFFFFAA00, shadow);

        if (progress > 0.01f) {
            int barX = x + 4, barY = y + 32, barW = width - 8, barH = 2;
            graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
            graphics.fill(barX, barY, barX + (int)(barW * Math.min(1f, progress)), barY + barH, 0xFF00AAFF);
        }
    }
}
