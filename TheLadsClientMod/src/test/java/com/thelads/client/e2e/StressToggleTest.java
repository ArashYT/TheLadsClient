package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import com.thelads.client.config.ConfigManager;
import com.thelads.client.config.ModConfig;

public class StressToggleTest {
    @Test
    public void testRapidToggle() {
        ConfigManager.loadConfig();
        for (int i = 0; i < 10000; i++) {
            boolean current = ConfigManager.getConfig().isCapesEnabled();
            ConfigManager.getConfig().setCapesEnabled(!current);
            boolean currentScale = ConfigManager.getConfig().isUiScalingEnabled();
            ConfigManager.getConfig().setUiScalingEnabled(!currentScale);
        }
        ConfigManager.save();
    }
}
