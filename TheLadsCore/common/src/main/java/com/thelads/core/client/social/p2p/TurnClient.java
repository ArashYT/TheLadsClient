package com.thelads.core.client.social.p2p;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TURN client (RFC 8656) supporting Allocate, Refresh, CreatePermission, ChannelBind, and ChannelData.
 * Authenticates using the long-term credential mechanism (RFC 5389).
 */
public final class TurnClient implements AutoCloseable {
    private final String rawUrl;
    private final String username;
    private final String password;
    private final InetSocketAddress serverAddress;
    private final DatagramSocket socket;
    private final boolean ownsSocket;

    private String realm;
    private String nonce;
    private byte[] key;
    private InetSocketAddress relayedAddress;
    private int lifetimeSeconds;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Bound channels: channelNumber -> peer address
    private final ConcurrentHashMap<Integer, InetSocketAddress> boundChannels = new ConcurrentHashMap<>();

    public TurnClient(String url, String username, String password) throws IOException {
        this(url, username, password, new DatagramSocket(), true);
    }

    public TurnClient(String url, String username, String password, DatagramSocket socket) throws IOException {
        this(url, username, password, socket, false);
    }

    private TurnClient(String url, String username, String password, DatagramSocket socket, boolean ownsSocket) throws IOException {
        this.rawUrl = url;
        this.username = username;
        this.password = password;
        this.socket = socket;
        this.ownsSocket = ownsSocket;
        this.serverAddress = parseServerAddress(url);
    }

    public static InetSocketAddress parseServerAddress(String url) {
        String u = url.trim();
        if (u.startsWith("turn:")) {
            u = u.substring(5);
        } else if (u.startsWith("stun:")) {
            u = u.substring(5);
        }
        int qIdx = u.indexOf('?');
        if (qIdx >= 0) {
            u = u.substring(0, qIdx);
        }
        String host;
        int port = 3478;
        int colon = u.lastIndexOf(':');
        if (colon >= 0) {
            host = u.substring(0, colon);
            port = Integer.parseInt(u.substring(colon + 1));
        } else {
            host = u;
        }
        return new InetSocketAddress(host, port);
    }

    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    public InetSocketAddress getRelayedAddress() {
        return relayedAddress;
    }

