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
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ICE-lite agent for peer-to-peer connectivity establishment.
 * Gathers host, srflx, and relay candidates, performs STUN connectivity checks,
 * nominates the best candidate pair, and transmits application datagrams.
 */
public final class IceAgent implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/IceAgent");
    // Limits on what the peer may send (F5): the peer is any friend, the backend relays its signals as they are
    static final int MAX_SIGNAL_CANDIDATES = 16, MAX_REMOTE_CANDIDATES = 32, MAX_PENDING_CHECKS = 512;
    static final long CHECK_PACE_MS = 20;
    private static final Pattern ICE_CHARS = Pattern.compile("[A-Za-z0-9+/]+");
    private static final Pattern IPV4 = Pattern.compile("(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");
    private static volatile long lastBadSignalLog;
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
                if (closed.get()) return;
                TurnClient tc = null;
                try {
                    // TurnClient creates its own dedicated DatagramSocket to avoid thread contention
                    tc = new TurnClient(ts.url(), ts.user(), ts.credential());
                    // Listed before allocating, so close() reaches it mid-allocation (F11); re-checked in case close() already ran
                    allocatedTurnClients.add(tc);
                    if (closed.get()) {
                        tc.close();
                        return;
                    }
                    tc.setDataHandler(this::handleIncomingTurnData);
                    InetSocketAddress relayed = tc.allocate();
                    if (closed.get()) {
                        tc.close();
                        return;
                    }
                    if (relayed != null) {
                        this.turnClient = tc;
                        localCandidates.add(new Candidate("relay", relayed.getAddress().getHostAddress(), relayed.getPort(), PRIORITY_RELAY));
                        break; // Stop at first successful allocation!
                    }
                    tc.close();
                    allocatedTurnClients.remove(tc);
                } catch (Exception ignored) {
                    if (tc != null) {
                        tc.close(); // Close any client that failed
                        allocatedTurnClients.remove(tc);
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

    /**
     * The peer's credentials and candidates (relayed by the backend from a friend: untrusted). A signal with anything out of bounds is
     * dropped whole and logged (at most every 10 s); the session goes on. Credentials can't change once set (no ICE restart).
     */
    public synchronized void onSignal(JsonObject data) {
        try {
            String ufrag = text(data, "ufrag"), pwd = text(data, "pwd");
            if (ufrag != null && (!iceString(ufrag, 4) || remoteUfrag != null && !remoteUfrag.equals(ufrag))) throw new IllegalArgumentException("ufrag");
            if (pwd != null && (!iceString(pwd, 22) || remotePwd != null && !remotePwd.equals(pwd))) throw new IllegalArgumentException("pwd");
            List<Candidate> parsed = new ArrayList<>();
            if (data.has("candidates")) {
                JsonArray arr = data.getAsJsonArray("candidates");
                if (arr.size() > MAX_SIGNAL_CANDIDATES) throw new IllegalArgumentException(arr.size() + " candidates");
                for (JsonElement el : arr) parsed.add(candidate(el.getAsJsonObject()));
            }
            if (ufrag != null) remoteUfrag = ufrag;
            if (pwd != null) remotePwd = pwd;
            for (Candidate c : parsed) {
                if (remoteCandidates.size() >= MAX_REMOTE_CANDIDATES) break;
                if (find(remoteCandidates, c.toSocketAddress()) == null) remoteCandidates.add(c);
            }
        } catch (RuntimeException bad) {
            logBadSignal(bad);
            return;
        }
        startConnectivityChecks();
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    /** RFC 8445 ice-chars, minLength..256 long. */
    private static boolean iceString(String s, int minLength) {
        return s.length() >= minLength && s.length() <= 256 && ICE_CHARS.matcher(s).matches();
    }

    /** host | srflx | relay at an IP literal (never a hostname: no DNS lookup for the peer) that can be a unicast peer, port 1-65535. */
    static Candidate candidate(JsonObject o) {
        String type = o.get("type").getAsString();
        if (!type.equals("host") && !type.equals("srflx") && !type.equals("relay")) throw new IllegalArgumentException("type " + type);
        String ip = o.get("ip").getAsString();
        if (!IPV4.matcher(ip).matches() && !(ip.indexOf(':') >= 0 && IPV6.matcher(ip).matches())) throw new IllegalArgumentException("ip " + ip);
        InetAddress addr;
        try {
            addr = InetAddress.getByName(ip); // a literal: parsed, never resolved
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("ip " + ip);
        }
        if (addr.isAnyLocalAddress() || addr.isMulticastAddress() || ip.equals("255.255.255.255")) throw new IllegalArgumentException("ip " + ip);
        int port = o.get("port").getAsInt();
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port " + port);
        long prio = o.has("prio") ? o.get("prio").getAsLong() : 0L;
        return new Candidate(type, addr.getHostAddress(), port, prio);
    }

    private static void logBadSignal(RuntimeException e) {
        long now = System.currentTimeMillis();
        if (now - lastBadSignalLog < 10_000) return;
        lastBadSignalLog = now;
        LOGGER.warn("IceAgent: ignored a malformed peer signal ({})", e.toString());
    }

    private synchronized void startConnectivityChecks() {
        if (remoteUfrag == null || remotePwd == null || remoteCandidates.isEmpty() || localCandidates.isEmpty()) return;
        // The peer drops a check that arrives before our signal does (it can't verify the USERNAME yet), so checks repeat until a pair
        // is nominated: one paced thread per agent (one check per CHECK_PACE_MS), ending at connect, close or 20 s (P2PSession's timeout).
        if (checking.compareAndSet(false, true)) {
            Thread repeat = new Thread(this::repeatChecks, "IceAgent-Checks-" + sid);
            repeat.setDaemon(true);
            repeat.setPriority(Thread.MIN_PRIORITY + 1);
            repeat.start();
        }
    }

    private void repeatChecks() {
        long end = System.currentTimeMillis() + 20_000;
        int next = 0;
        try {
            while (!connectedFuture.isDone() && !closed.get() && System.currentTimeMillis() < end) {
                synchronized (this) {
                    if (!connectedFuture.isDone()) next = sendNextCheck(next);
                }
                Thread.sleep(CHECK_PACE_MS);
            }
        } catch (InterruptedException ignored) {
        }
    }

    /** One check: the nomination while one is running (controlling), else pair number next of remote (by priority) x local. */
    private int sendNextCheck(int next) {
        CandidatePair nominated = nominating;
        if (nominated != null) {
            sendConnectivityCheck(nominated.local, nominated.remote, true);
            return next;
        }
        List<Candidate> sortedRemote = new ArrayList<>(remoteCandidates);
        sortedRemote.sort((a, b) -> Long.compare(b.prio, a.prio));
        int pairs = sortedRemote.size() * localCandidates.size();
        if (pairs == 0) return next;
        int i = next % pairs;
        sendConnectivityCheck(localCandidates.get(i % localCandidates.size()), sortedRemote.get(i / localCandidates.size()), false);
        return i + 1;
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
            if (pendingChecks.size() >= MAX_PENDING_CHECKS) pendingChecks.clear(); // unanswered checks: stale by now
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
                pendingChecks.remove(tidHex); // answered (F5: the table only holds unanswered checks)

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
