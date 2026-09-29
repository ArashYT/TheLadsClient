package com.thelads.core;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.ToolsHudElement;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HudOptimizationTest {
    static class Graphics extends LadsGraphicsTest.MockGraphics {
        Object epoch = new Object(); int measurements;
        @Override public Object textMetricsKey() { return epoch; }
        @Override public int textWidth(String text) { measurements++; return super.textWidth(text); }
    }
    @Test void unchangedTextMeasuresOnceAndInvalidatesForTextAndFonts() {
        var previous = LadsGameBridge.get();
        String[] address = { "example.org" };
        LadsGameBridge.set(new DefaultGameBridge() { @Override public String getServerAddress() { return address[0]; } });
        try {
            var element = new ToolsHudElement(); element.setModuleName("ServerAddress");
            var graphics = new Graphics();
            for (int i = 0; i < 1000; i++) { element.prepareRender(graphics, false); element.render(graphics); }
            assertEquals(1, graphics.measurements, "1000 stable frames should measure once");
            address[0] = "longer.example.org"; element.prepareRender(graphics, false);
            assertEquals(2, graphics.measurements);
            graphics.epoch = new Object(); element.prepareRender(graphics, false);
            assertEquals(3, graphics.measurements);
            graphics.epoch = null; element.prepareRender(graphics, false); element.prepareRender(graphics, false);
            assertEquals(5, graphics.measurements, "unknown adapters must not retain stale widths");
        } finally { LadsGameBridge.set(previous); }
    }
    @Test void fastPathMatchesGeneralPlacementAndDrawingAtViewportEdges() {
        var manager = HudManager.getInstance(); var elements = new ArrayList<>(manager.getElements());
        var settings = HudSettings.getInstance(); var groups = new ArrayList<>(settings.getGroups());
        var module = ModuleManager.getInstance().getModule("ServerAddress"); boolean enabled = module.isEnabled();
        var saved = settings.getPosition("ServerAddress");
        var graphics = new Graphics(); var element = new ToolsHudElement(); element.setModuleName("ServerAddress");
        try {
            ModuleSupport.registerBuiltIn("ServerAddress"); module.setEnabled(true);
            settings.getPositions().put("ServerAddress", new int[]{790, 599});
            manager.getElements().clear(); manager.getElements().add(element); settings.replaceGroups(List.of());
            manager.render(graphics); var fast = new ArrayList<>(graphics.drawCalls); graphics.drawCalls.clear();
            settings.replaceGroups(List.of(Set.of("unused-a", "unused-b")));
            manager.render(graphics); assertEquals(fast, graphics.drawCalls);
        } finally {
            manager.getElements().clear(); manager.getElements().addAll(elements); settings.replaceGroups(groups); module.setEnabled(enabled);
            if (saved == null) settings.getPositions().remove("ServerAddress"); else settings.getPositions().put("ServerAddress", saved);
        }
    }
}
