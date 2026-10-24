package com.thelads.core.client.social.p2p;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ICE-lite agent for peer-to-peer connectivity establishment.
 * Gathers host, srflx, and relay candidates, performs STUN connectivity checks,
 * nominates the best candidate pair, and transmits application datagrams.
 */
public final class IceAgent implements AutoCloseable {
    public static final long PRIORITY_HOST = 2130706431L;
    public static final long PRIORITY_SRFLX = 1694498815L;
    public static final long PRIORITY_RELAY = 16777215L;

    public static class Candidate {
        public final String type; // "host", "srflx", "relay"
        public final String ip;
        public final int port;
        public final long prio;

        public Candidate(String type, String ip, int port, long prio) {
            this.type = type;
            this.ip = ip;
            this.port = port;
            this.prio = prio;
        }

        public InetSocketAddress toSocketAddress() {
            return new InetSocketAddress(ip, port);
        }
    }

    private final String sid;
    private final boolean host; // true = controlling, false = controlled
    private final P2PSession.IceConfig config;
    private final Consumer<JsonObject> sendSignal;
    private final DatagramSocket socket;

    private final String localUfrag;
    private final String localPwd;
    private volatile String remoteUfrag;
    private volatile String remotePwd;

    private final List<Candidate> localCandidates = new CopyOnWriteArrayList<>();
    private final List<Candidate> remoteCandidates = new CopyOnWriteArrayList<>();

    private volatile TurnClient turnClient;
    private final List<TurnClient> allocatedTurnClients = new CopyOnWriteArrayList<>();
    private volatile InetSocketAddress selectedRemoteAddress;
    private volatile boolean selectedPairRelayed = false;

