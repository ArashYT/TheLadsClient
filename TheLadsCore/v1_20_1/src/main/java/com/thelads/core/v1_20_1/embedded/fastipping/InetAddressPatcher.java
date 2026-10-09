/*
 * This file is part of the Fast IP Ping project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2023  Fallen_Breath and contributors
 *
 * Fast IP Ping is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Fast IP Ping is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Fast IP Ping.  If not, see <https://www.gnu.org/licenses/>.
 */
// Modified by The Lads: repackaged into Lads Core (Fabric, Minecraft 1.17+ path only), ported to Minecraft 1.20.1 from the
// Lads 26.3 embedded copy; the patch now also rebuilds the resolved address (see ServerNameResolverMixin).
package com.thelads.core.v1_20_1.embedded.fastipping;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InetAddressPatcher
{
	public static final Logger LOGGER = LoggerFactory.getLogger("FastIpPing");

	@SuppressWarnings("UnstableApiUsage")
	public static InetAddress patch(String hostName, InetAddress addr) throws UnknownHostException
	{
		if (InetAddresses.isInetAddress(hostName))
		{
			InetAddress patched = InetAddress.getByAddress(addr.getHostAddress(), addr.getAddress());
			LOGGER.debug("Patching ip-only InetAddresses from {} to {}", addr, patched);
			addr = patched;
		}
		return addr;
	}

	/**
	 * The resolved address of an ip-only server, with the ip as its host name so that nothing asks the DNS for a reverse
	 * lookup; any other address is returned as it is.
	 */
	public static ResolvedServerAddress patch(String hostName, ResolvedServerAddress resolved)
	{
		try
		{
			InetSocketAddress socket = resolved.asInetSocketAddress();
			InetAddress patched = patch(hostName, socket.getAddress());
			return patched == socket.getAddress() ? resolved : ResolvedServerAddress.from(new InetSocketAddress(patched, socket.getPort()));
		}
		catch (UnknownHostException | RuntimeException e)
		{
			LOGGER.debug("Could not patch the address of {}", hostName, e);
			return resolved;
		}
	}
}
