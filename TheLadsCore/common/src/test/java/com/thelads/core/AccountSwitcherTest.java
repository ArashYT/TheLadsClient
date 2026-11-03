package com.thelads.core;

import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.client.auth.AccountTokens;
import com.thelads.core.client.auth.LadsAccount;
import com.thelads.core.client.auth.LiveSession;
import com.thelads.core.client.util.ClientPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AccountSwitcherTest {

    @Test
    public void testAccountSwitchingAndPersistence(@TempDir Path tempDir) {
        ClientPaths.setBaseDir(tempDir);

        AccountSwitcherScreen screen = new AccountSwitcherScreen();
        assertNotNull(screen.getSelectedAccount(), "Default account should be selected");

        screen.addOfflineAccount("ArashTest");
        LadsAccount selected = screen.getSelectedAccount();
        assertNotNull(selected);
        assertEquals("ArashTest", selected.getUsername());
        assertTrue(selected.isSelected());

        assertTrue(ClientPaths.getAccountsFile().exists(), "Accounts file should be saved");
        assertTrue(tempDir.resolve("lads_next_account.json").toFile().exists(), "Next-launch request should be saved");
        assertFalse(ClientPaths.getProfileFile().exists(), "Selecting an account must not impersonate a changed live Minecraft session");
    }

    private static final String SECRET = "eyJ.secret-token_4";
    private static final String MS_UUID = "069a79f444e94726a5befca90e38aaf5";
    private static final String MS_UUID_2 = "853c80ef3c3749fdaa49938b674adae6";

    /** The game: a session that changes only when the prepared step runs. */
    private static class FakeGame implements LiveSession {
        String name, uuid;
        boolean world;
        Exception prepareFails;
        RuntimeException commitFails;
        final List<String> tokensSeen = new ArrayList<>();
        int commits;

        FakeGame(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        static FakeGame offline(String name) {
            return new FakeGame(name, LadsAccount.offlineUuid(name).toString());
        }

        @Override public String username() { return name; }
        @Override public String uuid() { return uuid; }
        @Override public boolean inWorld() { return world; }

        @Override
        public Runnable prepare(LadsAccount target, String accessToken) throws Exception {
            UUID targetUuid = target.sessionUuid();
            tokensSeen.add(accessToken);
            if (prepareFails != null) throw prepareFails;
            return () -> {
                if (commitFails != null) throw commitFails;
                commits++;
                world = false;
                name = target.getUsername();
                uuid = targetUuid.toString();
            };
        }
    }

    /** The launcher on the other end of the pipe. */
    private static class FakeLauncher extends AccountTokens {
        final String reply;
        String handOff = "pipe-name 0123456789abcdef0123456789abcdef";
        final List<String> asked = new ArrayList<>();
        int switched;
        boolean closed;

        FakeLauncher(String reply) {
            this.reply = reply;
        }

        @Override protected String handOff() { return handOff; }

        @Override
        protected String ask(String pipe, String request) throws IOException {
            if (closed) throw new IOException("no pipe");
            asked.add(request);
            return reply;
        }

        @Override public void switched() { switched++; }
    }

    private static void finish(AccountSwitcherScreen screen) throws InterruptedException {
        for (int i = 0; i < 500 && screen.isSwitching(); i++) {
            screen.tick();
            Thread.sleep(5);
        }
        assertFalse(screen.isSwitching(), "the switch should have finished");
    }

    private static LadsAccount microsoft(AccountSwitcherScreen screen) {
        LadsAccount account = new LadsAccount("Notch", MS_UUID, "microsoft", "0");
        screen.getAccounts().add(account);
        return account;
    }

    @Test
    public void offlineToOfflineSwapsTheSessionWithoutTheLauncher(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        FakeGame game = FakeGame.offline("First");
        FakeLauncher launcher = new FakeLauncher("");
        AccountSwitcherScreen screen = new AccountSwitcherScreen(game, launcher);

        screen.addOfflineAccount("Second");
        assertEquals("First", game.name, "nothing changes before the switch has finished");
        finish(screen);

        assertEquals("Second", game.name);
        assertEquals(LadsAccount.offlineUuid("Second").toString(), game.uuid);
        assertEquals(List.of("0"), game.tokensSeen);
        assertTrue(launcher.asked.isEmpty());
        assertEquals(1, launcher.switched);
        assertEquals("Second", screen.getSelectedAccount().getUsername());
        assertTrue(screen.getStatusMessage().contains("Now playing as Second"));
        assertTrue(Files.readString(tempDir.resolve("lads_next_account.json")).contains("Second"), "the launcher's next launch follows");
        assertTrue(Files.readString(ClientPaths.getAccountsFile().toPath()).contains("Second"));
    }

    @Test
    public void aMicrosoftTokenComesFromTheLauncherAndIsWrittenNowhere(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        FakeGame game = FakeGame.offline("First");
        FakeLauncher launcher = new FakeLauncher("{\"token\":\"" + SECRET + "\"}");
        AccountSwitcherScreen screen = new AccountSwitcherScreen(game, launcher);
        LadsAccount target = microsoft(screen);

        screen.selectAccount(target);
        finish(screen);

        assertEquals("Notch", game.name);
        assertEquals(List.of(SECRET), game.tokensSeen);
        assertTrue(launcher.asked.get(0).contains("\"op\":\"token\"") && launcher.asked.get(0).contains("0123456789abcdef0123456789abcdef"));
        assertFalse(screen.getStatusMessage().contains(SECRET));
        assertFalse(target.toString().contains(SECRET));
        assertEquals("0", target.getAccessToken(), "the account object never holds the token");
        assertFalse(Files.readString(tempDir.resolve("lads_next_account.json")).contains(SECRET));
        assertFalse(Files.readString(ClientPaths.getAccountsFile().toPath()).contains(SECRET));
    }

    @Test
    public void microsoftToMicrosoftSwapNormalisesTypeAndUpdatesSession(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        Files.writeString(ClientPaths.getAccountsFile().toPath(),
            "[{\"username\":\"Notch\",\"uuid\":\"" + MS_UUID + "\",\"type\":\"Microsoft\",\"selected\":true},"
            + "{\"username\":\"jeb_\",\"uuid\":\"853c80ef-3c37-49fd-aa49-938b674adae6\",\"type\":\"Microsoft\",\"selected\":false}]");
        FakeGame game = new FakeGame("Notch", "069a79f4-44e9-4726-a5be-fca90e38aaf5");
        FakeLauncher launcher = new FakeLauncher("{\"token\":\"" + SECRET + "\"}");
        AccountSwitcherScreen screen = new AccountSwitcherScreen(game, launcher);
        assertEquals("Notch", screen.getSelectedAccount().getUsername());

        LadsAccount jeb = screen.getAccounts().get(1);
        screen.selectAccount(jeb);
        finish(screen);

        assertEquals("jeb_", game.name);
        assertEquals("853c80ef-3c37-49fd-aa49-938b674adae6", game.uuid);
        assertTrue(launcher.asked.get(0).contains("\"type\":\"microsoft\""), "request type must be lowercase microsoft");
        assertEquals(1, launcher.switched);
        assertEquals("jeb_", screen.getSelectedAccount().getUsername());
        String nextJson = Files.readString(tempDir.resolve("lads_next_account.json"));
        assertTrue(nextJson.contains("\"type\":\"microsoft\""));
        assertFalse(nextJson.contains(SECRET));
    }

    @Test
    public void aFailedSwitchLeavesEverythingAsItWas(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        record Case(String reply, boolean closed, String handOff, Exception prepareFails, RuntimeException commitFails, String targetUuid, String says) {}
        List<Case> cases = List.of(
            new Case("{\"error\":\"Sign in to Notch again in the launcher.\"}", false, null, null, null, MS_UUID, "Sign in to Notch again"),
            new Case("garbage", false, null, null, null, MS_UUID, "did not answer"),
            new Case("null", false, null, null, null, MS_UUID, "did not answer"),
            new Case("{\"error\":null}", false, null, null, null, MS_UUID, "did not answer"),
            new Case("", false, null, null, null, MS_UUID, "did not answer"),
            new Case("{}", false, null, null, null, MS_UUID, "did not answer"),
            new Case("", true, null, null, null, MS_UUID, "launcher that started this game is closed"),
            new Case("", false, "", null, null, MS_UUID, "Start the game from The Lads Launcher"),
            new Case("", false, "bad hand off line", null, null, MS_UUID, "Start the game from The Lads Launcher"),
            new Case("{\"token\":\"" + SECRET + "\"}", false, null, new IllegalStateException(SECRET), null, MS_UUID, "refused Notch"),
            new Case("{\"token\":\"" + SECRET + "\"}", false, null, null, new IllegalStateException(SECRET), MS_UUID, "refused Notch"),
            new Case("{\"token\":\"" + SECRET + "\"}", false, null, null, null, "not-a-valid-uuid", "refused Notch"));
        for (Case c : cases) {
            Files.deleteIfExists(tempDir.resolve("lads_next_account.json"));
            FakeGame game = FakeGame.offline("First");
            game.prepareFails = c.prepareFails();
            game.commitFails = c.commitFails();
            FakeLauncher launcher = new FakeLauncher(c.reply());
            launcher.closed = c.closed();
            if (c.handOff() != null) launcher.handOff = c.handOff();
            AccountSwitcherScreen screen = new AccountSwitcherScreen(game, launcher);
            LadsAccount before = screen.getSelectedAccount();
            LadsAccount target = new LadsAccount("Notch", c.targetUuid(), "microsoft", "0");
            screen.getAccounts().add(target);
            screen.selectAccount(target);
            finish(screen);

            assertEquals("First", game.name, c.says());
            assertEquals(0, game.commits, c.says());
            assertSame(before, screen.getSelectedAccount(), c.says());
            assertEquals(0, launcher.switched, c.says());
            assertFalse(Files.exists(tempDir.resolve("lads_next_account.json")), c.says());
            assertTrue(screen.getStatusMessage().contains(c.says()), screen.getStatusMessage());
            assertFalse(screen.getStatusMessage().contains(SECRET), c.says());
        }
    }

    @Test
    public void leavingAMicrosoftSessionForOfflineNeedsTheLaunchersWord(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        FakeGame game = new FakeGame("Notch", MS_UUID);
        FakeLauncher launcher = new FakeLauncher("{\"error\":\"Offline accounts are only available in debug builds.\"}");
        AccountSwitcherScreen screen = new AccountSwitcherScreen(game, launcher);

        screen.addOfflineAccount("Cracked");
        finish(screen);

        assertEquals("Notch", game.name);
        assertEquals(1, launcher.asked.size());
        assertTrue(screen.getStatusMessage().contains("debug builds"));
    }

    @Test
    public void inAWorldTheFirstClickOnlyAsks(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        FakeGame game = FakeGame.offline("First");
        game.world = true;
        AccountSwitcherScreen screen = new AccountSwitcherScreen(game, new FakeLauncher(""));

        screen.addOfflineAccount("Second");
        assertFalse(screen.isSwitching());
        assertTrue(screen.getStatusMessage().contains("leaves the world"));
        assertEquals("First", game.name);

        screen.addOfflineAccount("Second");
        assertTrue(screen.isSwitching());
        finish(screen);
        assertEquals("Second", game.name);
        assertFalse(game.world);
    }

    @Test
    public void cancelDropsTheSwitchAndAnotherClickWaitsItsTurn(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        FakeGame game = FakeGame.offline("First");
        AccountSwitcherScreen screen = new AccountSwitcherScreen(game, new FakeLauncher(""));

        screen.addOfflineAccount("Second");
        screen.addOfflineAccount("Third"); // ignored: one switch at a time
        screen.cancel();
        assertFalse(screen.isSwitching());
        Thread.sleep(50);
        screen.tick();
        assertEquals("First", game.name);
        assertEquals(0, game.commits);
    }

    @Test
    public void theLiveAccountShowsAsSelected(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        Files.writeString(ClientPaths.getAccountsFile().toPath(), "[{\"username\":\"First\",\"uuid\":\"" + LadsAccount.offlineUuid("First")
            + "\",\"type\":\"offline\",\"selected\":false},{\"username\":\"Notch\",\"uuid\":\"" + MS_UUID + "\",\"type\":\"microsoft\",\"selected\":true}]");
        FakeGame game = FakeGame.offline("First");
        AccountSwitcherScreen screen = new AccountSwitcherScreen(game, new FakeLauncher(""));
        assertEquals("First", screen.getSelectedAccount().getUsername());

        screen.selectAccount(screen.getSelectedAccount());
        assertFalse(screen.isSwitching(), "the account in use is not switched to");
        assertTrue(screen.getStatusMessage().contains("Already playing"));
    }

    @Test
    public void scrollingRevealsAccountsBeyondSixRows(@TempDir Path tempDir) {
        ClientPaths.setBaseDir(tempDir);
        AccountSwitcherScreen screen = new AccountSwitcherScreen();
        for (int i = 1; i <= 8; i++) {
            String name = "AltAcct" + i;
            screen.getAccounts().add(new LadsAccount(name, LadsAccount.offlineUuid(name).toString(), "offline", "0"));
        }
        assertEquals(9, screen.getAccounts().size()); // LadsPlayer + 8 alts
        for (int s = 0; s < 5; s++) {
            assertTrue(screen.mouseScrolled(400, 200, -1.0) || s >= 3);
        }
        // Bottom visible row (index 5 of 6 visible rows at scrollOffset 3 -> account index 8: AltAcct8)
        int cardX = (800 - 500) / 2;
        int fifthRowY = 50 + 32 + (5 * 36) + 10;
        assertTrue(screen.mouseClicked(cardX + 100, fifthRowY, 0));
        assertEquals("AltAcct8", screen.getSelectedAccount().getUsername());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void realWindowsNamedPipeExchangesLongMicrosoftTokenAndSwitchedNotice(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        String pipeName = "TheLadsClient-JavaPipeTest-" + UUID.randomUUID().toString().replace("-", "");
        String key = "0123456789abcdef0123456789abcdef";
        String longJwt = "eyJhbGciOiJIUzI1NiJ9." + "M".repeat(1500) + ".signature_ok";

        String psScript =
            "$ProgressPreference = 'SilentlyContinue'\n" +
            "$pipeName = '" + pipeName + "'\n" +
            "$jwt = '" + longJwt + "'\n" +
            "$pipes = @(0, 1) | ForEach-Object { [System.IO.Pipes.NamedPipeServerStream]::new($pipeName, [System.IO.Pipes.PipeDirection]::InOut, 4, [System.IO.Pipes.PipeTransmissionMode]::Byte, [System.IO.Pipes.PipeOptions]::Asynchronous -bor [System.IO.Pipes.PipeOptions]::CurrentUserOnly) }\n" +
            "try {\n" +
            "  [Console]::Out.WriteLine('READY'); [Console]::Out.Flush()\n" +
            "  for ($i = 0; $i -lt 2; $i++) {\n" +
            "    $srv = $pipes[$i]\n" +
            "    $srv.WaitForConnection()\n" +
            "    $sr = [System.IO.StreamReader]::new($srv, [System.Text.Encoding]::UTF8, $false, 1024, $true)\n" +
            "    $req = $sr.ReadLine()\n" +
            "    [Console]::Out.WriteLine('REQ:' + $req); [Console]::Out.Flush()\n" +
            "    $reply = if ($i -eq 0) { '{\"token\":\"' + $jwt + '\"}' + [char]10 } else { '{\"error\":\"\"}' + [char]10 }\n" +
            "    $bytes = [System.Text.Encoding]::UTF8.GetBytes($reply)\n" +
            "    $srv.Write($bytes, 0, $bytes.Length)\n" +
            "    $srv.Flush()\n" +
            "    $buf = New-Object byte[] 1\n" +
            "    try { [void]$srv.Read($buf, 0, 1) } catch {}\n" +
            "    $srv.Dispose()\n" +
            "  }\n" +
            "} finally { foreach ($p in $pipes) { try { $p.Dispose() } catch {} } }\n";

        String encoded = Base64.getEncoder().encodeToString(psScript.getBytes(StandardCharsets.UTF_16LE));
        Process server = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-InputFormat", "None", "-EncodedCommand", encoded)
            .start();
        server.getOutputStream().close();
        try (BufferedReader stdout = new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8))) {
            String ready = stdout.readLine();
            assertEquals("READY", ready);

            AccountTokens realTokens = new AccountTokens() {
                @Override
                protected String handOff() {
                    return "  " + pipeName + " " + key + "  ";
                }
            };

            FakeGame game = FakeGame.offline("First");
            AccountSwitcherScreen screen = new AccountSwitcherScreen(game, realTokens);
            LadsAccount msTarget = new LadsAccount("Notch", MS_UUID_2, "Microsoft", "0");
            screen.getAccounts().add(msTarget);

            screen.selectAccount(msTarget);
            finish(screen);

            assertEquals("Notch", game.name);
            assertEquals(List.of(longJwt), game.tokensSeen);

            String req1 = stdout.readLine();
            String req2 = stdout.readLine();
            assertTrue(server.waitFor(10, TimeUnit.SECONDS));
            assertNotNull(req1);
            assertTrue(req1.contains("\"op\":\"token\"") && req1.contains("\"type\":\"microsoft\""));
            assertNotNull(req2);
            assertTrue(req2.contains("\"op\":\"switched\""));
        } finally {
            server.destroyForcibly();
        }
    }
}
