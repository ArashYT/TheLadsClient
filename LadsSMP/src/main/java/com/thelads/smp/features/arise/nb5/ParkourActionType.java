package com.thelads.smp.features.arise.nb5;

/**
 * Enumeration of distinct parkour movement actions supported by NB5.
 */
public enum ParkourActionType {
    NONE(0),
    VAULT(1),
    WALL_JUMP(2),
    LEDGE_HANG(3),
    LEDGE_CLIMB(4),
    SLIDE(5),
    CRAWL(6),
    DODGE(7),
    ROLL(8),
    BREAKFALL(9);

    private final int id;

    ParkourActionType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static ParkourActionType byId(int id) {
        for (ParkourActionType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return NONE;
    }
}
