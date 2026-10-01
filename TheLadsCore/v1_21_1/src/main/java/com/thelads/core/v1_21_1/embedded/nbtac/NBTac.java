// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.nbtac;

import com.thelads.core.v1_21_1.embedded.nbtac.config.ModConfig;
import com.thelads.core.v1_21_1.embedded.nbtac.utils.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NBTac
{
	public static final String MOD_ID = "nbtac";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static NBTacLoaderInterface loaderInterface = null;

	public static boolean init(boolean isDedicatedServer, NBTacLoaderInterface loaderInterface)
	{
		if (isDedicatedServer)
		{
			LOGGER.info("Dedicated server detected - mod setup stopped!");
			return false;
		}

		NBTac.loaderInterface = loaderInterface;
		ModConfig.load();
		Fields.init();
		return true;
	}
}
