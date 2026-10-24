package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sound Volumes (Lunar's Better Sounds, OneClient's SoundTweaks, Badlion's Sounds Mod): a 0-200% slider per group of vanilla sounds
 * (Explosions, Note Blocks, Rain...), found through the Lads settings search. Each version multiplies a sound's own volume by its
 * group's slider where the game works out the volume it plays at (1.20.1 / 26.3 SoundEngine.calculateVolume, 1.8.9
 * SoundManager.getNormalizedVolume); the game still caps the result at full volume, so a boost lifts quiet sounds (rain, steps)
 * up to that cap. Groups match the sound id's path; 1.8.9 has its own ids. Visual only (audio, client side).
 */
public final class SoundVolumes {
    public static final String NAME = "Sound Volumes";

    /** A slider: its label, then the id rules for 1.9+ and for 1.8.9. A rule matches the id's path from its start, or anywhere after '*'. */
    public record Group(String label, String[] modern, String[] legacy) {}

    /** In order: the first group a sound matches is its own. */
    public static final List<Group> GROUPS = List.of(
        group("Explosions", of("entity.generic.explode", "entity.tnt.primed", "entity.creeper.primed", "entity.dragon_fireball.explode"),
            of("random.explode", "game.tnt.primed", "creeper.primed")),
        group("Note Blocks", of("block.note_block."), of("note.")),
        group("Rain", of("weather.rain"), of("ambient.weather.rain")),
        group("Thunder", of("entity.lightning_bolt."), of("ambient.weather.thunder")),
        group("Cave Ambience", of("ambient.cave"), of("ambient.cave")),
        group("Footsteps", of("*.step"), of("step.")),
        group("Glass Breaking", of("block.glass.break"), of("dig.glass")),
        group("Doors and Gates", of("*door.", "*fence_gate."), of("random.door_")),
        group("Chests", of("block.chest.", "block.ender_chest.", "block.barrel.", "block.shulker_box."), of("random.chest")),
        group("Anvils", of("block.anvil."), of("random.anvil_")),
        group("Pistons", of("block.piston."), of("tile.piston.")),
        group("Buttons and Levers", of("*button.", "block.lever.", "*pressure_plate.", "block.tripwire"), of("random.click", "random.wood_click")),
        group("Fire and Lava", of("block.fire.", "block.lava.", "item.flintandsteel.", "item.firecharge.", "entity.generic.burn"),
            of("fire.", "liquid.lava", "random.fizz")),
        group("Water", of("block.water.", "ambient.underwater.", "entity.player.swim", "entity.player.splash", "entity.generic.swim",
            "entity.generic.splash", "item.bucket."), of("liquid.water", "liquid.swim", "liquid.splash", "game.player.swim", "game.neutral.swim",
            "game.hostile.swim")),
        group("Portals", of("block.portal.", "block.end_portal"), of("portal.")),
        group("Block Breaking and Placing", of("block."), of("dig.")),
        group("Bows and Arrows", of("entity.arrow.", "item.crossbow.", "entity.player.attack.nodamage"),
            of("random.bow", "random.successful_hit")),
        group("Player Hurt", of("entity.player.hurt", "entity.player.death", "entity.player.big_fall", "entity.player.small_fall"),
            of("game.player.hurt", "game.player.die")),
        group("Attacks (1.9+)", of("entity.player.attack."), of()),
        group("Shields (1.9+)", of("item.shield."), of()),
        group("Totems (1.11+)", of("item.totem."), of()),
        group("Eating and Drinking", of("entity.generic.eat", "entity.generic.drink", "entity.player.burp", "item.honey_bottle.drink"),
            of("random.eat", "random.drink", "random.burp")),
        group("Experience", of("entity.experience_orb.", "entity.player.levelup"), of("random.orb", "random.levelup")),
        group("Item Pickup", of("entity.item.pickup"), of("random.pop")),
        group("Fireworks", of("entity.firework_rocket."), of("fireworks.")),
        group("Minecarts", of("entity.minecart."), of("minecart.")),
        group("Villagers", of("entity.villager.", "entity.wandering_trader."), of("mob.villager.")),
        group("Hostile Mobs", of("entity.zombie", "entity.husk", "entity.drowned", "entity.skeleton", "entity.stray", "entity.wither",
            "entity.creeper", "entity.spider", "entity.cave_spider", "entity.enderman", "entity.endermite", "entity.witch", "entity.slime",
            "entity.magma_cube", "entity.blaze", "entity.ghast", "entity.silverfish", "entity.guardian", "entity.elder_guardian",
            "entity.phantom", "entity.pillager", "entity.vindicator", "entity.evoker", "entity.ravager", "entity.vex", "entity.piglin",
            "entity.hoglin", "entity.zoglin", "entity.warden", "entity.ender_dragon", "entity.shulker", "entity.breeze", "entity.bogged"),
            of("mob.zombie", "mob.skeleton", "mob.creeper", "mob.spider", "mob.endermen", "mob.slime", "mob.magmacube", "mob.blaze",
                "mob.ghast", "mob.silverfish", "mob.wither", "mob.enderdragon", "mob.guardian", "mob.zombiepig")),
        group("Animals", of("entity.cow", "entity.mooshroom", "entity.pig", "entity.sheep", "entity.chicken", "entity.horse", "entity.donkey",
            "entity.mule", "entity.llama", "entity.wolf", "entity.cat", "entity.ocelot", "entity.rabbit", "entity.parrot", "entity.fox",
            "entity.bee", "entity.goat", "entity.panda", "entity.polar_bear", "entity.turtle", "entity.axolotl", "entity.frog", "entity.camel",
            "entity.sniffer", "entity.bat", "entity.squid", "entity.glow_squid", "entity.dolphin", "entity.iron_golem", "entity.snow_golem",
            "entity.allay", "entity.armadillo"),
            of("mob.cow", "mob.pig", "mob.sheep", "mob.chicken", "mob.horse", "mob.wolf", "mob.cat", "mob.rabbit", "mob.bat", "mob.irongolem")),
        group("Menu Clicks", of("ui.button.click"), of("gui.button.press")));

