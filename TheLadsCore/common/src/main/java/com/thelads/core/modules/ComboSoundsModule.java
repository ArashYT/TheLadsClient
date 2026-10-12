package com.thelads.core.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.sound.CustomSounds;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.IntTextOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.SoundListOption;
import com.thelads.core.config.SoundOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sound for each hit you land in a row without taking damage. A hit lands when the server hurts the entity you just attacked
 * (its hurt status on 1.8.9, its damage event on 1.20.1/26.x), so swings and hits into invulnerability don't count. The combo
 * ends when you take damage or after Combo Reset seconds without a hit. Hits past Combo 5 play the After Combo 5 sounds in their
 * order (else Combo 5 again). Sound Sets keep named Combo 1-5 choices to swap in at once.
 * <p>
 * Bow sounds: a long shot (your arrow hits from Long Shot Distance or further) and a headshot (it lands in the head, as Kill
 * Banner judges a head hit). Each version reports where its tracking saw your arrow cross an entity, the hurts, and the server's
 * arrow-hit-player event; a player hit is the event (its victim the player hurt just before it), a mob hit (Bow Sounds on Mobs)
 * the mob's hurt paired with your arrow's crossing.
 */
public class ComboSoundsModule extends Module {
    public static final String NAME = "Combo Sounds";
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** The server's hurt answers your attack within this (ping included); a hurt later is someone else's hit. */
    static final long CONFIRM_NANOS = 1_000_000_000L;
    /** An arrow's crossing and the server's word on that hit pair up within this. */
    static final long ARROW_NANOS = 1_000_000_000L;
    /** The arrow-hit-player event follows its victim's hurt in the same server tick: a hurt this recent names the victim. */
    static final long HURT_NANOS = 250_000_000L;
    public static final int AFTER_RANDOM = 0, AFTER_IN_ORDER = 1, AFTER_SHUFFLE = 2;
    /** Bow sounds to play (a bit set). */
    public static final int LONG_SHOT = 1, HEADSHOT = 2;
    /** The built-in set: no files, the pling rising each hit. */
    public static final SoundSet DEFAULT_PLING = new SoundSet("Default pling", List.of("", "", "", "", ""), List.of(), AFTER_RANDOM);

    public final BoolOption players = new BoolOption("Players", true);
    public final BoolOption mobs = new BoolOption("Mobs", false);
    public final SliderOption volume = new SliderOption("Volume", .6, 0, 1, .05);
    public final IntTextOption reset = new IntTextOption("Combo Reset (seconds)", 2, 1, 30);
    /** Saved Combo 1-5 choices (the settings draw them with Default pling first). Reset options keeps them. */
    public final SoundSets sets = new SoundSets("Sound Sets");
    /** Combo 1 to 5. Empty: the built-in pling, higher each step. */
    public final SoundOption[] steps = new SoundOption[5];
    /** Hits past Combo 5 play these (in After Combo 5 Order); empty: Combo 5 again. */
    public final SoundListOption after = new SoundListOption("After Combo 5");
    public final DropdownOption afterOrder = new DropdownOption("After Combo 5 Order", AFTER_RANDOM, "Random", "In Order", "Shuffle");
    public final BoolOption longShots = new BoolOption("Long Shots", true);
    public final SliderOption longShotDistance = new SliderOption("Long Shot Distance (blocks)", 50, 10, 120, 1).editable();
    /** Empty: the built-in level-up chime. */
    public final SoundOption longShotSound = new SoundOption("Long Shot Sound");
    public final BoolOption headshots = new BoolOption("Bow Headshots", true);
    /** Empty: the built-in helmet clink (a high anvil). */
    public final SoundOption headshotSound = new SoundOption("Headshot Sound");
    public final BoolOption bowMobs = new BoolOption("Bow Sounds on Mobs", false);
    /** The sound library's starred files (every version's library; not a setting, so Reset options keeps them). */
    public final SoundListOption favourites = new SoundListOption("Favourite Sounds") {
        @Override public void reset() {}
    };

