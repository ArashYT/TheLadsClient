package com.thelads.core.client.social.p2p;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TURN client (RFC 8656) supporting Allocate, Refresh, CreatePermission, ChannelBind, ChannelData,
 * and Send/Data indications.
 * Authenticates using the long-term credential mechanism (RFC 5389).
 */
public final class TurnClient implements AutoCloseable {
    public interface DataHandler {
        void onData(InetSocketAddress peer, byte[] data, int off, int len);
    }

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

    // Bound channels: channelNumber -> peer address, and peer address -> channelNumber
    private final ConcurrentHashMap<Integer, InetSocketAddress> boundChannels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, Integer> peerToChannel = new ConcurrentHashMap<>();
    private final java.util.Set<InetAddress> permittedIps = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<InetSocketAddress, Long> permissionExpiresAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> channelExpiresAt = new ConcurrentHashMap<>();
    // a random first channel: a spoofed ChannelData can't just assume 0x4000
    private final AtomicInteger nextChannelNumber = new AtomicInteger(0x4000 + new java.security.SecureRandom().nextInt(0x3000));
    private volatile long allocationExpiresAt = 0;

    // Asynchronous request dispatching
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();
    private volatile DataHandler dataHandler;
    private Thread readerThread;
    private Thread refresherThread;

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
        socket.setReceiveBufferSize(IceAgent.RECEIVE_BUFFER); // relayed sessions arrive here
        this.serverAddress = parseServerAddress(url);

        startReader();
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

    public void setDataHandler(DataHandler handler) {
        this.dataHandler = handler;
    }

    private void startReader() {
        readerThread = new Thread(this::runReader, "TurnClient-Reader");
        readerThread.setDaemon(true);
        readerThread.setPriority(Thread.MIN_PRIORITY + 1);
        readerThread.start();
    }

    private void runReader() {
        byte[] inBuf = new byte[2048];
        while (!closed.get() && !socket.isClosed()) {
            try {
                DatagramPacket inPkt = new DatagramPacket(inBuf, inBuf.length);
                socket.receive(inPkt);
                int len = inPkt.getLength();
                // F6: only the TURN server talks on this socket (responses, ChannelData, Data indications); anyone else is dropped
                if (len < 4 || !serverAddress.equals(inPkt.getSocketAddress())) continue;

                // 1. ChannelData (0x4000 .. 0x7FFE)
                if (isChannelData(inBuf, 0, len)) {
                    int chNum = getChannelNumber(inBuf, 0);
                    int dataLen = getChannelDataLength(inBuf, 0);
                    if (dataLen <= len - 4) {
                        InetSocketAddress peer = boundChannels.get(chNum);
                        DataHandler handler = this.dataHandler;
                        if (peer != null && handler != null) {
                            byte[] copy = new byte[dataLen];
                            System.arraycopy(inBuf, 4, copy, 0, dataLen);
                            handler.onData(peer, copy, 0, dataLen);
                        }
                    }
                    continue;
                }

                // 2. STUN message
                if (len >= StunMessage.HEADER_LENGTH) {
                    int cookie = StunMessage.getIntBE(inBuf, 4);
                    if (cookie == StunMessage.MAGIC_COOKIE) {
                        StunMessage msg = StunMessage.decode(inBuf, 0, len);
                        if (msg.getType() == StunMessage.DATA_INDICATION) {
                            InetSocketAddress peer = msg.getXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS);
                            byte[] data = msg.getDataAttribute();
                            DataHandler handler = this.dataHandler;
                            if (peer != null && data != null && handler != null) {
                                handler.onData(peer, data, 0, data.length);
                            }
                            continue;
                        }

                        // Response to pending request (its integrity is checked by the requester, which knows whether it signed)
                        String tidHex = toHex(msg.getTransactionId());
                        CompletableFuture<byte[]> f = pendingRequests.remove(tidHex);
                        if (f != null) {
                            f.complete(java.util.Arrays.copyOf(inBuf, len));
                            continue;
                        }
                    }
                }
            } catch (Exception e) {
                if (closed.get() || socket.isClosed()) break;
            }
        }
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

