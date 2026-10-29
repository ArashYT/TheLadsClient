package com.thelads.core.v26_2.feature;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Unit checks for WorldEditCuiPayload codec round-trip on 26.3.
 */
public final class WorldEditCuiCodecChecks {
    private static int passed;

    public static void main(String[] args) {
        checkChannelIdentifier();
        checkRoundTrip();
        checkRoundTripEmpty();
        System.out.println("PASS " + passed + " worldedit:cui codec checks");
        LadsApiCodecChecks.main(args);
    }

    private static void checkChannelIdentifier() {
        require("worldedit:cui".equals(WorldEditCuiPayload.TYPE.id().toString()), "channel identifier must be worldedit:cui");
        require(WorldEditCuiPayload.TYPE.equals(new WorldEditCuiPayload(new byte[0]).type()), "payload type() matches TYPE");
    }

    private static void checkRoundTrip() {
        byte[] data = "s|cuboid\np|0|1|2|3|0\n".getBytes(StandardCharsets.UTF_8);
        WorldEditCuiPayload original = new WorldEditCuiPayload(data);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WorldEditCuiPayload.CODEC.encode(buf, original);
            WorldEditCuiPayload decoded = WorldEditCuiPayload.CODEC.decode(buf);
            require(Arrays.equals(original.data(), decoded.data()), "payload round-trip matches original");
        } finally {
            buf.release();
        }
    }

    private static void checkRoundTripEmpty() {
        byte[] data = new byte[0];
        WorldEditCuiPayload original = new WorldEditCuiPayload(data);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WorldEditCuiPayload.CODEC.encode(buf, original);
            WorldEditCuiPayload decoded = WorldEditCuiPayload.CODEC.decode(buf);
            require(Arrays.equals(original.data(), decoded.data()), "empty payload round-trip matches original");
        } finally {
            buf.release();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}
