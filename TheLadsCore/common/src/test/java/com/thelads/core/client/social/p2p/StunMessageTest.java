package com.thelads.core.client.social.p2p;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class StunMessageTest {

    // Helper to unhexlify
    private static byte[] unhex(String s) {
        String clean = s.replaceAll("\\s+", "");
        byte[] b = new byte[clean.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    @Test
    public void roundTripsBindingWithIntegrityAndFingerprint() {
        // RFC 5769 2.1 Sample Request
        String reqHex =
                "00010058" +
                "2112a442" +
                "b7e7a701bc34d686fa87dfae" +
                "802200105354554e207465737420636c69656e74" +
                "002400046e0001ff" +
                "80290008932ff9b151263b36" +
                "000600096576746a3a68367659202020" +
                "000800149aeaa70cbfd8cb56781ef2b5b2d3f249c1b571a2" +
                "80280004e57a3bcf";

        byte[] reqBytes = unhex(reqHex);
        byte[] pw = "VOkJxbRl1RmTxUk/WvJxBt".getBytes(StandardCharsets.UTF_8);

        // Verify validation methods on raw bytes
        assertTrue(StunMessage.validateFingerprint(reqBytes, 0, reqBytes.length), "Fingerprint should validate on RFC 5769 2.1");
        assertTrue(StunMessage.validateMessageIntegrity(reqBytes, 0, reqBytes.length, pw), "Message integrity should validate on RFC 5769 2.1");

        // Decode RFC 5769 2.1
        StunMessage req = StunMessage.decode(reqBytes, 0, reqBytes.length);
        assertEquals(StunMessage.BINDING_REQUEST, req.getType());
        assertEquals("STUN test client", req.getStringAttribute(StunMessage.ATTR_SOFTWARE));
        assertEquals("evtj:h6vY", req.getStringAttribute(StunMessage.ATTR_USERNAME));
        assertEquals(0x6e0001ff, req.getIntAttribute(StunMessage.ATTR_PRIORITY));
        assertTrue(req.hasAttribute(StunMessage.ATTR_ICE_CONTROLLED));

        // RFC 5769 2.2 Sample IPv4 Response
        String respHex =
                "0101003c" +
                "2112a442" +
                "b7e7a701bc34d686fa87dfae" +
                "8022000b7465737420766563746f7220" +
                "002000080001a147e112a643" +
                "000800142b91f599fd9e90c38c7489f92af9ba53f06be7d7" +
                "80280004c07d4c96";

        byte[] respBytes = unhex(respHex);

        assertTrue(StunMessage.validateFingerprint(respBytes, 0, respBytes.length), "Fingerprint should validate on RFC 5769 2.2");
        assertTrue(StunMessage.validateMessageIntegrity(respBytes, 0, respBytes.length, pw), "Message integrity should validate on RFC 5769 2.2");

        StunMessage resp = StunMessage.decode(respBytes, 0, respBytes.length);
        assertEquals(StunMessage.BINDING_RESPONSE, resp.getType());
        assertEquals("test vector", resp.getStringAttribute(StunMessage.ATTR_SOFTWARE));
        InetSocketAddress mapped = resp.getXorAddressAttribute(StunMessage.ATTR_XOR_MAPPED_ADDRESS);
        assertNotNull(mapped);
        assertEquals("192.0.2.1", mapped.getAddress().getHostAddress());
        assertEquals(32853, mapped.getPort());

        // Re-encode and test roundtrip
        StunMessage fresh = new StunMessage(StunMessage.BINDING_RESPONSE, resp.getTransactionId());
        fresh.addStringAttribute(StunMessage.ATTR_SOFTWARE, "test vector");
        fresh.addXorAddressAttribute(StunMessage.ATTR_XOR_MAPPED_ADDRESS, mapped);

        byte[] encoded = fresh.encode(pw, true);
        assertEquals(respBytes.length, encoded.length);
        assertTrue(StunMessage.validateFingerprint(encoded, 0, encoded.length));
        assertTrue(StunMessage.validateMessageIntegrity(encoded, 0, encoded.length, pw));

        StunMessage freshDecoded = StunMessage.decode(encoded, 0, encoded.length);
        assertEquals(StunMessage.BINDING_RESPONSE, freshDecoded.getType());
        assertEquals("test vector", freshDecoded.getStringAttribute(StunMessage.ATTR_SOFTWARE));
        InetSocketAddress freshMapped = freshDecoded.getXorAddressAttribute(StunMessage.ATTR_XOR_MAPPED_ADDRESS);
        assertNotNull(freshMapped);
        assertEquals("192.0.2.1", freshMapped.getAddress().getHostAddress());
        assertEquals(32853, freshMapped.getPort());
    }

    @Test
    public void testLongTermCredentials() {
        // RFC 5769 2.4 Sample Request with Long-Term Authentication
        String msgHex =
                "00010060" +
                "2112a442" +
                "78ad3433c6ad72c029da412e" +
                "00060012e3839ee38388e383aae38383e382afe382b90000" +
                "0015001c662f2f3439396b39353464364f4c33346f4c39465354767936347341" +
                "0014000b6578616d706c652e6f726700" +
                "00080014f67024656dd64a3e02b8e0712e85c9a28ca89666";

        byte[] msgBytes = unhex(msgHex);
        String username = "\u30de\u30c8\u30ea\u30c3\u30af\u30b9";
        String realm = "example.org";
        String password = "TheMatrIX";

        byte[] key = StunMessage.deriveLongTermKey(username, realm, password);
        assertTrue(StunMessage.validateMessageIntegrity(msgBytes, 0, msgBytes.length, key));

        StunMessage msg = StunMessage.decode(msgBytes, 0, msgBytes.length);
        assertEquals(username, msg.getStringAttribute(StunMessage.ATTR_USERNAME));
        assertEquals(realm, msg.getStringAttribute(StunMessage.ATTR_REALM));
        assertEquals("f//499k954d6OL34oL9FSTvy64sA", msg.getStringAttribute(StunMessage.ATTR_NONCE));
    }
}