        StunMessage challengeResp = sendAndReceive(initBytes, initReq.getTransactionId(), 3000, null);
        if (challengeResp.getType() != StunMessage.ALLOCATE_ERROR) {
            // Unexpected success or wrong message
            if (challengeResp.getType() == StunMessage.ALLOCATE_RESPONSE) {
                this.relayedAddress = challengeResp.getXorAddressAttribute(StunMessage.ATTR_XOR_RELAYED_ADDRESS);
                Integer lt = challengeResp.getIntAttribute(StunMessage.ATTR_LIFETIME);
                this.lifetimeSeconds = lt == null ? 600 : lt;
                this.allocationExpiresAt = System.currentTimeMillis() + (long) this.lifetimeSeconds * 1000L;
                ensureRefresherStarted();
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
        StunMessage authResp = signedRequest(StunMessage.ALLOCATE_REQUEST,
            r -> r.addIntAttribute(StunMessage.ATTR_REQUESTED_TRANSPORT, 0x11000000)); // UDP (17)
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
        this.allocationExpiresAt = System.currentTimeMillis() + (long) this.lifetimeSeconds * 1000L;
        ensureRefresherStarted();
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

        StunMessage resp = signedRequest(StunMessage.REFRESH_REQUEST, r -> r.addIntAttribute(StunMessage.ATTR_LIFETIME, lifetimeSec));
        if (resp.getType() == StunMessage.REFRESH_ERROR) {
            throw new IOException("Refresh failed: " + resp.getErrorCode() + " " + resp.getErrorMessage());
        }
        Integer lt = resp.getIntAttribute(StunMessage.ATTR_LIFETIME);
        this.lifetimeSeconds = lt == null ? 0 : lt;
        if (this.lifetimeSeconds > 0) {
            this.allocationExpiresAt = System.currentTimeMillis() + (long) this.lifetimeSeconds * 1000L;
        } else {
            this.allocationExpiresAt = 0;
        }
        return this.lifetimeSeconds;
    }

    /**
     * Creates a permission for the peer IP to send to our relayed address (RFC 8656 section 9).
     * Permissions have a lifetime of 5 minutes (300 seconds).
     */
    public synchronized void createPermission(InetSocketAddress peerAddress) throws IOException {
        if (closed.get()) throw new IOException("TurnClient is closed");
        if (key == null) throw new IOException("Cannot create permission before allocate");

        StunMessage resp = signedRequest(StunMessage.CREATE_PERMISSION_REQUEST,
            r -> r.addXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS, peerAddress));
        if (resp.getType() == StunMessage.CREATE_PERMISSION_ERROR) {
            throw new IOException("CreatePermission failed: " + resp.getErrorCode() + " " + resp.getErrorMessage());
        }
        permittedIps.add(peerAddress.getAddress());
        permissionExpiresAt.put(peerAddress, System.currentTimeMillis() + 300_000L); // 5 minutes
    }

    public boolean hasPermission(InetAddress peerIp) {
        return permittedIps.contains(peerIp);
    }

