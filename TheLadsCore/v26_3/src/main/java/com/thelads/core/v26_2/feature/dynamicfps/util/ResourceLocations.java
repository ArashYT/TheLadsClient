// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.util;

import net.minecraft.resources.Identifier;

public class ResourceLocations {
	public static Identifier of(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}
}
