package com.thelads.core;

import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.client.auth.AccountTokens;
import com.thelads.core.client.auth.LadsAccount;
import com.thelads.core.client.auth.LiveSession;
import com.thelads.core.client.util.ClientPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    /** The game: a session that changes only when the prepared step runs. */
    private static class FakeGame implements LiveSession {
        String name, uuid;
        boolean world;
        Exception prepareFails;
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
            tokensSeen.add(accessToken);
            if (prepareFails != null) throw prepareFails;
            return () -> {
                commits++;
                world = false;
                name = target.getUsername();
                uuid = target.sessionUuid().toString();
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
    public void aFailedSwitchLeavesEverythingAsItWas(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        record Case(String reply, boolean closed, String handOff, Exception prepareFails, String says) {}
        List<Case> cases = List.of(
            new Case("{\"error\":\"Sign in to Notch again in the launcher.\"}", false, null, null, "Sign in to Notch again"),
            new Case("garbage", false, null, null, "did not answer"),
            new Case("", false, null, null, "did not answer"),
            new Case("{}", false, null, null, "did not answer"),
            new Case("", true, null, null, "launcher that started this game is closed"),
            new Case("", false, "", null, "Start the game from The Lads Launcher"),
            new Case("", false, "bad hand off line", null, "Start the game from The Lads Launcher"),
            new Case("{\"token\":\"" + SECRET + "\"}", false, null, new IllegalStateException(SECRET), "refused Notch"));
        for (Case c : cases) {
            Files.deleteIfExists(tempDir.resolve("lads_next_account.json"));
            FakeGame game = FakeGame.offline("First");
            game.prepareFails = c.prepareFails();
            FakeLauncher launcher = new FakeLauncher(c.reply());
            launcher.closed = c.closed();
            if (c.handOff() != null) launcher.handOff = c.handOff();
            AccountSwitcherScreen screen = new AccountSwitcherScreen(game, launcher);
            LadsAccount before = screen.getSelectedAccount();
            screen.selectAccount(microsoft(screen));
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
}
