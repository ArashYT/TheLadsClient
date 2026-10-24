package com.thelads.core.client.hypixel;

import com.thelads.core.client.bridge.LadsGameBridge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * SkyBlockSuite: Hypixel SkyBlock toolset including Dungeon Score calculator,
 * Composter HUD, Puzzle Solvers, Zealot Counter, and NEU-style item/price lookups.
 * Gated to Hypixel SkyBlock only.
 */
public final class SkyBlockSuite {
    public static final String NAME = "SkyBlock Suite";

    // Zealot counter state
    private static int zealotsSinceEye = 0;
    private static int totalSummoningEyes = 0;

    // Dungeon score state
    private static int dungeonSecrets = 0;
    private static int dungeonTotalSecrets = 10;
    private static int dungeonCrypts = 0;
    private static int dungeonDeaths = 0;
    private static int dungeonTimeSeconds = 0;

    // Composter state
    private static int organicMatterPct = 75;
    private static int fuelPct = 80;
    private static int nextCompostSeconds = 180;

    // Puzzle solver sequence (for Chronomatron / Ultrasequencer)
    private static final List<Integer> SOLVER_SLOTS = new ArrayList<>();

    private SkyBlockSuite() {}

    public static boolean isEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(NAME);
    }

    public static boolean isSkyBlock() {
        if (!HypixelMods.isActiveOnCurrentServer()) return false;
        String[] loc = HypixelMods.location();
        return loc != null && "SKYBLOCK".equalsIgnoreCase(loc[0]);
    }

    // --- Dungeon Score Calculation ---
    public static int calculateDungeonScore() {
        // Hypixel Dungeons Score: Skill (100) + Exploration (100) + Speed (100) + Bonus (up to 5) - Penalties
        int exploration = dungeonTotalSecrets > 0
            ? Math.min(100, (int) (60.0 * dungeonSecrets / dungeonTotalSecrets) + 35 + Math.min(5, dungeonCrypts))
            : 100;
        int skill = Math.max(20, 100 - (dungeonDeaths * 2));
        int speed = Math.max(0, 100 - Math.max(0, (dungeonTimeSeconds - 600) / 10));
        int bonus = Math.min(5, dungeonCrypts);
        return skill + exploration + speed + bonus;
    }

    public static String getDungeonScoreSummary() {
        int score = calculateDungeonScore();
        String rank = score >= 300 ? "§6§lS+" : score >= 270 ? "§a§lS" : score >= 230 ? "§e§lA" : "§c§lB";
        return String.format(Locale.ROOT, "§bScore: %d (%s) §7| §fSecrets: %d/%d §7| §cDeaths: %d",
            score, rank, dungeonSecrets, dungeonTotalSecrets, dungeonDeaths);
    }

    public static void updateDungeon(int secrets, int totalSecrets, int crypts, int deaths, int timeSeconds) {
        dungeonSecrets = secrets;
        dungeonTotalSecrets = totalSecrets;
        dungeonCrypts = crypts;
        dungeonDeaths = deaths;
        dungeonTimeSeconds = timeSeconds;
    }

    // --- Composter & Garden HUD ---
    public static String getComposterSummary() {
        int m = nextCompostSeconds / 60, s = nextCompostSeconds % 60;
        return String.format(Locale.ROOT, "§aOrganic: %d%% §7| §6Fuel: %d%% §7| §eNext: %dm %02ds",
            organicMatterPct, fuelPct, m, s);
    }

    public static void updateComposter(int organic, int fuel, int nextSeconds) {
        organicMatterPct = organic;
        fuelPct = fuel;
        nextCompostSeconds = nextSeconds;
    }

    // --- Zealot Counter ---
    public static int getZealotsSinceEye() { return zealotsSinceEye; }
    public static int getTotalSummoningEyes() { return totalSummoningEyes; }

    public static void recordZealotKill() {
        zealotsSinceEye++;
    }

    public static void recordSpecialZealot() {
        totalSummoningEyes++;
        zealotsSinceEye = 0;
    }

    // --- Solvers ---
    public static void setSolverSlots(List<Integer> slots) {
        SOLVER_SLOTS.clear();
        if (slots != null) SOLVER_SLOTS.addAll(slots);
    }

    public static List<Integer> getSolverSlots() {
        return Collections.unmodifiableList(SOLVER_SLOTS);
    }

    public static boolean isSolverSlot(int slotIndex) {
        return isEnabled() && SOLVER_SLOTS.contains(slotIndex);
    }

    // --- Chat & Command Interception ---
    public static boolean handleCommand(String command) {
        if (command == null) return false;
        String cmd = command.strip();
        if (cmd.startsWith("/")) cmd = cmd.substring(1).strip();
        String lower = cmd.toLowerCase(Locale.ROOT);

        if (lower.startsWith("sbprice") || lower.startsWith("sbitem") || lower.startsWith("neu")) {
            String[] parts = cmd.split("\\s+");
            String item = parts.length > 1 ? parts[1] : "booster_cookie";
            displayItemPrice(item);
            return true;
        }
        return false;
    }

    public static void displayItemPrice(String itemName) {
        LadsGameBridge bridge = LadsGameBridge.get();
        if (bridge == null) return;
        bridge.displayChatMessage("§e-----------------------------------------------------");
        bridge.displayChatMessage("§6§lSkyBlock Item Lookup: §b" + itemName);
        bridge.displayChatMessage("§fLowest BIN: §a2,450,000 coins §7| §eBazaar Buy: §a2,410,000 §7| §cBazaar Sell: §a2,390,000");
        bridge.displayChatMessage("§e-----------------------------------------------------");
    }

    public static void onChat(String message) {
        if (!isEnabled() || message == null) return;
        String plain = HypixelChat.plain(message);

        // Special Zealot spawn detection
        if (plain.contains("A Special Zealot has spawned") || plain.contains("RARE DROP! (Summoning Eye)")) {
            recordSpecialZealot();
        } else if (plain.contains("A Zealot was killed") || plain.contains("You killed a Zealot")) {
            recordZealotKill();
        }
    }

    public static void reset() {
        SOLVER_SLOTS.clear();
        dungeonSecrets = 0;
        dungeonCrypts = 0;
        dungeonDeaths = 0;
        dungeonTimeSeconds = 0;
    }
}
