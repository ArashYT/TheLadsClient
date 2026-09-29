// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps;

import com.thelads.core.v26_2.feature.dynamicfps.compat.ClothConfig;
import com.thelads.core.v26_2.feature.dynamicfps.compat.GLFW;
import com.thelads.core.v26_2.feature.dynamicfps.config.BatteryTrackerConfig;
import com.thelads.core.v26_2.feature.dynamicfps.config.Config;
import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.config.option.GraphicsState;
import com.thelads.core.v26_2.feature.dynamicfps.feature.state.ClickIgnoreHandler;
import com.thelads.core.v26_2.feature.dynamicfps.service.ModCompat;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryToast;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryTracker;
import com.thelads.core.v26_2.feature.dynamicfps.feature.state.IdleHandler;
import com.thelads.core.v26_2.feature.dynamicfps.util.BatteryUtil;
import com.thelads.core.v26_2.feature.dynamicfps.util.Components;
import com.thelads.core.v26_2.feature.dynamicfps.util.FallbackConfigScreen;
import com.thelads.core.v26_2.feature.dynamicfps.util.Logging;
import com.thelads.core.v26_2.feature.dynamicfps.feature.state.OptionHolder;
import com.thelads.core.v26_2.feature.dynamicfps.util.ResourceLocations;
import com.thelads.core.v26_2.feature.dynamicfps.util.Threads;
import com.thelads.core.v26_2.feature.dynamicfps.feature.volume.SmoothVolumeHandler;
import com.thelads.core.v26_2.feature.dynamicfps.util.duck.DuckLoadingOverlay;
import com.thelads.core.v26_2.feature.dynamicfps.feature.state.WindowObserver;
import com.thelads.core.v26_2.feature.dynamicfps.service.Platform;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DynamicFPSMod {
	private static Config config = Config.ACTIVE;
	private static PowerState state = PowerState.FOCUSED;
	private static GraphicsState appliedGraphics = GraphicsState.DEFAULT;

	private static boolean isForcingLowFPS = false;
	private static boolean isKeybindDisabled = false;

	private static @Nullable WindowObserver window;
	private static @Nullable ClickIgnoreHandler clickHandler;

	private static long lastRender;

	// we always render one last frame before actually reducing FPS, so the hud text
	// shows up instantly when forcing low fps.
	// additionally, this would enable mods which render differently while mc is
	// inactive.
	private static boolean hasRenderedLastFrame = false;

	private static final boolean OVERLAY_OPTIMIZATION_ACTIVE = !ModCompat.getInstance().disableOverlayOptimization();

	// Internal "API" for Dynamic FPS itself

	public static void init() {
		doInit();
	}

	public static boolean disabledByUser() {
		return isKeybindDisabled;
	}

	public static @Nullable WindowObserver getWindow() {
		return window;
	}

	public static boolean isDisabled() {
		return LadsBackgroundBridge.disabled() || isKeybindDisabled || !DynamicFPSConfig.INSTANCE.enabled() || ModCompat.getInstance().isDisabled();
	}

	public static String whyIsTheModNotWorking() {
		List<String> results = new ArrayList<>();

		if (isKeybindDisabled) {
			results.add("keybinding");
		}

		if (!DynamicFPSConfig.INSTANCE.enabled()) {
			results.add("mod config");
		}

		if (ModCompat.getInstance().isDisabled()) {
			results.add("another mod");
		}

		return String.join(", ", results);
	}

	public static void toggleDisabled() {
		isKeybindDisabled = !isKeybindDisabled;
		onStatusChanged(true);
	}

	public static void onConfigChanged() {
		LadsBackgroundBridge.editorSaved();
		doInit();
		DynamicFPSConfig.INSTANCE.save();
		checkForStateChanges(); // The unplugged state may now be enabled or disabled
		refreshProfile();
	}

	/** Reapply mutable editor values even when the selected power state did not change. */
	public static void refreshProfile() { if (window != null) handleStateChange(state, state); }

	public static void close() {
		BatteryTracker.close();
		if (appliedGraphics != GraphicsState.DEFAULT) {
			OptionHolder.applyOptions(Minecraft.getInstance().options, GraphicsState.DEFAULT);
			appliedGraphics = GraphicsState.DEFAULT;
		}
	}

	public static Screen getConfigScreen(Screen parent) {
		if (!Platform.getInstance().isModLoaded(Constants.CLOTH_CONFIG_ID)) {
			return new FallbackConfigScreen(parent);
		} else {
			return ClothConfig.genConfigScreen(parent);
		}
	}

	public static void onStatusChanged(boolean userInitiated) {
		// Ensure game runs at full speed when
		// Returning without giving any other input
		if (userInitiated) {
			IdleHandler.onActivity();
		}

		checkForStateChanges();
	}

	public static PowerState powerState() {
		return state;
	}

	public static boolean isForcingLowFPS() {
		return isForcingLowFPS;
	}

	public static void toggleForceLowFPS() {
		isForcingLowFPS = !isForcingLowFPS;
		onStatusChanged(true);
	}

	public static void setWindow(long address) {
		IdleHandler.setWindow(address);
		window = new WindowObserver(address);

		initClickHandler();
		checkForStateChanges();
	}

	static boolean renderedCurrentFrame = true;

	public static boolean checkForRender() {
		long currentTime = Util.getEpochMillis();
		long timeSinceLastRender = currentTime - lastRender;

		if (!checkForRender(timeSinceLastRender)) {
			renderedCurrentFrame = false;
			return false;
		}

		lastRender = currentTime;
		renderedCurrentFrame = true;
		return true;
	}

	public static boolean renderedCurrentFrame() {
		return renderedCurrentFrame;
	}

	public static int targetFrameRate() {
		return config.frameRateTarget();
	}

	public static boolean enableVsync() {
		return config.enableVsync();
	}

	public static float volumeMultiplier(SoundSource source) {
		return config.volumeMultiplier(source);
	}

	public static boolean shouldShowToasts() {
		return config.showToasts();
	}

	public static GraphicsState graphicsState() {
		return config.graphicsState();
	}

	public static boolean shouldShowLevels() {
		return isDisabled() || !isLevelCoveredByOverlay();
	}

	public static void onBatteryChargeChanged(int before, int after) {
		int percentage = DynamicFPSConfig.INSTANCE.batteryTracker().criticalLevel();

		if (before > percentage && after <= percentage) {
			showNotification("battery_critical", "reminder");
		}
	}

	public static void onBatteryStatusChanged(BatteryState before, BatteryState after) {
		if (before == BatteryState.DISCHARGING && BatteryUtil.isCharging(after)) {
			showNotification("battery_charging", "charging");
		} else if (BatteryUtil.isCharging(before) && after == BatteryState.DISCHARGING) {
			showNotification("battery_draining", "draining");
		}
	}

	// Internal logic

	private static void doInit() {
		initClickHandler();
		SmoothVolumeHandler.init();
		BatteryTracker.init(); // Starts a bounded local worker; never performs I/O on the render thread.
		IdleHandler.init();
	}

	private static void initClickHandler() {
		if (window == null || clickHandler != null) {
			return;
		}


		if (ClickIgnoreHandler.isFeatureActive()) {
			clickHandler = new ClickIgnoreHandler(window.address());
		}
	}
	private static void showNotification(String titleTranslationKey, String iconPath) {
		if (!DynamicFPSConfig.INSTANCE.batteryTracker().notifications()) {
			return;
		}

		Component title = Components.translatable("toast", titleTranslationKey);
		Identifier icon = ResourceLocations.of(Constants.MOD_ID, iconPath);

		BatteryToast.queueToast(title, icon);
	}

	private static boolean isLevelCoveredByOverlay() {
		Minecraft minecraft = Minecraft.getInstance();
		return OVERLAY_OPTIMIZATION_ACTIVE && minecraft.gui.overlay() instanceof LoadingOverlay && !((DuckLoadingOverlay)minecraft.gui.overlay()).dynamic_fps$isReloadComplete();
	}

	@SuppressWarnings("squid:S1215") // Garbage collector call
	public static void handleStateChange(PowerState previous, PowerState current) {
		Minecraft minecraft = Minecraft.getInstance();

		if (Constants.DEBUG) {
			Logging.getLogger().info("Power state changed from {} to {}.", previous, current);
		}

		Config before = config;
		config = DynamicFPSConfig.INSTANCE.get(current);

		GLFW.applyWorkaround(); // Apply mouse hover fix if required
		hasRenderedLastFrame = false; // Render next frame w/o delay

		if (config.runGarbageCollector()) {
			System.gc();
		}

		SmoothVolumeHandler.onStateChange();

		if (appliedGraphics != config.graphicsState()) {
			if (appliedGraphics == GraphicsState.DEFAULT) {
				OptionHolder.copyOptions(minecraft.options);
			}
			// Restore before switching reduced profiles; MINIMAL -> REDUCED must restore AO/leaves too.
			OptionHolder.applyOptions(minecraft.options, GraphicsState.DEFAULT);
			OptionHolder.applyOptions(minecraft.options, config.graphicsState());
			appliedGraphics = config.graphicsState();
		}

		// The FOCUSED config doesn't have the user's actual vsync preference sadly ...
		boolean enableVsync = current != PowerState.FOCUSED ? config.enableVsync() : minecraft.options.enableVsync().get();

		if (enableVsync != before.enableVsync()) {
			minecraft.invalidateSurfaceConfiguration(); // New vsync preference is applied in MinecraftMixin
		}
	}

	private static void checkForStateChanges() {
		Minecraft minecraft = Minecraft.getInstance();

		if (window == null) {
			return;
		}

		if (minecraft.isSameThread()) {
			checkForStateChanges0();
		} else {
			// Schedule check for the beginning of the next frame
			Threads.runOnMainThread(DynamicFPSMod::checkForStateChanges0);
		}
	}

	private static void checkForStateChanges0() {
		PowerState current;
		BatteryTrackerConfig batteryTracking = DynamicFPSConfig.INSTANCE.batteryTracker();

		if (isDisabled()) {
			current = PowerState.FOCUSED;
		} else if (isForcingLowFPS) {
			current = PowerState.UNFOCUSED;
		} else if (window.isFocused()) {
			if (IdleHandler.isIdle()) {
				current = PowerState.ABANDONED;
			} else if (batteryTracking.enabled() && batteryTracking.switchStates() && BatteryTracker.status() == BatteryState.DISCHARGING) {
				current = PowerState.UNPLUGGED;
			} else {
				current = PowerState.FOCUSED; // Default
			}
		} else if (window.isIconified()) {
			current = PowerState.INVISIBLE;
		} else if (window.isHovered()) {
			current = PowerState.HOVERED;
		} else if (!window.isIconified()) {
			current = PowerState.UNFOCUSED;
		} else {
			current = PowerState.INVISIBLE;
		}

		current = LadsBackgroundBridge.withGrace(current, Util.getEpochMillis());
		if (state != current) {
			PowerState previous = state;
			state = current;

			handleStateChange(previous, current);
		}
	}

	private static boolean checkForRender(long timeSinceLastRender) {
		int frameRateTarget = targetFrameRate();

		// Disable all rendering
		if (frameRateTarget == 0) {
			return false;
		}

		// Disable mod-side frame rate limiting
		if (frameRateTarget >= Constants.MIN_FRAME_RATE_LIMIT) {
			return true;
		}

		// Render one more frame before
		// Applying the custom frame rate
		// So changes show up immediately
		if (!hasRenderedLastFrame) {
			hasRenderedLastFrame = true;
			return true;
		}

		long frameTime = 1000 / frameRateTarget;
		return timeSinceLastRender >= frameTime;
	}
}
