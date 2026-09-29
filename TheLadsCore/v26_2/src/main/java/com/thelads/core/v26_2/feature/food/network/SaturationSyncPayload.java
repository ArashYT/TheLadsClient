// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SaturationSyncPayload(float saturation) implements CustomPacketPayload
{
	public static final StreamCodec<FriendlyByteBuf, SaturationSyncPayload> CODEC = CustomPacketPayload.codec(SaturationSyncPayload::write, SaturationSyncPayload::new);
	public static final CustomPacketPayload.Type<SaturationSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("appleskin", "saturation"));

	public SaturationSyncPayload(FriendlyByteBuf buf)
	{
		this(buf.readFloat());
	}

	public void write(FriendlyByteBuf buf)
	{
		buf.writeFloat(saturation);
	}

	public float getSaturation()
	{
		return saturation;
	}

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return ID;
	}
}
