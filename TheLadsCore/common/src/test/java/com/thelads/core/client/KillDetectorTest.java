package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.client.killbanner.KillDetector.Kind;
import com.thelads.core.modules.KillBannerModule;
import java.util.HashSet;
import java.util.List;
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

    @Test void namesDropRanksAndTags() {
        assertEquals(Set.of("Arash", "Nick"), KillDetector.names("Arash", "\u00a76[MVP\u00a7c+\u00a76] Nick \u00a77[GUILD]"));
        assertEquals(Set.of("Arash"), KillDetector.names("Arash", "Arash"));
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
        assertEquals(Set.of(KillBannerStyle.REAVER, KillBannerStyle.ROGUE), skins);

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