    /**
     * Binds a channel number (0x4000 - 0x7FFE) to a peer address (RFC 8656 section 11).
     * Channel bindings have a lifetime of 10 minutes (600 seconds).
     */
    public synchronized void channelBind(int channelNumber, InetSocketAddress peerAddress) throws IOException {
        if (closed.get()) throw new IOException("TurnClient is closed");
        if (channelNumber < 0x4000 || channelNumber > 0x7FFE) {
            throw new IllegalArgumentException("Invalid channel number: 0x" + Integer.toHexString(channelNumber));
        }

        // Mapped before asking: the server's first ChannelData can come right behind its answer, before this thread runs again
        InetSocketAddress previous = boundChannels.put(channelNumber, peerAddress);
        StunMessage resp;
        try {
            resp = signedRequest(StunMessage.CHANNEL_BIND_REQUEST, r -> {
                r.addIntAttribute(StunMessage.ATTR_CHANNEL_NUMBER, channelNumber << 16);
                r.addXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS, peerAddress);
            });
        } catch (IOException e) {
            if (previous == null) boundChannels.remove(channelNumber, peerAddress);
            throw e;
        }
        if (resp.getType() == StunMessage.CHANNEL_BIND_ERROR) {
            if (previous == null) boundChannels.remove(channelNumber, peerAddress);
            throw new IOException("ChannelBind failed: " + resp.getErrorCode() + " " + resp.getErrorMessage());
        }
        peerToChannel.put(peerAddress, channelNumber);
        channelExpiresAt.put(channelNumber, System.currentTimeMillis() + 600_000L); // 10 minutes
    }

    /**
     * Binds the next available channel (0x4000+) to a peer address.
     */
    public synchronized int channelBind(InetSocketAddress peerAddress) throws IOException {
        Integer existing = peerToChannel.get(peerAddress);
        if (existing != null) {
            channelBind(existing, peerAddress);
            return existing;
        }
        int ch = nextChannelNumber.getAndIncrement();
        if (ch > 0x7FFE) {
            throw new IOException("Exhausted TURN channel numbers (0x4000-0x7FFE)");
        }
        channelBind(ch, peerAddress);
        return ch;
    }

    public Integer getBoundChannel(InetSocketAddress peerAddress) {
        return peerToChannel.get(peerAddress);
    }

    public InetSocketAddress getPeerForChannel(int channelNumber) {
        return boundChannels.get(channelNumber);
    }

    public boolean isChannelBound(int channelNumber) {
        return boundChannels.containsKey(channelNumber);
    }

    /**
     * Sends data encapsulated in a Send indication (RFC 8656 section 11.1).
     * Used before channel binding completes (e.g. for initial connectivity checks).
     */
    public void sendIndication(InetSocketAddress peerAddress, byte[] data, int offset, int length) throws IOException {
        StunMessage ind = new StunMessage(StunMessage.SEND_INDICATION);
        ind.addXorAddressAttribute(StunMessage.ATTR_XOR_PEER_ADDRESS, peerAddress);
        byte[] payload = new byte[length];
        System.arraycopy(data, offset, payload, 0, length);
        ind.addDataAttribute(payload);
        byte[] encoded = ind.encode(null, true);
        DatagramPacket dp = new DatagramPacket(encoded, encoded.length, serverAddress);
        socket.send(dp);
    }

    private synchronized void ensureRefresherStarted() {
        if (refresherThread == null && !closed.get()) {
            refresherThread = new Thread(this::runRefresher, "TurnClient-Refresher");
            refresherThread.setDaemon(true);
            refresherThread.setPriority(Thread.MIN_PRIORITY + 1);
            refresherThread.start();
        }
    }

    private void runRefresher() {
        while (!closed.get()) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
            if (closed.get()) break;

            long now = System.currentTimeMillis();

            // Refresh allocation before 10 min expiry (60s before)
            if (allocationExpiresAt > 0 && now >= allocationExpiresAt - 60_000) {
                try {
                    refresh(600);
                } catch (Exception ignored) {
                }
            }

            // Refresh permissions before 5 min expiry (30s before)
            for (java.util.Map.Entry<InetSocketAddress, Long> entry : permissionExpiresAt.entrySet()) {
                if (now >= entry.getValue() - 30_000) {
                    try {
                        createPermission(entry.getKey());
                    } catch (Exception ignored) {
                    }
                }
            }

            // Refresh channel bindings before 10 min expiry (60s before)
            for (java.util.Map.Entry<Integer, Long> entry : channelExpiresAt.entrySet()) {
                if (now >= entry.getValue() - 60_000) {
                    InetSocketAddress peer = boundChannels.get(entry.getKey());
                    if (peer != null) {
                        try {
                            channelBind(entry.getKey(), peer);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
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

    /**
     * A request signed with the long-term credentials (USERNAME, REALM, NONCE, MESSAGE-INTEGRITY). A 438 Stale Nonce answer brings the
     * server's new NONCE (and REALM): taken, and the request is sent once more (RFC 8656 4.3), so a long relay session survives nonce
     * rotation.
     */
    private StunMessage signedRequest(int type, java.util.function.Consumer<StunMessage> attributes) throws IOException {
        for (int attempt = 0; ; attempt++) {
            StunMessage req = new StunMessage(type);
            attributes.accept(req);
            req.addStringAttribute(StunMessage.ATTR_USERNAME, username);
            req.addStringAttribute(StunMessage.ATTR_REALM, realm);
            req.addStringAttribute(StunMessage.ATTR_NONCE, nonce);
            StunMessage resp = sendAndReceive(req.encode(key, true), req.getTransactionId(), 3000, key);
            String newNonce = resp.getStringAttribute(StunMessage.ATTR_NONCE);
            if (attempt > 0 || (resp.getType() & 0x0110) != 0x0110 || resp.getErrorCode() != 438 || newNonce == null) return resp;
            String newRealm = resp.getStringAttribute(StunMessage.ATTR_REALM);
            if (newRealm != null && !newRealm.equals(realm)) {
                realm = newRealm;
                key = StunMessage.deriveLongTermKey(username, realm, password);
            }
            nonce = newNonce;
        }
    }

    /** The server's answer; for a request signed with key (long-term), a success answer must carry a valid MESSAGE-INTEGRITY (F6). */
    private StunMessage sendAndReceive(byte[] requestBytes, byte[] expectedTid, int timeoutMs, byte[] key) throws IOException {
        // Only the reader thread completes answers: waiting on it would block every relayed packet, then time out
        if (Thread.currentThread() == readerThread) throw new IOException("TURN request on the TURN reader thread");
        String tidHex = toHex(expectedTid);
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pendingRequests.put(tidHex, future);
        try {
            DatagramPacket outPkt = new DatagramPacket(requestBytes, requestBytes.length, serverAddress);
            socket.send(outPkt);
            byte[] raw = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            StunMessage resp = StunMessage.decode(raw, 0, raw.length);
            boolean success = (resp.getType() & 0x0110) == 0x0100;
            if (key != null && success && !StunMessage.validateMessageIntegrity(raw, 0, raw.length, key)) {
                throw new IOException("TURN response without a valid MESSAGE-INTEGRITY");
            }
            return resp;
        } catch (IOException e) {
            throw e;
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IOException("Timed out waiting for TURN response", e);
        } catch (Exception e) {
            throw new IOException("TURN request failed: " + e.getMessage(), e);
        } finally {
            pendingRequests.remove(tidHex);
        }
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xFF));
        }
        return sb.toString();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (refresherThread != null) {
                refresherThread.interrupt();
            }
            if (readerThread != null) {
                readerThread.interrupt();
            }
            for (CompletableFuture<byte[]> f : pendingRequests.values()) {
                f.cancel(true);
            }
            pendingRequests.clear();
            try {
                if (key != null) {
                    // Try to send refresh with 0 lifetime to deallocate cleanly
                    StunMessage req = new StunMessage(StunMessage.REFRESH_REQUEST);
                    req.addIntAttribute(StunMessage.ATTR_LIFETIME, 0);
                    req.addStringAttribute(StunMessage.ATTR_USERNAME, username);
                    req.addStringAttribute(StunMessage.ATTR_REALM, realm);
                    req.addStringAttribute(StunMessage.ATTR_NONCE, nonce);
                    byte[] bytes = req.encode(key, true);
                    DatagramPacket outPkt = new DatagramPacket(bytes, bytes.length, serverAddress);
                    socket.send(outPkt);
                }
            } catch (Exception ignored) {
            }
            if (ownsSocket && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
