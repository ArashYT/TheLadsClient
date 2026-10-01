// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class NBTacFabric implements ModInitializer, NBTacLoaderInterface
{
	private static final FabricLoader FABRIC_LOADER = FabricLoader.getInstance();
	public static final boolean isDedicatedServer = FABRIC_LOADER.getEnvironmentType() == EnvType.SERVER;

	@Override public void onInitialize()
	{
		NBTac.init(isDedicatedServer, this);
	}

	@Override public boolean isModPresent(String id)
	{
		return FABRIC_LOADER.getModContainer(id).isPresent();
	}

	@Override public boolean isFabric()
	{
		return true;
	}
}
