package com.thelads.core.client.social;

import com.thelads.core.client.qa.QaProgress;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, inert unless the Verification harness sets LADS_VERIFY_P2P=host|guest (with LADS_P2P_PEER = the other game's offline name
 * and LADS_SOCIAL_URL = a local dev social backend): the friends + P2P hosting scenario between two QA games, driven each client tick
 * by the version. Host: befriend the guest, open the world to Friends (approval: ask), accept the join request, see the guest in the
 * world, chat both ways, kick the guest, stop hosting. Guest: accept the friend request, see the host as joinable, ask to join, connect
 * through the P2P proxy, chat both ways, get kicked. Photographs each screen; logs "Lads p2p QA PASS i: ..." and
 * "Lads p2p QA END: n passed, m failed". Product behaviour is unchanged: everything here goes through the public SocialState API.
 */
public final class P2PQa {
    /** What the scenario needs from a game version. */
    public interface Game {
        /** In a world (singleplayer or a server), alive, no screen open. */
        boolean inWorld();
        /** Host: players on the integrated server; guest: players in the tab list. */
        int players();
        void chat(String text);
        /** A chat line containing this text was received since the game started. */
        boolean received(String text);
        void screenshot(String name) throws Exception;
        /** Host, QA only: the integrated server stops asking Mojang (both QA accounts are offline ones). */
        void offlineServer();
        /** The text of the disconnected screen, or null while none is shown. */
        String disconnected();
        void closeScreen();
    }

    public static final String ROLE = System.getenv("LADS_VERIFY_P2P");
    public static final boolean ACTIVE = "host".equals(ROLE) || "guest".equals(ROLE);
    private static final String PEER = System.getenv("LADS_P2P_PEER");
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final long STEP_TIMEOUT_MS = Long.getLong("lads.p2pQa.stepTimeoutMs", 180_000L);
    private static final String HOST_HELLO = "lads p2p hello from host", GUEST_HELLO = "lads p2p hello from guest";
    private static final String[] HOST_STEPS = {"online", "friends", "friends screen", "hosting", "host screen", "join request",
        "guest in world", "chat", "kick", "stop hosting"};
    private static final String[] GUEST_STEPS = {"online", "friend request", "joinable", "connecting", "in host's world", "chat", "kicked"};

    private static int step, passed, failed, wait, resend;
    private static long stepSince = -1;
    private static boolean finished, sent, leaving;
    private static UUID peer;

    private P2PQa() {}

    /** One client tick; true once the scenario ended (passed or failed). */
    public static boolean tick(Game game) {
        if (!ACTIVE || finished) return finished;
        if (stepSince < 0) {
            stepSince = System.currentTimeMillis();
            QaProgress.begin("p2p " + ROLE, ("host".equals(ROLE) ? HOST_STEPS : GUEST_STEPS).length);
            LOGGER.info("Lads p2p QA BEGIN: {} with peer {} via {}", ROLE, PEER, System.getenv("LADS_SOCIAL_URL"));
        }
        if (wait > 0) { wait--; return false; }
        String[] steps = "host".equals(ROLE) ? HOST_STEPS : GUEST_STEPS;
        try {
            boolean done = "host".equals(ROLE) ? host(game) : guest(game);
            if (done) next(steps);
            else if (System.currentTimeMillis() - stepSince > STEP_TIMEOUT_MS)
                throw new IllegalStateException("timed out after " + STEP_TIMEOUT_MS / 1000 + " s: " + state(game));
        } catch (Exception e) {
            failed++;
            QaProgress.result(QaProgress.Result.FAIL);
            LOGGER.error("Lads p2p QA FAILED at step {} ({}): {}", step + 1, steps[step], e.toString());
            end();
        }
        return finished;
    }

    private static void next(String[] steps) {
        passed++;
        LOGGER.info("Lads p2p QA PASS {}: {} {} ({})", passed, ROLE, steps[step], state(null));
        QaProgress.step(steps[step]);
        step++;
        sent = false;
        stepSince = System.currentTimeMillis();
        wait = 10;
        if (step >= steps.length) end();
    }

    private static void end() {
        finished = true;
        QaProgress.done();
        LOGGER.info("Lads p2p QA END: {} passed, {} failed", passed, failed);
    }

    private static String state(Game game) {
        SocialState s = SocialState.get();
        return "social " + s.connectionStatus() + ", friends " + s.friends().size() + ", incoming " + s.incoming().size()
            + ", hosting " + s.hostSession().isHosting() + ", guests " + s.hostSession().activeGuestCount() + ", guest session "
            + s.isGuestSessionActive() + ", connecting " + s.connectingTo() + (game == null ? "" : ", players " + game.players()
            + ", in world " + game.inWorld() + ", disconnected " + game.disconnected());
    }

    private static FriendEntry friend(List<FriendEntry> list) {
        for (FriendEntry f : list) if (f.name() != null && f.name().equalsIgnoreCase(PEER)) return f;
        return null;
    }

