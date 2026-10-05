package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.client.killbanner.KillDetector.Kind;
import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Kills the client sees (KillDetector) and the banner each kill shows (KillBannerModule.next). */
class KillDetectorTest {
    private static final long S = 1_000_000_000L;
    private static final Set<String> ME = Set.of("Arash");

    @Test void deathOfWhatIHitLastIsOneKill() {
        var d = new KillDetector();
        assertNull(d.died(7, 0), "a stranger's death is not a kill");
        d.hitByMe(7, List.of(), Kind.MOB, true, S);
        var kill = d.died(7, 2 * S);
        assertNotNull(kill);
        assertEquals(Kind.MOB, kill.kind());
        assertTrue(kill.headshot(), "the last hit landed on the head");
        assertNull(d.died(7, 2 * S + 1), "death event plus zero health is one kill");
        d.hitByMe(8, List.of(), Kind.BOSS, false, S);
        assertNull(d.died(8, 7 * S), "credit lasts 5 seconds, as vanilla's");
        d.hitByMe(9, List.of(), Kind.MOB, false, S);
        d.hitByOther(9, 2 * S);
        assertNull(d.died(9, 3 * S), "someone else's later hit takes the credit");
        d.hitByMe(9, List.of(), Kind.MOB, false, 4 * S);
        assertNotNull(d.died(9, 5 * S), "hitting again takes it back");
    }

    @Test void pluginServerKillMessages() {
        var d = new KillDetector();
        d.hitByMe(3, List.of("Bob"), Kind.PLAYER, false, S);
        assertNull(d.chat("[MVP+] Bob: gg Arash", ME, 2 * S), "player chat naming both is not a kill");
        assertNull(d.chat("<Bob> Arash ez", ME, 2 * S));
        assertNull(d.chat("You were killed by Bob.", ME, 2 * S), "my own death is not a kill");
        assertNull(d.chat("Bob was killed by Steve.", ME, 2 * S), "someone else's kill");
        assertNull(d.chat("Bob was killed by Steve, assisted by Arash.", ME, 2 * S), "an assist is not a kill");
        assertNull(d.chat("Bob was killed by Steve. You have 3 kills", ME, 2 * S), "the killer named first after 'by' is not me");
        var kill = d.chat("\u00a7cBob \u00a77was spooked by \u00a7aArash\u00a77. \u00a7b\u00a7lFINAL KILL!", ME, 2 * S);
        assertNotNull(kill, "Hypixel's themed kill message, colour codes and all");
        assertEquals(Kind.PLAYER, kill.kind());
        assertNull(d.died(3, 2 * S), "the same kill's death event does not count again");
        assertNull(d.chat("Bob was killed by Arash.", ME, 3 * S), "nor a second message for it");
        d.hitByMe(4, List.of("Carl"), Kind.PLAYER, false, 10 * S);
        assertNotNull(d.chat("You killed Carl!", ME, 11 * S));
        d.hitByMe(5, List.of("Dave"), Kind.PLAYER, false, 10 * S);
        assertNotNull(d.chat("KILL! on [120] Dave +15 XP", ME, 11 * S), "The Pit");
        d.hitByMe(6, List.of("Erin"), Kind.PLAYER, false, 10 * S);
        assertNotNull(d.chat("Erin hit the ground too hard whilst trying to escape [VIP] Arash", ME, 12 * S), "vanilla wording");
        d.hitByMe(7, List.of("Finn"), Kind.PLAYER, false, 10 * S);
        assertNull(d.chat("Finn was killed by Arash.", ME, 21 * S), "kill messages count for 10 seconds after my hit");
        d.hitByMe(8, List.of("Gus"), Kind.MOB, false, 30 * S);
        assertNull(d.chat("Gus was killed by Arash.", ME, 31 * S), "only players have kill messages");
    }

    private enum Seen { KILL, DEATH, NONE }

