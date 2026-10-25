package com.thelads.core.client.social.p2p;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encodes and decodes STUN messages (RFC 5389 / RFC 8489 / RFC 8656 / RFC 8445).
 * Supports XOR-MAPPED-ADDRESS, MESSAGE-INTEGRITY, FINGERPRINT, and ICE/TURN attributes.
 */
public final class StunMessage {
    public static final int MAGIC_COOKIE = 0x2112A442;
    public static final int HEADER_LENGTH = 20;

    // Message Types (RFC 5389 & RFC 8656)
    public static final int BINDING_REQUEST = 0x0001;
    public static final int BINDING_RESPONSE = 0x0101;
    public static final int BINDING_ERROR = 0x0111;

    public static final int ALLOCATE_REQUEST = 0x0003;
    public static final int ALLOCATE_RESPONSE = 0x0103;
    public static final int ALLOCATE_ERROR = 0x0113;

    public static final int REFRESH_REQUEST = 0x0004;
    public static final int REFRESH_RESPONSE = 0x0104;
    public static final int REFRESH_ERROR = 0x0114;

    public static final int SEND_INDICATION = 0x0016;
    public static final int DATA_INDICATION = 0x0017;

    public static final int CREATE_PERMISSION_REQUEST = 0x0008;
    public static final int CREATE_PERMISSION_RESPONSE = 0x0108;
    public static final int CREATE_PERMISSION_ERROR = 0x0118;

    public static final int CHANNEL_BIND_REQUEST = 0x0009;
    public static final int CHANNEL_BIND_RESPONSE = 0x0109;
    public static final int CHANNEL_BIND_ERROR = 0x0119;

    // Attributes (RFC 5389, RFC 8656, RFC 8445)
    public static final int ATTR_MAPPED_ADDRESS = 0x0001;
    public static final int ATTR_USERNAME = 0x0006;
    public static final int ATTR_MESSAGE_INTEGRITY = 0x0008;
    public static final int ATTR_ERROR_CODE = 0x0009;
    public static final int ATTR_UNKNOWN_ATTRIBUTES = 0x000A;
    public static final int ATTR_CHANNEL_NUMBER = 0x000C;
    public static final int ATTR_LIFETIME = 0x000D;
    public static final int ATTR_XOR_PEER_ADDRESS = 0x0012;
    public static final int ATTR_DATA = 0x0013;
    public static final int ATTR_REALM = 0x0014;
    public static final int ATTR_NONCE = 0x0015;
    public static final int ATTR_XOR_RELAYED_ADDRESS = 0x0016;
    public static final int ATTR_EVEN_PORT = 0x0018;
    public static final int ATTR_REQUESTED_TRANSPORT = 0x0019;
    public static final int ATTR_DONT_FRAG = 0x001A;
    public static final int ATTR_XOR_MAPPED_ADDRESS = 0x0020;
    public static final int ATTR_PRIORITY = 0x0024;
    public static final int ATTR_USE_CANDIDATE = 0x0025;
    public static final int ATTR_SOFTWARE = 0x8022;
    public static final int ATTR_FINGERPRINT = 0x8028;
    public static final int ATTR_ICE_CONTROLLED = 0x8029;
    public static final int ATTR_ICE_CONTROLLING = 0x802A;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static class Attribute {
        public final int type;
        public final byte[] value;

        public Attribute(int type, byte[] value) {
            this.type = type;
            this.value = value == null ? new byte[0] : value;
        }
    }

    private int type;
    private final byte[] transactionId = new byte[12];
    private final List<Attribute> attributes = new ArrayList<>();

    public StunMessage(int type) {
        this.type = type;
        RANDOM.nextBytes(this.transactionId);
    }

