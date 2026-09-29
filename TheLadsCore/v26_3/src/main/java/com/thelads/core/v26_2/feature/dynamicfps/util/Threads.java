// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.util;

import net.minecraft.client.Minecraft;

public class Threads {
	/**
	 * Schedule a task on the main thread.
	 */
	public static void runOnMainThread(Runnable runnable) {
		Minecraft.getInstance().schedule(runnable);
	}

	/**
	 * Create a thread and immediately start it.
	 */
	public static Thread create(String name, Runnable runnable) {
		Thread thread = new Thread(runnable, "dynamic-fps-" + name);
		thread.start();
		return thread;
	}
}
