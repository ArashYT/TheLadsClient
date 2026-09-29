// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NaturalRegenerationSyncPayload(boolean naturalRegeneration) implements CustomPacketPayload
{
	public static final StreamCodec<FriendlyByteBuf, NaturalRegenerationSyncPayload> CODEC = CustomPacketPayload.codec(NaturalRegenerationSyncPayload::write, NaturalRegenerationSyncPayload::new);
	public static final CustomPacketPayload.Type<NaturalRegenerationSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("appleskin", "natural_regeneration"));

	public NaturalRegenerationSyncPayload(FriendlyByteBuf buf)
	{
		this(buf.readBoolean());
	}

	public void write(FriendlyByteBuf buf)
	{
		buf.writeBoolean(naturalRegeneration);
	}

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return ID;
	}
}