    public StunMessage(int type, byte[] transactionId) {
        this.type = type;
        if (transactionId != null && transactionId.length == 12) {
            System.arraycopy(transactionId, 0, this.transactionId, 0, 12);
        } else {
            RANDOM.nextBytes(this.transactionId);
        }
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public byte[] getTransactionId() {
        return Arrays.copyOf(transactionId, transactionId.length);
    }

    public List<Attribute> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(int type, byte[] value) {
        attributes.add(new Attribute(type, value));
    }

    public byte[] getRawAttribute(int type) {
        for (Attribute a : attributes) {
            if (a.type == type) {
                return a.value;
            }
        }
        return null;
    }

    public boolean hasAttribute(int type) {
        return getRawAttribute(type) != null;
    }

    public String getStringAttribute(int type) {
        byte[] v = getRawAttribute(type);
        return v == null ? null : new String(v, StandardCharsets.UTF_8);
    }

    public void addStringAttribute(int type, String str) {
        addAttribute(type, str.getBytes(StandardCharsets.UTF_8));
    }

    public Long getLongAttribute(int type) {
        byte[] v = getRawAttribute(type);
        if (v == null || v.length < 8) return null;
        ByteBuffer bb = ByteBuffer.wrap(v).order(ByteOrder.BIG_ENDIAN);
        return bb.getLong();
    }

    public void addLongAttribute(int type, long val) {
        ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        bb.putLong(val);
        addAttribute(type, bb.array());
    }

    public Integer getIntAttribute(int type) {
        byte[] v = getRawAttribute(type);
        if (v == null || v.length < 4) return null;
        ByteBuffer bb = ByteBuffer.wrap(v).order(ByteOrder.BIG_ENDIAN);
        return bb.getInt();
    }

    public void addIntAttribute(int type, int val) {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(val);
        addAttribute(type, bb.array());
    }

    public InetSocketAddress getXorAddressAttribute(int type) {
        byte[] v = getRawAttribute(type);
        if (v == null || v.length < 8) return null;
        int family = v[1] & 0xFF;
        int xPort = ((v[2] & 0xFF) << 8) | (v[3] & 0xFF);
        int port = xPort ^ ((MAGIC_COOKIE >> 16) & 0xFFFF);

        try {
            if (family == 0x01) { // IPv4
                byte[] ipBytes = new byte[4];
                byte[] cookieBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(MAGIC_COOKIE).array();
                for (int i = 0; i < 4; i++) {
                    ipBytes[i] = (byte) (v[4 + i] ^ cookieBytes[i]);
                }
                InetAddress addr = InetAddress.getByAddress(ipBytes);
                return new InetSocketAddress(addr, port);
            } else if (family == 0x02) { // IPv6
                if (v.length < 20) return null;
                byte[] ipBytes = new byte[16];
                byte[] xorKey = new byte[16];
                ByteBuffer.wrap(xorKey).order(ByteOrder.BIG_ENDIAN).putInt(MAGIC_COOKIE);
                System.arraycopy(transactionId, 0, xorKey, 4, 12);
                for (int i = 0; i < 16; i++) {
                    ipBytes[i] = (byte) (v[4 + i] ^ xorKey[i]);
                }
                InetAddress addr = InetAddress.getByAddress(ipBytes);
                return new InetSocketAddress(addr, port);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void addXorAddressAttribute(int type, InetSocketAddress address) {
        InetAddress addr = address.getAddress();
        int port = address.getPort();
        int xPort = port ^ ((MAGIC_COOKIE >> 16) & 0xFFFF);

        if (addr instanceof Inet4Address) {
            byte[] ipBytes = addr.getAddress();
            byte[] cookieBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(MAGIC_COOKIE).array();
            byte[] val = new byte[8];
            val[0] = 0x00;
            val[1] = 0x01; // IPv4
            val[2] = (byte) ((xPort >> 8) & 0xFF);
            val[3] = (byte) (xPort & 0xFF);
            for (int i = 0; i < 4; i++) {
                val[4 + i] = (byte) (ipBytes[i] ^ cookieBytes[i]);
            }
            addAttribute(type, val);
        } else if (addr instanceof Inet6Address) {
            byte[] ipBytes = addr.getAddress();
            byte[] xorKey = new byte[16];
            ByteBuffer.wrap(xorKey).order(ByteOrder.BIG_ENDIAN).putInt(MAGIC_COOKIE);
            System.arraycopy(transactionId, 0, xorKey, 4, 12);
            byte[] val = new byte[20];
            val[0] = 0x00;
            val[1] = 0x02; // IPv6
            val[2] = (byte) ((xPort >> 8) & 0xFF);
            val[3] = (byte) (xPort & 0xFF);
            for (int i = 0; i < 16; i++) {
                val[4 + i] = (byte) (ipBytes[i] ^ xorKey[i]);
            }
            addAttribute(type, val);
        }
    }

    public byte[] getDataAttribute() {
        return getRawAttribute(ATTR_DATA);
    }

    public void addDataAttribute(byte[] data) {
        addAttribute(ATTR_DATA, data);
    }

    public int getErrorCode() {
        byte[] v = getRawAttribute(ATTR_ERROR_CODE);
        if (v == null || v.length < 4) return -1;
        int errClass = v[2] & 0x07;
        int number = v[3] & 0xFF;
        return errClass * 100 + number;
    }

    public String getErrorMessage() {
        byte[] v = getRawAttribute(ATTR_ERROR_CODE);
        if (v == null || v.length <= 4) return "";
        return new String(v, 4, v.length - 4, StandardCharsets.UTF_8);
    }

    public void addErrorCode(int code, String reason) {
        byte[] reasonBytes = (reason == null ? "" : reason).getBytes(StandardCharsets.UTF_8);
        byte[] val = new byte[4 + reasonBytes.length];
        val[0] = 0;
        val[1] = 0;
        val[2] = (byte) ((code / 100) & 0x07);
        val[3] = (byte) (code % 100);
        System.arraycopy(reasonBytes, 0, val, 4, reasonBytes.length);
        addAttribute(ATTR_ERROR_CODE, val);
    }

    public static byte[] deriveLongTermKey(String username, String realm, String password) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String s = username + ":" + realm + ":" + password;
            return md5.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] deriveShortTermKey(String password) {
        return password.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] encode() {
        return encode(null, false);
    }

    public byte[] encode(byte[] keyForIntegrity, boolean addFingerprint) {
        // Calculate size of existing attributes (excluding integrity and fingerprint if they are to be added)
        int attrBytesLen = 0;
        for (Attribute a : attributes) {
            if (a.type == ATTR_MESSAGE_INTEGRITY || a.type == ATTR_FINGERPRINT) {
                continue;
            }
            int padded = (a.value.length + 3) & ~3;
            attrBytesLen += 4 + padded;
        }

        int totalLen = HEADER_LENGTH + attrBytesLen;
        if (keyForIntegrity != null) {
            totalLen += 4 + 20; // MESSAGE-INTEGRITY attribute
        }
        if (addFingerprint) {
            totalLen += 4 + 4; // FINGERPRINT attribute
        }

        ByteBuffer buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) type);
        buf.putShort((short) 0); // Length placeholder
        buf.putInt(MAGIC_COOKIE);
        buf.put(transactionId);

        // Write attributes up to integrity
        for (Attribute a : attributes) {
            if (a.type == ATTR_MESSAGE_INTEGRITY || a.type == ATTR_FINGERPRINT) {
                continue;
            }
            buf.putShort((short) a.type);
            buf.putShort((short) a.value.length);
            buf.put(a.value);
            int pad = ((a.value.length + 3) & ~3) - a.value.length;
            for (int p = 0; p < pad; p++) {
                buf.put((byte) 0);
            }
        }

        // Add MESSAGE-INTEGRITY if requested
        if (keyForIntegrity != null) {
            int lenBeforeIntegrity = buf.position() - HEADER_LENGTH;
            int lenForHmac = lenBeforeIntegrity + 24; // Including 24-byte MESSAGE-INTEGRITY attribute
            buf.putShort(2, (short) lenForHmac);

            byte[] hmacInput = new byte[HEADER_LENGTH + lenBeforeIntegrity];
            System.arraycopy(buf.array(), 0, hmacInput, 0, hmacInput.length);

            try {
                Mac mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(keyForIntegrity, "HmacSHA1"));
                byte[] hmac = mac.doFinal(hmacInput);

                buf.putShort((short) ATTR_MESSAGE_INTEGRITY);
                buf.putShort((short) 20);
                buf.put(hmac);
            } catch (Exception e) {
                throw new RuntimeException("HMAC-SHA1 failed", e);
            }
        }

        // Add FINGERPRINT if requested
        if (addFingerprint) {
            int lenBeforeFingerprint = buf.position() - HEADER_LENGTH;
            int lenForCrc = lenBeforeFingerprint + 8; // Including 8-byte FINGERPRINT attribute
            buf.putShort(2, (short) lenForCrc);

            byte[] crcInput = new byte[HEADER_LENGTH + lenBeforeFingerprint];
            System.arraycopy(buf.array(), 0, crcInput, 0, crcInput.length);

            CRC32 crc = new CRC32();
            crc.update(crcInput);
            long crcVal = (crc.getValue() ^ 0x5354554EL) & 0xFFFFFFFFL;

            buf.putShort((short) ATTR_FINGERPRINT);
            buf.putShort((short) 4);
            buf.putInt((int) crcVal);
        }

        // Set final message length in header
        buf.putShort(2, (short) (buf.position() - HEADER_LENGTH));
        return buf.array();
    }

