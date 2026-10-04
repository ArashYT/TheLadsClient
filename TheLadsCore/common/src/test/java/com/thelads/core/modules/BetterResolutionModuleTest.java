package com.thelads.core.modules;

import com.google.gson.JsonParser;
import com.thelads.core.client.RenderScalePolicy;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BetterResolutionModuleTest {
    private com.google.gson.JsonObject before;
    @BeforeEach void save() { before = ConfigManager.toJson(); }
    @AfterEach void restore() { ConfigManager.applyJson(before); }

    private static BetterResolutionModule module() {
        return (BetterResolutionModule) ModuleManager.getInstance().getModule(BetterResolutionModule.NAME);
    }

    @Test void savedRenderScaleSettingsCarryOver() {
        ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"RenderScale\":{\"enabled\":false,\"options\":"
            + "{\"Preset\":0,\"Scale\":75,\"Algorithm\":1,\"Dynamic Resolution\":true,\"Target FPS\":3,\"Min Scale\":75}}}}").getAsJsonObject());
        var settings = module().settings(false);
        assertEquals(new RenderScalePolicy.Settings(false, 0, 75, RenderScalePolicy.NEAREST, true, 120, 75), settings);
        assertNull(ModuleManager.getInstance().getModule("RenderScale"), "one module drives the world resolution");
    }

    @Test void newKeyWinsOverALeftoverOldOne() {
        ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"RenderScale\":{\"options\":{\"Scale\":75}},"
            + "\"BetterResolution\":{\"options\":{\"Scale\":60}}}}").getAsJsonObject());
        assertEquals(60, module().scale.getValue());
    }

    @Test void launcherToggleBeforeTheFirst170LaunchKeepsTheOldOptions() {
        ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"RenderScale\":{\"enabled\":true,\"options\":{\"Scale\":75}},"
            + "\"BetterResolution\":{\"enabled\":false}}}").getAsJsonObject());
        assertFalse(module().isEnabled(), "the launcher's newer switch wins");
        assertEquals(75, module().scale.getValue(), "the options only the old entry has carry over");
    }

    @Test void freshDefaultsRenderNativeWithSmoothReady() {
        var fresh = new BetterResolutionModule();
        var settings = fresh.settings(false);
        assertTrue(settings.enabled());
        assertEquals(1.0, settings.maximumScale(), "100 %: nothing changes until the player picks a scale");
        assertEquals(RenderScalePolicy.SMOOTH, settings.method());
        assertFalse(fresh.settings(true).enabled(), "stands down while an external Better Resolution is loaded");
    }

    @Test void scaleSliderIsLockedWhileAPresetIsUsed() {
        var fresh = new BetterResolutionModule();
        assertFalse(fresh.locked(fresh.scale));
        fresh.preset.setIndex(2);
        assertTrue(fresh.locked(fresh.scale));
        assertFalse(fresh.locked(fresh.method));
        fresh.scale.setValue(60);
        assertEquals(.75, fresh.settings(false).maximumScale(), "the preset, not the slider, sets the scale");
    }
}
