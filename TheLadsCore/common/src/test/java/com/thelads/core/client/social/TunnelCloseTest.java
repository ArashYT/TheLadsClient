package com.thelads.core.client.social;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.social.p2p.P2PSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * QA (1 of 7 kicks): the guest saw "Connection Lost" instead of "Kicked by host". The host's last bytes, then its close at once,
 * must reach the game in full before a clean EOF, while the game is still talking (unread bytes at the proxy used to reset it).
 */
class TunnelCloseTest {

    @AfterEach
    void reset() {
        GuestSession.setP2PStarter(null);
        HypixelSafetyManager.setP2PSession(null, null);
    }

    @Test
    void hostsLastBytesReachTheGameBeforeEof() throws Exception {
        for (int i = 0; i < 50; i++) runOnce("tunnel-close-" + i + "-" + System.nanoTime(), i);
    }

    private static void runOnce(String sid, int round) throws Exception {
        P2PSession.IceConfig cfg = new P2PSession.IceConfig(List.of(), List.of(), false);
        CompletableFuture<Integer> proxyPort = new CompletableFuture<>();
        SocialBridge bridge = new SocialBridge() {
            @Override public void connectToLocal(int port, String name) { proxyPort.complete(port); }
        };
        SocialState guestState = new SocialState(() -> bridge, () -> null, null);
        guestState.setClientSender(m -> {});
        GuestSession guest = guestState.guestSession();

        CompletableFuture<P2PSession> hostStart = P2PSession.start(sid, true, cfg, guest::onSignal);
        P2PSession hostSignals = (P2PSession) hostStart; // the future is the session (signals reach it while it connects)
        guest.start(sid, UUID.randomUUID(), "Host", "world", cfg, hostSignals::onSignal);
        P2PSession host = hostStart.get(10, TimeUnit.SECONDS);
        int port = proxyPort.get(10, TimeUnit.SECONDS);

        byte[] last = new byte[20_000 + round * 97];
        for (int i = 0; i < last.length; i++) last[i] = (byte) (i * 31 + round);

        try (Socket game = new Socket("127.0.0.1", port)) {
            game.setSoTimeout(10_000);
            Thread keepAlive = new Thread(() -> { // the game keeps sending while the host ends: bytes the proxy has not read yet
                try {
                    OutputStream out = game.getOutputStream();
                    byte[] packet = new byte[64];
                    while (true) {
                        out.write(packet);
                        Thread.sleep(1);
                    }
                } catch (Exception ignored) {
                }
            }, "TunnelCloseTest-KeepAlive");
            keepAlive.setDaemon(true);
            keepAlive.start();
            Thread.sleep(20);

            // The host bridge when the integrated server kicks and closes its socket: the last bytes, then the session ends at once
            host.out(P2PSession.MINECRAFT).write(last);
            host.out(P2PSession.MINECRAFT).flush();
            host.close("Loopback socket closed");
            Thread.sleep(100); // the game reads a moment later: what it was sent waits in its socket buffer (a reset drops it there)

            ByteArrayOutputStream got = new ByteArrayOutputStream();
            InputStream in = game.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) got.write(buf, 0, n); // a reset throws here instead of reaching EOF
            assertArrayEquals(last, got.toByteArray(), "round " + round + ": every byte, then EOF");
        }
    }
}