    private int target = -1, combo;
    private long attackedAt, lastHit;
    private final Random random = new Random();
    private int afterLast = -1;
    private final List<Integer> bag = new ArrayList<>();

    private record Crossing(boolean player, boolean head, double distance, long at) {}
    private final Map<Integer, Crossing> crossings = new HashMap<>();
    private int hurtId = -1, waitId = -1;
    private boolean hurtPlayer, waitHeadOnly;
    private double hurtDistance, waitDistance, shotDistance;
    private long hurtAt, waitAt;

    public ComboSoundsModule() {
        super(NAME, "A sound for every hit you land in a row without taking damage, rising with the combo, and for long bow "
            + "shots and headshots. Pick your own MP3 or WAV sounds, trimmed to the part you want.");
        addOption(players);
        addOption(mobs);
        addOption(volume);
        addOption(reset);
        addOption(sets);
        for (int i = 0; i < steps.length; i++) steps[i] = addOption(new SoundOption("Combo " + (i + 1)));
        addOption(after);
        addOption(afterOrder);
        addOption(longShots);
        addOption(longShotDistance);
        addOption(longShotSound);
        addOption(headshots);
        addOption(headshotSound);
        addOption(bowMobs);
        addOption(favourites);
    }

    /** Options the settings do not list (the library keeps the favourites). */
    public boolean hidden(Option option) {
        return option == favourites;
    }

    /** Your attack on a living entity (a player or not). */
    public void attacked(int entityId, boolean player, long now) {
        if (player ? players.get() : mobs.get()) {
            target = entityId;
            attackedAt = now;
        }
    }

    /** An entity was hurt ({@code self}: you, which ends the combo). Returns the combo step your landed hit makes, else 0. */
    public int hurt(int entityId, boolean self, long now) {
        if (self) {
            combo = 0;
            return 0;
        }
        if (entityId != target || now - attackedAt > CONFIRM_NANOS) return 0;
        target = -1;
        combo = combo > 0 && now - lastHit <= reset.get() * 1_000_000_000L ? combo + 1 : 1;
        lastHit = now;
        return combo;
    }

    /** The hits in the combo now (0: none yet, or it ended on your damage). */
    public int combo() {
        return combo;
    }

    public SoundOption slot(int step) {
        return steps[Math.max(1, Math.min(steps.length, step)) - 1];
    }

    /** The built-in pling's pitch for a step: up a little each hit, to the highest Minecraft plays. */
    public static float pitch(int step) {
        return Math.min(2f, .7f + .15f * (Math.max(1, step) - 1));
    }

    /**
     * The file for hit {@code step} past Combo 5 from After Combo 5, "" when that list is empty: Random never repeats the last one,
     * In Order goes down the list from the first each combo, Shuffle plays each once in a random order, then shuffles again.
     */
    String afterSound(int step) {
        List<String> files = after.get();
        int n = files.size();
        if (n == 0) return "";
        int index;
        if (afterOrder.getIndex() == AFTER_IN_ORDER) index = Math.max(0, step - steps.length - 1) % n;
        else if (afterOrder.getIndex() == AFTER_SHUFFLE) {
            if (bag.isEmpty() || bag.stream().anyMatch(i -> i >= n)) {
                bag.clear();
                for (int i = 0; i < n; i++) bag.add(i);
                Collections.shuffle(bag, random);
                if (n > 1 && bag.get(0) == afterLast) Collections.swap(bag, 0, n - 1); // no repeat across two rounds
            }
            index = bag.remove(0);
        } else index = n == 1 ? 0 : afterLast < 0 || afterLast >= n ? random.nextInt(n) : (afterLast + 1 + random.nextInt(n - 1)) % n;
        afterLast = index;
        return files.get(index);
    }