    /**
     * Real chat lines, as servers send them (colour codes included), with the local player "Arash" (shown nicked as "Nick")
     * and "Bob" (nicked "Bobby") hit a moment ago: KILL is Arash's kill of Bob, DEATH Arash's own death.
     */
    private static final Object[][] LINES = {
        // Vanilla 1.8.9 (en_US) death messages with a player killer.
        {Seen.KILL, "Bob was slain by Arash"},
        {Seen.KILL, "Bob was slain by Arash using [Diamond Sword]"},
        {Seen.KILL, "Bob was shot by Arash"},
        {Seen.KILL, "Bob was shot by Arash using [Bow]"},
        {Seen.KILL, "Bob was fireballed by Arash"},
        {Seen.KILL, "Bob was pummeled by Arash"},
        {Seen.KILL, "Bob was killed by Arash using magic"},
        {Seen.KILL, "Bob was blown up by Arash"},
        {Seen.KILL, "Bob was doomed to fall by Arash"},
        {Seen.KILL, "Bob fell too far and was finished by Arash"},
        {Seen.KILL, "Bob walked into fire whilst fighting Arash"},
        {Seen.KILL, "Bob was burnt to a crisp whilst fighting Arash"},
        {Seen.KILL, "Bob tried to swim in lava to escape Arash"},
        {Seen.KILL, "Bob drowned whilst trying to escape Arash"},
        {Seen.KILL, "Bob walked into a cactus whilst trying to escape Arash"},
        {Seen.NONE, "Bob fell from a high place"},
        {Seen.NONE, "Bob was slain by Zombie"},
        {Seen.NONE, "Bob was slain by Steve"},
        {Seen.NONE, "Bob was slain by Arashi"},
        {Seen.DEATH, "Arash was slain by Bob"},
        {Seen.DEATH, "Arash was slain by Zombie"},
        {Seen.DEATH, "Arash fell from a high place"},
        {Seen.DEATH, "Arash fell out of the world"},
        {Seen.DEATH, "Arash drowned"},
        // Vanilla 26.x.
        {Seen.KILL, "Bob was slain by Arash using [Netherite Sword]"},
        {Seen.KILL, "Bob was shot by Arash using [Crossbow]"},
        {Seen.KILL, "Bob was impaled by Arash"},
        {Seen.KILL, "Bob was smashed by Arash"},
        {Seen.KILL, "Bob was stung to death by Arash"},
        {Seen.KILL, "Bob didn't want to live in the same world as Arash"},
        {Seen.KILL, "Bob walked into the danger zone due to Arash"},
        {Seen.KILL, "Bob hit the ground too hard whilst trying to escape Arash"},
        {Seen.KILL, "Bob experienced kinetic energy whilst trying to escape Arash"},
        {Seen.KILL, "Bob was squashed by a falling anvil whilst fighting Arash"},
        {Seen.KILL, "Bob went off with a bang due to a firework fired from [Crossbow] by Arash"},
        {Seen.KILL, "Bob was killed while trying to hurt Arash"},
        {Seen.DEATH, "Arash was slain by Bob using [Mace]"},
        {Seen.DEATH, "Arash didn't want to live in the same world as Bob"},
        {Seen.DEATH, "Arash hit the ground too hard"},
        // Hypixel Bed Wars.
        {Seen.KILL, "Bob was killed by Arash."},
        {Seen.KILL, "Bob was killed by Arash. FINAL KILL!"},
        {Seen.KILL, "Bob was knocked into the void by Arash. FINAL KILL!"},
        {Seen.KILL, "Bob was shot by Arash."},
        {Seen.KILL, "\u00a79Bob \u00a77was spooked by \u00a7cArash\u00a77. \u00a7b\u00a7lFINAL KILL!"},
        {Seen.KILL, "Bob was hit off by a love bomb from Arash."},
        {Seen.KILL, "Bob was given the cold shoulder by Arash."},
        {Seen.KILL, "Bob was glazed in BBQ sauce by Arash."},
        {Seen.KILL, "\u00a7c[RED] \u00a7cBob \u00a77was killed by \u00a79[BLUE] \u00a79Arash\u00a77. \u00a7b\u00a7lFINAL KILL!"},
        {Seen.NONE, "Bob fell into the void."},
        {Seen.NONE, "BED DESTRUCTION > Red Bed was destroyed by Arash!"},
        {Seen.NONE, "+10 coins! (Final Kill)"},
        {Seen.NONE, "[MVP+] Bob: gg Arash"},
        {Seen.DEATH, "Arash was killed by Bob."},
        {Seen.DEATH, "Arash fell into the void."},
        {Seen.DEATH, "Arash was knocked into the void by Bob. FINAL KILL!"},
        // Hypixel SkyWars, Mega Walls (ranks and class tags), Duels and The Bridge.
        {Seen.KILL, "Bob was thrown into the void by Arash."},
        {Seen.KILL, "Bob was knocked off a cliff by Arash."},
        {Seen.KILL, "[MVP+] Bob was killed by [VIP] Arash."},
        {Seen.KILL, "[HUN] Bob was killed by [ZOM] Arash"},
        {Seen.KILL, "Bob was slain by Arash."},
        {Seen.NONE, "Bob was killed by Steve, assisted by Arash."},
        {Seen.NONE, "Bob was killed by Steve. You have 3 kills"},
        // Hypixel The Pit.
        {Seen.KILL, "KILL! on [120] Bob +15.00XP +10.00g"},
        {Seen.NONE, "ASSIST! 50% on [80] Bob +5.00XP"},
        {Seen.DEATH, "DEATH! by [100] Bob"},
        // Minemen Club, PvP Legacy and other practice / FFA servers.
        {Seen.KILL, "Bob (4.5\u2764) was killed by Arash (10.0\u2764)"},
        {Seen.KILL, "Bob was killed by Arash [12.5\u2764]"},
        {Seen.KILL, "[FFA] Bob was killed by Arash"},
        {Seen.KILL, "\u00bb Bob was killed by Arash."},
        {Seen.KILL, "Bob has been slain by Arash"},
        {Seen.KILL, "Arash killed Bob"},
        {Seen.KILL, "Arash has killed Bob!"},
        {Seen.KILL, "Arash eliminated Bob"},
        {Seen.KILL, "You killed Bob!"},
        {Seen.KILL, "You have killed Bob"},
        {Seen.DEATH, "You were killed by Bob"},
        {Seen.DEATH, "You died!"},
        {Seen.NONE, "<Bob> Arash killed me"},
        {Seen.NONE, "From [VIP] Bob: you killed me Arash"},
        {Seen.NONE, "You were spawned in Limbo."},
        // Nicknames: the local player's tab-list name, the victim's.
        {Seen.KILL, "Bob was killed by Nick."},
        {Seen.KILL, "Bobby was killed by Arash."},
    };

