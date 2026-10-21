package com.thelads.core.client.hypixel.quickplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Complete catalog of Hypixel /play modes and lobbies across all supported minigames.
 */
public final class QuickplayCatalog {
    private static final List<QuickplayMode> ALL_MODES = new ArrayList<>();
    private static final Map<String, QuickplayMode> BY_ID = new LinkedHashMap<>();
    private static final Map<QuickplayCategory, List<QuickplayMode>> BY_CATEGORY = new LinkedHashMap<>();
    private static final List<String> ALL_MODE_IDS = new ArrayList<>();

    static {
        // Bed Wars
        add("bedwars_eight_one", "Solo", "/play bedwars_eight_one", QuickplayCategory.BEDWARS, "bed");
        add("bedwars_eight_two", "Doubles", "/play bedwars_eight_two", QuickplayCategory.BEDWARS, "bed");
        add("bedwars_four_three", "3v3v3v3", "/play bedwars_four_three", QuickplayCategory.BEDWARS, "bed");
        add("bedwars_four_four", "4v4v4v4", "/play bedwars_four_four", QuickplayCategory.BEDWARS, "bed");
        add("bedwars_two_four", "4v4", "/play bedwars_two_four", QuickplayCategory.BEDWARS, "bed");
        add("bedwars_eight_one_rush", "Rush Solo", "/play bedwars_eight_one_rush", QuickplayCategory.BEDWARS, "feather");
        add("bedwars_eight_two_rush", "Rush Doubles", "/play bedwars_eight_two_rush", QuickplayCategory.BEDWARS, "feather");
        add("bedwars_four_four_rush", "Rush 4v4", "/play bedwars_four_four_rush", QuickplayCategory.BEDWARS, "feather");
        add("bedwars_eight_two_ultimate", "Ultimate Doubles", "/play bedwars_eight_two_ultimate", QuickplayCategory.BEDWARS, "golden_apple");
        add("bedwars_four_four_ultimate", "Ultimate 4v4", "/play bedwars_four_four_ultimate", QuickplayCategory.BEDWARS, "golden_apple");
        add("bedwars_eight_two_lucky", "Lucky Doubles", "/play bedwars_eight_two_lucky", QuickplayCategory.BEDWARS, "gold_block");
        add("bedwars_four_four_lucky", "Lucky 4v4", "/play bedwars_four_four_lucky", QuickplayCategory.BEDWARS, "gold_block");
        add("bedwars_eight_two_voidless", "Voidless Doubles", "/play bedwars_eight_two_voidless", QuickplayCategory.BEDWARS, "obsidian");
        add("bedwars_four_four_voidless", "Voidless 4v4", "/play bedwars_four_four_voidless", QuickplayCategory.BEDWARS, "obsidian");
        add("bedwars_eight_two_armed", "Armed Doubles", "/play bedwars_eight_two_armed", QuickplayCategory.BEDWARS, "bow");
        add("bedwars_four_four_armed", "Armed 4v4", "/play bedwars_four_four_armed", QuickplayCategory.BEDWARS, "bow");
        add("bedwars_eight_two_swap", "Swap Doubles", "/play bedwars_eight_two_swap", QuickplayCategory.BEDWARS, "ender_pearl");
        add("bedwars_four_four_swap", "Swap 4v4", "/play bedwars_four_four_swap", QuickplayCategory.BEDWARS, "ender_pearl");
        add("bedwars_eight_two_underworld", "Underworld Doubles", "/play bedwars_eight_two_underworld", QuickplayCategory.BEDWARS, "magma_cream");
        add("bedwars_four_four_underworld", "Underworld 4v4", "/play bedwars_four_four_underworld", QuickplayCategory.BEDWARS, "magma_cream");
        add("bedwars_castle", "Castle (40v40)", "/play bedwars_castle", QuickplayCategory.BEDWARS, "iron_sword");

        // SkyWars
        add("solo_normal", "Solo Normal", "/play solo_normal", QuickplayCategory.SKYWARS, "feather");
        add("solo_insane", "Solo Insane", "/play solo_insane", QuickplayCategory.SKYWARS, "feather");
        add("teams_normal", "Teams Normal", "/play teams_normal", QuickplayCategory.SKYWARS, "feather");
        add("teams_insane", "Teams Insane", "/play teams_insane", QuickplayCategory.SKYWARS, "feather");
        add("mega_normal", "Mega Normal", "/play mega_normal", QuickplayCategory.SKYWARS, "iron_sword");
        add("mega_doubles", "Mega Doubles", "/play mega_doubles", QuickplayCategory.SKYWARS, "iron_sword");
        add("ranked_normal", "Ranked Normal", "/play ranked_normal", QuickplayCategory.SKYWARS, "diamond_sword");
        add("sw_lucky", "Lucky Blocks", "/play sw_lucky", QuickplayCategory.SKYWARS, "gold_block");

        // Duels
        add("duels_classic_duel", "Classic 1v1", "/play duels_classic_duel", QuickplayCategory.DUELS, "iron_sword");
        add("duels_classic_doubles", "Classic 2v2", "/play duels_classic_doubles", QuickplayCategory.DUELS, "iron_sword");
        add("duels_sw_duel", "SkyWars 1v1", "/play duels_sw_duel", QuickplayCategory.DUELS, "feather");
        add("duels_sw_doubles", "SkyWars 2v2", "/play duels_sw_doubles", QuickplayCategory.DUELS, "feather");
        add("duels_bow_duel", "Bow 1v1", "/play duels_bow_duel", QuickplayCategory.DUELS, "bow");
        add("duels_bowspleef_duel", "Bow Spleef", "/play duels_bowspleef_duel", QuickplayCategory.DUELS, "tnt");
        add("duels_potion_duel", "NoDebuff 1v1", "/play duels_potion_duel", QuickplayCategory.DUELS, "potion");
        add("duels_combo_duel", "Combo 1v1", "/play duels_combo_duel", QuickplayCategory.DUELS, "raw_fish");
        add("duels_op_duel", "OP 1v1", "/play duels_op_duel", QuickplayCategory.DUELS, "diamond_chestplate");
        add("duels_op_doubles", "OP 2v2", "/play duels_op_doubles", QuickplayCategory.DUELS, "diamond_chestplate");
        add("duels_mw_duel", "Mega Walls 1v1", "/play duels_mw_duel", QuickplayCategory.DUELS, "iron_sword");
        add("duels_mw_doubles", "Mega Walls 2v2", "/play duels_mw_doubles", QuickplayCategory.DUELS, "iron_sword");
        add("duels_sumo_duel", "Sumo 1v1", "/play duels_sumo_duel", QuickplayCategory.DUELS, "slime_ball");
        add("duels_blitz_duel", "Blitz 1v1", "/play duels_blitz_duel", QuickplayCategory.DUELS, "stone_sword");
        add("duels_bridge_duel", "The Bridge 1v1", "/play duels_bridge_duel", QuickplayCategory.DUELS, "clay_ball");
        add("duels_bridge_doubles", "The Bridge 2v2", "/play duels_bridge_doubles", QuickplayCategory.DUELS, "clay_ball");
        add("duels_bridge_threes", "The Bridge 3v3", "/play duels_bridge_threes", QuickplayCategory.DUELS, "clay_ball");
        add("duels_bridge_four", "The Bridge 4v4", "/play duels_bridge_four", QuickplayCategory.DUELS, "clay_ball");
        add("duels_bridge_2v2v2v2", "The Bridge 2v2v2v2", "/play duels_bridge_2v2v2v2", QuickplayCategory.DUELS, "clay_ball");
        add("duels_bridge_3v3v3v3", "The Bridge 3v3v3v3", "/play duels_bridge_3v3v3v3", QuickplayCategory.DUELS, "clay_ball");
        add("duels_capture_duel", "The Bridge CTF", "/play duels_capture_duel", QuickplayCategory.DUELS, "banner");
        add("duels_boxing_duel", "Boxing 1v1", "/play duels_boxing_duel", QuickplayCategory.DUELS, "leather_chestplate");
        add("duels_parkour_eight", "Parkour", "/play duels_parkour_eight", QuickplayCategory.DUELS, "water_bucket");
        add("bedwars_two_one_duels", "Bed Wars 1v1", "/play bedwars_two_one_duels", QuickplayCategory.DUELS, "bed");
        add("bedwars_two_two_duels", "Bed Wars 2v2", "/play bedwars_two_two_duels", QuickplayCategory.DUELS, "bed");
        add("bedwars_four_four_duels", "Bed Wars 4v4", "/play bedwars_four_four_duels", QuickplayCategory.DUELS, "bed");
        add("duels_uhc_duel", "UHC 1v1", "/play duels_uhc_duel", QuickplayCategory.DUELS, "golden_apple");
        add("duels_uhc_doubles", "UHC 2v2", "/play duels_uhc_doubles", QuickplayCategory.DUELS, "golden_apple");
        add("duels_uhc_four", "UHC 4v4", "/play duels_uhc_four", QuickplayCategory.DUELS, "golden_apple");
        add("duels_uhc_meetup", "UHC Meetup", "/play duels_uhc_meetup", QuickplayCategory.DUELS, "golden_apple");

        // Murder Mystery
        add("murder_classic", "Classic", "/play murder_classic", QuickplayCategory.MURDER_MYSTERY, "iron_sword");
        add("murder_assassins", "Assassins", "/play murder_assassins", QuickplayCategory.MURDER_MYSTERY, "bow");
        add("murder_infection", "Infection", "/play murder_infection", QuickplayCategory.MURDER_MYSTERY, "rotten_flesh");
        add("murder_double_up", "Double Up", "/play murder_double_up", QuickplayCategory.MURDER_MYSTERY, "iron_sword");
        add("murder_hardcore", "Hardcore", "/play murder_hardcore", QuickplayCategory.MURDER_MYSTERY, "skull");

        // Build Battle
        add("build_battle_solo_normal", "Solo Normal", "/play build_battle_solo_normal", QuickplayCategory.BUILD_BATTLE, "painting");
        add("build_battle_teams_normal", "Teams Normal", "/play build_battle_teams_normal", QuickplayCategory.BUILD_BATTLE, "painting");
        add("build_battle_solo_pro", "Solo Pro", "/play build_battle_solo_pro", QuickplayCategory.BUILD_BATTLE, "painting");
        add("build_battle_guess_the_build", "Guess the Build", "/play build_battle_guess_the_build", QuickplayCategory.BUILD_BATTLE, "clock");
        add("build_battle_speed_builders", "Speed Builders", "/play build_battle_speed_builders", QuickplayCategory.BUILD_BATTLE, "brick");

        // Arcade Games
        add("arcade_farm_hunt", "Farm Hunt", "/play arcade_farm_hunt", QuickplayCategory.ARCADE, "wheat");
        add("arcade_party_games_1", "Party Games", "/play arcade_party_games_1", QuickplayCategory.ARCADE, "cake");
        add("arcade_hole_in_the_wall", "Hole in the Wall", "/play arcade_hole_in_the_wall", QuickplayCategory.ARCADE, "glass");
        add("arcade_hypixel_says", "Hypixel Says", "/play arcade_hypixel_says", QuickplayCategory.ARCADE, "paper");
        add("arcade_mini_walls", "Mini Walls", "/play arcade_mini_walls", QuickplayCategory.ARCADE, "wool");
        add("arcade_blocking_dead", "Blocking Dead", "/play arcade_blocking_dead", QuickplayCategory.ARCADE, "skull");
        add("arcade_dragon_wars", "Dragon Wars", "/play arcade_dragon_wars", QuickplayCategory.ARCADE, "dragon_egg");
        add("arcade_creeper_attack", "Creeper Attack", "/play arcade_creeper_attack", QuickplayCategory.ARCADE, "tnt");
        add("arcade_pixel_painters", "Pixel Painters", "/play arcade_pixel_painters", QuickplayCategory.ARCADE, "painting");
        add("arcade_bounty_hunters", "Bounty Hunters", "/play arcade_bounty_hunters", QuickplayCategory.ARCADE, "bow");
        add("arcade_soccer", "Football", "/play arcade_soccer", QuickplayCategory.ARCADE, "leather_boots");
        add("arcade_zombies_dead_end", "Zombies (Dead End)", "/play arcade_zombies_dead_end", QuickplayCategory.ARCADE, "iron_door");
        add("arcade_zombies_bad_blood", "Zombies (Bad Blood)", "/play arcade_zombies_bad_blood", QuickplayCategory.ARCADE, "iron_door");
        add("arcade_zombies_alien_arcadium", "Zombies (Alien)", "/play arcade_zombies_alien_arcadium", QuickplayCategory.ARCADE, "iron_door");
        add("arcade_hide_and_seek_prop_hunt", "Prop Hunt", "/play arcade_hide_and_seek_prop_hunt", QuickplayCategory.ARCADE, "hay_block");
        add("arcade_hide_and_seek_party_pooper", "Party Pooper", "/play arcade_hide_and_seek_party_pooper", QuickplayCategory.ARCADE, "coal");
        add("arcade_pixel_party", "Pixel Party", "/play arcade_pixel_party", QuickplayCategory.ARCADE, "note_block");
        add("arcade_ender_spleef", "Ender Spleef", "/play arcade_ender_spleef", QuickplayCategory.ARCADE, "diamond_shovel");

        // The TNT Games
        add("tnt_tntrun", "TNT Run", "/play tnt_tntrun", QuickplayCategory.TNT_GAMES, "tnt");
        add("tnt_pvprun", "PVP Run", "/play tnt_pvprun", QuickplayCategory.TNT_GAMES, "iron_sword");
        add("tnt_bowspleef", "Bow Spleef", "/play tnt_bowspleef", QuickplayCategory.TNT_GAMES, "bow");
        add("tnt_tntag", "TNT Tag", "/play tnt_tntag", QuickplayCategory.TNT_GAMES, "tnt");
        add("tnt_capture", "TNT Wizards", "/play tnt_capture", QuickplayCategory.TNT_GAMES, "blaze_rod");

        // Classic Games
        add("mw_standard", "Mega Walls Standard", "/play mw_standard", QuickplayCategory.CLASSIC_GAMES, "iron_sword");
        add("mw_face_off", "Mega Walls Face Off", "/play mw_face_off", QuickplayCategory.CLASSIC_GAMES, "iron_sword");
        add("blitz_solo_normal", "Blitz SG Solo", "/play blitz_solo_normal", QuickplayCategory.CLASSIC_GAMES, "golden_apple");
        add("blitz_teams_normal", "Blitz SG Teams", "/play blitz_teams_normal", QuickplayCategory.CLASSIC_GAMES, "golden_apple");
        add("blitz_solo_nokits", "Blitz SG No Kits", "/play blitz_solo_nokits", QuickplayCategory.CLASSIC_GAMES, "golden_apple");
        add("mcgo_normal", "Cops & Crims Defusal", "/play mcgo_normal", QuickplayCategory.CLASSIC_GAMES, "iron_ingot");
        add("mcgo_deathmatch", "Cops & Crims TDM", "/play mcgo_deathmatch", QuickplayCategory.CLASSIC_GAMES, "iron_ingot");
        add("mcgo_gungame", "Cops & Crims Gun Game", "/play mcgo_gungame", QuickplayCategory.CLASSIC_GAMES, "iron_ingot");
        add("paintball", "Paintball Warfare", "/play paintball", QuickplayCategory.CLASSIC_GAMES, "snowball");
        add("vampirez", "VampireZ", "/play vampirez", QuickplayCategory.CLASSIC_GAMES, "wither_skeleton_skull");
        add("arena", "Arena Brawl", "/play arena", QuickplayCategory.CLASSIC_GAMES, "blaze_powder");
        add("quake", "Quakecraft", "/play quake", QuickplayCategory.CLASSIC_GAMES, "firework_charge");

        // Other Games
        add("uhc_solo", "UHC Solo", "/play uhc_solo", QuickplayCategory.OTHER, "golden_apple");
        add("uhc_teams", "UHC Teams", "/play uhc_teams", QuickplayCategory.OTHER, "golden_apple");
        add("super_smash_solo_normal", "Smash Heroes Solo", "/play super_smash_solo_normal", QuickplayCategory.OTHER, "golden_sword");
        add("super_smash_1v1_normal", "Smash Heroes 1v1", "/play super_smash_1v1_normal", QuickplayCategory.OTHER, "golden_sword");
        add("super_smash_2v2_normal", "Smash Heroes 2v2", "/play super_smash_2v2_normal", QuickplayCategory.OTHER, "golden_sword");
        add("super_smash_teams_normal", "Smash Heroes Teams", "/play super_smash_teams_normal", QuickplayCategory.OTHER, "golden_sword");
        add("wool_wool_wars_two_four", "Wool Wars (4v4)", "/play wool_wool_wars_two_four", QuickplayCategory.OTHER, "wool");
        add("wool_sheep_wars_two_six", "Sheep Wars (6v6)", "/play wool_sheep_wars_two_six", QuickplayCategory.OTHER, "wool");
        add("wool_capture_the_wool_two_twenty", "Capture the Wool (20v20)", "/play wool_capture_the_wool_two_twenty", QuickplayCategory.OTHER, "wool");
        add("pit", "The Pit", "/play pit", QuickplayCategory.OTHER, "diamond_sword");
        add("sb", "SkyBlock", "/play sb", QuickplayCategory.OTHER, "grass");
        add("housing", "Housing", "/play housing", QuickplayCategory.OTHER, "door");

        // Lobbies
        add("lobby_main", "Main Lobby", "/lobby main", QuickplayCategory.LOBBIES, "compass");
        add("lobby_bedwars", "Bed Wars Lobby", "/lobby bedwars", QuickplayCategory.LOBBIES, "bed");
        add("lobby_skywars", "SkyWars Lobby", "/lobby skywars", QuickplayCategory.LOBBIES, "feather");
        add("lobby_duels", "Duels Lobby", "/lobby duels", QuickplayCategory.LOBBIES, "diamond_sword");
        add("lobby_arcade", "Arcade Lobby", "/lobby arcade", QuickplayCategory.LOBBIES, "slime_ball");
        add("lobby_tnt", "TNT Games Lobby", "/lobby tnt", QuickplayCategory.LOBBIES, "tnt");
        add("lobby_mw", "Mega Walls Lobby", "/lobby megawalls", QuickplayCategory.LOBBIES, "iron_sword");
        add("lobby_blitz", "Blitz SG Lobby", "/lobby blitz", QuickplayCategory.LOBBIES, "golden_apple");
        add("lobby_mcgo", "Cops & Crims Lobby", "/lobby mcgo", QuickplayCategory.LOBBIES, "iron_ingot");
        add("lobby_uhc", "UHC Lobby", "/lobby uhc", QuickplayCategory.LOBBIES, "golden_apple");
        add("lobby_smash", "Smash Heroes Lobby", "/lobby smash", QuickplayCategory.LOBBIES, "golden_sword");
        add("lobby_wool", "Wool Games Lobby", "/lobby wool", QuickplayCategory.LOBBIES, "wool");
        add("lobby_pit", "The Pit Lobby", "/lobby pit", QuickplayCategory.LOBBIES, "diamond_sword");
        add("lobby_sb", "SkyBlock Hub", "/lobby skyblock", QuickplayCategory.LOBBIES, "grass");
        add("lobby_housing", "Housing Lobby", "/lobby housing", QuickplayCategory.LOBBIES, "door");
        add("lobby_mm", "Murder Mystery Lobby", "/lobby murder", QuickplayCategory.LOBBIES, "iron_sword");
        add("lobby_bb", "Build Battle Lobby", "/lobby buildbattle", QuickplayCategory.LOBBIES, "painting");
        add("lobby_replay", "Replay Theater", "/lobby replay", QuickplayCategory.LOBBIES, "clock");
        add("limbo", "Limbo", "/limbo", QuickplayCategory.LOBBIES, "barrier");
    }