    /** Plays a step: After Combo 5's next file past 5, else the step's upload, else the built-in pling at the step's pitch. */
    public void play(int step) {
        if (volume.getValue() <= 0) return;
        String extra = step > steps.length ? afterSound(step) : "";
        SoundOption slot = slot(step);
        if (!extra.isEmpty()) CustomSounds.play(extra, volume.getValue());
        else if (slot.isSet()) CustomSounds.play(slot.get(), volume.getValue());
        else LadsGameBridge.get().playComboSound(pitch(step), (float) volume.getValue());
    }

    /** The settings' Play button for one of this module's sounds. */
    public void preview(SoundOption sound) {
        if (sound == longShotSound || sound == headshotSound) playBow(sound == longShotSound ? LONG_SHOT : HEADSHOT);
        else play(java.util.Arrays.asList(steps).indexOf(sound) + 1);
    }

    // Bow sounds ------------------------------------------------------------------------------------------------------------

    /** One of your arrows crossed entity {@code id}'s box {@code distance} blocks from you, in its head or not. Returns the sounds to play. */
    public int arrowCrossed(int id, boolean player, boolean head, double distance, long now) {
        if (!player && !bowMobs.get()) return 0;
        crossings.values().removeIf(crossing -> now - crossing.at() > ARROW_NANOS);
        if (id == waitId && now - waitAt <= ARROW_NANOS) { // the server confirmed this hit first
            waitId = -1;
            return waitHeadOnly ? headshots.get() && head ? HEADSHOT : 0 : landed(waitDistance, head);
        }
        crossings.put(id, new Crossing(player, head, distance, now));
        return 0;
    }

    /** An entity other than you was hurt {@code distance} blocks from you: a mob your arrow crossed is a bow hit. Returns the sounds to play. */
    public int bowHurt(int id, boolean player, double distance, long now) {
        hurtId = id;
        hurtPlayer = player;
        hurtDistance = distance;
        hurtAt = now;
        if (player || !bowMobs.get()) return 0; // players: the arrow-hit-player event says whose arrow it was
        Crossing crossing = crossings.get(id);
        if (crossing != null && now - crossing.at() <= ARROW_NANOS) {
            crossings.remove(id);
            return landed(distance, crossing.head());
        }
        wait(id, distance, now, false); // your arrow's, if its crossing follows
        return 0;
    }

    /** The server's word that your arrow hit a player (its arrow-hit-player event). Returns the sounds to play. */
    public int arrowHitPlayer(long now) {
        int id = -1;
        double distance = 0;
        if (hurtPlayer && hurtId >= 0 && now - hurtAt <= HURT_NANOS) {
            id = hurtId;
            distance = hurtDistance;
        } else { // no hurt seen: the player your latest arrow crossed
            long latest = Long.MIN_VALUE;
            for (Map.Entry<Integer, Crossing> entry : crossings.entrySet()) {
                Crossing crossing = entry.getValue();
                if (crossing.player() && now - crossing.at() <= ARROW_NANOS && crossing.at() > latest) {
                    latest = crossing.at();
                    id = entry.getKey();
                    distance = crossing.distance();
                }
            }
        }
        hurtId = -1;
        if (id < 0) return 0;
        Crossing crossing = crossings.remove(id);
        if (crossing != null && now - crossing.at() <= ARROW_NANOS) return landed(distance, crossing.head());
        wait(id, distance, now, true); // the long shot now, the headshot once the crossing is seen
        return landed(distance, false);
    }

    private void wait(int id, double distance, long now, boolean headOnly) {
        waitId = id;
        waitDistance = distance;
        waitAt = now;
        waitHeadOnly = headOnly;
    }

    private int landed(double distance, boolean head) {
        shotDistance = distance;
        return (longShots.get() && distance >= longShotDistance.getValue() ? LONG_SHOT : 0) | (headshots.get() && head ? HEADSHOT : 0);
    }

    /** Plays bow sounds: each its file, else its built-in sound. */
    public void playBow(int sounds) {
        if (sounds == 0 || volume.getValue() <= 0) return;
        if ((sounds & LONG_SHOT) != 0) playBow(longShotSound, false);
        if ((sounds & HEADSHOT) != 0) playBow(headshotSound, true);
    }

