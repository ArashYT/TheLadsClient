package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The HEADSHOT label's box: the skin's PrimaryColor, as the game tints its headshot background and its pips. */
class KillBannerHeadshotTest {
    @Test
    void everySkinsBoxIsItsPipColour() {
        for (KillBannerStyle style : KillBannerStyle.values())
            for (int v = 0; v < style.variantNames.length; v++)
                assertEquals(style.accent(v), style.headshotBox(v), style.id + " v" + v);
        assertEquals(0x70EF5F, KillBannerStyle.ONI.headshotBox(0));
        assertEquals(0xC80000, KillBannerStyle.REAVER.headshotBox(1), "Reaver's red variant");
        assertEquals(.3f, KillBannerStyle.HEADSHOT_BOX_ALPHA, "drawn at the game's 0.3 over the backdrop");
    }

    @Test
    void theOptionalArtIsOnlyWhereTheGameHasIt() {
        assertNotNull(KillBannerStyle.ONI.pipUpAsset(0), "Oni ships its Up pip texture");
        assertNull(KillBannerStyle.ONI.headshotEmblemAsset(0), "and has no headshot badge");
        assertNotNull(KillBannerStyle.HOLOMERIDIAN.headshotEmblemAsset(0), "Holo Meridian (Sea of Stars) swaps its emblem on a headshot");
        assertNotNull(KillBannerStyle.HOLOMERIDIAN.headshotEmblemAsset(3));
        assertNotNull(KillBannerStyle.ONIEP6.pipUpAsset(0), "a skin on shared art finds its donor's");
        assertNull(KillBannerStyle.CHAMPIONS2024.pipUpAsset(0), "a Banner Swap skin has no pips");
    }
}
