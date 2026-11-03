package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsNetworkHubDialog;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.ladsapi.LadsApiClient;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LadsNetworkStatusTest {

    @AfterEach void tearDown() {
        LadsNetwork.clearPendingCommand();
        LadsNetwork.installConnector(null);
        LadsNetwork.installCommandLookup(null);
        LadsNetworkStatus.setCachedForTest(LadsNetworkStatus.Snapshot.IDLE);
        LadsApiClient.reset();
        LadsApiClient.install(null, "");
        LadsGameBridge.set(new DefaultGameBridge());
    }

    @Test void slpPingParsesPlayersMotdAndLatency() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread server = new Thread(() -> {
                try (Socket s = listener.accept()) {
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());

                    // Read handshake + status request
                    readFrame(in);
                    readFrame(in);

                    String statusJson = "{\"version\":{\"name\":\"Velocity 1.21\",\"protocol\":767},"
                        + "\"players\":{\"max\":250,\"online\":47},"
                        + "\"description\":{\"text\":\"§c§lThe Lads Network\\n§7Season 4 Live!\"}}";
                    byte[] jsonBytes = statusJson.getBytes(StandardCharsets.UTF_8);
                    ByteArrayOutputStream body = new ByteArrayOutputStream();
                    writeVarInt(body, 0x00);
                    writeVarInt(body, jsonBytes.length);
                    body.write(jsonBytes);
                    writeFrame(out, body.toByteArray());

                    // Echo ping -> pong
                    byte[] ping = readFrame(in);
                    Thread.sleep(15);
                    writeFrame(out, ping);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            server.start();

            LadsNetworkStatus.Snapshot snap = LadsNetworkStatus.pingHost("127.0.0.1", listener.getLocalPort(), 3000);
            server.join(5000);

            assertTrue(snap.online());
            assertEquals(47, snap.onlinePlayers());
            assertEquals(250, snap.maxPlayers());
            assertTrue(snap.pingMs() >= 1 && snap.pingMs() < 2000);
            assertEquals("The Lads Network Season 4 Live!", snap.motd());

            LadsNetworkStatus.setCachedForTest(snap);
            assertTrue(LadsNetworkStatus.online());
            assertEquals(47, LadsNetworkStatus.players());
            String line = LadsNetworkStatus.titleStatusLine();
            assertTrue(line.contains("47/250 ONLINE") && line.contains("MC.ARASHYT.CA"), line);
        }
    }

    @Test void subServerRoutingAndHubDialogDispatch() {
        List<String> events = new ArrayList<>();
        List<String> connected = new ArrayList<>();
        List<String> sentChat = new ArrayList<>();
        boolean[] ingame = {false};
        boolean[] hasPlayer = {false};
        String[] serverAddr = {"Singleplayer"};

        LadsGameBridge bridge = new DefaultGameBridge() {
            @Override public boolean isIngame() { return ingame[0]; }
            @Override public boolean hasPlayer() { return hasPlayer[0]; }
            @Override public String getServerAddress() { return serverAddr[0]; }
            @SuppressWarnings("unused")
            public void connectToServer(String name, String address) {
                events.add("connect:" + name + "@" + address);
                connected.add(name + "@" + address);
            }
            @Override public void sendChatLine(String text) {
                sentChat.add(text);
            }
        };
        LadsGameBridge.set(bridge);

        // 1. From main menu: clicking Parkour closes settings BEFORE connecting to mc.arashyt.ca and queues /server parkour
        LadsNetworkHubDialog dialog = new LadsNetworkHubDialog();
        dialog.setOnAfterJoin(() -> events.add("afterJoin"));
        dialog.open();
        assertTrue(dialog.isOpen());
        LadsGraphics stubGraphics = stubGraphics(800, 500, new ArrayList<>());
        dialog.render(stubGraphics, 400, 250);
        var parkourRect = dialog.subServerBounds("parkour");
        assertNotNull(parkourRect);
        assertTrue(dialog.click(parkourRect.x() + 5, parkourRect.y() + 5, 0));
        assertEquals(List.of("afterJoin", "connect:The Lads Network@mc.arashyt.ca"), events,
            "onAfterJoin must run before connectToServer so closing LadsSettingsScreen never clobbers ConnectScreen");
        assertEquals(List.of("The Lads Network@mc.arashyt.ca"), connected);
        assertEquals("/server parkour", LadsNetwork.pendingCommand());
        assertTrue(sentChat.isEmpty(), "Not logged in yet");

        // Simulate completing login onto mc.arashyt.ca and ticking 30 settle ticks
        ingame[0] = true;
        hasPlayer[0] = true;
        serverAddr[0] = "mc.arashyt.ca";
        for (int i = 0; i < 35; i++) {
            LadsNetwork.tick(bridge);
        }
        assertEquals(List.of("/server parkour"), sentChat);
        assertNull(LadsNetwork.pendingCommand());

        // 2. While already connected to mc.arashyt.ca: switching to PvP Duels sends /server pvpduels immediately
        LadsNetwork.joinSubServer(bridge, LadsNetwork.findSubServer("pvpduels"));
        assertEquals(1, connected.size(), "No reconnect needed when already on mc.arashyt.ca");
        assertEquals(List.of("/server parkour", "/server pvpduels"), sentChat);

        // 3. Dynamic hello game & hub command show in Hub Dialog and dispatch when Return to Hub is clicked
        LadsApiClient.install(bytes -> {}, "26.3");
        LadsApiClient.receive("{\"t\":\"hello\",\"v\":1,\"server\":\"The Lads Network\",\"game\":\"PvP Duels\",\"hub\":\"/hub\"}"
            .getBytes(StandardCharsets.UTF_8));
        List<String> texts = new ArrayList<>();
        dialog.open();
        dialog.render(stubGraphics(800, 500, texts), 400, 250);
        assertTrue(texts.stream().anyMatch(t -> t.contains("PvP Duels")), "Active 1.13.0 game shown in dialog: " + texts);
        assertTrue(texts.stream().anyMatch(t -> t.contains("/hub")), "Dynamic hub command shown in dialog: " + texts);
        var mainRect = dialog.joinMainBounds();
        assertTrue(dialog.click(mainRect.x() + 5, mainRect.y() + 5, 0));
        assertEquals(List.of("/server parkour", "/server pvpduels", "/hub"), sentChat);
    }

    @Test void settingsCatalogKeepsImportAndNetworkButtonsApartAndOpensDialog() {
        LadsSettingsScreen.requestOpenNetworkHub();
        LadsSettingsScreen preOpened = new LadsSettingsScreen();
        assertTrue(preOpened.networkHub().isOpen(), "requestOpenNetworkHub opens the hub dialog on the next settings screen");
        preOpened.networkHub().close();

        LadsSettingsScreen screen = new LadsSettingsScreen();
        screen.render(stubGraphics(800, 500, new ArrayList<>()), 0, 0);
        var importBtn = screen.controlBounds("client-import");
        var networkBtn = screen.controlBounds("lads-network");
        assertNotNull(importBtn, "Import from client button must remain present");
        assertNotNull(networkBtn, "Lads Network button must be present alongside Import from client");
        assertTrue(importBtn.x() + importBtn.width() <= networkBtn.x(), "Import and Lads Network buttons must never overlap");
        assertTrue(screen.mouseClicked(networkBtn.x() + 4, networkBtn.y() + 4, 0));
        assertTrue(screen.networkHub().isOpen());
    }

    private static LadsGraphics stubGraphics(int w, int h, List<String> drawnText) {
        return new LadsGraphics() {
            @Override public void fill(int x1, int y1, int x2, int y2, int color) {}
            @Override public void drawText(String text, int x, int y, int color, boolean shadow) { if (text != null) drawnText.add(text); }
            @Override public void drawCenteredText(String text, int cx, int y, int color, boolean shadow) { if (text != null) drawnText.add(text); }
            @Override public int textWidth(String text) { return text != null ? text.length() * 6 : 0; }
            @Override public int fontHeight() { return 9; }
            @Override public int getScaledWidth() { return w; }
            @Override public int getScaledHeight() { return h; }
            @Override public void pushPose() {}
            @Override public void popPose() {}
            @Override public void translate(float x, float y) {}
            @Override public void scale(float sx, float sy) {}
            @Override public void enableScissor(int x1, int y1, int x2, int y2) {}
            @Override public void disableScissor() {}
            @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) {}
            @Override public void drawHead(String username, String uuid, int x, int y, int size) {}
        };
    }

    private static byte[] readFrame(DataInputStream in) throws Exception {
        int len = 0;
        for (int shift = 0; ; shift += 7) {
            int b = in.read();
            len |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
        }
        byte[] buf = new byte[len];
        in.readFully(buf);
        return buf;
    }

    private static void writeFrame(DataOutputStream out, byte[] body) throws Exception {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        writeVarInt(frame, body.length);
        frame.write(body);
        out.write(frame.toByteArray());
        out.flush();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int v) {
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }
}
