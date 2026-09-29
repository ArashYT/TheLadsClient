// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.feature.state;

import com.thelads.core.v26_2.feature.dynamicfps.DynamicFPSMod;
import com.thelads.core.v26_2.feature.dynamicfps.config.DynamicFPSConfig;
import com.thelads.core.v26_2.feature.dynamicfps.config.option.IdleCondition;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryTracker;
import com.thelads.core.v26_2.feature.dynamicfps.service.Platform;
import com.thelads.core.v26_2.feature.dynamicfps.feature.battery.BatteryState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;

public class IdleHandler {
	private static boolean active = false;
	private static boolean wasIdle = false;

	private static long previousActivity = 0L;

	private static Vec3 prevPosition = Vec3.ZERO;
	private static Vec3 prevLookAngle = Vec3.ZERO;

	private static @Nullable GLFWCursorPosCallback previousCursorPosCallback;

	public static void init() {
		if (active) {
			return;
		}

		DynamicFPSConfig config = DynamicFPSConfig.INSTANCE;

		if (config.idle().timeout() == 0) {
			return;
		}

		if (config.idle().condition() == IdleCondition.ON_BATTERY && !BatteryTracker.hasBatteries()) {
			return;
		}

		active = true;
		onActivity();

		if (DynamicFPSMod.getWindow() != null) {
			setWindow(DynamicFPSMod.getWindow().address());
		}

		Platform.getInstance().registerStartTickEvent(IdleHandler::checkActivity);
	}

	public static void setWindow(long address) {
		if (active) {
			previousCursorPosCallback = GLFW.glfwSetCursorPosCallback(address, IdleHandler::onMove);
		}
	}

	public static void onActivity() {
		previousActivity = Util.getEpochMillis();
	}

	public static boolean isIdle() {
		DynamicFPSConfig config = DynamicFPSConfig.INSTANCE;

		if (config.idle().timeout() == 0) {
			return false;
		}

		if (config.idle().condition() == IdleCondition.ON_BATTERY && !(BatteryTracker.status() == BatteryState.DISCHARGING)) {
			return false;
		}

		return (Util.getEpochMillis() - previousActivity) >= (long) config.idle().timeout() * 1000;
	}

	private static void checkActivity() {
		checkPlayerActivity();

		boolean idle = isIdle();

		if (idle != wasIdle) {
			wasIdle = idle;
			DynamicFPSMod.onStatusChanged(!idle);
		}
	}

	private static void checkPlayerActivity() {
		Player player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		Vec3 position = player.position();
		Vec3 lookAngle = player.getLookAngle();

		if (!position.equals(prevPosition) || !lookAngle.equals(prevLookAngle)) {
			onActivity();
		}

		prevPosition = position;
		prevLookAngle = lookAngle;
	}

	// Mouse events

	private static void onMove(long address, double x, double y) {
		onActivity();

		if (previousCursorPosCallback != null) {
			previousCursorPosCallback.invoke(address, x, y);
		}
	}
}
