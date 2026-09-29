package com.thelads.core.v26_2.feature.dynamicfps;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.mojang.blaze3d.platform.Window;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.dynamicfps.config.Config;
import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.config.option.GraphicsState;
import com.thelads.core.v26_2.feature.dynamicfps.config.option.IdleCondition;
import com.thelads.core.v26_2.feature.dynamicfps.config.option.IgnoreInitialClick;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryState;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryTracker;
import com.thelads.core.v26_2.feature.dynamicfps.feature.state.ClickIgnoreHandler;
import com.thelads.core.v26_2.feature.dynamicfps.feature.state.IdleHandler;
import com.thelads.core.v26_2.feature.dynamicfps.feature.state.OptionHolder;
import com.thelads.core.v26_2.feature.dynamicfps.feature.state.WindowObserver;
import com.thelads.core.v26_2.feature.dynamicfps.feature.volume.SmoothVolumeHandler;
import com.thelads.core.v26_2.feature.dynamicfps.mixin.SoundManagerAccessor;
import com.thelads.core.v26_2.feature.dynamicfps.service.Platform;
import com.thelads.core.v26_2.feature.dynamicfps.util.JsonUtil;
import com.thelads.core.v26_2.feature.dynamicfps.util.KeyMappingHandler;
import com.thelads.core.v26_2.feature.dynamicfps.util.duck.DuckSoundEngine;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Opt-in QA of actual transformed hooks using synchronous synthetic focus/battery state.
 * Never changes real focus, plays a sound, saves configuration, or retains edited preferences. */