    /** 1.8.9's ids ({@link Group#legacy}): set by the 1.8.9 adapter before any sound plays. */
    public static boolean legacy;
    private static final float[] FACTOR = new float[GROUPS.size()];
    /** The module is on and a slider is not at 100%: otherwise every sound keeps its volume without a lookup. */
    private static boolean any;
    /** Sound id path to its group (-1: none), worked out once per id. */
    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();
    private static Module module;

    private SoundVolumes() {}

    /** Once a client tick. */
    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        boolean on = module != null && module.isEnabled(), changed = false;
        for (int i = 0; i < FACTOR.length; i++) {
            FACTOR[i] = on && module.getOption(GROUPS.get(i).label()) instanceof SliderOption slider ? (float) slider.getValue() / 100F : 1F;
            changed |= FACTOR[i] != 1F;
        }
        any = changed;
    }

    /** A sound's volume times its group's slider ({@code path}: its id without the namespace, e.g. "entity.generic.explode"). */
    public static float volume(float volume, String path) {
        if (!any || path == null) return volume;
        int group = CACHE.computeIfAbsent(path, SoundVolumes::group);
        return group < 0 ? volume : volume * FACTOR[group];
    }

    static int group(String path) {
        for (int i = 0; i < GROUPS.size(); i++) {
            for (String rule : legacy ? GROUPS.get(i).legacy() : GROUPS.get(i).modern()) {
                if (rule.charAt(0) == '*' ? path.contains(rule.substring(1)) : path.startsWith(rule)) return i;
            }
        }
        return -1;
    }

    private static Group group(String label, String[] modern, String[] legacy) {
        return new Group(label, modern, legacy);
    }

    private static String[] of(String... rules) {
        return rules;
    }
}
