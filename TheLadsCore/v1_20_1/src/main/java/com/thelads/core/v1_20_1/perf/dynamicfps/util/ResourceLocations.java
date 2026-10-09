// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v1_20_1.perf.dynamicfps.util;

import net.minecraft.resources.ResourceLocation;

public class ResourceLocations {
	public static ResourceLocation of(String namespace, String path) {
		return new ResourceLocation(namespace, path);
	}
}