    public static StunMessage decode(byte[] data, int offset, int length) {
        if (length < HEADER_LENGTH) {
            throw new IllegalArgumentException("STUN message too short: " + length);
        }
        ByteBuffer buf = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN);
        int msgType = buf.getShort() & 0xFFFF;
        if ((msgType & 0xC000) != 0) {
            throw new IllegalArgumentException("Invalid STUN message type: " + msgType);
        }
        int msgLength = buf.getShort() & 0xFFFF;
        int cookie = buf.getInt();
        if (cookie != MAGIC_COOKIE) {
            throw new IllegalArgumentException("Invalid STUN magic cookie: 0x" + Integer.toHexString(cookie));
        }
        byte[] tid = new byte[12];
        buf.get(tid);

        if (HEADER_LENGTH + msgLength > length) {
            throw new IllegalArgumentException("Truncated STUN message: declared " + msgLength + ", available " + (length - HEADER_LENGTH));
        }

        StunMessage msg = new StunMessage(msgType, tid);
        boolean afterIntegrity = false; // RFC 8489 14.5: after MESSAGE-INTEGRITY only FINGERPRINT counts (F4: no appended USE-CANDIDATE)
        int bytesRead = 0;
        while (bytesRead + 4 <= msgLength) {
            int attrType = buf.getShort() & 0xFFFF;
            int attrLen = buf.getShort() & 0xFFFF;
            bytesRead += 4;

            if (bytesRead + attrLen > msgLength) {
                break; // Truncated attribute
            }
            byte[] attrVal = new byte[attrLen];
            buf.get(attrVal);
            bytesRead += attrLen;

            int pad = ((attrLen + 3) & ~3) - attrLen;
            if (bytesRead + pad <= msgLength) {
                for (int p = 0; p < pad; p++) {
                    buf.get();
                }
                bytesRead += pad;
            }
            if (!afterIntegrity || attrType == ATTR_FINGERPRINT) msg.addAttribute(attrType, attrVal);
            if (attrType == ATTR_MESSAGE_INTEGRITY) afterIntegrity = true;
        }
        return msg;
    }

    public static boolean validateFingerprint(byte[] data, int offset, int length) {
        if (length < HEADER_LENGTH + 8) return false;
        ByteBuffer buf = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN);
        int msgLength = buf.getShort(offset + 2) & 0xFFFF;
        if (HEADER_LENGTH + msgLength > length) return false;

        // Check if the last attribute is FINGERPRINT
        int fpOffset = offset + HEADER_LENGTH + msgLength - 8;
        int attrType = buf.getShort(fpOffset) & 0xFFFF;
        int attrLen = buf.getShort(fpOffset + 2) & 0xFFFF;
        if (attrType != ATTR_FINGERPRINT || attrLen != 4) return false;

        long expectedFp = buf.getInt(fpOffset + 4) & 0xFFFFFFFFL;

        CRC32 crc = new CRC32();
        crc.update(data, offset, fpOffset - offset);
        long computedFp = (crc.getValue() ^ 0x5354554EL) & 0xFFFFFFFFL;
        return expectedFp == computedFp;
    }

    public static boolean validateMessageIntegrity(byte[] data, int offset, int length, byte[] key) {
        if (length < HEADER_LENGTH + 24) return false;
        ByteBuffer buf = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN);
        int msgLength = buf.getShort(offset + 2) & 0xFFFF;
        if (HEADER_LENGTH + msgLength > length) return false;

        // Search for MESSAGE-INTEGRITY attribute
        int pos = offset + HEADER_LENGTH;
        int miOffset = -1;
        while (pos + 4 <= offset + HEADER_LENGTH + msgLength) {
            int attrType = buf.getShort(pos) & 0xFFFF;
            int attrLen = buf.getShort(pos + 2) & 0xFFFF;
            if (attrType == ATTR_MESSAGE_INTEGRITY && attrLen == 20) {
                miOffset = pos;
                break;
            }
            pos += 4 + ((attrLen + 3) & ~3);
        }

        if (miOffset == -1) return false;

        byte[] expectedHmac = new byte[20];
        System.arraycopy(data, miOffset + 4, expectedHmac, 0, 20);

        // Header length field for HMAC computation is length up to and including MESSAGE-INTEGRITY
        int lenForHmac = miOffset - (offset + HEADER_LENGTH) + 24;
        byte[] hmacInput = new byte[miOffset - offset];
        System.arraycopy(data, offset, hmacInput, 0, hmacInput.length);
        hmacInput[2] = (byte) ((lenForHmac >> 8) & 0xFF);
        hmacInput[3] = (byte) (lenForHmac & 0xFF);

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] computedHmac = mac.doFinal(hmacInput);
            return MessageDigest.isEqual(expectedHmac, computedHmac);
        } catch (Exception e) {
            return false;
        }
    }

    public static int getIntBE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) |
               ((b[off + 1] & 0xFF) << 16) |
               ((b[off + 2] & 0xFF) << 8) |
               (b[off + 3] & 0xFF);
    }

    public static void putIntBE(byte[] b, int off, int val) {
        b[off]     = (byte) ((val >>> 24) & 0xFF);
        b[off + 1] = (byte) ((val >>> 16) & 0xFF);
        b[off + 2] = (byte) ((val >>> 8) & 0xFF);
        b[off + 3] = (byte) (val & 0xFF);
    }

    public static short getShortBE(byte[] b, int off) {
        return (short) (((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF));
    }

    public static void putShortBE(byte[] b, int off, short val) {
        b[off]     = (byte) ((val >>> 8) & 0xFF);
        b[off + 1] = (byte) (val & 0xFF);
    }

    public static long getLongBE(byte[] b, int off) {
        return (((long) b[off] & 0xFF) << 56) |
               (((long) b[off + 1] & 0xFF) << 48) |
               (((long) b[off + 2] & 0xFF) << 40) |
               (((long) b[off + 3] & 0xFF) << 32) |
               (((long) b[off + 4] & 0xFF) << 24) |
               (((long) b[off + 5] & 0xFF) << 16) |
               (((long) b[off + 6] & 0xFF) << 8) |
               ((long) b[off + 7] & 0xFF);
    }

    public static void putLongBE(byte[] b, int off, long val) {
        b[off]     = (byte) ((val >>> 56) & 0xFF);
        b[off + 1] = (byte) ((val >>> 48) & 0xFF);
        b[off + 2] = (byte) ((val >>> 40) & 0xFF);
        b[off + 3] = (byte) ((val >>> 32) & 0xFF);
        b[off + 4] = (byte) ((val >>> 24) & 0xFF);
        b[off + 5] = (byte) ((val >>> 16) & 0xFF);
        b[off + 6] = (byte) ((val >>> 8) & 0xFF);
        b[off + 7] = (byte) (val & 0xFF);
    }
}