    private final CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);

    private volatile Consumer<byte[]> packetConsumer;
    private Thread readerThread;

    // Pending STUN check transaction IDs: tid -> PairCheck
    private final ConcurrentHashMap<String, CandidatePair> pendingChecks = new ConcurrentHashMap<>();
    // STUN server Binding requests while gathering: tid -> our mapped (server-reflexive) address
    private final ConcurrentHashMap<String, CompletableFuture<InetSocketAddress>> stunTransactions = new ConcurrentHashMap<>();

    private static class CandidatePair {
        final Candidate local;
        final Candidate remote;
        final boolean nominate; // a USE-CANDIDATE check (controlling only)

        CandidatePair(Candidate local, Candidate remote, boolean nominate) {
            this.local = local;
            this.remote = remote;
            this.nominate = nominate;
        }
    }

    /** Controlling only: the first valid pair, nominated with USE-CANDIDATE checks until the controlled agent answers one. */
    private volatile CandidatePair nominating;

    public IceAgent(String sid, boolean host, P2PSession.IceConfig config, Consumer<JsonObject> sendSignal) throws IOException {
        this(sid, host, config, sendSignal, new DatagramSocket(0, InetAddress.getByName("0.0.0.0")));
    }

    public IceAgent(String sid, boolean host, P2PSession.IceConfig config, Consumer<JsonObject> sendSignal, DatagramSocket socket) throws IOException {
        this.sid = sid;
        this.host = host;
        this.config = config == null ? new P2PSession.IceConfig(Collections.emptyList(), Collections.emptyList(), false) : config;
        this.sendSignal = sendSignal;
        this.socket = socket;

        this.localUfrag = randomString(8);
        this.localPwd = randomString(22);

        startReader();
    }

    private static String randomString(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom rand = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public String getLocalUfrag() {
        return localUfrag;
    }

    public String getLocalPwd() {
        return localPwd;
    }

    public List<Candidate> getLocalCandidates() {
        return Collections.unmodifiableList(localCandidates);
    }

    public CompletableFuture<Void> getConnectedFuture() {
        return connectedFuture;
    }

    public boolean isRelayed() {
        return selectedPairRelayed;
    }

    /** The peer address of the selected pair (null until connected). */
    public InetSocketAddress getSelectedRemoteAddress() {
        return selectedRemoteAddress;
    }

    public long getBytesSent() {
        return bytesSent.get();
    }

    public long getBytesReceived() {
        return bytesReceived.get();
    }

    public void setPacketConsumer(Consumer<byte[]> consumer) {
        this.packetConsumer = consumer;
    }

    public CompletableFuture<Void> gatherAndSendSignal() {
        return CompletableFuture.runAsync(() -> {
            try {
                gatherCandidates();
                sendCandidateSignal();
                synchronized (this) {
                    startConnectivityChecks();
                }
            } catch (Exception e) {
                connectedFuture.completeExceptionally(e);
            }
        });
    }

    private void gatherCandidates() {
        int localPort = socket.getLocalPort();

        // 1. Host candidates
        if (!config.relayOnly()) {
            try {
                Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
                while (ifaces.hasMoreElements()) {
                    NetworkInterface iface = ifaces.nextElement();
                    if (iface.isUp() && !iface.isLoopback()) {
                        Enumeration<InetAddress> addrs = iface.getInetAddresses();
                        while (addrs.hasMoreElements()) {
                            InetAddress addr = addrs.nextElement();
                            if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                                localCandidates.add(new Candidate("host", addr.getHostAddress(), localPort, PRIORITY_HOST));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            // Always include loopback
            localCandidates.add(new Candidate("host", "127.0.0.1", localPort, PRIORITY_HOST));
            gatherServerReflexive();
        }

        // 2. TURN candidates (relay)
        if (config.turn() != null && !config.turn().isEmpty()) {
            for (P2PSession.TurnServer ts : config.turn()) {
                TurnClient tc = null;
                try {
                    // TurnClient creates its own dedicated DatagramSocket to avoid thread contention
                    tc = new TurnClient(ts.url(), ts.user(), ts.credential());
                    tc.setDataHandler(this::handleIncomingTurnData);
                    InetSocketAddress relayed = tc.allocate();
                    if (relayed != null) {
                        this.turnClient = tc;
                        allocatedTurnClients.add(tc);
                        localCandidates.add(new Candidate("relay", relayed.getAddress().getHostAddress(), relayed.getPort(), PRIORITY_RELAY));
                        break; // Stop at first successful allocation!
                    } else {
                        tc.close(); // Close any client that failed
                    }
                } catch (Exception ignored) {
                    if (tc != null) {
                        tc.close(); // Close any client that failed
                    }
                }
            }
        }
    }

    /**
     * Our address as each STUN server sees this socket (RFC 5389 Binding through the reader thread): srflx candidates beside the host
     * ones. All servers at once, up to 3 tries each, 3 s in total; a server that does not answer just adds nothing.
     */
    private void gatherServerReflexive() {
        List<InetSocketAddress> servers = new ArrayList<>();
        List<byte[]> requests = new ArrayList<>();
        List<CompletableFuture<InetSocketAddress>> answers = new ArrayList<>();
        for (String url : config.stun()) {
            try {
                InetSocketAddress server = TurnClient.parseServerAddress(url);
                if (server.isUnresolved()) continue;
                StunMessage request = new StunMessage(StunMessage.BINDING_REQUEST);
                CompletableFuture<InetSocketAddress> answer = new CompletableFuture<>();
                stunTransactions.put(toHex(request.getTransactionId()), answer);
                servers.add(server);
                requests.add(request.encode(null, true));
                answers.add(answer);
            } catch (RuntimeException ignored) { // a bad url: no srflx from it
            }
        }
        long end = System.currentTimeMillis() + 3000;
        for (int attempt = 0; attempt < 3 && !answers.isEmpty(); attempt++) {
            for (int i = 0; i < servers.size(); i++) {
                if (answers.get(i).isDone()) continue;
                try {
                    socket.send(new DatagramPacket(requests.get(i), requests.get(i).length, servers.get(i)));
                } catch (IOException ignored) {
                }
            }
            long wait = Math.min(1000, end - System.currentTimeMillis());
            if (wait <= 0) break;
            try {
                CompletableFuture.allOf(answers.toArray(new CompletableFuture[0])).get(wait, TimeUnit.MILLISECONDS);
                break;
            } catch (TimeoutException retry) {
                // resend to the servers that have not answered
            } catch (Exception e) {
                break;
            }
        }
        stunTransactions.clear();
        for (CompletableFuture<InetSocketAddress> answer : answers) {
            InetSocketAddress mapped = answer.getNow(null);
            if (mapped != null && find(localCandidates, mapped) == null) {
                localCandidates.add(new Candidate("srflx", mapped.getAddress().getHostAddress(), mapped.getPort(), PRIORITY_SRFLX));
            }
        }
    }

    private static Candidate find(List<Candidate> candidates, InetSocketAddress address) {
        String ip = address.getAddress().getHostAddress();
        for (Candidate c : candidates) if (c.port == address.getPort() && c.ip.equals(ip)) return c;
        return null;
    }

    private void handleIncomingTurnData(InetSocketAddress peer, byte[] data, int off, int len) {
        if (len >= StunMessage.HEADER_LENGTH) {
            int firstByte = data[off] & 0xFF;
            if ((firstByte & 0xC0) == 0) {
                int cookie = StunMessage.getIntBE(data, off + 4);
                if (cookie == StunMessage.MAGIC_COOKIE) {
                    handleStunPacket(data, off, len, peer, true);
                    return;
                }
            }
        }
        byte[] payload = new byte[len];
        System.arraycopy(data, off, payload, 0, len);
        handleDemuxedPacket(payload, peer);
    }

    private void sendCandidateSignal() {
        JsonObject json = new JsonObject();
        json.addProperty("ufrag", localUfrag);
        json.addProperty("pwd", localPwd);

        JsonArray arr = new JsonArray();
        for (Candidate c : localCandidates) {
            JsonObject o = new JsonObject();
            o.addProperty("type", c.type);
            o.addProperty("ip", c.ip);
            o.addProperty("port", c.port);
            o.addProperty("prio", c.prio);
            arr.add(o);
        }
        json.add("candidates", arr);
        json.addProperty("end", true);

        sendSignal.accept(json);
    }

    public synchronized void onSignal(JsonObject data) {
        if (data.has("ufrag")) {
            this.remoteUfrag = data.get("ufrag").getAsString();
        }
        if (data.has("pwd")) {
            this.remotePwd = data.get("pwd").getAsString();
        }
        if (data.has("candidates")) {
            JsonArray arr = data.getAsJsonArray("candidates");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String type = o.get("type").getAsString();
                String ip = o.get("ip").getAsString();
                int port = o.get("port").getAsInt();
                long prio = o.has("prio") ? o.get("prio").getAsLong() : 0L;
                remoteCandidates.add(new Candidate(type, ip, port, prio));
            }
        }

        startConnectivityChecks();
    }

    private synchronized void startConnectivityChecks() {
        if (remoteUfrag == null || remotePwd == null || remoteCandidates.isEmpty() || localCandidates.isEmpty()) return;
        sendCheckRound();
        // The peer drops a check that arrives before our signal does (it can't verify the USERNAME yet), so checks repeat until a pair
        // is nominated: one paced thread per agent, ending at connect, close or 20 s (P2PSession's timeout).
        if (checking.compareAndSet(false, true)) {
            Thread repeat = new Thread(this::repeatChecks, "IceAgent-Checks-" + sid);
            repeat.setDaemon(true);
            repeat.setPriority(Thread.MIN_PRIORITY + 1);
            repeat.start();
        }
    }

    private void repeatChecks() {
        long end = System.currentTimeMillis() + 20_000;
        try {
            while (!connectedFuture.isDone() && !closed.get() && System.currentTimeMillis() < end) {
                Thread.sleep(200);
                synchronized (this) {
                    if (!connectedFuture.isDone()) sendCheckRound();
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void sendCheckRound() {
        CandidatePair nominated = nominating;
        if (nominated != null) { // controlling: only the nomination is repeated now
            sendConnectivityCheck(nominated.local, nominated.remote, true);
            return;
        }
        List<Candidate> sortedRemote = new ArrayList<>(remoteCandidates);
        sortedRemote.sort((a, b) -> Long.compare(b.prio, a.prio));

        for (Candidate r : sortedRemote) {
            for (Candidate l : localCandidates) {
                sendConnectivityCheck(l, r, false);
            }
        }
    }

    private void sendConnectivityCheck(Candidate local, Candidate remote, boolean nominate) {
        try {
            StunMessage req = new StunMessage(StunMessage.BINDING_REQUEST);
            req.addStringAttribute(StunMessage.ATTR_USERNAME, remoteUfrag + ":" + localUfrag);
            req.addLongAttribute(StunMessage.ATTR_PRIORITY, local.prio);
            if (host) {
                req.addLongAttribute(StunMessage.ATTR_ICE_CONTROLLING, 0x1234567890ABCDEFL);
                if (nominate) req.addAttribute(StunMessage.ATTR_USE_CANDIDATE, new byte[0]);
            } else {
                req.addLongAttribute(StunMessage.ATTR_ICE_CONTROLLED, 0x1234567890ABCDEFL);
            }

            byte[] key = StunMessage.deriveShortTermKey(remotePwd);
            byte[] encoded = req.encode(key, true);

            String tidHex = toHex(req.getTransactionId());
            pendingChecks.put(tidHex, new CandidatePair(local, remote, nominate));

            InetSocketAddress target = remote.toSocketAddress();
            if ("relay".equals(local.type) && turnClient != null) {
                // RFC 8656: create permission for peer IP before sending checks through relay
                if (!turnClient.hasPermission(target.getAddress())) {
                    turnClient.createPermission(target);
                }
                Integer ch = turnClient.getBoundChannel(target);
                if (ch != null) {
                    turnClient.sendChannelData(ch, encoded, 0, encoded.length);
                } else {
                    // Send indications before channel bind completes
                    turnClient.sendIndication(target, encoded, 0, encoded.length);
                }
            } else {
                DatagramPacket dp = new DatagramPacket(encoded, encoded.length, target);
                socket.send(dp);
            }
        } catch (Exception ignored) {
        }
    }

    private void startReader() {
        readerThread = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (!closed.get() && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    int len = packet.getLength();
                    if (len <= 0) continue;

                    InetSocketAddress sender = (InetSocketAddress) packet.getSocketAddress();

                    // Check for STUN message (first 2 bits 00 and magic cookie at offset 4)
                    if (len >= StunMessage.HEADER_LENGTH) {
                        int firstByte = buf[0] & 0xFF;
                        if ((firstByte & 0xC0) == 0) {
                            int cookie = StunMessage.getIntBE(buf, 4);
                            if (cookie == StunMessage.MAGIC_COOKIE) {
                                handleStunPacket(buf, 0, len, sender, false);
                                continue;
                            }
                        }
                    }

                    // Direct application packet
                    byte[] payload = new byte[len];
                    System.arraycopy(buf, 0, payload, 0, len);
                    handleDemuxedPacket(payload, sender);

                } catch (Exception e) {
                    if (closed.get() || socket.isClosed()) break;
                }
            }
        }, "IceAgent-Reader-" + sid);
        readerThread.setDaemon(true);
        readerThread.setPriority(Thread.MIN_PRIORITY + 1);
        readerThread.start();
    }

    private void handleStunPacket(byte[] buf, int off, int len, InetSocketAddress sender, boolean viaRelay) {
        try {
            StunMessage msg = StunMessage.decode(buf, off, len);
            if (msg.getType() == StunMessage.BINDING_RESPONSE && !viaRelay) {
                // A STUN server's answer while gathering, matched by its random transaction id (FINGERPRINT is optional there)
                CompletableFuture<InetSocketAddress> answer = stunTransactions.remove(toHex(msg.getTransactionId()));
                if (answer != null) {
                    answer.complete(msg.getXorAddressAttribute(StunMessage.ATTR_XOR_MAPPED_ADDRESS));
                    return;
                }
            }

            // Finding 1: Every incoming connectivity check request/response must pass FINGERPRINT
            if (!StunMessage.validateFingerprint(buf, off, len)) {
                return;
            }

            if (msg.getType() == StunMessage.BINDING_REQUEST) {
                // Finding 1: USERNAME = "<localUfrag>:<remoteUfrag>"
                String username = msg.getStringAttribute(StunMessage.ATTR_USERNAME);
                String expectedUsername = localUfrag + ":" + (remoteUfrag != null ? remoteUfrag : "");
                if (username == null || !username.equals(expectedUsername)) {
                    return;
                }

                // Finding 1: MESSAGE-INTEGRITY using short-term key from our local password
                byte[] integrityKey = StunMessage.deriveShortTermKey(localPwd);
                if (!StunMessage.validateMessageIntegrity(buf, off, len, integrityKey)) {
                    return;
                }
                if (!viaRelay && find(remoteCandidates, sender) == null) {
                    // Peer-reflexive: the peer's NAT mapping it could not tell us; the next check round includes it
                    remoteCandidates.add(new Candidate("prflx", sender.getAddress().getHostAddress(), sender.getPort(), PRIORITY_SRFLX));
                }

                boolean useCandidate = msg.hasAttribute(StunMessage.ATTR_USE_CANDIDATE);

                StunMessage resp = new StunMessage(StunMessage.BINDING_RESPONSE, msg.getTransactionId());
                resp.addXorAddressAttribute(StunMessage.ATTR_XOR_MAPPED_ADDRESS, sender);

                byte[] key = StunMessage.deriveShortTermKey(localPwd);
                byte[] encoded = resp.encode(key, true);

                if (viaRelay && turnClient != null) {
                    if (!turnClient.hasPermission(sender.getAddress())) {
                        turnClient.createPermission(sender);
                    }
                    Integer ch = turnClient.getBoundChannel(sender);
                    if (ch != null) {
                        turnClient.sendChannelData(ch, encoded, 0, encoded.length);
                    } else {
                        turnClient.sendIndication(sender, encoded, 0, encoded.length);
                    }
                } else {
                    DatagramPacket dp = new DatagramPacket(encoded, encoded.length, sender);
                    socket.send(dp);
                }

                // Controlled: exactly the pair the controller nominated, as it reached us (never one of our own checks)
                if (useCandidate && !host) {
                    select(sender, viaRelay);
                }
            } else if (msg.getType() == StunMessage.BINDING_RESPONSE) {
                // Finding 1: Response must match an outstanding transaction id
                String tidHex = toHex(msg.getTransactionId());
                CandidatePair pair = pendingChecks.get(tidHex);
                if (pair == null) {
                    return; // unknown transaction id -> drop silently
                }

                // Finding 1: MESSAGE-INTEGRITY using short-term key from peer pwd
                if (remotePwd == null) return;
                byte[] integrityKey = StunMessage.deriveShortTermKey(remotePwd);
                if (!StunMessage.validateMessageIntegrity(buf, off, len, integrityKey)) {
                    return;
                }

                // Valid only on a symmetric path (the answer comes from where the check went); the controlled agent waits for the
                // controller's nomination instead of picking a pair itself.
                if (!host || !sender.equals(pair.remote.toSocketAddress())) return;
                if (pair.nominate) {
                    select(sender, "relay".equals(pair.local.type) || "relay".equals(pair.remote.type));
                } else {
                    startNomination(pair);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void handleDemuxedPacket(byte[] payload, InetSocketAddress sender) {
        // Finding 1: Data packets are accepted only from the nominated pair's address
        if (selectedRemoteAddress == null || !sender.equals(selectedRemoteAddress)) {
            return;
        }
        bytesReceived.addAndGet(payload.length);
        Consumer<byte[]> c = packetConsumer;
        if (c != null) {
            c.accept(payload);
        }
    }

    /** Controlling: the first valid pair is the one nominated; the others are dropped. */
    private synchronized void startNomination(CandidatePair valid) {
        if (nominating != null || connectedFuture.isDone()) return;
        nominating = valid;
        sendConnectivityCheck(valid.local, valid.remote, true);
    }

    private synchronized void select(InetSocketAddress remoteAddr, boolean isRelayed) {
        if (!connectedFuture.isDone()) {
            this.selectedRemoteAddress = remoteAddr;
            this.selectedPairRelayed = isRelayed;

            if (isRelayed && turnClient != null) {
                try {
                    if (!turnClient.hasPermission(remoteAddr.getAddress())) {
                        turnClient.createPermission(remoteAddr);
                    }
                    // RFC 8656: ChannelBind only for the nominated pair
                    turnClient.channelBind(remoteAddr);
                } catch (Exception ignored) {
                }
            }
            connectedFuture.complete(null);
        }
    }

    public void send(byte[] data, int offset, int length) throws IOException {
        if (closed.get()) throw new IOException("IceAgent is closed");
        if (selectedRemoteAddress == null) throw new IOException("IceAgent not connected yet");

        if (selectedPairRelayed && turnClient != null) {
            Integer ch = turnClient.getBoundChannel(selectedRemoteAddress);
            if (ch != null) {
                turnClient.sendChannelData(ch, data, offset, length);
            } else {
                turnClient.sendIndication(selectedRemoteAddress, data, offset, length);
            }
        } else {
            DatagramPacket dp = new DatagramPacket(data, offset, length, selectedRemoteAddress);
            socket.send(dp);
        }
        bytesSent.addAndGet(length);
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
            for (TurnClient tc : allocatedTurnClients) {
                try {
                    tc.close();
                } catch (Exception ignored) {
                }
            }
            if (turnClient != null && !allocatedTurnClients.contains(turnClient)) {
                try {
                    turnClient.close();
                } catch (Exception ignored) {
                }
            }
            if (!socket.isClosed()) {
                socket.close();
            }
            if (readerThread != null) {
                readerThread.interrupt();
            }
            connectedFuture.completeExceptionally(new IOException("IceAgent closed"));
        }
    }
}
