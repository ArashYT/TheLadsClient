package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ladsapi.LadsApi;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

public class LadsApi189Test {

    @Test
    public void testChannelMatch() {
        Assert.assertTrue("Exact channel must match", LadsApi189.isLadsApiChannel("lads:api"));
        Assert.assertFalse("Null channel must not match", LadsApi189.isLadsApiChannel(null));
        Assert.assertFalse("Empty channel must not match", LadsApi189.isLadsApiChannel(""));
        Assert.assertFalse("Case mismatch must not match", LadsApi189.isLadsApiChannel("LADS:API"));
        Assert.assertFalse("Subchannel must not match", LadsApi189.isLadsApiChannel("lads:api:sub"));
        Assert.assertFalse("Hypixel ping channel must not match", LadsApi189.isLadsApiChannel("hypixel:ping"));
        Assert.assertFalse("FML register must not match", LadsApi189.isLadsApiChannel("REGISTER"));
    }

    @Test
    public void testPayloadSizeGate() {
        Assert.assertFalse("Zero bytes rejected", LadsApi189.isPayloadSizeValid(0));
        Assert.assertFalse("Negative bytes rejected", LadsApi189.isPayloadSizeValid(-1));
        Assert.assertTrue("1 byte accepted", LadsApi189.isPayloadSizeValid(1));
        Assert.assertTrue("MAX_IN bytes accepted", LadsApi189.isPayloadSizeValid(LadsApi.MAX_IN));
        Assert.assertFalse("MAX_IN + 1 bytes rejected", LadsApi189.isPayloadSizeValid(LadsApi.MAX_IN + 1));
        Assert.assertFalse("Integer.MAX_VALUE rejected", LadsApi189.isPayloadSizeValid(Integer.MAX_VALUE));
    }

    @Test
    public void testSendSizeGate() {
        Assert.assertFalse("Null payload rejected", LadsApi189.isSendSizeValid(null));
        Assert.assertFalse("Empty payload rejected", LadsApi189.isSendSizeValid(new byte[0]));
        Assert.assertTrue("1 byte payload accepted", LadsApi189.isSendSizeValid(new byte[1]));
        Assert.assertTrue("MAX_OUT bytes accepted", LadsApi189.isSendSizeValid(new byte[LadsApi.MAX_OUT]));
        Assert.assertFalse("MAX_OUT + 1 bytes rejected", LadsApi189.isSendSizeValid(new byte[LadsApi.MAX_OUT + 1]));
    }

    @Test
    public void testNonDestructiveByteBufRead() {
        byte[] original = new byte[]{10, 20, 30, 40, 50};
        ByteBuf buf = Unpooled.wrappedBuffer(original);
        int initialReaderIndex = buf.readerIndex();
        Assert.assertEquals("Initial reader index is 0", 0, initialReaderIndex);

        int readable = buf.readableBytes();
        Assert.assertEquals("Readable bytes match original length", 5, readable);
        Assert.assertTrue("Payload size is valid", LadsApi189.isPayloadSizeValid(readable));

        byte[] copied = new byte[readable];
        buf.getBytes(buf.readerIndex(), copied);

        Assert.assertEquals("Reader index must NOT change after getBytes", 0, buf.readerIndex());
        Assert.assertArrayEquals("Copied bytes must match original", original, copied);
    }

    @Test
    public void testInstallAndRegistration() {
        LadsApi189.register();
        Assert.assertTrue(LadsApi189.isSendSizeValid(new byte[100]));
    }

    public static void main(String[] args) {
        LadsApi189Test test = new LadsApi189Test();
        test.testChannelMatch();
        test.testPayloadSizeGate();
        test.testSendSizeGate();
        test.testNonDestructiveByteBufRead();
        test.testInstallAndRegistration();
        System.out.println("PASS all LadsApi189 pure logic unit tests");
    }
}
