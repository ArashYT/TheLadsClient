// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.service;

import java.util.Optional;
import java.util.ServiceLoader;

class Services {
	static Platform PLATFORM = new LadsPlatform();
	static ModCompat MOD_COMPAT = new LadsModCompat();

	static <T> T loadService(Class<T> type) {
		Optional<T> optional = ServiceLoader.load(type).findFirst();

		if (optional.isPresent()) {
			return optional.get();
		} else {
			throw new RuntimeException("Failed to load Dynamic FPS " + type.getSimpleName() + " service!");
		}
	}
}
