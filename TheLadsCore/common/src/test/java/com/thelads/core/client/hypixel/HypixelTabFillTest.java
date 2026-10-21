package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HypixelTabFillTest {
    private final List<String> knownPlayers = Arrays.asList("Alice", "Bob", "Charlie", "David");

    @Test
    void testCommandCompletions() {
        List<String> pMatches = HypixelTabFill.complete("/p", knownPlayers);
        assertTrue(pMatches.contains("/party"));
        assertTrue(pMatches.contains("/p"));
        assertTrue(pMatches.contains("/play"));

        List<String> plaMatches = HypixelTabFill.complete("/pla", knownPlayers);
        assertEquals(List.of("/play"), plaMatches);

        List<String> frMatches = HypixelTabFill.complete("/fr", knownPlayers);
        assertEquals(List.of("/friend"), frMatches);

        List<String> lobbyMatches = HypixelTabFill.complete("/lo", knownPlayers);
        assertTrue(lobbyMatches.contains("/lobby"));
        assertTrue(lobbyMatches.contains("/locraw"));

        List<String> tellMatches = HypixelTabFill.complete("/wh", knownPlayers);
        assertTrue(tellMatches.contains("/whereami"));
        assertTrue(tellMatches.contains("/who"));
        assertTrue(tellMatches.contains("/whisper"));
    }

    @Test
    void testPlayModeCompletions() {
        List<String> bedwarsMatches = HypixelTabFill.complete("/play bed", knownPlayers);
        assertFalse(bedwarsMatches.isEmpty());
        assertTrue(bedwarsMatches.contains("bedwars_eight_one"));
        assertTrue(bedwarsMatches.contains("bedwars_eight_two"));
        assertTrue(bedwarsMatches.contains("bedwars_four_four"));

        List<String> soloMatches = HypixelTabFill.complete("/play solo_", knownPlayers);
        assertEquals(List.of("solo_insane", "solo_normal"), soloMatches);

        List<String> duelsMatches = HypixelTabFill.complete("/play duels_sw", knownPlayers);
        assertEquals(List.of("duels_sw_doubles", "duels_sw_duel"), duelsMatches);

        List<String> allModes = HypixelTabFill.complete("/play ", knownPlayers);
        assertTrue(allModes.size() > 50);

        // Subsequent space after mode argument returns empty
        List<String> extraSpace = HypixelTabFill.complete("/play solo_normal extra", knownPlayers);
        assertTrue(extraSpace.isEmpty());
    }

    @Test
    void testLobbyCompletions() {
        List<String> bedLobby = HypixelTabFill.complete("/lobby bed", knownPlayers);
        assertEquals(List.of("bedwars"), bedLobby);

        List<String> shortLobby = HypixelTabFill.complete("/l du", knownPlayers);
        assertEquals(List.of("duels"), shortLobby);

        List<String> skyLobbies = HypixelTabFill.complete("/hub sky", knownPlayers);
        assertEquals(List.of("skyblock", "skywars"), skyLobbies);
    }

    @Test
    void testDirectMessagePlayerCompletions() {
        List<String> msgMatches = HypixelTabFill.complete("/msg Bo", knownPlayers);
        assertEquals(List.of("Bob"), msgMatches);

        List<String> wMatches = HypixelTabFill.complete("/w al", knownPlayers);
        assertEquals(List.of("Alice"), wMatches);

        List<String> tellMatches = HypixelTabFill.complete("/tell C", knownPlayers);
        assertEquals(List.of("Charlie"), tellMatches);

        List<String> duelMatches = HypixelTabFill.complete("/duel D", knownPlayers);
        assertEquals(List.of("David"), duelMatches);

        List<String> reportMatches = HypixelTabFill.complete("/report A", knownPlayers);
        assertEquals(List.of("Alice"), reportMatches);

        // After player name and space (typing message), should return empty
        List<String> messageBody = HypixelTabFill.complete("/msg Bob hello there", knownPlayers);
        assertTrue(messageBody.isEmpty());

        // /r takes no player name
        List<String> rMatches = HypixelTabFill.complete("/r Bo", knownPlayers);
        assertTrue(rMatches.isEmpty());
    }

    @Test
    void testPartySubcommandsAndPlayers() {
        // Subcommand completion
        List<String> partyInv = HypixelTabFill.complete("/party in", knownPlayers);
        assertTrue(partyInv.contains("invite"));

        // Player completion on /party invite <name>
        List<String> partyInvitePlayer = HypixelTabFill.complete("/party invite Bo", knownPlayers);
        assertEquals(List.of("Bob"), partyInvitePlayer);

        // Player completion on /p kick <name>
        List<String> partyKickPlayer = HypixelTabFill.complete("/p kick Al", knownPlayers);
        assertEquals(List.of("Alice"), partyKickPlayer);

        // Direct /p <player> shorthand
        List<String> partyShort = HypixelTabFill.complete("/p Ch", knownPlayers);
        assertTrue(partyShort.contains("Charlie"));

        // Non-player subcommand returns empty for second argument
        List<String> warpArg = HypixelTabFill.complete("/party warp Bo", knownPlayers);
        assertTrue(warpArg.isEmpty());
    }

    @Test
    void testFriendSubcommandsAndPlayers() {
        // Subcommand completion
        List<String> friendAdd = HypixelTabFill.complete("/friend ad", knownPlayers);
        assertTrue(friendAdd.contains("add"));

        // Player completion on /friend add <name>
        List<String> friendAddPlayer = HypixelTabFill.complete("/friend add Bo", knownPlayers);
        assertEquals(List.of("Bob"), friendAddPlayer);

        // Player completion on /f remove <name>
        List<String> friendRemovePlayer = HypixelTabFill.complete("/f remove Da", knownPlayers);
        assertEquals(List.of("David"), friendRemovePlayer);

        // Direct /f <player> shorthand
        List<String> friendShort = HypixelTabFill.complete("/f Al", knownPlayers);
        assertTrue(friendShort.contains("Alice"));
    }

    @Test
    void testNonCommandChatReturnsEmpty() {
        assertTrue(HypixelTabFill.complete("hello world", knownPlayers).isEmpty());
        assertTrue(HypixelTabFill.complete("", knownPlayers).isEmpty());
        assertTrue(HypixelTabFill.complete(null, knownPlayers).isEmpty());
    }

    @Test
    void testCompletionRangeStart() {
        assertEquals(0, HypixelTabFill.getCompletionRangeStart("/p"));
        assertEquals(0, HypixelTabFill.getCompletionRangeStart("/play"));
        assertEquals(6, HypixelTabFill.getCompletionRangeStart("/play bed"));
        assertEquals(14, HypixelTabFill.getCompletionRangeStart("/party invite Bob"));
    }

    @Test
    void testMergeCompletions() {
        String[] server = new String[] {"/party", "/play"};
        List<String> client = Arrays.asList("/play", "/p", "/friend");

        String[] merged = HypixelTabFill.mergeCompletions(server, client);
        assertEquals(4, merged.length);
        assertEquals("/party", merged[0]);
        assertEquals("/play", merged[1]);
        assertEquals("/p", merged[2]);
        assertEquals("/friend", merged[3]);
    }
}