    private static Seen seen(String line, java.util.function.Consumer<KillDetector> setup, Map<Integer, Set<String>> players) {
        var d = new KillDetector();
        setup.accept(d);
        boolean kill = d.chat(line, Set.of("Arash", "Nick"), () -> players, 2 * S) != null;
        boolean death = KillDetector.myDeath(line, Set.of("Arash", "Nick"));
        assertFalse(kill && death, line + ": both a kill and a death");
        return kill ? Seen.KILL : death ? Seen.DEATH : Seen.NONE;
    }

    @Test void serverFormats() {
        List<String> wrong = new ArrayList<>();
        for (Object[] row : LINES) {
            String line = (String) row[1];
            Seen got = seen(line, d -> d.hitByMe(3, List.of("Bob", "Bobby"), Kind.PLAYER, false, S), Map.of());
            if (got != row[0]) wrong.add(line + " -> " + got + ", expected " + row[0]);
        }
        assertEquals(List.of(), wrong);
    }

    /** Vanilla death messages by translation key and arguments: the same verdicts in any client language. */
    @Test void translatedDeathMessages() {
        assertNull(KillDetector.deathLine("chat.type.text", List.of("Bob", "hello")), "only death messages");
        assertNull(KillDetector.deathLine("death.attack.player", List.of()));
        var hit = (java.util.function.Consumer<KillDetector>) d -> d.hitByMe(3, List.of("Bob"), Kind.PLAYER, false, S);
        assertEquals(Seen.KILL, seen(KillDetector.deathLine("death.attack.player", List.of("Bob", "Arash")), hit, Map.of()));
        assertEquals(Seen.KILL, seen(KillDetector.deathLine("death.attack.arrow.item", List.of("Bob", "Arash", "[Bow]")), hit, Map.of()));
        assertEquals(Seen.KILL, seen(KillDetector.deathLine("death.fell.assist", List.of("[Red] Bob", "[Blue] Arash")), hit, Map.of()));
        assertEquals(Seen.NONE, seen(KillDetector.deathLine("death.attack.mob", List.of("Bob", "Zombie")), hit, Map.of()));
        assertEquals(Seen.DEATH, seen(KillDetector.deathLine("death.attack.player", List.of("Arash", "Bob")), hit, Map.of()));
        assertEquals(Seen.DEATH, seen(KillDetector.deathLine("death.fell.accident.generic", List.of("Arash")), hit, Map.of()));
    }

