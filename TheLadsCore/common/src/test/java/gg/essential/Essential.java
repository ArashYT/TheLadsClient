package gg.essential;

import java.util.Set;

/** Test stand-in for the Essential API WorldCheats reaches by reflection (names as in Essential 1.5.0.1). */
public final class Essential {
    public static final Essential INSTANCE = new Essential();
    public final WorldManager world = new WorldManager();
    public final Manager manager = new Manager();

    public static Essential getInstance() { return INSTANCE; }
    public Worlds getWorldsManager() { return new Worlds(); }
    public State getIntegratedServerManager() { return new State(manager); }

    public final class Worlds {
        public State getIntegratedServerWorld() { return new State(world); }
    }

    static final class State {
        private final Object value;
        State(Object value) { this.value = value; }
        public Object getUntracked() { return value; }
    }

    /** kotlin.jvm.functions.Function1's shape. */
    public interface Function1 { Object invoke(Object value); }

    public static final class Settings {
        final String gameMode, difficulty;
        final boolean locked, cheats;
        final Set<String> ops;
        Settings(String gameMode, String difficulty, boolean locked, boolean cheats, Set<String> ops) {
            this.gameMode = gameMode; this.difficulty = difficulty; this.locked = locked; this.cheats = cheats; this.ops = ops;
        }
        public String getGameMode() { return gameMode; }
        public String getDifficulty() { return difficulty; }
        public boolean getDifficultyLocked() { return locked; }
        public boolean getCheats() { return cheats; }
        public Set<String> getOps() { return ops; }
        public Settings copy(String gameMode, String difficulty, boolean locked, boolean cheats, Set<String> ops) {
            return new Settings(gameMode, difficulty, locked, cheats, ops);
        }
        public static Settings copy$default(Settings s, String a, String b, boolean c, boolean d, Set<String> e, int mask, Object marker) { return s; }
    }

    public static final class WorldManager {
        public Settings settings = new Settings("Creative", "Normal", true, false, Set.of("friend"));
        public State getGameSettings() { return new State(settings); }
        public void updateLocalGameSettings(Function1 update) { settings = (Settings) update.invoke(settings); }
    }

    public static final class Manager {
        public Boolean applied;
        public void setAppliedCheatsEnabled(Boolean cheats) { applied = cheats; }
    }
}
