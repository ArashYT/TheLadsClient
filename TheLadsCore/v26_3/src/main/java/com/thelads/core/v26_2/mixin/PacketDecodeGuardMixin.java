package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.client.PacketErrorPolicy;
import com.thelads.core.v26_2.feature.ConnectionTweaks;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.SkipPacketException;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * IgnorePacketErrors: a play packet from the server that can't be decoded is dropped with the rest of its frame, so the next
 * frame decodes normally, instead of closing the connection. Frame splitting and decompression errors still disconnect.
 */
@Mixin(PacketDecoder.class)
abstract class PacketDecodeGuardMixin {
    @Shadow @Final private ProtocolInfo<?> protocolInfo;

    @WrapMethod(method = "decode", require = 1)
    private void lads$dropUnreadableFrame(ChannelHandlerContext context, ByteBuf in, List<Object> out, Operation<Void> decode) throws Exception {
        if (protocolInfo.flow() != PacketFlow.CLIENTBOUND || protocolInfo.id() != ConnectionProtocol.PLAY || !ConnectionTweaks.ignorePacketErrors()) {
            decode.call(context, in, out);
            return;
        }
        int decoded = out.size();
        try {
            decode.call(context, in, out);
        } catch (Exception failure) {
            if (failure instanceof SkipPacketException || !PacketErrorPolicy.skippable(failure)) throw failure;
            while (out.size() > decoded) out.remove(out.size() - 1);
            in.skipBytes(in.readableBytes());
            PacketErrorPolicy.skipped("an unreadable play packet", failure);
        }
    }
}