public final class NativeDynamicFPSProbe {
    private static boolean titleDone;
    private static int passed;
    private NativeDynamicFPSProbe() {}
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (titleDone || !Boolean.getBoolean("thelads.verifyBackgroundPolicies") || mc.gui.screen() == null || mc.gui.overlay() != null) return;
        titleDone = true;
        try { run(); }
        catch (Throwable failure) { LoggerFactory.getLogger("TheLadsCore").error("Lads background policy probe FAILED", failure); }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int run() throws ReflectiveOperationException {
        passed = 0;
        Minecraft mc = Minecraft.getInstance();
        Module module = NativeQualityOfLife.module("DynamicFPS");
        boolean enabled = module.isEnabled(); long modified = module.getLastModified();
        Map<Option, JsonElement> preferences = new LinkedHashMap<>();
        for (Option option : module.getOptions()) preferences.put(option, option.save().deepCopy());
        var config = DynamicFPSConfig.INSTANCE;
        Map<Field, Object> originalConfig = fields(config, DynamicFPSConfig.class, false);
        Map<Field, Object> engine = fields(null, DynamicFPSMod.class, true);
        Map<Field, Object> bridge = fields(null, LadsBackgroundBridge.class, true);
        Map<Field, Object> idle = fields(null, IdleHandler.class, true);
        Map<Field, Object> sound = fields(null, SmoothVolumeHandler.class, true);
        Map<Field, Object> graphicsBaseline = fields(null, OptionHolder.class, true);
        Map<SoundSource, Float> volumeOverrides = (Map) field(SmoothVolumeHandler.class, "currentOverrides").get(null);
        Map<SoundSource, Float> originalOverrides = new LinkedHashMap<>(volumeOverrides);
        Map<OptionInstance, Object> originalGraphics = graphics(mc.options);
        WindowObserver observer = DynamicFPSMod.getWindow();
        if (observer == null) throw new IllegalStateException("Background window callbacks have not initialized");
        boolean focused = field(WindowObserver.class, "isFocused").getBoolean(observer);
        boolean hovered = field(WindowObserver.class, "isHovered").getBoolean(observer);
        boolean iconified = field(WindowObserver.class, "isIconified").getBoolean(observer);
        var actualFocused = field(Window.class, "focused"); var actualIconified = field(Window.class, "iconified");
        boolean actualFocus = actualFocused.getBoolean(mc.getWindow()), actualHidden = actualIconified.getBoolean(mc.getWindow());
        var batteryStatus = field(BatteryTracker.class, "status"); var batteryPresent = field(BatteryTracker.class, "present");
        Object batteryBefore = batteryStatus.get(null), presentBefore = batteryPresent.get(null);
        try {
            module.setEnabled(false); DynamicFPSMod.onStatusChanged(false);
            Map<OptionInstance, Object> baseline = graphics(mc.options);
            var copy = JsonUtil.fromJson(JsonUtil.toJsonTree(config), DynamicFPSConfig.class);
            fields(copy, DynamicFPSConfig.class, false).forEach((key, value) -> set(key, config, value));
            for (var state : PowerState.values()) {
                if (state == PowerState.FOCUSED) continue;
                field(Config.class, "state").set(config.get(state), state);
                config.get(state).setRunGarbageCollector(false);
                config.get(state).setGraphicsState(GraphicsState.DEFAULT);
            }
            config.setEnabled(true); config.setUncapMenuFrameRate(false); config.idle().setTimeout(0);
            config.idle().setCondition(IdleCondition.VANILLA); config.batteryTracker().setEnabled(false);
            field(DynamicFPSMod.class, "isKeybindDisabled").setBoolean(null, false);
            field(DynamicFPSMod.class, "isForcingLowFPS").setBoolean(null, false);
            ((DropdownOption) module.getOption("Mode")).setIndex(0);
            actualFocused.setBoolean(mc.getWindow(), true); actualIconified.setBoolean(mc.getWindow(), false);
            FramerateLimitTracker tracker = new FramerateLimitTracker(mc.options, mc); tracker.setFramerateLimit(144);
            int vanillaFocused = tracker.getFramerateLimit();
            module.setEnabled(true); window(observer, true, false, false);
            require(tracker.getFramerateLimit() == vanillaFocused, "focused FPS preserves actual vanilla setting");
            config.get(PowerState.UNFOCUSED).setFrameRateTarget(1); window(observer, false, false, false);
            require(DynamicFPSMod.powerState() == PowerState.UNFOCUSED, "unfocused profile selected");
            require(tracker.getFramerateLimit() == 15, "actual tracker retains responsive event loop for 1 FPS rendering");
            require(DynamicFPSMod.targetFrameRate() == 1, "one render per second policy");
            require(render(0), "one final update before low FPS");
            require(!render(0), "sub-frame render skipped");
            require(render(1000), "one-second budget allows frame");
            config.get(PowerState.INVISIBLE).setFrameRateTarget(0); window(observer, false, true, true);
            require(DynamicFPSMod.powerState() == PowerState.INVISIBLE, "minimized outranks stale hover");
            require(DynamicFPSMod.targetFrameRate() == 0 && !render(10000), "zero FPS suspends rendering");
            require(tracker.getFramerateLimit() == 15, "zero FPS keeps event loop responsive");
            window(observer, true, false, false); require(render(0), "focus resumes rendering immediately");
            config.get(PowerState.HOVERED).setFrameRateTarget(53); window(observer, false, true, false);
            require(tracker.getFramerateLimit() == 53, "hover uses separate cap");
            config.get(PowerState.HOVERED).setEnableVsync(true); DynamicFPSMod.refreshProfile();
            require(DynamicFPSMod.enableVsync(), "hover VSync selected");
            config.get(PowerState.HOVERED).setShowToasts(false);
            require(!DynamicFPSMod.shouldShowToasts(), "hover toast policy selected");
            window(observer, false, false, true); actualIconified.setBoolean(mc.getWindow(), true);
            ((DropdownOption) module.getOption("Mode")).setIndex(2); DynamicFPSMod.onStatusChanged(false);
            require(DynamicFPSMod.isDisabled() && tracker.getFramerateLimit() == vanillaFocused, "Off preserves configured FPS without vanilla idle limiter");
            module.setEnabled(false); ((DropdownOption) module.getOption("Mode")).setIndex(0);
            require(tracker.getFramerateLimit() == vanillaFocused, "disabled module does not restore removed vanilla idle limiter");
            actualIconified.setBoolean(mc.getWindow(), false); module.setEnabled(true); window(observer, true, false, false);
            ((DropdownOption) module.getOption("Mode")).setIndex(1); window(observer, false, false, false);
            require(DynamicFPSMod.powerState() == PowerState.FOCUSED, "Balanced grants three seconds grace");
            field(LadsBackgroundBridge.class, "unfocusedSince").setLong(null, Util.getEpochMillis() - 3100);
            DynamicFPSMod.onStatusChanged(false);
            require(DynamicFPSMod.powerState() == PowerState.UNFOCUSED, "grace expires without another focus callback");
            ((DropdownOption) module.getOption("Mode")).setIndex(0);
            config.get(PowerState.UNFOCUSED).setVolumeMultiplier(SoundSource.MASTER, .4f);
            config.get(PowerState.UNFOCUSED).setVolumeMultiplier(SoundSource.MUSIC, .2f);
            config.volumeTransitionSpeed().setUp(10); config.volumeTransitionSpeed().setDown(10);
            DynamicFPSMod.refreshProfile(); fade();
            double rawMaster = mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get();
            double rawMusic = mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get();
            require(close(mc.options.getSoundSourceVolume(SoundSource.MASTER), rawMaster * .4), "master attenuation reaches actual Options getter");
            require(close(mc.options.getSoundSourceVolume(SoundSource.MUSIC), rawMusic * .2), "category attenuation reaches actual Options getter");
            require(mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get() == rawMaster, "volume preference remains unchanged");
            config.get(PowerState.INVISIBLE).setVolumeMultiplier(SoundSource.MASTER, 0); window(observer, false, false, true); fade();
            require(mc.options.getSoundSourceVolume(SoundSource.MUSIC) == 0, "muted master silences every category");
            SoundEngine soundEngine = ((SoundManagerAccessor) mc.getSoundManager()).lads$backgroundSoundEngine();
            require(soundEngine instanceof DuckSoundEngine, "live sound engine has update hook");
            require(soundEngine.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1)) == SoundEngine.PlayResult.NOT_STARTED, "live engine rejects new muted sound");
            window(observer, true, false, false); fade();
            require(close(mc.options.getSoundSourceVolume(SoundSource.MASTER), rawMaster), "foreground restores volume");
            config.get(PowerState.UNFOCUSED).setGraphicsState(GraphicsState.MINIMAL); window(observer, false, false, false);
            require(mc.options.particles().get() == ParticleStatus.MINIMAL, "actual particle option reduced");
            require(!mc.options.ambientOcclusion().get(), "minimal disables AO");
            require(!mc.options.entityShadows().get() && mc.options.entityDistanceScaling().get() == .5, "entity shadows and render distance reduced");
            config.get(PowerState.UNFOCUSED).setGraphicsState(GraphicsState.REDUCED); DynamicFPSMod.refreshProfile();
            require(mc.options.ambientOcclusion().get().equals(baseline.get(mc.options.ambientOcclusion())), "minimal to reduced restores AO");
            require(mc.options.cutoutLeaves().get().equals(baseline.get(mc.options.cutoutLeaves())), "minimal to reduced restores leaves");
            module.setEnabled(false); DynamicFPSMod.onStatusChanged(false); fade();
            require(baseline.entrySet().stream().allMatch(entry -> entry.getValue().equals(entry.getKey().get())), "disable restores all eleven graphics options");
            module.setEnabled(true); config.idle().setTimeout(1); config.idle().setCondition(IdleCondition.NONE);
            field(IdleHandler.class, "previousActivity").setLong(null, Util.getEpochMillis() - 2000); window(observer, true, false, false);
            require(DynamicFPSMod.powerState() == PowerState.ABANDONED, "idle selects separate profile");
            tracker.onInputReceived(); DynamicFPSMod.onStatusChanged(false);
            require(DynamicFPSMod.powerState() == PowerState.FOCUSED, "actual input hook wakes idle policy");
            if (mc.player != null) {
                field(IdleHandler.class, "previousActivity").setLong(null, 1);
                field(IdleHandler.class, "prevPosition").set(null, mc.player.position().add(1, 0, 0));
                method(IdleHandler.class, "checkPlayerActivity").invoke(null);
                require(!IdleHandler.isIdle(), "real player movement detects controller activity");
                field(IdleHandler.class, "previousActivity").setLong(null, 1);
                field(IdleHandler.class, "prevLookAngle").set(null, Vec3.ZERO);
                method(IdleHandler.class, "checkPlayerActivity").invoke(null);
                require(!IdleHandler.isIdle(), "real player look detects controller activity");
            }
            config.idle().setCondition(IdleCondition.ON_BATTERY); batteryStatus.set(null, BatteryState.CHARGING);
            field(IdleHandler.class, "previousActivity").setLong(null, 1);
            require(!IdleHandler.isIdle(), "battery-only idle ignores mains");
            batteryStatus.set(null, BatteryState.DISCHARGING); require(IdleHandler.isIdle(), "battery-only idle detects discharging");
            config.idle().setTimeout(0); config.batteryTracker().setEnabled(true); config.batteryTracker().setSwitchStates(true);
            batteryPresent.setBoolean(null, true); window(observer, true, false, false);
            require(DynamicFPSMod.powerState() == PowerState.UNPLUGGED, "synthetic discharge selects battery profile");
            batteryStatus.set(null, BatteryState.CHARGING); DynamicFPSMod.onStatusChanged(false);
            require(DynamicFPSMod.powerState() == PowerState.FOCUSED, "AC restores foreground profile");
            require(!config.downloadNatives() && !config.mockBatteryData(), "production battery never downloads natives or mocks data");
            config.setIgnoreInitialClick(IgnoreInitialClick.CONSTANT); require(ClickIgnoreHandler.isFeatureActive(), "focus click policy selectable");
            require(Arrays.stream(KeyMappingHandler.getHandlers()).allMatch(handler -> Arrays.asList(mc.options.keyMappings).contains(handler.keyMapping())), "key bindings installed in real options");
            require(((ActionOption) module.getOption("Background Profiles")).isAvailable(), "Lads advanced action wired");
            // Cloth Config ships in the pack but the user may switch it off; the editor then falls back to its own screen.
            boolean cloth = Platform.getInstance().isModLoaded(Constants.CLOTH_CONFIG_ID);
            require(DynamicFPSMod.getConfigScreen(mc.gui.screen()).getClass().getName().contains(cloth ? "clothconfig" : "FallbackConfigScreen"),
                cloth ? "complete editor constructs with shipped Cloth Config" : "editor falls back without Cloth Config (switched off)");
        } finally {
            try { module.setEnabled(false); DynamicFPSMod.onStatusChanged(false); }
            finally {
                restore(originalConfig, config); originalGraphics.forEach(OptionInstance::set);
                restore(graphicsBaseline, null); restore(engine, null); restore(bridge, null); restore(idle, null); restore(sound, null);
                volumeOverrides.clear(); volumeOverrides.putAll(originalOverrides);
                field(WindowObserver.class, "isFocused").setBoolean(observer, focused);
                field(WindowObserver.class, "isHovered").setBoolean(observer, hovered);
                field(WindowObserver.class, "isIconified").setBoolean(observer, iconified);
                actualFocused.setBoolean(mc.getWindow(), actualFocus); actualIconified.setBoolean(mc.getWindow(), actualHidden);
                batteryStatus.set(null, batteryBefore); batteryPresent.set(null, presentBefore);
                preferences.forEach(Option::load); module.setEnabled(enabled); module.setLastModified(modified);
                for (SoundSource source : SoundSource.values()) method(SmoothVolumeHandler.class, "updateVolume", SoundSource.class).invoke(null, source);
                mc.invalidateSurfaceConfiguration();
            }
        }
        LoggerFactory.getLogger("TheLadsCore").info("Lads background policy probe END: {} passed, 0 failed (transformed hooks; synthetic focus/battery; preferences restored)", passed);
        return passed;
    }
    private static void require(boolean value, String name) { if (!value) throw new IllegalStateException(name); passed++; }
    private static boolean close(double actual, double expected) { return Math.abs(actual - expected) < .00001; }
    private static void window(WindowObserver observer, boolean focused, boolean hovered, boolean iconified) throws ReflectiveOperationException {
        field(WindowObserver.class, "isFocused").setBoolean(observer, focused); field(WindowObserver.class, "isHovered").setBoolean(observer, hovered);
        field(WindowObserver.class, "isIconified").setBoolean(observer, iconified); DynamicFPSMod.onStatusChanged(false);
    }
    private static boolean render(long elapsed) throws ReflectiveOperationException { return (boolean) method(DynamicFPSMod.class, "checkForRender", long.class).invoke(null, elapsed); }
    private static void fade() throws ReflectiveOperationException { for (int i = 0; i < 4; i++) method(SmoothVolumeHandler.class, "tickVolumes").invoke(null); }
    private static Field field(Class<?> type, String name) throws ReflectiveOperationException { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static java.lang.reflect.Method method(Class<?> type, String name, Class<?>... arguments) throws ReflectiveOperationException { var method = type.getDeclaredMethod(name, arguments); method.setAccessible(true); return method; }
    private static Map<Field, Object> fields(Object instance, Class<?> type, boolean statics) throws ReflectiveOperationException {
        Map<Field, Object> result = new LinkedHashMap<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) != statics || Modifier.isFinal(field.getModifiers())) continue;
            field.setAccessible(true); result.put(field, field.get(instance));
        }
        return result;
    }
    private static void set(Field field, Object instance, Object value) { try { field.set(instance, value); } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); } }
    private static void restore(Map<Field, Object> values, Object instance) { values.forEach((field, value) -> set(field, instance, value)); }
    @SuppressWarnings("rawtypes") private static Map<OptionInstance, Object> graphics(Options options) {
        Map<OptionInstance, Object> values = new LinkedHashMap<>();
        for (OptionInstance option : new OptionInstance[]{ options.biomeBlendRadius(), options.cloudRange(), options.cloudStatus(), options.graphicsPreset(), options.ambientOcclusion(), options.particles(), options.entityShadows(), options.entityDistanceScaling(), options.cutoutLeaves(), options.improvedTransparency(), options.weatherRadius() }) values.put(option, option.get());
        return values;
    }
}
