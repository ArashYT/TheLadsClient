package com.thelads.core.v1_20_1.perf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.client.PacketErrorPolicy;
import com.thelads.core.v1_20_1.perf.connection.ConnectionTweaks1201;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * IgnorePacketErrors: a play packet from the server that can't be decoded is dropped with the rest of its frame, so the next
 * frame decodes normally, instead of closing the connection ("Internal Exception: DecoderException"). Handler failures need
 * no guard on 1.20.1: the client logs and continues. Frame splitting and decompression errors still disconnect.
 */
@Mixin(PacketDecoder.class)
abstract class PacketDecodeGuardMixin {
    @Shadow @Final private PacketFlow flow;

    @WrapMethod(method = "decode", require = 1)
    private void lads$dropUnreadableFrame(ChannelHandlerContext context, ByteBuf in, List<Object> out, Operation<Void> decode) throws Exception {
        if (flow != PacketFlow.CLIENTBOUND || context.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get() != ConnectionProtocol.PLAY
            || !ConnectionTweaks1201.ignorePacketErrors()) {
            decode.call(context, in, out);
            return;
        }
        int decoded = out.size();
        try {
            decode.call(context, in, out);
        } catch (Exception failure) {
            if (!PacketErrorPolicy.skippable(failure)) throw failure;
            while (out.size() > decoded) out.remove(out.size() - 1);
            in.skipBytes(in.readableBytes());
            PacketErrorPolicy.skipped("an unreadable play packet", failure);
        }
    }
}