    private void playBow(SoundOption sound, boolean headshot) {
        if (sound.isSet()) CustomSounds.play(sound.get(), volume.getValue());
        else LadsGameBridge.get().playBowSound(headshot, (float) volume.getValue());
    }

    // Sound Sets ------------------------------------------------------------------------------------------------------------

    /** A named Combo 1-5 with its After Combo 5 sounds and order. */
    public record SoundSet(String name, List<String> steps, List<String> after, int order) {
        public SoundSet {
            steps = List.copyOf(steps);
            after = List.copyOf(after);
        }
    }

    /** The player's Sound Sets, saved with the shared config (so on every version); Reset options keeps them. */
    public static final class SoundSets extends Option {
        private final List<SoundSet> sets = new ArrayList<>();

        SoundSets(String name) { super(name); }

        public List<SoundSet> get() { return List.copyOf(sets); }
        void add(SoundSet set) { sets.add(set); }
        void set(int index, SoundSet set) { if (index >= 0 && index < sets.size()) sets.set(index, set); }
        void remove(int index) { if (index >= 0 && index < sets.size()) sets.remove(index); }

        @Override public JsonElement save() {
            JsonArray array = new JsonArray();
            for (SoundSet set : sets) {
                JsonObject json = new JsonObject();
                json.addProperty("name", set.name());
                JsonArray steps = new JsonArray(), after = new JsonArray();
                set.steps().forEach(steps::add);
                set.after().forEach(after::add);
                json.add("steps", steps);
                json.add("after", after);
                json.addProperty("order", set.order());
                array.add(json);
            }
            return array;
        }

        @Override public void load(JsonElement element) {
            sets.clear();
            try {
                for (JsonElement entry : element.getAsJsonArray()) {
                    JsonObject json = entry.getAsJsonObject();
                    List<String> steps = new ArrayList<>(), after = new ArrayList<>();
                    for (JsonElement file : json.getAsJsonArray("steps")) if (steps.size() < 5) steps.add(file.getAsString());
                    while (steps.size() < 5) steps.add("");
                    if (json.has("after")) for (JsonElement file : json.getAsJsonArray("after")) after.add(file.getAsString());
                    sets.add(new SoundSet(json.get("name").getAsString(), steps, after, json.has("order") ? json.get("order").getAsInt() : AFTER_RANDOM));
                }
            } catch (Exception ignored) {}
        }
    }

    /** Default pling, then the player's sets. */
    public List<SoundSet> soundSets() {
        List<SoundSet> all = new ArrayList<>();
        all.add(DEFAULT_PLING);
        all.addAll(sets.get());
        return all;
    }

    private List<String> stepFiles() {
        List<String> files = new ArrayList<>();
        for (SoundOption step : steps) files.add(step.get());
        return files;
    }

    /** Are Combo 1-5 and After Combo 5 this set's now? */
    public boolean using(SoundSet set) {
        return set.steps().equals(stepFiles()) && set.after().equals(after.get()) && (set.after().isEmpty() || set.order() == afterOrder.getIndex());
    }

    /** Puts a set's sounds into Combo 1-5 and After Combo 5. */
    public void use(SoundSet set) {
        for (int i = 0; i < steps.length; i++) steps[i].set(i < set.steps().size() ? set.steps().get(i) : "");
        after.set(set.after());
        afterOrder.setIndex(set.order());
    }

    /** Saves Combo 1-5 and After Combo 5 as a new set ("Set 1", "Set 2"...); returns its index among the player's sets. */
    public int saveSet() {
        List<String> names = soundSets().stream().map(set -> set.name().toLowerCase(Locale.ROOT)).toList();
        int n = 1;
        while (names.contains("set " + n)) n++;
        sets.add(new SoundSet("Set " + n, stepFiles(), after.get(), afterOrder.getIndex()));
        return sets.get().size() - 1;
    }