    private QuickplayCatalog() {}

    private static void add(String id, String name, String command, QuickplayCategory category, String iconKey) {
        QuickplayMode mode = new QuickplayMode(id, name, command, category, iconKey);
        ALL_MODES.add(mode);
        BY_ID.put(id.toLowerCase(Locale.ROOT), mode);
        BY_CATEGORY.computeIfAbsent(category, k -> new ArrayList<>()).add(mode);
        ALL_MODE_IDS.add(id);
    }

    public static List<QuickplayMode> getAllModes() {
        return Collections.unmodifiableList(ALL_MODES);
    }

    public static List<String> getAllModeIds() {
        return Collections.unmodifiableList(ALL_MODE_IDS);
    }

    public static List<QuickplayMode> getModes(QuickplayCategory category) {
        List<QuickplayMode> list = BY_CATEGORY.get(category);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public static QuickplayMode findById(String id) {
        if (id == null) return null;
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static List<QuickplayMode> search(String query) {
        if (query == null || query.trim().isEmpty()) return getAllModes();
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<QuickplayMode> results = new ArrayList<>();
        for (QuickplayMode mode : ALL_MODES) {
            if (mode.name().toLowerCase(Locale.ROOT).contains(q)
                || mode.id().toLowerCase(Locale.ROOT).contains(q)
                || mode.category().getDisplayName().toLowerCase(Locale.ROOT).contains(q)) {
                results.add(mode);
            }
        }
        return Collections.unmodifiableList(results);
    }
}
