package com.thelads.core.v1_20_1.hud;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * QA only: with a {@code .lads-qa-hud} file in a game folder under artifacts/verification, the Lads HUD's elements (enabled, available,
 * where they are drawn and how big) are logged every 100 ticks as "Lads qa hud: ...". Inert otherwise.
 */
final class HudQa1201 {
    private static Path marker;
    private static boolean checked;
    private static int ticks;

    private HudQa1201() {}

    /** True when the harness's QA script file (.lads-qa-script.txt) has a "# <flag>" comment line: how a script switches QA logging on. */
    static boolean scriptAsks(Path dir, String flag) {
        try {
            Path script = dir.resolve(".lads-qa-script.txt");
            return Files.isRegularFile(script) && Files.readAllLines(script).stream().anyMatch(line -> line.strip().equals("# " + flag));
        } catch (java.io.IOException failure) {
            return false;
        }
    }

    static void tick(Minecraft mc) {
        if (!checked) {
            checked = true;
            Path dir = FabricLoader.getInstance().getGameDir();
            String normalized = dir.toAbsolutePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            if (normalized.contains("/artifacts/verification/") && (Files.isRegularFile(dir.resolve(".lads-qa-hud")) || scriptAsks(dir, "hud-qa")))
                marker = dir.resolve(".lads-qa-hud");
        }
        if (marker == null || mc.level == null || mc.player == null || ++ticks % 100 != 0) return;
        try {
            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            var adapter = new GuiGraphicsLadsAdapter1201(graphics, mc.font);
            StringBuilder out = new StringBuilder("Lads qa hud: gui " + graphics.guiWidth() + "x" + graphics.guiHeight() + " opacity=" + com.thelads.core.v1_20_1.feature.NativeAutohide1201.level() + " |");
            for (HudElement element : HudManager.getInstance().getElements()) {
                if (!element.isEnabled()) continue;
                var bounds = element.measureBounds(adapter, false);
                out.append(' ').append(element.getModuleName()).append("(avail=").append(element.isAvailable()).append(" at ")
                    .append(bounds.x()).append(',').append(bounds.y()).append(' ').append(bounds.width()).append('x').append(bounds.height())
                    .append(" xy=").append(element.getX()).append(',').append(element.getY())
                    .append(" saved=").append(java.util.Arrays.toString(com.thelads.core.config.HudSettings.getInstance().getPosition(element.getModuleName())))
                    .append(" default=").append(java.util.Arrays.toString(com.thelads.core.client.hud.HudDefaults.origin(element.getModuleName(), graphics.guiWidth(), graphics.guiHeight(), bounds.width(), bounds.height()))).append(')');
            }
            Hud1201.LOG.info(out.toString());
        } catch (RuntimeException failure) {
            Hud1201.LOG.warn("Lads qa hud failed", failure);
        }
    }
}