    private static boolean host(Game game) throws Exception {
        SocialState s = SocialState.get();
        switch (step) {
            case 0 -> { return game.inWorld() && "Connected".equals(s.connectionStatus()); }
            case 1 -> { // befriend the guest: our request (again every 10 s until the guest is online to be found), the guest accepts it
                if (!sent || ++resend % 200 == 0) { s.addFriend(PEER); sent = true; }
                FriendEntry f = friend(s.friends());
                if (f == null) return false;
                peer = f.id();
                return true;
            }
            case 2 -> { return shot(game, "p2p-host-friends", () -> SocialBridge.get().openFriendsScreen()); }
            case 3 -> {
                if (!sent) {
                    game.offlineServer();
                    HostOptions o = new HostOptions();
                    o.scope = HostOptions.Scope.FRIENDS;
                    o.approval = HostOptions.Approval.ASK; // a join request toast, answered below
                    if ("1".equals(System.getenv("LADS_P2P_NO_BACKUP"))) o.backupOnOpen = false; // QA: isolates the backup step
                    s.startHosting(o);
                    sent = true;
                }
                return s.hostSession().isHosting();
            }
            case 4 -> { return shot(game, "p2p-host-screen", () -> SocialBridge.get().openHostScreen()); }
            case 5 -> {
                List<SocialState.JoinRequest> requests = s.joinRequests();
                if (requests.isEmpty()) return false;
                if (!sent) { // the toast is photographed before the answer takes it away (26.3 shoots the next frame)
                    game.screenshot("p2p-host-join-request-toast");
                    sent = true;
                    return false;
                }
                s.answerJoin(requests.get(0).reqId(), true);
                return true;
            }
            case 6 -> {
                if (s.hostSession().activeGuestCount() < 1 || game.players() < 2) return false;
                game.screenshot("p2p-host-guest-in-world");
                return true;
            }
            case 7 -> {
                if (!sent) { game.chat(HOST_HELLO); sent = true; }
                return game.received(GUEST_HELLO);
            }
            case 8 -> {
                if (!sent) { s.guestAction(peer, SocialBridge.GuestAction.KICK); sent = true; }
                return s.hostSession().activeGuestCount() == 0 && game.players() == 1;
            }
            case 9 -> {
                if (!sent) { s.stopHosting(); sent = true; }
                return !s.hostSession().isHosting();
            }
            default -> { return true; }
        }
    }

    private static boolean guest(Game game) throws Exception {
        SocialState s = SocialState.get();
        switch (step) {
            case 0 -> { return "Connected".equals(s.connectionStatus()); }
            case 1 -> {
                FriendEntry in = friend(s.incoming());
                if (in != null && !sent) { s.accept(in.id()); sent = true; }
                FriendEntry f = friend(s.friends());
                if (f == null) return false;
                peer = f.id();
                return true;
            }
            case 2 -> {
                FriendEntry f = friend(s.friends());
                if (f == null || !s.canJoin(f).ok()) return false;
                return shot(game, "p2p-guest-friends-joinable", () -> SocialBridge.get().openFriendsScreen());
            }
            case 3 -> {
                // LADS_P2P_LEAVE_FIRST=1 (QA): leave our own world before asking, which sidesteps the host's first signal being dropped
                // while SocialState leaves the world after p2p_start (found by this QA; the default path keeps testing it).
                if ("1".equals(System.getenv("LADS_P2P_LEAVE_FIRST")) && SocialBridge.get().inWorldOrServer()) {
                    if (!leaving) { leaving = true; SocialBridge.get().leaveWorld(() -> { }); }
                    return false;
                }
                if (!sent) { s.requestJoin(peer); sent = true; }
                if (s.connectingTo() == null && !s.isGuestSessionActive()) return false;
                game.screenshot("p2p-guest-connecting");
                return true;
            }
            case 4 -> {
                if (!game.inWorld() || !s.isGuestSessionActive()) return false;
                game.screenshot("p2p-guest-in-host-world");
                return true;
            }
            case 5 -> {
                if (!game.received(HOST_HELLO)) return false;
                if (!sent) { game.chat(GUEST_HELLO); sent = true; }
                return true;
            }
            case 6 -> {
                String why = game.disconnected();
                if (why == null) return false;
                LOGGER.info("Lads p2p QA: guest disconnected: {}", why.replace('\n', ' '));
                game.screenshot("p2p-guest-kicked");
                return true;
            }
            default -> { return true; }
        }
    }

    private static int shotTicks;

    /** Opens a screen, photographs it a second later, closes it on the next tick (26.3 takes the shot from the next frame). */
    private static boolean shot(Game game, String name, Runnable open) throws Exception {
        if (shotTicks == 0) open.run();
        if (++shotTicks < 20) return false;
        if (shotTicks == 20) {
            game.screenshot(name);
            return false;
        }
        shotTicks = 0;
        game.closeScreen();
        return true;
    }

    /** True when the scenario ended with a failed step (1.8.9's self-test turns it into its own failure). */
    public static boolean failedAny() { return failed > 0; }

    /** Lower-case role for file names. */
    public static String role() { return ROLE == null ? "" : ROLE.toLowerCase(Locale.ROOT); }
}