    public int getLifetimeSeconds() {
        return lifetimeSeconds;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    /**
     * Executes the initial Allocate handshake with long-term authentication.
     */
    public synchronized InetSocketAddress allocate() throws IOException {
        if (closed.get()) throw new IOException("TurnClient is closed");

        // 1. Send initial Allocate Request without credentials
        StunMessage initReq = new StunMessage(StunMessage.ALLOCATE_REQUEST);
        initReq.addIntAttribute(StunMessage.ATTR_REQUESTED_TRANSPORT, 0x11000000); // UDP (17)
        byte[] initBytes = initReq.encode(null, true);

        StunMessage challengeResp = sendAndReceive(initBytes, initReq.getTransactionId(), 3000);
        if (challengeResp.getType() != StunMessage.ALLOCATE_ERROR) {
            // Unexpected success or wrong message
            if (challengeResp.getType() == StunMessage.ALLOCATE_RESPONSE) {
                this.relayedAddress = challengeResp.getXorAddressAttribute(StunMessage.ATTR_XOR_RELAYED_ADDRESS);
                Integer lt = challengeResp.getIntAttribute(StunMessage.ATTR_LIFETIME);
                this.lifetimeSeconds = lt == null ? 600 : lt;
                return this.relayedAddress;
            }
            throw new IOException("Expected 401 challenge, got: 0x" + Integer.toHexString(challengeResp.getType()));
        }

        this.realm = challengeResp.getStringAttribute(StunMessage.ATTR_REALM);
        this.nonce = challengeResp.getStringAttribute(StunMessage.ATTR_NONCE);
        if (realm == null || nonce == null) {
            throw new IOException("Missing REALM or NONCE in 401 response");
        }
        this.key = StunMessage.deriveLongTermKey(username, realm, password);

        // 2. Send authenticated Allocate Request
        StunMessage authReq = new StunMessage(StunMessage.ALLOCATE_REQUEST);
        authReq.addIntAttribute(StunMessage.ATTR_REQUESTED_TRANSPORT, 0x11000000);
        authReq.addStringAttribute(StunMessage.ATTR_USERNAME, username);
        authReq.addStringAttribute(StunMessage.ATTR_REALM, realm);
        authReq.addStringAttribute(StunMessage.ATTR_NONCE, nonce);
        byte[] authBytes = authReq.encode(key, true);

        StunMessage authResp = sendAndReceive(authBytes, authReq.getTransactionId(), 3000);
        if (authResp.getType() == StunMessage.ALLOCATE_ERROR) {
            throw new IOException("Allocate failed: " + authResp.getErrorCode() + " " + authResp.getErrorMessage());
        }
        if (authResp.getType() != StunMessage.ALLOCATE_RESPONSE) {
            throw new IOException("Expected Allocate Response, got: 0x" + Integer.toHexString(authResp.getType()));
        }

        this.relayedAddress = authResp.getXorAddressAttribute(StunMessage.ATTR_XOR_RELAYED_ADDRESS);
        if (this.relayedAddress == null) {
            throw new IOException("No XOR-RELAYED-ADDRESS in Allocate response");
        }
        Integer lt = authResp.getIntAttribute(StunMessage.ATTR_LIFETIME);
        this.lifetimeSeconds = lt == null ? 600 : lt;
        return this.relayedAddress;
    }

    /**
     * Refreshes the allocation lifetime. If lifetimeSec == 0, terminates the allocation.
     */
    public synchronized int refresh(int lifetimeSec) throws IOException {
        if (closed.get()) throw new IOException("TurnClient is closed");
        if (key == null || realm == null || nonce == null) {
            throw new IOException("Cannot refresh before successful allocate");
        }

        StunMessage req = new StunMessage(StunMessage.REFRESH_REQUEST);
        req.addIntAttribute(StunMessage.ATTR_LIFETIME, lifetimeSec);
        req.addStringAttribute(StunMessage.ATTR_USERNAME, username);
        req.addStringAttribute(StunMessage.ATTR_REALM, realm);
        req.addStringAttribute(StunMessage.ATTR_NONCE, nonce);
        byte[] bytes = req.encode(key, true);

        StunMessage resp = sendAndReceive(bytes, req.getTransactionId(), 3000);
        if (resp.getType() == StunMessage.REFRESH_ERROR) {
            throw new IOException("Refresh failed: " + resp.getErrorCode() + " " + resp.getErrorMessage());
        }
        Integer lt = resp.getIntAttribute(StunMessage.ATTR_LIFETIME);
        this.lifetimeSeconds = lt == null ? 0 : lt;
        return this.lifetimeSeconds;
    }

    /**
     * Creates a permission for the peer IP to send to our relayed address.
     */
    public synchronized void createPermission(InetSocketAddress peerAddress) throws IOException {
        if (closed.get()) throw new IOException("TurnClient is closed");
        if (key == null) throw new IOException("Cannot create permission before allocate");

        StunMessage req = new StunMessage(StunMessage.CREATE_PERMISSION_REQUEST);
        req.addXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS, peerAddress);
        req.addStringAttribute(StunMessage.ATTR_USERNAME, username);
        req.addStringAttribute(StunMessage.ATTR_REALM, realm);
        req.addStringAttribute(StunMessage.ATTR_NONCE, nonce);
        byte[] bytes = req.encode(key, true);

        StunMessage resp = sendAndReceive(bytes, req.getTransactionId(), 3000);
        if (resp.getType() == StunMessage.CREATE_PERMISSION_ERROR) {
            throw new IOException("CreatePermission failed: " + resp.getErrorCode() + " " + resp.getErrorMessage());
        }
    }

