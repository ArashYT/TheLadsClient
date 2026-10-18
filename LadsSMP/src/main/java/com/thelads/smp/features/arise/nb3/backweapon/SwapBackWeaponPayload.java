package com.thelads.smp.features.arise.nb3.backweapon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S packet requesting a hotbar swap with the player's back weapon.
 */
public record SwapBackWeaponPayload() implements CustomPacketPayload {
    public static final Type<SwapBackWeaponPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "swap_back_weapon"));
    public static final StreamCodec<ByteBuf, SwapBackWeaponPayload> STREAM_CODEC = StreamCodec.unit(new SwapBackWeaponPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
