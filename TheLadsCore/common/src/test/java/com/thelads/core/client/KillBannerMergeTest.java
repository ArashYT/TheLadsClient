package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.modules.KillBannerModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Episode duplicates folded into their base skin (killbanner/merged.properties, tools/killbanner/merge_skins.py). */
class KillBannerMergeTest {
    @Test
    void aFoldedSkinIsHiddenAndResolvesToItsBaseSkinsVariant() {
        assertTrue(KillBannerStyle.IONEP5.hidden());
        assertSame(KillBannerStyle.ION, KillBannerStyle.IONEP5.mergedInto());
        assertEquals(1, KillBannerStyle.IONEP5.variantOffset(), "Ion's own variant comes first");
        assertEquals(5, KillBannerStyle.ION.variantNames.length, "Default, EP 5, EP 5 variants 1 to 3");
        assertEquals("EP 5 VARIANT 2", KillBannerStyle.ION.variantNames[3]);
        assertFalse(KillBannerStyle.ION.hidden());
        assertNull(KillBannerStyle.ION.mergedInto());
        assertFalse(KillBannerStyle.shown().contains(KillBannerStyle.IONEP5), "not offered");
        assertTrue(KillBannerStyle.shown().contains(KillBannerStyle.ION));
        assertEquals(KillBannerStyle.values().length - 30, KillBannerStyle.shown().size(), "30 folded away");
        var pick = KillBannerModule.pick(KillBannerStyle.IONEP5, 2, KillBannerStyle.IONEP5);
        assertSame(KillBannerStyle.ION, pick.style());
        assertEquals(3, pick.variant(), "Ion EP 5 variant 2 is Ion's variant 3");
        assertSame(KillBannerStyle.ION, pick.soundStyle());
        assertEquals(1, KillBannerModule.pick(KillBannerStyle.ONIEP6, 1, KillBannerStyle.ONIEP6).variant(), "a pure duplicate keeps its variant");
    }

    @Test
    void aFoldedVariantKeepsItsOwnArtColourAndSounds() {
        assertEquals("/assets/theladscore/killbanner/ionep5/emblem_v2.png", KillBannerStyle.ION.emblemAsset(3));
        assertEquals("/assets/theladscore/killbanner/ion/emblem.png", KillBannerStyle.ION.emblemAsset(0));
        assertEquals(0x00DD87, KillBannerStyle.ION.accent(2), "Ion EP 5 variant 1's green");
        assertEquals("ionep5", KillBannerStyle.ION.soundId(1), "the EP 5 sounds");
        assertEquals("ion", KillBannerStyle.ION.soundId(0));
        assertEquals("reaverep5", KillBannerStyle.REAVER.soundId(4), "Reaver's EP 5 variant plays Reaver 2.0's sounds");
        assertEquals("reaver", KillBannerStyle.REAVER.soundId(1));
        assertEquals("sovereignep8", KillBannerStyle.SOVEREIGN.soundId(9), "past the last variant: the last");
        // Reaver's EP 5 variant (Reaver 2.0) keeps its own frame and ring; RGX 11z Pro EP 4 shares RGX's
        assertEquals(KillBannerStyle.REAVER.frameAsset(), KillBannerStyle.REAVER.frameAsset(0));
        assertNotEquals(KillBannerStyle.REAVER.frameAsset(), KillBannerStyle.REAVER.frameAsset(4));
        assertNotEquals(KillBannerStyle.REAVER.ringAsset(), KillBannerStyle.REAVER.ringAsset(4));
        assertEquals(KillBannerStyle.RGX11ZPRO.frameAsset(), KillBannerStyle.RGX11ZPRO.frameAsset(1));
        assertEquals(KillBannerStyle.ION.ringAsset(), KillBannerStyle.ION.ringAsset(3), "the same line keeps its ring");
    }

    @Test
    void bundlesBecomeOneSkin() {
        assertEquals("ORA BY ONETAP", KillBannerStyle.ORABYONETAP_IGNITION.displayName);
        assertArrayEquals(new String[] {"IGNITION", "LAWYER", "RAJA", "RENEGADE", "WATCH"}, KillBannerStyle.ORABYONETAP_IGNITION.variantNames);
        assertEquals("orabyonetap-raja", KillBannerStyle.ORABYONETAP_IGNITION.soundId(2));
        assertEquals("ego2_audiocircle_raja", com.thelads.core.client.killbanner.KillBannerFx.tier(KillBannerStyle.ORABYONETAP_IGNITION, 2, 3).name(), "Raja's own FX");
        assertEquals(4, KillBannerStyle.BUBBLEGUMDEATHWISH.variantNames.length);
        assertEquals("CHAMPIONS", KillBannerStyle.CHAMPIONS2021.displayName);
        assertEquals("2023", KillBannerStyle.CHAMPIONS2021.variantNames[2]);
    }
}