    /**
     * Binds a channel number (0x4000 - 0x7FFE) to a peer address.
     */
    public synchronized void channelBind(int channelNumber, InetSocketAddress peerAddress) throws IOException {
        if (closed.get()) throw new IOException("TurnClient is closed");
        if (channelNumber < 0x4000 || channelNumber > 0x7FFE) {
            throw new IllegalArgumentException("Invalid channel number: 0x" + Integer.toHexString(channelNumber));
        }

        StunMessage req = new StunMessage(StunMessage.CHANNEL_BIND_REQUEST);
        req.addIntAttribute(StunMessage.ATTR_CHANNEL_NUMBER, channelNumber << 16);
        req.addXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS, peerAddress);
        req.addStringAttribute(StunMessage.ATTR_USERNAME, username);
        req.addStringAttribute(StunMessage.ATTR_REALM, realm);
        req.addStringAttribute(StunMessage.ATTR_NONCE, nonce);
        byte[] bytes = req.encode(key, true);

        StunMessage resp = sendAndReceive(bytes, req.getTransactionId(), 3000);
        if (resp.getType() == StunMessage.CHANNEL_BIND_ERROR) {
            throw new IOException("ChannelBind failed: " + resp.getErrorCode() + " " + resp.getErrorMessage());
        }
        boundChannels.put(channelNumber, peerAddress);
    }

    /**
     * Sends data encapsulated in a ChannelData message (RFC 8656 section 12).
     */
    public void sendChannelData(int channelNumber, byte[] data, int offset, int length) throws IOException {
        byte[] pkt = encodeChannelData(channelNumber, data, offset, length);
        DatagramPacket dp = new DatagramPacket(pkt, pkt.length, serverAddress);
        socket.send(dp);
    }

    public static byte[] encodeChannelData(int channelNumber, byte[] data, int offset, int length) {
        byte[] pkt = new byte[4 + length];
        pkt[0] = (byte) ((channelNumber >> 8) & 0xFF);
        pkt[1] = (byte) (channelNumber & 0xFF);
        pkt[2] = (byte) ((length >> 8) & 0xFF);
        pkt[3] = (byte) (length & 0xFF);
        System.arraycopy(data, offset, pkt, 4, length);
        return pkt;
    }

    public static boolean isChannelData(byte[] data, int offset, int length) {
        if (length < 4) return false;
        int firstByte = data[offset] & 0xFF;
        // Channel numbers are 0x4000 through 0x7FFE (first byte 0x40 through 0x7F)
        return (firstByte >= 0x40 && firstByte <= 0x7F);
    }

    public static int getChannelNumber(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    public static int getChannelDataLength(byte[] data, int offset) {
        return ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private StunMessage sendAndReceive(byte[] requestBytes, byte[] expectedTid, int timeoutMs) throws IOException {
        DatagramPacket outPkt = new DatagramPacket(requestBytes, requestBytes.length, serverAddress);
        byte[] inBuf = new byte[2048];
        DatagramPacket inPkt = new DatagramPacket(inBuf, inBuf.length);

        int origTimeout = socket.getSoTimeout();
        socket.setSoTimeout(timeoutMs);
        try {
            long deadline = System.currentTimeMillis() + timeoutMs;
            socket.send(outPkt);

            while (System.currentTimeMillis() < deadline) {
                try {
                    socket.receive(inPkt);
                    if (!inPkt.getAddress().equals(serverAddress.getAddress()) &&
                        !inPkt.getSocketAddress().equals(serverAddress)) {
                        continue;
                    }
                    if (inPkt.getLength() < StunMessage.HEADER_LENGTH) continue;

                    // Verify magic cookie
                    int cookie = ByteBuffer.wrap(inBuf, 4, 4).getInt();
                    if (cookie != StunMessage.MAGIC_COOKIE) continue;

                    byte[] tid = new byte[12];
                    System.arraycopy(inBuf, 8, tid, 0, 12);
                    if (Arrays.equals(tid, expectedTid)) {
                        return StunMessage.decode(inBuf, 0, inPkt.getLength());
                    }
                } catch (java.net.SocketTimeoutException e) {
                    throw new IOException("Timed out waiting for TURN response", e);
                }
            }
            throw new IOException("Timed out waiting for matching Transaction ID");
        } finally {
            try {
                socket.setSoTimeout(origTimeout);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                if (key != null) {
                    // Try to send refresh with 0 lifetime to deallocate cleanly
                    refresh(0);
                }
            } catch (Exception ignored) {
            }
            if (ownsSocket && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
