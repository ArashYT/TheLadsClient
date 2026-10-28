package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.TextOption;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Hypixel chat lines as Hypixel sends them (formatting codes included where it uses them), the Mod API ping and the modules. */
class HypixelModsTest {
    private static final long S = 1_000_000_000L;
    private final List<String> sent = new ArrayList<>();
    private final List<byte[]> payloads = new ArrayList<>();
    private int[] listed = {1, 1, 1};
    private InetSocketAddress address = new InetSocketAddress("203.0.113.5", 25565);
    private String server = "mc.hypixel.net";
    private final Object connection = new Object(), world = new Object();

    private final LadsGameBridge game = new DefaultGameBridge() {
        @Override public String getServerAddress() { return server; }
        @Override public int[] getListedLatencies() { return listed; }
        @Override public InetSocketAddress getServerSocketAddress() { return address; }
        @Override public void sendChatLine(String text) { sent.add(text); }
        @Override public boolean sendPluginMessage(String channel, byte[] data) { assertEquals(RealPing.CHANNEL, channel); payloads.add(data); return true; }
    };

    @BeforeEach void setUp() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        m.getOptions().forEach(Option::reset);
        m.setEnabled(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_FRIEND)).set(true);
        LadsGameBridge.set(game);
        HypixelMods.resetForTest();
    }

    @AfterEach void tearDown() {
        HypixelMods.tick(game, null, null, 0);
        LadsGameBridge.set(new DefaultGameBridge());
        HypixelSafetyManager.qaOverride = false;
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        m.getOptions().forEach(Option::reset);
        m.setEnabled(true);
    }

    @Test void defaults() {
        Module m = HypixelMods.module();
        assertEquals(HypixelMods.HYPIXEL_MODS, m.getName());
        assertTrue(m.getDescription().startsWith("Hypixel only.") && m.getDescription().contains("anyone could then add you"));
        assertTrue(m.isEnabled());
        java.util.Map<String, Boolean> on = new java.util.HashMap<>();
        for (String name : new String[] {HypixelMods.AUTO_GG, HypixelMods.AUTO_FRIEND, HypixelMods.AUTO_TIP, HypixelMods.PLAY_AGAIN, HypixelMods.AUTO_WHO, HypixelMods.CLEANUP}) {
            on.put(name, ((BoolOption) m.getOption(name)).get());
        }
        assertEquals(java.util.Map.of(HypixelMods.AUTO_GG, true, HypixelMods.AUTO_TIP, true, HypixelMods.CLEANUP, true,
            HypixelMods.AUTO_FRIEND, false, HypixelMods.PLAY_AGAIN, false, HypixelMods.AUTO_WHO, false), on, "Auto Friend: anyone could add you, opt in");
        DropdownOption replayWhen = (DropdownOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN);
        assertNotNull(replayWhen);
        assertEquals("After death", replayWhen.getValue());
        BoolOption waitParty = (BoolOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY);
        assertNotNull(waitParty);
        assertTrue(waitParty.get());
    }

    @Test void locrawAnswerAfterAnotherCommandAndPingMissesReset() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_FRIEND)).set(true);
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        HypixelMods.chat("Friend request from Steve", 2 * S);
        HypixelMods.tick(game, world, connection, 4 * S);
        assertEquals(List.of("/locraw", "/friend accept Steve"), sent);
        assertTrue(HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"SKYWARS\",\"mode\":\"solo_insane\"}", 5 * S),
            "still our /locraw's answer though /friend went out after it");
        assertArrayEquals(new String[] {"SKYWARS", "solo_insane"}, HypixelMods.location());
        // Mod API: two misses, then an answer, then two more misses: still on the Mod API (three in a row would end it).
        payloads.clear();
        Object c = new Object();
        long t = 0;
        RealPing.tick(game, c, t);
        for (int i = 0; i < 3; i++) { t += 6 * S; RealPing.tick(game, c, t); }
        RealPing.receive(RealPing.CHANNEL, new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'}, t + 1);
        for (int i = 0; i < 2; i++) { t += 6 * S; RealPing.tick(game, c, t); }
        int before = payloads.size();
        t += 6 * S;
        RealPing.tick(game, c, t);
        assertEquals(before + 1, payloads.size(), "an answer resets the miss count: " + payloads.size());
    }

    @Test void gameEndInEveryModeButNotFromPlayers() {
        String[] ends = {
            "                       1st Killer - §r§b[MVP§r§c+§r§b] Steve§r§7 - 7",              // Bed Wars, SkyWars
            "                                Winner - §r§a[VIP] Steve",                         // SkyWars
            "                              Winner: §r§aPLAYERS",                               // Murder Mystery
            "                      Murderer: Alex (3 Kills)",
            "                       1st Place - [VIP] Steve",                                  // TNT Run, Arcade
            "                    1st - Steve (Steve's build)",                                 // Build Battle
            "             §6§lWINNER! §r§b[MVP+] Steve §r§7[VIP] Alex",                        // Duels
            "                      Winning Team - Red",
            "                         Red won the game!",
            "                              GAME OVER!",
            "                   You survived 15 rounds!",                                       // Zombies
            "                Alpha Infected: Steve (5 infections)",
            "  #1 Steve (01:23:45)",
            "                          Classic Duel - 2:13",                                    // Duels
            "                     Bridge CTF Duel - 12:34",
            "                  They captured all wools!",
            "             [MVP+] Steve - 12 Final Kills",                                       // Mega Walls
            "                 Zombies - 1:23:45 (Round 12)",
            "§6This game has been recorded. §r§e§lClick here to watch the Replay!",
            "▬▬▬▬▬▬▬▬▬▬\n                                 Bed Wars\n\n                 1st Killer - Steve - 3\n▬▬▬▬▬▬▬▬▬▬",
        };
        for (String line : ends) assertTrue(HypixelChat.gameEnd(line), line);
        String[] not = {
            "[MVP+] Troll: 1st Killer - me - 99", "Party > [VIP] Troll: GAME OVER!", "§7Troll§7: Winner: me", "From [MVP+] Troll: Steve won the game!",
            "[VIP] Troll: You survived 3 rounds!", "Bed Wars", "                                 Bed Wars", "The game starts in 1 second!",
        };
        for (String line : not) assertFalse(HypixelChat.gameEnd(line), line);
        assertTrue(HypixelChat.gameStart("§eThe game starts in §r§c1§r§e second!"));
        assertTrue(HypixelChat.gameStart("Cages open in: 1 second!"));
        assertFalse(HypixelChat.gameStart("The game starts in 2 seconds!"));
        assertFalse(HypixelChat.gameStart("[VIP] Troll: The game starts in 1 second!"));
    }

    @Test void friendRequestsUnknownCommandsAndLocraw() {
        String request = "§9§m-----------------------------------------------------§r§9\n§eFriend request from §r§b[MVP§r§c+§r§b] Steve_2§r§e\n"
            + "§r§a§l[ACCEPT] §r§8- §r§c§l[DENY] §r§8- §r§7§l[IGNORE]§r\n§9§m-----------------------------------------------------§r";
        assertEquals("Steve_2", HypixelChat.friendRequest(request));
        assertEquals("Alex", HypixelChat.friendRequest("Friend request from Alex"));
        assertNull(HypixelChat.friendRequest("[VIP] Troll: Friend request from Troll"));
        assertTrue(HypixelChat.unknownCommand("§cUnknown command. Type \"/help\" for help."));
        assertTrue(HypixelChat.unknownCommand("Unknown or incomplete command, see below for error"));
        assertTrue(HypixelChat.unknownCommand("Unknown command. Try /help for a list of commands"));
        assertFalse(HypixelChat.unknownCommand("[VIP] Troll: Unknown command lol"));
        assertArrayEquals(new String[] {"BEDWARS", "BEDWARS_EIGHT_ONE"},
            HypixelChat.location("{\"server\":\"mini123A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_ONE\",\"map\":\"Lighthouse\"}"));
        assertArrayEquals(new String[] {"BEDWARS", null}, HypixelChat.location("{\"server\":\"dynamiclobby12\",\"gametype\":\"BEDWARS\",\"lobbyname\":\"bedwarslobby5\"}"));
        assertNull(HypixelChat.location("[VIP] Troll: {\"server\":\"x\"}"));
        assertEquals("/play bedwars_eight_one", HypixelChat.playCommand("BEDWARS", "BEDWARS_EIGHT_ONE"));
        assertEquals("/play solo_insane", HypixelChat.playCommand("SKYWARS", "solo_insane"));
        assertEquals("/play blitz_solo_normal", HypixelChat.playCommand("SURVIVAL_GAMES", "solo_normal"));
        assertEquals("/play duels_classic_duel", HypixelChat.playCommand("DUELS", "DUELS_CLASSIC_DUEL"));
        assertEquals("/play tnt_tntrun", HypixelChat.playCommand("TNTGAMES", "TNTRUN"));
        assertNull(HypixelChat.playCommand("BEDWARS", null));
        assertNull(HypixelChat.playCommand("SKYBLOCK", "hub"));
        assertNull(HypixelChat.playCommand("MAIN", "lobby"));
    }

    @Test void chatCleanupKinds() {
        Object[][] lines = {
            {"§b[MVP§c+§b] Steve§f §6joined the lobby!", HypixelChat.Spam.LOBBY_JOIN},
            {" §b>§c>§a>§r §6[MVP§2++§6] Steve§f §6joined the lobby! §a<§c<§b<", HypixelChat.Spam.LOBBY_JOIN},
            {"[MVP+] Steve spooked into the lobby!", HypixelChat.Spam.LOBBY_JOIN},
            {"§b✦ §r§b[MVP§r§c+§r§b] Steve §r§7found a §r§e★★★★★ §r§bMystery Box§r§7!", HypixelChat.Spam.MYSTERY_BOX},
            {"[VIP] Steve found Legendary Rainbow Necklace in a Mystery Box!", HypixelChat.Spam.MYSTERY_BOX},
            {"Steve has found Angel's Descent in the Soul Well!", HypixelChat.Spam.MYSTERY_BOX},
            {"They have gifted 15 ranks so far!", HypixelChat.Spam.GIFTS},
            {"[MVP++] Steve gifted the rank of MVP+ to Alex!", HypixelChat.Spam.GIFTS},
            {"You tipped 5 players in 5 different games!", HypixelChat.Spam.TIPS},
            {"You already tipped everyone that has boosters active, so there isn't anybody to be tipped right now!", HypixelChat.Spam.TIPS},
            {"No one has a network booster active right now! Try again later.", HypixelChat.Spam.TIPS},
            {"➲ Steve activated 3x coins for Bed Wars!", HypixelChat.Spam.BOOSTERS},
            {"Buy Network Boosters at https://store.hypixel.net", HypixelChat.Spam.BOOSTERS},
            {"[WATCHDOG ANNOUNCEMENT]\nWatchdog has banned 5,431 players in the last 7 days.\nStaff have banned an additional 1,234 in the last 7 days.\n"
                + "Blacklisted modifications are a bannable offense!", HypixelChat.Spam.WATCHDOG},
            {"+5 Karma!", HypixelChat.Spam.KARMA},
        };
        for (Object[] line : lines) assertEquals(line[1], HypixelChat.spam((String) line[0]), (String) line[0]);
        for (String line : new String[] {"[MVP+] Steve: joined the lobby!", "[VIP] Troll: They have gifted 15 ranks so far!", "Steve: +5 Karma!",
            "Friend > Steve joined.", "[MVP+] Steve: I found a Mystery Box!", "§eThe game starts in §r§c1§r§e second!"})
            assertNull(HypixelChat.spam(line), line);
    }

    /** The regression set from the 1.9.1 research (resources/hypixel/chat-fixtures.json: Hypixel lines per mode, and tricky negatives). */
    @Test void researchFixtures() throws Exception {
        com.google.gson.JsonArray fixtures;
        try (var in = getClass().getResourceAsStream("/hypixel/chat-fixtures.json")) {
            fixtures = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        }
        int checked = 0;
        for (var element : fixtures) {
            var fixture = element.getAsJsonObject();
            String raw = fixture.get("raw").getAsString(), kind = fixture.get("kind").getAsString(), what = kind + ": " + raw;
            HypixelChat.Spam spam = HypixelChat.spam(raw);
            switch (kind) {
                case "game_end" -> assertTrue(HypixelChat.gameEnd(raw), what);
                case "friend_request" -> assertNotNull(HypixelChat.friendRequest(raw), what);
                case "tip" -> assertEquals(HypixelChat.Spam.TIPS, spam, what);
                case "lobby_join" -> assertEquals(HypixelChat.Spam.LOBBY_JOIN, spam, what);
                case "spam" -> assertNotNull(spam, what);
                case "none" -> {
                    assertFalse(HypixelChat.gameEnd(raw), what);
                    assertNull(HypixelChat.friendRequest(raw), what);
                    assertNull(spam, what);
                }
                default -> fail("unknown kind " + what);
            }
            if (!kind.equals("game_end")) assertFalse(HypixelChat.gameEnd(raw), what);
            checked++;
        }
        assertTrue(checked >= 50, "fixtures read: " + checked);
    }

    @Test void modApiPingPacketsMatchTheSpec() {
        assertArrayEquals(new byte[] {1}, RealPing.request()); // VarInt version 1
        byte[] pong = {1, 1, 4, 'p', 'o', 'n', 'g'};            // success, version 1, "pong"
        assertEquals(1, RealPing.decode(pong));
        assertEquals(-3, RealPing.decode(new byte[] {0, 3}));   // error: rate limited
        assertEquals(-1, RealPing.decode(new byte[] {0, 1}));   // error: disabled
        assertEquals(0, RealPing.decode(new byte[] {1, 2, 4, 'p', 'o', 'n', 'g'})); // another version: ignored
        assertEquals(0, RealPing.decode(new byte[] {1, 1, 9, 'p'})); // cut short
        assertEquals(0, RealPing.decode(new byte[0]));
    }

    @Test void realPingOnHypixelHidesOthersAndMeasuresYours() {
        RealPing.tick(game, connection, 0);
        assertTrue(RealPing.fakeListing());
        assertEquals(RealPing.NONE, RealPing.own(1), "no measured ping yet: no number");
        assertEquals(RealPing.NONE, RealPing.row(1, false));
        RealPing.tick(game, connection, 2 * S);
        assertEquals(1, payloads.size(), "a Mod API ping went out");
        assertTrue(RealPing.receive(RealPing.CHANNEL, new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'}, 2 * S + 42_000_000L));
        assertEquals(42, RealPing.own(1));
        assertEquals(42, RealPing.row(1, true));
        assertEquals(RealPing.NONE, RealPing.row(1, false));
        RealPing.tick(game, connection, 3 * S);
        assertEquals(1, payloads.size(), "at most one ping every 5 s");
        RealPing.tick(game, connection, 7 * S + 1);
        assertEquals(2, payloads.size());
        assertFalse(RealPing.receive("minecraft:brand", new byte[] {1}, 8 * S));
    }

    /** Nothing shows the measured ping (Ping HUD and PingView off): no Mod API or status pings at all, the listed values stand. */
    @Test void realPingStaysQuietWithoutAPingDisplay() {
        Module hud = ModuleManager.getInstance().getModule("PingHUD"), view = ModuleManager.getInstance().getModule("PingView");
        boolean hudWas = hud.isEnabledSetting(), viewWas = view.isEnabledSetting();
        address = null; // no real status ping
        try {
            hud.setEnabled(false);
            view.setEnabled(false);
            RealPing.tick(game, connection, 0);
            RealPing.tick(game, connection, 2 * S);
            assertFalse(RealPing.fakeListing());
            assertTrue(payloads.isEmpty(), "no Mod API ping");
            assertEquals(1, RealPing.own(1));
            view.setEnabled(true);
            RealPing.tick(game, connection, 3 * S);
            assertTrue(RealPing.fakeListing());
            assertEquals(1, payloads.size(), "PingView on: measured again");
        } finally {
            hud.setEnabled(hudWas);
            view.setEnabled(viewWas);
        }
    }

    @Test void fakeListingIsDetectedOnAnyNetworkButNotLocally() {
        server = "play.example.net";
        address = null; // no status ping in this test
        assertFalse(RealPing.fakeListed(new int[] {1, 0, 1}, null));
        assertTrue(RealPing.fakeListed(new int[] {1, 0, 1}, new InetSocketAddress("203.0.113.5", 25565)));
        assertFalse(RealPing.fakeListed(new int[] {1, 0, 1}, new InetSocketAddress("192.0.2.1", 25565)), "LAN: 1 ms can be real");
        assertFalse(RealPing.fakeListed(new int[] {1, 0, 1}, new InetSocketAddress("127.0.0.1", 25565)));
        assertFalse(RealPing.fakeListed(new int[] {1}, new InetSocketAddress("203.0.113.5", 25565)), "one player proves nothing");
        assertFalse(RealPing.fakeListed(new int[] {1, 37, 1}, new InetSocketAddress("203.0.113.5", 25565)));
        listed = new int[] {40, 55};
        RealPing.tick(game, connection, 0);
        assertFalse(RealPing.fakeListing());
        assertEquals(40, RealPing.own(40));
        assertEquals(55, RealPing.row(55, false));
    }

    @Test void statusPingAgainstALocalServer() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread server = new Thread(() -> {
                try (Socket s = listener.accept()) {
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    byte[] handshake = read(in), request = read(in);
                    assertEquals(0, handshake[0]);
                    assertEquals(1, handshake[handshake.length - 1], "next state: status");
                    assertArrayEquals(new byte[] {0}, request);
                    byte[] json = "{\"version\":{\"name\":\"1.8\",\"protocol\":47}}".getBytes(StandardCharsets.UTF_8);
                    ByteArrayOutputStream status = new ByteArrayOutputStream();
                    status.write(0);
                    RealPing.writeVarInt(status, json.length);
                    status.write(json);
                    write(out, status.toByteArray());
                    byte[] ping = read(in);
                    assertEquals(1, ping[0]);
                    Thread.sleep(30);
                    write(out, ping); // pong: the same id and payload
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            server.start();
            int ms = RealPing.statusPing(new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort()), "localhost", 3000);
            server.join(5000);
            assertTrue(ms >= 25 && ms < 2000, "status ping " + ms + " ms");
        }
    }

    private static byte[] read(InputStream in) throws Exception {
        int length = 0;
        for (int shift = 0; ; shift += 7) { int b = in.read(); length |= (b & 0x7F) << shift; if ((b & 0x80) == 0) break; }
        byte[] body = new byte[length];
        new DataInputStream(in).readFully(body);
        return body;
    }

    private static void write(DataOutputStream out, byte[] body) throws Exception {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        RealPing.writeVarInt(packet, body.length);
        packet.write(body);
        out.write(packet.toByteArray());
        out.flush();
    }

    @Test void autoGgPlayAgainAndTheRateLimit() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_TIP)).set(false);
        ((TextOption) m.getOption(HypixelMods.AUTO_GG + ": Second Message")).setValue("good game");
        ((SliderOption) m.getOption(HypixelMods.AUTO_GG + ": Second Message seconds")).setValue(0);
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        assertEquals(List.of("/locraw"), sent);
        assertTrue(HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_ONE\",\"map\":\"Aquarium\"}", 3 * S),
            "our own /locraw answer is hidden");
        assertFalse(HypixelMods.chat("                       1st Killer - Steve - 7", 10 * S));
        assertFalse(HypixelMods.chat("                       2nd Killer - Alex - 3", 10 * S));
        assertFalse(HypixelMods.chat("§6This game has been recorded. §r§e§lClick here to watch the Replay!", 10 * S));
        for (long t = 10 * S; t <= 20 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/ac gg", "/ac good game", "/play bedwars_eight_one"), sent, "once per game, in order");
        HypixelMods.chat("                       1st Killer - Steve - 7", 21 * S);
        for (long t = 21 * S; t <= 30 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(4, sent.size(), "a second summary in the same world says nothing");
        // A new world (the next game) re-arms it.
        Object next = new Object();
        sent.clear();
        HypixelMods.tick(game, next, connection, 40 * S);
        HypixelMods.chat("                                Winner - Steve", 41 * S);
        for (long t = 41 * S; t <= 50 * S; t += S / 20) HypixelMods.tick(game, next, connection, t);
        assertEquals(List.of("/locraw", "/ac gg", "/ac good game"), sent, "no /play without a /locraw answer");
    }

    @Test void commandsAreAtLeastOneAndAHalfSecondsApart() {
        List<Long> times = new ArrayList<>();
        LadsGameBridge timed = new DefaultGameBridge() {
            long now;
            @Override public String getServerAddress() { return server; }
            @Override public void sendChatLine(String text) { times.add(now); }
            @Override public int getPing() { return (int) (now = clock); }
        };
        HypixelMods.tick(timed, world, connection, 0);
        for (int i = 0; i < 5; i++) HypixelMods.chat("Friend request from Player" + i, S);
        for (clock = S; clock <= 20 * S; clock += S / 20) { timed.getPing(); HypixelMods.tick(timed, world, connection, clock); }
        assertEquals(5, times.size());
        for (int i = 1; i < times.size(); i++) assertTrue(times.get(i) - times.get(i - 1) >= HypixelMods.GAP, times.toString());
    }
    private long clock;

    @Test void autoFriendOncePerPlayerAndAutoTipStopsWhenUnknown() {
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.chat("Friend request from [VIP] Steve", S);
        HypixelMods.chat("Friend request from [VIP] Steve", 2 * S);
        for (long t = S; t <= 5 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/friend accept Steve"), sent);
        sent.clear();
        HypixelMods.tick(game, world, connection, 31 * S);
        assertEquals(List.of("/tip all"), sent, "the first tip half a minute after joining");
        HypixelMods.chat("§cUnknown command. Type \"/help\" for help.", 32 * S);
        assertTrue(HypixelMods.tipStopped());
        for (long t = 32 * S; t <= 3600 * S; t += 10 * S) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/tip all"), sent, "no more tips this session");
        HypixelMods.tick(game, world, new Object(), 3601 * S);
        assertFalse(HypixelMods.tipStopped(), "a new connection tries again");
    }

    @Test void autoWhoAndCleanupOnlyOnHypixel() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.AUTO_WHO)).set(true);
        HypixelMods.tick(game, world, connection, 0);
        assertTrue(HypixelMods.chat("[MVP+] Steve joined the lobby!", S));
        assertFalse(HypixelMods.chat("[MVP+] Steve: hello", S));
        HypixelMods.chat("§eThe game starts in §r§c1§r§e second!", S);
        for (long t = S; t <= 5 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/who"), sent);
        m.getOption(HypixelMods.CLEANUP + ": Lobby joins").load(new com.google.gson.JsonPrimitive(false));
        assertFalse(HypixelMods.chat("[MVP+] Steve joined the lobby!", 6 * S), "that kind switched off");
        server = "play.example.net";
        sent.clear();
        HypixelMods.tick(game, world, connection, 7 * S);
        assertFalse(HypixelMods.chat("They have gifted 15 ranks so far!", 7 * S), "off Hypixel nothing is hidden");
        HypixelMods.chat("Friend request from Steve", 7 * S);
        for (long t = 7 * S; t <= 60 * S; t += S / 2) HypixelMods.tick(game, world, connection, t);
        assertTrue(sent.isEmpty(), "off Hypixel nothing is sent: " + sent);
        HypixelSafetyManager.qaOverride = true;
        assertTrue(HypixelSafetyManager.isHypixel(), "the QA override makes any server Hypixel");
    }

    @Test void featureDoesNotRunWhenGroupModuleDisabled() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.AUTO_FRIEND)).set(true);
        m.setEnabled(false);
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.chat("Friend request from Steve", S);
        for (long t = S; t <= 5 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertTrue(sent.isEmpty(), "group module is disabled: nothing is sent");
        assertFalse(HypixelMods.chat("[MVP+] Steve joined the lobby!", S), "cleanup disabled too");
    }

    @Test void configMigration() {
        com.google.gson.JsonObject modules = new com.google.gson.JsonObject();
        com.google.gson.JsonObject oldFriend = new com.google.gson.JsonObject();
        oldFriend.addProperty("enabled", true);
        modules.add("Auto Friend", oldFriend);
        com.google.gson.JsonObject oldGg = new com.google.gson.JsonObject();
        oldGg.addProperty("enabled", false);
        com.google.gson.JsonObject oldGgOpts = new com.google.gson.JsonObject();
        oldGgOpts.addProperty("Message", "custom gg");
        oldGg.add("options", oldGgOpts);
        modules.add("Auto GG", oldGg);

        com.thelads.core.config.ConfigManager.migrateHypixelMods(modules);

        assertFalse(modules.has("Auto Friend"));
        assertFalse(modules.has("Auto GG"));
        assertTrue(modules.has("HYPIXEL Mods"));
        com.google.gson.JsonObject group = modules.getAsJsonObject("HYPIXEL Mods");
        assertTrue(group.get("enabled").getAsBoolean());
        com.google.gson.JsonObject opts = group.getAsJsonObject("options");
        assertTrue(opts.get("Auto Friend").getAsBoolean());
        assertFalse(opts.get("Auto GG").getAsBoolean());
        assertEquals("custom gg", opts.get("Auto GG: Message").getAsString());
    }

    @Test void shouldReplayDecisionLogic() {
        // gameOver is always true regardless of other settings
        assertTrue(HypixelMods.shouldReplay("After death", true, false, true, 2));
        assertTrue(HypixelMods.shouldReplay("After death", true, true, true, 2));
        assertTrue(HypixelMods.shouldReplay("When game ends", true, false, true, 2));
        assertTrue(HypixelMods.shouldReplay("When game ends", false, true, true, 0));

        // When game ends mode with !gameOver -> always false
        assertFalse(HypixelMods.shouldReplay("When game ends", false, true, false, 0));
        assertFalse(HypixelMods.shouldReplay("When game ends", true, true, false, 0));
        assertFalse(HypixelMods.shouldReplay("When game ends", true, true, false, 0, true, true));

        // After death mode: self alive -> false
        assertFalse(HypixelMods.shouldReplay("After death", false, false, false, 0));
        assertFalse(HypixelMods.shouldReplay("After death", true, false, false, 0));
        assertFalse(HypixelMods.shouldReplay("After death", true, false, false, 2));

        // After death mode: self out, waitParty false -> true
        assertTrue(HypixelMods.shouldReplay("After death", false, true, false, 0));
        assertTrue(HypixelMods.shouldReplay("After death", false, true, false, 2));

        // After death mode: self out, waitParty true:
        // Party alive == 0 -> true
        assertTrue(HypixelMods.shouldReplay("After death", true, true, false, 0));
        // Party alive > 0 -> false
        assertFalse(HypixelMods.shouldReplay("After death", true, true, false, 1));
        assertFalse(HypixelMods.shouldReplay("After death", true, true, false, 2));
        // Timed out -> true even with party alive
        assertTrue(HypixelMods.shouldReplay("After death", true, true, false, 1, true, true));
        // Unknown mode with party alive > 0 -> false (waits for game end)
        assertFalse(HypixelMods.shouldReplay("After death", true, true, false, 1, false, false));
        // Unknown mode with party alive == 0 -> true
        assertTrue(HypixelMods.shouldReplay("After death", true, true, false, 0, false, false));
    }

    @Test void partyTrackingFromChatAndPartyList() {
        HypixelMods.setPlayerNameForTest("Me");
        HypixelMods.tick(game, world, connection, 0);

        // /party list role lines
        HypixelMods.chat("-----------------------------------------------------", S);
        HypixelMods.chat("Party Members (3)", S);
        HypixelMods.chat("Party Leader: [MVP+] Leader_Player ●", S);
        HypixelMods.chat("Party Moderators: [VIP] Mod_Player ●", S);
        HypixelMods.chat("Party Members: [MVP+] Me ● [VIP] Teammate_1 ●", S);
        HypixelMods.chat("-----------------------------------------------------", S);

        assertTrue(HypixelMods.partyMembers().contains("leader_player"));
        assertTrue(HypixelMods.partyMembers().contains("mod_player"));
        assertTrue(HypixelMods.partyMembers().contains("me"));
        assertTrue(HypixelMods.partyMembers().contains("teammate_1"));
        // Local player "me" is excluded from alive teammates
        assertEquals(Set.of("leader_player", "mod_player", "teammate_1"), HypixelMods.partyAlive());

        // Player join
        HypixelMods.chat("[Party] [VIP] New_Player joined the party.", 2 * S);
        assertTrue(HypixelMods.partyMembers().contains("new_player"));
        assertTrue(HypixelMods.partyAlive().contains("new_player"));

        // Player leave
        HypixelMods.chat("[Party] New_Player has left the party.", 3 * S);
        assertFalse(HypixelMods.partyMembers().contains("new_player"));
        assertFalse(HypixelMods.partyAlive().contains("new_player"));

        // Player kick
        HypixelMods.chat("Mod_Player was removed from the party.", 4 * S);
        assertFalse(HypixelMods.partyMembers().contains("mod_player"));

        // Player chat negative check: player typing party message does not affect roster
        HypixelMods.chat("Party > [MVP+] Leader_Player: Teammate_1 has left the party.", 5 * S);
        assertTrue(HypixelMods.partyMembers().contains("teammate_1"));
        HypixelMods.chat("From [VIP] Troll: You left the party.", 5 * S);
        assertFalse(HypixelMods.partyMembers().isEmpty());

        // Party disband
        HypixelMods.chat("The party was disbanded because all invites expired and the party was empty.", 6 * S);
        assertTrue(HypixelMods.partyMembers().isEmpty());
        assertTrue(HypixelMods.partyAlive().isEmpty());

        // You join a party
        HypixelMods.chat("You joined [MVP+] Solo_Leader's party!", 7 * S);
        assertEquals(Set.of("solo_leader"), HypixelMods.partyMembers());
        assertEquals(Set.of("solo_leader"), HypixelMods.partyAlive());
    }

    @Test void eliminationAndDeathDetectionPerMode() {
        // Bed Wars: only FINAL KILL, eliminated, or disconnected count
        assertEquals("victim", HypixelChat.eliminatedPlayer("BEDWARS", "victim was killed by killer. FINAL KILL!"));
        assertEquals("victim", HypixelChat.eliminatedPlayer("BEDWARS", "[RED] victim was knocked into the void by [BLUE] killer. FINAL KILL!"));
        assertEquals("victim", HypixelChat.eliminatedPlayer("BEDWARS", "victim has been eliminated!"));
        assertEquals("victim", HypixelChat.eliminatedPlayer("BEDWARS", "victim disconnected."));
        // Regular Bed Wars kill does NOT eliminate
        assertNull(HypixelChat.eliminatedPlayer("BEDWARS", "victim was killed by killer."));

        // Sky Wars: regular kill counts
        assertEquals("victim", HypixelChat.eliminatedPlayer("SKYWARS", "victim was killed by killer."));
        assertEquals("victim", HypixelChat.eliminatedPlayer("SKYWARS", "victim fell into the void."));
        assertEquals("victim", HypixelChat.eliminatedPlayer("SKYWARS", "victim disconnected."));

        // Duels: kill counts
        assertEquals("victim", HypixelChat.eliminatedPlayer("DUELS", "victim was slain by killer."));

        // Player chat negative: player chat with death line does not match
        assertNull(HypixelChat.eliminatedPlayer("BEDWARS", "[MVP+] Troll: victim was killed by killer. FINAL KILL!"));
        assertNull(HypixelChat.eliminatedPlayer("SKYWARS", "Party > Troll: victim died."));

        // Self out checks
        assertTrue(HypixelChat.isSelfOut("BEDWARS", "You died! Want to play again? Click here to play again!", "Me"));
        assertTrue(HypixelChat.isSelfOut("BEDWARS", "You have been eliminated!", "Me"));
        assertTrue(HypixelChat.isSelfOut("BEDWARS", "You are now a spectator!", "Me"));
        assertTrue(HypixelChat.isSelfOut("BEDWARS", "Me was killed by killer. FINAL KILL!", "Me"));
        assertFalse(HypixelChat.isSelfOut("BEDWARS", "Teammate was killed by killer. FINAL KILL!", "Me"));
        assertFalse(HypixelChat.isSelfOut("BEDWARS", "[MVP+] Troll: You died!", "Me"));
    }

    @Test void autoPlayAgainWaitsForPartyBeforeReplaying() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_TIP)).set(false);
        ((DropdownOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN)).setIndex(0); // "After death"
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY)).set(true);
        ((SliderOption) m.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds")).setValue(0);

        HypixelMods.setPlayerNameForTest("Me");
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        assertEquals(List.of("/locraw"), sent);
        HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_TWO\",\"map\":\"Aquarium\"}", 3 * S);

        // Form party of 2
        HypixelMods.chat("You joined [MVP+] Bob's party!", 4 * S);
        assertEquals(Set.of("bob"), HypixelMods.partyAlive());

        // You die
        HypixelMods.chat("You died! Want to play again? Click here to play again!", 5 * S);
        assertTrue(HypixelMods.isSelfOut());
        for (long t = 5 * S; t <= 10 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        // Bob is still alive, no /play sent!
        assertEquals(List.of("/locraw"), sent);

        // Teammate Bob dies on FINAL KILL
        HypixelMods.chat("Bob was killed by Enemy. FINAL KILL!", 11 * S);
        assertEquals(0, HypixelMods.partyAlive().size());
        for (long t = 11 * S; t <= 15 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        // Now /play is sent!
        assertEquals(List.of("/locraw", "/play bedwars_eight_two"), sent);
    }

    @Test void autoPlayAgainWhenGameEndsMode() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_TIP)).set(false);
        ((DropdownOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN)).setIndex(1); // "When game ends"
        ((SliderOption) m.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds")).setValue(0);
        ((SliderOption) m.getOption(HypixelMods.AUTO_GG + ": Delay seconds")).setValue(0);

        HypixelMods.setPlayerNameForTest("Me");
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        assertEquals(List.of("/locraw"), sent);
        HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_ONE\",\"map\":\"Aquarium\"}", 3 * S);

        // You die
        HypixelMods.chat("You died! Want to play again? Click here to play again!", 4 * S);
        for (long t = 4 * S; t <= 10 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        // Mode is "When game ends", death sends nothing!
        assertEquals(List.of("/locraw"), sent);

        // Game over arrives
        HypixelMods.chat("                       1st Killer - Enemy - 7", 11 * S);
        for (long t = 11 * S; t <= 15 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/ac gg", "/play bedwars_eight_one"), sent);
    }

    @Test void autoPlayAgainWithoutPartyReplaysImmediately() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_TIP)).set(false);
        ((DropdownOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN)).setIndex(0); // "After death"
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY)).set(true);
        ((SliderOption) m.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds")).setValue(0);

        HypixelMods.setPlayerNameForTest("Me");
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"SKYWARS\",\"mode\":\"solo_insane\",\"map\":\"Aquarium\"}", 3 * S);

        // No party: you die in SkyWars -> replays immediately
        HypixelMods.chat("Me fell into the void.", 5 * S);
        for (long t = 5 * S; t <= 10 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/play solo_insane"), sent);
    }

    @Test void autoPlayAgainSafetyTimeout() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_TIP)).set(false);
        ((DropdownOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN)).setIndex(0); // "After death"
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY)).set(true);
        ((SliderOption) m.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds")).setValue(0);

        HypixelMods.setPlayerNameForTest("Me");
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_FOUR_FOUR\",\"map\":\"Aquarium\"}", 3 * S);
        HypixelMods.chat("You joined [MVP+] Bob's party!", 4 * S);

        // You are eliminated
        HypixelMods.chat("You have been eliminated!", 5 * S);
        // Tick up to 4 minutes: still blocked because Bob is alive
        for (long t = 5 * S; t <= 245 * S; t += 30 * S) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw"), sent);

        // Tick past 5 minutes (306 seconds): timeout fires!
        HypixelMods.tick(game, world, connection, 306 * S);
        for (long t = 306 * S; t <= 310 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/play bedwars_four_four"), sent);
    }

    @Test void autoPlayAgainUnknownModeWaitsForGameEnd() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_TIP)).set(false);
        ((DropdownOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN)).setIndex(0); // "After death"
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY)).set(true);
        ((SliderOption) m.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds")).setValue(0);
        ((SliderOption) m.getOption(HypixelMods.AUTO_GG + ": Delay seconds")).setValue(0);

        HypixelMods.setPlayerNameForTest("Me");
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        // Unknown mode: PROTOTYPE
        HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"PROTOTYPE\",\"mode\":\"pixel_party\",\"map\":\"Lobby\"}", 3 * S);
        HypixelMods.chat("You joined [MVP+] Bob's party!", 4 * S);

        // You die
        HypixelMods.chat("You have been eliminated!", 5 * S);
        for (long t = 5 * S; t <= 10 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        // Unknown mode falls back to waiting for game end
        assertEquals(List.of("/locraw"), sent);
    }

    @Test void bedWarsRespawningDeathDoesNotCountAsSelfOut() {
        // Pure chat logic checks
        assertFalse(HypixelChat.isSelfOut("BEDWARS", "You died!", "Me"));
        assertFalse(HypixelChat.isSelfOut("BEDWARS", "You will respawn in 5 seconds!", "Me"));
        assertFalse(HypixelChat.isSelfOut("BEDWARS", "You died!\nYou will respawn in 5 seconds!", "Me"));
        assertFalse(HypixelChat.isSelfOut("BEDWARS", "Me was knocked into the void by Enemy.", "Me"));

        // Final death lines DO count
        assertTrue(HypixelChat.isSelfOut("BEDWARS", "You died! Want to play again? Click here to play again!", "Me"));
        assertTrue(HypixelChat.isSelfOut("BEDWARS", "You have been eliminated!", "Me"));
        assertTrue(HypixelChat.isSelfOut("BEDWARS", "You are now a spectator!", "Me"));
        assertTrue(HypixelChat.isSelfOut("BEDWARS", "Me was killed by Enemy. FINAL KILL!", "Me"));

        // Module end-to-end check in solo Bed Wars
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_TIP)).set(false);
        ((DropdownOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN)).setIndex(0); // "After death"
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY)).set(false);
        ((SliderOption) m.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds")).setValue(0);

        HypixelMods.setPlayerNameForTest("Me");
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_ONE\",\"map\":\"Lighthouse\"}", 3 * S);
        HypixelMods.chat("The game starts in 1 second!", 4 * S);

        // Respawning death occurs
        HypixelMods.chat("You died!", 5 * S);
        HypixelMods.chat("You will respawn in 5 seconds!", 5 * S);
        assertFalse(HypixelMods.isSelfOut());
        for (long t = 5 * S; t <= 10 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        // Player is respawning, must NOT trigger replay!
        assertEquals(List.of("/locraw"), sent);

        // Actual final elimination occurs
        HypixelMods.chat("Me was killed by Enemy. FINAL KILL!", 11 * S);
        assertTrue(HypixelMods.isSelfOut());
        for (long t = 11 * S; t <= 15 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/play bedwars_eight_one"), sent);
    }

    @Test void partyListRateLimitingAndEliminatedTeammatePersistence() {
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_TIP)).set(false);
        ((DropdownOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.REPLAY_WHEN)).setIndex(0); // "After death"
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN + ": " + HypixelMods.WAIT_PARTY)).set(true);
        ((SliderOption) m.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds")).setValue(0);

        HypixelMods.setPlayerNameForTest("Me");

        // 1. Solo player: game start should NOT send /party list
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_ONE\",\"map\":\"Lighthouse\"}", 3 * S);
        HypixelMods.chat("The game starts in 1 second!", 4 * S);
        for (long t = 4 * S; t <= 8 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw"), sent);
        assertFalse(HypixelMods.isInParty());

        // 2. Party with already known members: game start should NOT send /party list
        HypixelMods.chat("You joined [MVP+] Bob's party!", 9 * S);
        assertTrue(HypixelMods.isInParty());
        assertEquals(Set.of("bob"), HypixelMods.partyMembers());
        HypixelMods.chat("The game starts in 1 second!", 10 * S);
        for (long t = 10 * S; t <= 14 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        // Only /locraw, no /party list
        assertEquals(List.of("/locraw"), sent);

        // 3. In party (known from party chat), but roster unknown: game start DOES send /party list at most once
        HypixelMods.resetForTest();
        sent.clear();
        HypixelMods.setPlayerNameForTest("Me");
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"BEDWARS\",\"mode\":\"BEDWARS_FOUR_FOUR\",\"map\":\"Lighthouse\"}", 3 * S);
        // Party chat observed -> inParty is true, but PARTY_MEMBERS is empty
        HypixelMods.chat("Party > [MVP+] Leader: lets win this", 4 * S);
        assertTrue(HypixelMods.isInParty());
        assertTrue(HypixelMods.partyMembers().isEmpty());

        HypixelMods.chat("The game starts in 1 second!", 5 * S);
        // Advance past 2s delay for /party list
        for (long t = 5 * S; t <= 8 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/party list"), sent);

        // Even with another game start, rate limited to at most once per game
        HypixelMods.chat("The game starts in 1 second!", 9 * S);
        for (long t = 9 * S; t <= 12 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/party list"), sent);

        // 4. Bob is eliminated BEFORE /party list response arrives
        HypixelMods.chat("Bob was killed by Enemy. FINAL KILL!", 13 * S);
        assertTrue(HypixelMods.partyEliminated().contains("bob"));

        // Delayed /party list response arrives listing Bob and Alice
        HypixelMods.chat("Party Members (3)", 14 * S);
        HypixelMods.chat("Party Members: [MVP+] Me ● [VIP] Bob ● [MVP] Alice ●", 14 * S);
        // Alice is added to alive teammates, but Bob was already eliminated so Bob must NOT be revived!
        assertTrue(HypixelMods.partyAlive().contains("alice"));
        assertFalse(HypixelMods.partyAlive().contains("bob"));

        // You die
        HypixelMods.chat("You have been eliminated!", 15 * S);
        for (long t = 15 * S; t <= 18 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        // Alice is still alive, no /play yet
        assertEquals(List.of("/locraw", "/party list"), sent);

        // Alice is eliminated
        HypixelMods.chat("Alice was killed by Enemy. FINAL KILL!", 19 * S);
        for (long t = 19 * S; t <= 22 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        // All alive party members eliminated -> /play sent!
        assertEquals(List.of("/locraw", "/party list", "/play bedwars_four_four"), sent);
    }

    @Test void cosmeticKillMessagesPartyNoticesAndNewGameDropTheGg() {
        // Real lines from a 2026-10-15 log: kill-message cosmetics, not "was killed by".
        assertEquals("Arash_YT", HypixelChat.eliminatedPlayer("SKYWARS", "Arash_YT took the L to m310n_."));
        assertEquals("UnofficiallyRed", HypixelChat.eliminatedPlayer("SKYWARS", "UnofficiallyRed got rekt by m310n_."));
        assertEquals("UnofficiallyRed", HypixelChat.eliminatedPlayer("DUELS", "UnofficiallyRed was given the cold shoulder by Arash_YT using Baton."));
        assertEquals("Bob", HypixelChat.eliminatedPlayer("DUELS", "[RED] Bob lost the draw to [BLUE] Me. FINAL KILL!"));
        assertNull(HypixelChat.eliminatedPlayer("DUELS", "You summoned [MVP+] UnofficiallyRed to your server."));
        assertNull(HypixelChat.eliminatedPlayer("DUELS", "[MVP+] Arash_YT invited [MVP+] UnofficiallyRed to the party! They have 60 seconds to accept."));
        assertNull(HypixelChat.eliminatedPlayer("DUELS", "[MVP+] UnofficiallyRed has invited you to UHC Duels!"));
        assertNull(HypixelChat.eliminatedPlayer("DUELS", "[MVP+] Steve: Bob got rekt by Me"), "player chat");
        assertNull(HypixelChat.eliminatedPlayer("BEDWARS", "Bob got rekt by Me."), "Bed Wars: only final kills count");
        assertTrue(HypixelChat.isPartyChat("Your party was split for balancing purposes."));
        assertTrue(HypixelChat.isPartyChat("Your stats did not change because you dueled someone in your party!"));
        assertFalse(HypixelChat.isPartyChat("[MVP+] Steve: in your party"));
        assertTrue(HypixelChat.newGame("Found a server running Duels: UHC DUEL! Sending you to mini149AM!"));
        assertTrue(HypixelChat.newGame("Arash_YT has joined (2/2)!"));
        assertFalse(HypixelChat.newGame("[MVP+] Steve: Sending you to mini1A!"));

        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        ((BoolOption) m.getOption(HypixelMods.PLAY_AGAIN)).set(true);
        ((BoolOption) m.getOption(HypixelMods.AUTO_TIP)).set(false);
        ((TextOption) m.getOption(HypixelMods.AUTO_GG + ": Second Message")).setValue("good game");
        ((SliderOption) m.getOption(HypixelMods.AUTO_GG + ": Second Message seconds")).setValue(5);
        ((SliderOption) m.getOption(HypixelMods.PLAY_AGAIN + ": Delay seconds")).setValue(0);
        HypixelMods.setPlayerNameForTest("Me");
        HypixelMods.tick(game, world, connection, 0);
        HypixelMods.tick(game, world, connection, 2 * S);
        HypixelMods.chat("{\"server\":\"mini1A\",\"gametype\":\"DUELS\",\"mode\":\"DUELS_UHC_DUEL\",\"map\":\"Arena\"}", 3 * S);
        HypixelMods.chat("The game starts in 1 second!", 4 * S);
        // Party of two in Duels: we die to the party member, the game ends at once.
        HypixelMods.chat("Your party was split for balancing purposes.", 5 * S);
        HypixelMods.chat("Me took the L to Red.", 10 * S);
        assertTrue(HypixelMods.isSelfOut(), "a cosmetic kill message counts as our death");
        HypixelMods.chat("         [MVP+] Me   [MVP+] Red WINNER!", 10 * S);
        for (long t = 10 * S; t <= 13 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/ac gg"), sent, "gg goes before /play, even with a 0 s Play Again delay");
        for (long t = 13 * S; t <= 20 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertEquals(List.of("/locraw", "/ac gg", "/ac good game", "/play duels_uhc_duel"), sent, "then the second gg, then /play");
        // Next game: moved there before the second gg is due -> neither it nor /play goes out.
        sent.clear();
        HypixelMods.chat("The game starts in 1 second!", 30 * S);
        HypixelMods.chat("Me took the L to Red.", 31 * S);
        HypixelMods.chat("         [MVP+] Me   [MVP+] Red WINNER!", 31 * S);
        for (long t = 31 * S; t <= 36 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        HypixelMods.chat("Me has joined (1/2)!", 36 * S);
        for (long t = 36 * S; t <= 50 * S; t += S / 20) HypixelMods.tick(game, world, connection, t);
        assertTrue(sent.contains("/ac gg") && !sent.contains("/ac good game") && !sent.contains("/play duels_uhc_duel"),
            "no second gg (or /play) in the next game " + sent);
        assertTrue(HypixelMods.isInParty(), "'Your party was split' says we are in a party");
    }

    @Test void rateLimitedModApiPingFallsBackToStatusPingsAndSmooths() {
        address = null; // no real status ping
        RealPing.tick(game, connection, 0);
        RealPing.tick(game, connection, 2 * S);
        assertEquals(1, payloads.size());
        RealPing.receive(RealPing.CHANNEL, new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'}, 2 * S + 40_000_000L);
        assertEquals(40, RealPing.own(1));
        RealPing.tick(game, connection, 7 * S);
        assertEquals(2, payloads.size());
        RealPing.receive(RealPing.CHANNEL, new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'}, 7 * S + 80_000_000L);
        assertEquals(60, RealPing.own(1), "rolling median of [40, 80]: (40 + 80) / 2");
        RealPing.tick(game, connection, 13 * S);
        assertEquals(3, payloads.size());
        RealPing.receive(RealPing.CHANNEL, new byte[] {0, 3}, 13 * S + 10_000_000L); // rate limited
        for (long t = 14 * S; t < 23 * S; t += S) RealPing.tick(game, connection, t);
        assertEquals(3, payloads.size(), "no Mod API ping for 10 s after 'rate limited' (status pings stand in)");
        RealPing.tick(game, connection, 24 * S);
        assertEquals(4, payloads.size(), "then the Mod API again");
    }

    @Test void rollingMedianFiltersSpikesAndSettlesFast() {
        RealPing.PingTracker tracker = new RealPing.PingTracker(3);
        tracker.add(15, 1 * S);
        assertEquals(15, tracker.get(1 * S), "first sample available at once");
        tracker.add(17, 2 * S);
        assertEquals(16, tracker.get(2 * S), "median of [15, 17] is 16");
        // Outlier spike: 200 ms packet delay into [15, 17, 200]
        tracker.add(200, 3 * S);
        assertEquals(17, tracker.get(3 * S), "median of [15, 17, 200] is 17: spike filtered");
        tracker.add(16, 4 * S);
        assertEquals(17, tracker.get(4 * S), "median of [17, 200, 16] is 17");
        // Route step change from ~16 to 80 ms: settles in exactly 2 samples
        tracker.add(80, 5 * S); // buffer has [200, 16, 80] -> median 80
        assertEquals(80, tracker.get(5 * S), "settles to 80 within 2 samples of step change");
        tracker.add(80, 6 * S); // buffer has [16, 80, 80] -> median 80
        assertEquals(80, tracker.get(6 * S));
        // Stale expiration
        assertEquals(-1, tracker.get(6 * S + 31 * S), "stale after 30 s");
        // Fresh sample after stale resets buffer
        tracker.add(25, 6 * S + 32 * S);
        assertEquals(25, tracker.get(6 * S + 32 * S));
    }

    @Test void statusPingAndModApiMinimumSelection() {
        // Status ping measures pure socket RTT (15 ms); Mod API includes 0-50 ms server tick delay (42 ms)
        assertEquals(15, RealPing.aggregate(15, 42), "minimum selected when both exist");
        assertEquals(42, RealPing.aggregate(-1, 42), "falls back to Mod API when status ping fails");
        assertEquals(15, RealPing.aggregate(15, -1), "falls back to status ping when Mod API unavailable");
        assertEquals(RealPing.NONE, RealPing.aggregate(-1, -1), "NONE when neither available");
    }

    @Test void statusPingPacketFramingWithFakeStreams() throws Exception {
        ByteArrayOutputStream serverToClient = new ByteArrayOutputStream();
        // Server writes status response: packet id 0, JSON string
        byte[] json = "{\"version\":{\"name\":\"1.8.9\",\"protocol\":47}}".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream statusBody = new ByteArrayOutputStream();
        RealPing.writeVarInt(statusBody, 0); // packet id 0
        RealPing.writeVarInt(statusBody, json.length);
        statusBody.write(json);
        RealPing.writeVarInt(serverToClient, statusBody.size());
        serverToClient.write(statusBody.toByteArray());

        // Server writes pong response: packet id 1, 8-byte payload
        ByteArrayOutputStream pongBody = new ByteArrayOutputStream();
        RealPing.writeVarInt(pongBody, 1); // packet id 1
        pongBody.write(new byte[] {0, 0, 0, 0, 0, 0, 0, 42});
        RealPing.writeVarInt(serverToClient, pongBody.size());
        serverToClient.write(pongBody.toByteArray());

        ByteArrayInputStream in = new ByteArrayInputStream(serverToClient.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int ms = RealPing.statusPing(in, out, "mc.hypixel.net", 25565);
        assertTrue(ms >= 0 && ms < 5000, "measured status ping " + ms + " ms");

        // Verify packets sent from client to server
        ByteArrayInputStream clientToServer = new ByteArrayInputStream(out.toByteArray());
        byte[] handshakePacket = read(clientToServer);
        DataInputStream hIn = new DataInputStream(new ByteArrayInputStream(handshakePacket));
        assertEquals(0, hIn.read()); // packet id 0
        assertEquals(47, hIn.read()); // protocol version 47
        int hostLen = hIn.read();
        byte[] hostBytes = new byte[hostLen];
        hIn.readFully(hostBytes);
        assertEquals("mc.hypixel.net", new String(hostBytes, StandardCharsets.UTF_8));
        int port = hIn.readUnsignedShort();
        assertEquals(25565, port);
        assertEquals(1, hIn.read()); // state 1: status

        byte[] statusReq = read(clientToServer);
        assertEquals(1, statusReq.length);
        assertEquals(0, statusReq[0]); // packet id 0

        byte[] pingPacket = read(clientToServer);
        assertEquals(9, pingPacket.length);
        assertEquals(1, pingPacket[0]); // packet id 1
        assertEquals(42, pingPacket[8]);
    }

    @Test void statusPingFailsOnInvalidStatusPacketId() throws Exception {
        ByteArrayOutputStream serverToClient = new ByteArrayOutputStream();
        // Server writes packet with id 2 (invalid for status state)
        ByteArrayOutputStream badBody = new ByteArrayOutputStream();
        RealPing.writeVarInt(badBody, 2);
        RealPing.writeVarInt(serverToClient, badBody.size());
        serverToClient.write(badBody.toByteArray());

        ByteArrayInputStream in = new ByteArrayInputStream(serverToClient.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(IOException.class, () -> RealPing.statusPing(in, out, "mc.hypixel.net", 25565));
    }

    @Test void statusPingFailsOnInvalidPongPacketId() throws Exception {
        ByteArrayOutputStream serverToClient = new ByteArrayOutputStream();
        // Server writes valid status response
        byte[] json = "{}".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream statusBody = new ByteArrayOutputStream();
        RealPing.writeVarInt(statusBody, 0);
        RealPing.writeVarInt(statusBody, json.length);
        statusBody.write(json);
        RealPing.writeVarInt(serverToClient, statusBody.size());
        serverToClient.write(statusBody.toByteArray());

        // But writes packet with id 3 instead of pong (id 1)
        ByteArrayOutputStream badPong = new ByteArrayOutputStream();
        RealPing.writeVarInt(badPong, 3);
        RealPing.writeVarInt(serverToClient, badPong.size());
        serverToClient.write(badPong.toByteArray());

        ByteArrayInputStream in = new ByteArrayInputStream(serverToClient.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(IOException.class, () -> RealPing.statusPing(in, out, "mc.hypixel.net", 25565));
    }
}
