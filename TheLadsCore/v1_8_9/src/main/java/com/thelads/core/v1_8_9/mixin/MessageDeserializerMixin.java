package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.PacketErrorPolicy;
import com.thelads.core.v1_8_9.feature.ConnectionTweaks189;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.MessageDeserializer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * IgnorePacketErrors on 1.8.9: a play packet from the server that can't be decoded (unknown id, bad data, extra bytes) is dropped
 * with the rest of its frame instead of closing the connection. The frame splitter and decompression still disconnect on errors.
 * Mixin 0.7 has no method wrapping: the HEAD injection runs decode once more itself (lads$inside), inside a try.
 */
@Mixin(MessageDeserializer.class)
public abstract class MessageDeserializerMixin extends ByteToMessageDecoder {
    @Shadow @Final private EnumPacketDirection direction;
    @Unique private boolean lads$inside;

    @Shadow protected abstract void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) throws Exception;

    @Inject(method = "decode", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$dropUnreadableFrame(ChannelHandlerContext context, ByteBuf in, List<Object> out, CallbackInfo ci) throws Exception {
        if (lads$inside || direction != EnumPacketDirection.CLIENTBOUND || !ConnectionTweaks189.ignorePacketErrors()
            || context.channel().attr(NetworkManager.attrKeyConnectionState).get() != EnumConnectionState.PLAY) return;
        ci.cancel();
        int decoded = out.size();
        lads$inside = true;
        try {
            decode(context, in, out);
        } catch (Exception failure) {
            if (!PacketErrorPolicy.skippable(failure)) throw failure;
            while (out.size() > decoded) out.remove(out.size() - 1);
            in.skipBytes(in.readableBytes());
            PacketErrorPolicy.skipped("an unreadable play packet", failure);
        } finally {
            lads$inside = false;
        }
    }
}