    /** A player in the world the client saw no hit on (1.8.9 arrows, a rod or fireball knock-off): plain kill words only. */
    @Test void killsWithoutAHitNeedPlainWords() {
        Map<Integer, Set<String>> world = Map.of(3, Set.of("Bob"), 4, Set.of("Carl"));
        java.util.function.Consumer<KillDetector> none = d -> {};
        assertEquals(Seen.KILL, seen("Bob was shot by Arash.", none, world));
        assertEquals(Seen.KILL, seen("Bob was knocked into the void by Arash. FINAL KILL!", none, world));
        assertEquals(Seen.KILL, seen("Carl was slain by Arash using [Bow]", none, world));
        assertEquals(Seen.NONE, seen("Bob was spooked by Arash.", none, world), "a themed line needs a hit first");
        assertEquals(Seen.NONE, seen("Bob was kicked from the guild by Arash!", none, world));
        assertEquals(Seen.NONE, seen("Dave was shot by Arash.", none, world), "not a player in the world");
        assertEquals(Seen.NONE, seen("Bob was shot by Arash.", none, Map.of()));
    }

    /** Every kill counts once: the same death's signals once, a new death after a new hit again, five deaths at once five times. */
    @Test void rapidKillsAllCountDuplicatesDoNot() {
        var d = new KillDetector();
        d.hitByMe(3, List.of("Bob"), Kind.PLAYER, false, S);
        assertNotNull(d.died(3, 2 * S));
        assertNull(d.chat("Bob was killed by Arash.", ME, 2 * S), "the kill message of the death already counted");
        assertNull(d.died(3, 2 * S + 1), "and its zero health update");
        d.hitByMe(3, List.of("Bob"), Kind.PLAYER, false, 3 * S); // respawned in place (The Bridge, The Pit): the same entity
        assertNotNull(d.chat("Bob was killed by Arash.", ME, 3 * S + 1), "killed again 1 s later: a second kill");
        d.hitByMe(9, List.of("Bob"), Kind.PLAYER, false, 4 * S); // respawned as a new entity with the same name
        assertNotNull(d.chat("Bob was killed by Arash.", ME, 4 * S + 1), "the old record's kill does not hide the new one");
        assertNull(d.chat("Bob was killed by Arash.", ME, 4 * S + 2), "the same message twice is one kill");

        var mobs = new KillDetector();
        for (int id = 10; id < 15; id++) mobs.hitByMe(id, List.of(), Kind.MOB, false, S);
        int kills = 0;
        for (int id = 10; id < 15; id++) if (mobs.died(id, 2 * S) != null) kills++;
        for (int id = 10; id < 15; id++) if (mobs.died(id, 2 * S) != null) kills++; // each one's zero health update too
        assertEquals(5, kills, "five deaths in the same tick are five kills");
    }

    @Test void namesDropRanksAndTags() {
        assertEquals(Set.of("Arash", "Nick"), KillDetector.names("Arash", "\u00a76[MVP\u00a7c+\u00a76] Nick \u00a77[GUILD]"));
        assertEquals(Set.of("Arash"), KillDetector.names("Arash", "Arash"));
    }