    /** Renames the player's set {@code index}; a blank name keeps the old one. */
    public void renameSet(int index, String name) {
        String trimmed = name == null ? "" : name.trim();
        List<SoundSet> saved = sets.get();
        if (trimmed.isEmpty() || index < 0 || index >= saved.size()) return;
        SoundSet set = saved.get(index);
        sets.set(index, new SoundSet(trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed, set.steps(), set.after(), set.order()));
    }

    public void deleteSet(int index) { sets.remove(index); }

    /** A deleted sound leaves every slot, list and set (its slots fall back to the built-in sound). */
    public void forget(String file) {
        for (SoundOption step : steps) if (step.get().equals(file)) step.set("");
        if (longShotSound.get().equals(file)) longShotSound.set("");
        if (headshotSound.get().equals(file)) headshotSound.set("");
        after.removeAll(file);
        favourites.removeAll(file);
        List<SoundSet> saved = sets.get();
        for (int i = 0; i < saved.size(); i++) {
            SoundSet set = saved.get(i);
            List<String> stepFiles = set.steps().stream().map(step -> step.equals(file) ? "" : step).toList();
            sets.set(i, new SoundSet(set.name(), stepFiles, set.after().stream().filter(f -> !f.equals(file)).toList(), set.order()));
        }
    }

    // Hooks -----------------------------------------------------------------------------------------------------------------

    private static ComboSoundsModule active() {
        return ModuleManager.getInstance().getModule(NAME) instanceof ComboSoundsModule module && module.isEnabled() ? module : null;
    }

    /** True while a version should track your arrows for bow sounds (the module is on with a bow sound on). */
    public static boolean bowActive() {
        ComboSoundsModule module = active();
        return module != null && (module.longShots.get() || module.headshots.get());
    }

    /** Each version's attack hook (client thread). */
    public static void onAttack(int entityId, boolean player) {
        ComboSoundsModule module = active();
        if (module != null) module.attacked(entityId, player, System.nanoTime());
    }

    /** Each version's hurt hook (client thread): plays the step a landed hit makes. */
    public static void onHurt(int entityId, boolean self) {
        ComboSoundsModule module = active();
        if (module == null) return;
        int step = module.hurt(entityId, self, System.nanoTime());
        if (step > 0) module.play(step);
    }

    /** As {@link #onHurt(int, boolean)}, also for bow sounds: whether the hurt entity is a player, and how far from you it is. */
    public static void onHurt(int entityId, boolean self, boolean player, double distance) {
        onHurt(entityId, self);
        ComboSoundsModule module = active();
        if (module != null && !self) module.bow(module.bowHurt(entityId, player, distance, System.nanoTime()), "hurt");
    }

    /** Each version's arrow tracking (client tick): one of your arrows crossed this entity's box. */
    public static void onArrowCrossed(int entityId, boolean player, boolean head, double distance) {
        ComboSoundsModule module = active();
        if (module != null) module.bow(module.arrowCrossed(entityId, player, head, distance, System.nanoTime()), "arrow");
    }

    /** Each version's arrow-hit-player game event (1.8.9 change game state 6, ARROW_HIT_PLAYER). */
    public static void onArrowHitPlayer() {
        ComboSoundsModule module = active();
        if (module != null) module.bow(module.arrowHitPlayer(System.nanoTime()), "arrow hit player");
    }

    private void bow(int sounds, String signal) {
        if (sounds == 0) return;
        LOGGER.info("Lads bow sound ({}): {}{} at {} blocks", signal, (sounds & LONG_SHOT) != 0 ? "long shot " + fileOrBuiltIn(longShotSound) + " " : "",
            (sounds & HEADSHOT) != 0 ? "headshot " + fileOrBuiltIn(headshotSound) : "", String.format(Locale.ROOT, "%.1f", shotDistance));
        playBow(sounds);
    }

    private static String fileOrBuiltIn(SoundOption sound) {
        return sound.isSet() ? sound.get() : "(built-in)";
    }
}
