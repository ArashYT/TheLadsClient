package com.thelads.core.client.hypixel.quickplay;

public enum QuickplayCategory {
    BEDWARS("Bed Wars", "bed"),
    SKYWARS("SkyWars", "feather"),
    DUELS("Duels", "diamond_sword"),
    ARCADE("Arcade Games", "slime_ball"),
    MURDER_MYSTERY("Murder Mystery", "iron_sword"),
    BUILD_BATTLE("Build Battle", "painting"),
    TNT_GAMES("The TNT Games", "tnt"),
    CLASSIC_GAMES("Classic Games", "golden_apple"),
    OTHER("Other Games", "nether_star"),
    LOBBIES("Lobbies", "compass");

    private final String displayName;
    private final String iconKey;

    QuickplayCategory(String displayName, String iconKey) {
        this.displayName = displayName;
        this.iconKey = iconKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconKey() {
        return iconKey;
    }
}
