package com.thelads.smp.features.arise.nb1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S payload carrying updated clipboard text from client UI to server item stack.
 */
public record SaveClipboardPayload(String text) implements CustomPacketPayload {
    public static final Type<SaveClipboardPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "save_clipboard"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveClipboardPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    SaveClipboardPayload::text,
                    SaveClipboardPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
