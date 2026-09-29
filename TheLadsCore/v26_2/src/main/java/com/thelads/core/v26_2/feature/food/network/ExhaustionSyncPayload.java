// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ExhaustionSyncPayload(float exhaustion) implements CustomPacketPayload
{
	public static final StreamCodec<FriendlyByteBuf, ExhaustionSyncPayload> CODEC = CustomPacketPayload.codec(ExhaustionSyncPayload::write, ExhaustionSyncPayload::new);
	public static final CustomPacketPayload.Type<ExhaustionSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("appleskin", "exhaustion"));

	public ExhaustionSyncPayload(FriendlyByteBuf buf)
	{
		this(buf.readFloat());
	}

	public void write(FriendlyByteBuf buf)
	{
		buf.writeFloat(exhaustion);
	}

	public float getExhaustion()
	{
		return exhaustion;
	}

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return ID;
	}
}
