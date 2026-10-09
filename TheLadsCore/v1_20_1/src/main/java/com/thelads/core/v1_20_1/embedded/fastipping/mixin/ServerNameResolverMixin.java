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
// Modified by The Lads: repackaged into Lads Core (Fabric, Minecraft 1.17+ path only) and ported to Minecraft 1.20.1. The 26.3
// copy patches the InetAddress inside ServerAddressResolver's lambda, a synthetic method whose name differs between the dev,
// intermediary and Forge-mapped game; this version wraps the resolver call in ServerNameResolver.resolveAddress instead, which
// has the same effect (the patched address is what AddressCheck and the connection see) and a stable name.
package com.thelads.core.v1_20_1.embedded.fastipping.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.embedded.fastipping.FastIpPingInit1201;
import com.thelads.core.v1_20_1.embedded.fastipping.InetAddressPatcher;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// used in mc >= 1.17
@Mixin(ServerNameResolver.class)
public abstract class ServerNameResolverMixin
{
	@WrapOperation(
			method = "resolveAddress",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/resolver/ServerAddressResolver;resolve(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Ljava/util/Optional;"
			)
	)
	private Optional<ResolvedServerAddress> setHostnameToIpAddressToAvoidReversedDnsLookupOnGetHostname(ServerAddressResolver resolver, ServerAddress address, Operation<Optional<ResolvedServerAddress>> original)
	{
		Optional<ResolvedServerAddress> resolved = original.call(resolver, address);
		if (resolved.isEmpty())
		{
			return resolved;
		}
		ResolvedServerAddress patched = InetAddressPatcher.patch(address.getHost(), resolved.get());
		if (patched != resolved.get())
		{
			FastIpPingInit1201.patched();
			return Optional.of(patched);
		}
		return resolved;
	}
}
