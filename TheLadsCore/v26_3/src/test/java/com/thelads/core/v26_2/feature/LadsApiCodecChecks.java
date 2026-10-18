package com.thelads.core.v26_2.feature;

import com.thelads.core.client.ladsapi.LadsApi;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Unit test for LadsApiPayload codec round-trip and size limit enforcement.
 * Runs independently without launching the Minecraft client or server.
 */
public final class LadsApiCodecChecks {
    private static int passed;

    public static void main(String[] args) {
        checkChannelIdentifier();
        checkRoundTripNormal();
        checkRoundTripEmpty();
        checkRoundTripMaxIn();
        checkRejectOversized();
        System.out.println("PASS " + passed + " lads:api codec checks: round-trip, empty, max bound, and oversize rejection");
    }

    private static void checkChannelIdentifier() {
        require("lads:api".equals(LadsApiPayload.TYPE.id().toString()), "channel identifier must be lads:api");
        require(LadsApiPayload.TYPE.equals(new LadsApiPayload(new byte[0]).type()), "payload type() matches TYPE");
    }

    private static void checkRoundTripNormal() {
        byte[] data = "{\"t\":\"hello\",\"v\":1,\"server\":\"Lads SMP\"}".getBytes(StandardCharsets.UTF_8);
        LadsApiPayload original = new LadsApiPayload(data);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            LadsApiPayload.CODEC.encode(buf, original);
            LadsApiPayload decoded = LadsApiPayload.CODEC.decode(buf);
            require(Arrays.equals(original.data(), decoded.data()), "normal payload round-trip matches original");
        } finally {
            buf.release();
        }
    }

    private static void checkRoundTripEmpty() {
        byte[] data = new byte[0];
        LadsApiPayload original = new LadsApiPayload(data);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            LadsApiPayload.CODEC.encode(buf, original);
            LadsApiPayload decoded = LadsApiPayload.CODEC.decode(buf);
            require(Arrays.equals(original.data(), decoded.data()), "empty payload round-trip matches original");
        } finally {
            buf.release();
        }
    }

    private static void checkRoundTripMaxIn() {
        byte[] data = new byte[LadsApi.MAX_IN];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i & 0x7F);
        }
        LadsApiPayload original = new LadsApiPayload(data);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(LadsApi.MAX_IN));
        try {
            LadsApiPayload.CODEC.encode(buf, original);
            LadsApiPayload decoded = LadsApiPayload.CODEC.decode(buf);
            require(Arrays.equals(original.data(), decoded.data()), "max-size payload (" + LadsApi.MAX_IN + " bytes) round-trip matches");
        } finally {
            buf.release();
        }
    }

    private static void checkRejectOversized() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(LadsApi.MAX_IN + 1));
        try {
            buf.writeZero(LadsApi.MAX_IN + 1);
            boolean rejected = false;
            try {
                LadsApiPayload.CODEC.decode(buf);
            } catch (IllegalArgumentException expected) {
                rejected = expected.getMessage() != null && expected.getMessage().contains("exceeds max size");
            }
            require(rejected, "payload exceeding MAX_IN (" + (LadsApi.MAX_IN + 1) + " bytes) is rejected with IllegalArgumentException");
        } finally {
            buf.release();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
        passed++;
    }
}