    /** 1.6.0 saved Style, Custom Banner and Custom Sound as indices: they keep meaning Base, Reaver, Rogue and Custom. */
    @Test void savedIndicesKeepTheirSkins() {
        var module = new KillBannerModule();
        assertArrayEquals(new String[] {"Base", "Reaver", "Rogue", "Custom"}, java.util.Arrays.copyOf(module.bannerStyle.getChoices(), 4));
        assertArrayEquals(new String[] {"Base", "Reaver", "Rogue"}, java.util.Arrays.copyOf(module.customVisual.getChoices(), 3));
        assertArrayEquals(new String[] {"Base", "Reaver", "Rogue"}, java.util.Arrays.copyOf(module.customSound.getChoices(), 3));
        assertEquals(KillBannerStyle.DEFAULT, KillBannerModule.skin(KillBannerModule.BASE));
        assertEquals(KillBannerStyle.REAVER, KillBannerModule.skin(KillBannerModule.REAVER));
        assertEquals(KillBannerStyle.ROGUE, KillBannerModule.skin(KillBannerModule.ROGUE));
        for (KillBannerStyle style : KillBannerStyle.values()) {
            assertEquals(style, KillBannerModule.skin(KillBannerModule.styleIndexOf(style)), style + " round-trips through its Style index");
            if (style.ordinal() > 2) assertEquals(style.displayName, module.bannerStyle.getChoices()[KillBannerModule.styleIndexOf(style)]);
            assertEquals(style, KillBannerModule.visualOrSoundSkin(style.ordinal()));
        }
        module.skinVariants.setValue("aemondir:3,nosuchskin:2,phaseguard:9");
        assertEquals(3, module.getVariant(KillBannerStyle.AEMONDIR));
        assertEquals(0, module.getVariant(KillBannerStyle.DEFAULT), "an unknown skin id is ignored");
        assertEquals(KillBannerStyle.PHASEGUARD.variantNames.length - 1, module.getVariant(KillBannerStyle.PHASEGUARD), "an out-of-range variant is clamped");
        module.skinVariants.setValue("");
        assertEquals(0, module.getVariant(KillBannerStyle.AEMONDIR), "a reset clears the saved variants");
    }

    @Test void randomizerModes() {
        var module = new KillBannerModule();
        module.bannerStyle.setIndex(KillBannerModule.ROGUE);
        module.rogueVariant.setIndex(2);
        assertEquals(new KillBannerModule.Pick(KillBannerStyle.ROGUE, 2, KillBannerStyle.ROGUE), module.next(), "randomize off");

        module.bannerStyle.setIndex(KillBannerModule.CUSTOM);
        module.customVisual.setIndex(KillBannerModule.REAVER);
        module.customSound.setIndex(KillBannerModule.ROGUE);
        module.reaverVariant.setIndex(3);
        assertEquals(new KillBannerModule.Pick(KillBannerStyle.REAVER, 3, KillBannerStyle.ROGUE), module.next(), "custom: Reaver's banner, Rogue's sound");

        module.randomize.setIndex(KillBannerModule.RANDOM_VARIANT);
        Set<Integer> variants = new HashSet<>();
        KillBannerModule.Pick previous = null;
        for (int i = 0; i < 200; i++) {
            var pick = module.next();
            assertEquals(KillBannerStyle.REAVER, pick.style());
            assertEquals(KillBannerStyle.ROGUE, pick.soundStyle(), "custom sound kept while the variant changes");
            assertNotEquals(previous, pick, "never the same twice in a row");
            variants.add(pick.variant());
            previous = pick;
        }
        assertEquals(4, variants.size());

        module.randomize.setIndex(KillBannerModule.RANDOM_SKIN);
        Set<KillBannerStyle> skins = new HashSet<>();
        for (int i = 0; i < 200; i++) { var pick = module.next(); skins.add(pick.style()); assertEquals(pick.style(), pick.soundStyle()); }
        assertTrue(skins.size() > 2, "random skin picks from all available skins");
        assertTrue(skins.contains(KillBannerStyle.REAVER) || skins.contains(KillBannerStyle.ROGUE));

        module.randomize.setIndex(KillBannerModule.RANDOM_CHOSEN);
        module.randomPool.setValue("");
        module.togglePool(KillBannerStyle.ROGUE, 1);
        module.togglePool(KillBannerStyle.REAVER, 2);
        module.togglePool(KillBannerStyle.REAVER, 2);
        for (int i = 0; i < 20; i++) assertEquals(new KillBannerModule.Pick(KillBannerStyle.ROGUE, 1, KillBannerStyle.ROGUE), module.next(), "one chosen entry");
        module.randomPool.setValue("");
        assertEquals(module.chosen(), module.next(), "an empty pool falls back to the chosen banner");
    }

    @Test void headshotLabelStartsHidden() {
        assertFalse(new KillBannerModule().headshotText.get());
    }
}
