// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.util;

import com.thelads.core.v26_2.feature.dynamicfps.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Logging {
	private static final Logger logger = LoggerFactory.getLogger(Constants.MOD_ID);

	public static Logger getLogger() {
		return logger;
	}
}
