package com.thelads.core.client.hypixel;

import com.thelads.core.client.bridge.LadsGameBridge;
import java.util.Locale;

/**
 * Tracks session statistics across Hypixel minigames without requiring an API key.
 * Feeds on chat lines parsed by HypixelChat.
 */
public final class SessionTracker {
    private static int games;
    private static int wins;
    private static int losses;
    private static int kills;
    private static int finalKills;
    private static int deaths;
    private static int bedsBroken;
    private static int currentStreak;
    private static int bestStreak;

    private static boolean gameInProgress;
    private static boolean recordedResult;
    private static boolean wonThisGame;
    private static boolean selfEliminated;

    private SessionTracker() {}

    public static synchronized void reset() {
        games = 0;
        wins = 0;
        losses = 0;
        kills = 0;
        finalKills = 0;
        deaths = 0;
        bedsBroken = 0;
        currentStreak = 0;
        bestStreak = 0;
        gameInProgress = false;
        recordedResult = false;
        wonThisGame = false;
        selfEliminated = false;
    }

    public static synchronized void onGameStart() {
        gameInProgress = true;
        recordedResult = false;
        wonThisGame = false;
        selfEliminated = false;
    }

    public static synchronized void onKill(boolean finalKill) {
        kills++;
        if (finalKill) {
            finalKills++;
        }
    }

    public static synchronized void onDeath() {
        deaths++;
    }

    public static synchronized void onBedBroken() {
        bedsBroken++;
    }

    public static synchronized void onSelfEliminated() {
        selfEliminated = true;
    }

    public static synchronized void onVictory() {
        wonThisGame = true;
        recordGameEnd(true);
    }

    public static synchronized void onGameEnd() {
        if (!recordedResult) {
            recordGameEnd(wonThisGame);
        }
    }

    public static synchronized void onGameLeft() {
        if (gameInProgress && !recordedResult) {
            // Leaving mid-game after being eliminated counts as a loss
            recordGameEnd(false);
        }
        gameInProgress = false;
    }

    private static void recordGameEnd(boolean won) {
        if (recordedResult) return;
        recordedResult = true;
        gameInProgress = false;
        games++;
        if (won) {
            wins++;
            currentStreak++;
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak;
            }
        } else {
            losses++;
            currentStreak = 0;
        }
    }

    /**
     * Processes server chat messages to detect game events, kills, beds, and outcomes.
     */
    public static synchronized void onServerChat(String message, String myName) {
        if (message == null || message.isEmpty()) return;

        if (HypixelChat.gameStart(message)) {
            onGameStart();
            return;
        }

        if (HypixelChat.isBedBroken(message, myName)) {
            onBedBroken();
            return;
        }

        HypixelChat.KillEvent kill = HypixelChat.parseKill(message);
        if (kill != null) {
            if (myName != null && !myName.isEmpty() && myName.equalsIgnoreCase(kill.killer())) {
                onKill(kill.finalKill());
            }
            if (myName != null && !myName.isEmpty() && myName.equalsIgnoreCase(kill.victim())) {
                onDeath();
                if (kill.finalKill()) {
                    onSelfEliminated();
                }
            }
            return;
        }

        if (myName != null && !myName.isEmpty() && HypixelChat.isSelfDeath(message, myName)) {
            onDeath();
            return;
        }

        if (HypixelChat.isVictory(message, myName)) {
            onVictory();
            return;
        }

        if (HypixelChat.gameEnd(message)) {
            onGameEnd();
            return;
        }

        if (HypixelChat.newGame(message)) {
            onGameLeft();
        }
    }

    public static synchronized int getGames() { return games; }
    public static synchronized int getWins() { return wins; }
    public static synchronized int getLosses() { return losses; }
    public static synchronized int getKills() { return kills; }
    public static synchronized int getFinalKills() { return finalKills; }
    public static synchronized int getDeaths() { return deaths; }
    public static synchronized int getBedsBroken() { return bedsBroken; }
    public static synchronized int getCurrentStreak() { return currentStreak; }
    public static synchronized int getBestStreak() { return bestStreak; }
    public static synchronized boolean isGameInProgress() { return gameInProgress; }

    public static synchronized double getWinLossRatio() {
        return losses == 0 ? wins : (double) wins / losses;
    }

    public static synchronized double getKillDeathRatio() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    public static synchronized String formatRatio(double ratio, int denominator) {
        if (denominator == 0) {
            return String.format(Locale.ROOT, "%.2f", ratio);
        }
        return String.format(Locale.ROOT, "%.2f", ratio);
    }

    /**
     * Formats the summary text for the in-game HUD element.
     */
    public static synchronized String formatHudText() {
        String wlStr = losses == 0 ? (wins > 0 ? wins + ".00" : "0.00") : String.format(Locale.ROOT, "%.2f", (double) wins / losses);
        String kdStr = deaths == 0 ? (kills > 0 ? kills + ".00" : "0.00") : String.format(Locale.ROOT, "%.2f", (double) kills / deaths);
        StringBuilder sb = new StringBuilder(128);
        sb.append("§eSession Stats§r\n");
        sb.append("Games: ").append(games).append(" (").append(wins).append("W - ").append(losses).append("L)\n");
        sb.append("Streak: ").append(currentStreak).append(" (Best: ").append(bestStreak).append(")\n");
        sb.append("K/D: ").append(kdStr).append(" (").append(kills).append("/").append(deaths).append(")");
        if (finalKills > 0 || bedsBroken > 0) {
            sb.append("\nFinals: ").append(finalKills);
            if (bedsBroken > 0) {
                sb.append(" | Beds: ").append(bedsBroken);
            }
        }
        return sb.toString();
    }

    /**
     * Prints the full color-formatted session summary locally to chat.
     */
    public static synchronized void printChatSummary() {
        String wlStr = losses == 0 ? (wins > 0 ? wins + ".00" : "0.00") : String.format(Locale.ROOT, "%.2f", (double) wins / losses);
        String kdStr = deaths == 0 ? (kills > 0 ? kills + ".00" : "0.00") : String.format(Locale.ROOT, "%.2f", (double) kills / deaths);
        LadsGameBridge bridge = LadsGameBridge.get();
        if (bridge == null) return;
        bridge.displayChatMessage("§6§m---------------------------------------------");
        bridge.displayChatMessage("§e§lSession Statistics (The Lads Client)");
        bridge.displayChatMessage("§7Games Played: §f" + games + " §7(§a" + wins + "W §7- §c" + losses + "L§7)");
        bridge.displayChatMessage("§7Win Streak: §e" + currentStreak + " §7(Best: §6" + bestStreak + "§7)");
        bridge.displayChatMessage("§7Kills: §a" + kills + " §7(Final Kills: §a" + finalKills + "§7)");
        bridge.displayChatMessage("§7Deaths: §c" + deaths);
        bridge.displayChatMessage("§7K/D Ratio: §e" + kdStr + " §7| W/L Ratio: §e" + wlStr);
        if (bedsBroken > 0) {
            bridge.displayChatMessage("§7Beds Broken: §c" + bedsBroken);
        }
        bridge.displayChatMessage("§6§m---------------------------------------------");
    }
}
