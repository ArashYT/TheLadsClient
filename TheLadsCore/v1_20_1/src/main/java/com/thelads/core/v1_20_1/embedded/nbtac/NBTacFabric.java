// Adapted from NBT Autocomplete 2.1 for Minecraft 1.20.1 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.nbtac;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class NBTacFabric implements ModInitializer, NBTacLoaderInterface
{
	private static final FabricLoader FABRIC_LOADER = FabricLoader.getInstance();
	public static final boolean isDedicatedServer = FABRIC_LOADER.getEnvironmentType() == EnvType.SERVER;

	/** The Lads: called by EmbeddedMods1201 while the original nbtac jar is not installed. */
	public static void init()
	{
		new NBTacFabric().onInitialize();
	}

	@Override public void onInitialize()
	{
		NBTac.init(isDedicatedServer, this);
	}

	@Override public boolean isModPresent(String id)
	{
		return com.thelads.core.v1_20_1.compat.ModPresence1201.loaded(id); // The Lads: also sees Forge mods under Connector
	}

	@Override public boolean isFabric()
	{
		return true;
	}
}
