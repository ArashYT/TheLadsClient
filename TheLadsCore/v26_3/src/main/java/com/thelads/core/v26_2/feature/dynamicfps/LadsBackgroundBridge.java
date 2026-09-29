package com.thelads.core.v26_2.feature.dynamicfps;

import com.thelads.core.config.ActionOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryTracker;
import com.thelads.core.v26_2.feature.dynamicfps.service.ModCompat;
import com.thelads.core.v26_2.feature.dynamicfps.service.Platform;
import net.minecraft.client.Minecraft;

/** Connects the complete background profiles to the Lads module toggle and editor. */
public final class LadsBackgroundBridge {
    private static boolean initialized, previousEnabled;
    private static int previousMode, previousUnfocused, previousHidden;
    private static long unfocusedSince = -1;
    private LadsBackgroundBridge() {}
    private static Module module() { return ModuleManager.getInstance().getModule("DynamicFPS"); }
    public static boolean disabled() {
        return !initialized || !module().isEnabled() || NativeQualityOfLife.choice("DynamicFPS", "Mode", 0) == 2;
    }
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        // The complete profile is authoritative on startup (including migration of the retired mod's JSON).
        syncQuickSettings();
        DynamicFPSConfig.INSTANCE.setEnabled(module().isEnabled());
        previousEnabled = module().isEnabled();
        previousMode = NativeQualityOfLife.choice("DynamicFPS", "Mode", 0);
        ((ActionOption) module().getOption("Background Profiles")).setAction(() -> {
            var minecraft = Minecraft.getInstance();
            DynamicFPSConfig.INSTANCE.setEnabled(module().isEnabled());
            minecraft.setScreenAndShow(DynamicFPSMod.getConfigScreen(minecraft.gui.screen()));
        });
        Platform.getInstance().registerStartTickEvent(LadsBackgroundBridge::tick);
        ModCompat.initialize();
    }
    public static void tick() {
        if (!initialized) return;
        boolean enabled = module().isEnabled();
        int mode = NativeQualityOfLife.choice("DynamicFPS", "Mode", 0);
        int unfocused = (int) NativeQualityOfLife.number("DynamicFPS", "Unfocused FPS", 1);
        int hidden = (int) NativeQualityOfLife.number("DynamicFPS", "Hidden FPS", 0);
        boolean lifecycleChange = enabled != previousEnabled || mode != previousMode;
        if (unfocused != previousUnfocused || hidden != previousHidden) {
            DynamicFPSConfig.INSTANCE.get(PowerState.UNFOCUSED).setFrameRateTarget(unfocused);
            DynamicFPSConfig.INSTANCE.get(PowerState.INVISIBLE).setFrameRateTarget(hidden);
            previousUnfocused = unfocused; previousHidden = hidden;
            DynamicFPSConfig.INSTANCE.save();
            DynamicFPSMod.refreshProfile();
        }
        if (lifecycleChange) {
            previousEnabled = enabled; previousMode = mode;
            DynamicFPSConfig.INSTANCE.setEnabled(enabled);
            if (disabled()) BatteryTracker.close(); else BatteryTracker.init();
            DynamicFPSMod.onStatusChanged(true);
        } else if (mode == 1) {
            // Balanced mode needs a timer check even when no new focus callback arrives.
            DynamicFPSMod.onStatusChanged(false);
        }
        NativeDynamicFPSProbe.tick();
    }
    public static void editorSaved() {
        module().setEnabled(DynamicFPSConfig.INSTANCE.enabled());
        module().touch();
        previousEnabled = module().isEnabled();
        syncQuickSettings();
        ConfigManager.save();
    }
    public static void syncQuickSettings() {
        previousUnfocused = DynamicFPSConfig.INSTANCE.get(PowerState.UNFOCUSED).frameRateTarget();
        previousHidden = DynamicFPSConfig.INSTANCE.get(PowerState.INVISIBLE).frameRateTarget();
        ((SliderOption) module().getOption("Unfocused FPS")).setValue(previousUnfocused);
        ((SliderOption) module().getOption("Hidden FPS")).setValue(previousHidden);
    }
    public static PowerState withGrace(PowerState state, long now) {
        if (state != PowerState.UNFOCUSED || DynamicFPSMod.isForcingLowFPS()) {
            unfocusedSince = -1;
            return state;
        }
        if (unfocusedSince < 0) unfocusedSince = now;
        return NativeQualityOfLife.choice("DynamicFPS", "Mode", 0) == 1 && now - unfocusedSince < 3000
            ? PowerState.FOCUSED : state;
    }
}
