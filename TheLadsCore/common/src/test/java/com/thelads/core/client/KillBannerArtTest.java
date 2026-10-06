package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KillBannerArtTest {
    /** Every skin's art for each variant and kill count, and a registered sound for each kill count (shared copies included). */
    @Test
    void allBannersHaveTheirArtAndSounds() throws Exception {
        JsonObject sounds;
        try (var in = KillBannerArtTest.class.getResourceAsStream("/assets/theladscore/sounds.json")) {
            sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (KillBannerStyle style : KillBannerStyle.values()) {
            List<String> art = new ArrayList<>();
            if (style.type == KillBannerStyle.Type.BANNER_SWAP) for (int k = 1; k <= 5; k++) art.add(style.swapAsset(k));
            else if (!style.isAnimated()) {
                if (style.hasFrame) art.add(style.frameAsset());
                if (style.hasRing) art.add(style.ringAsset());
                for (int v = 0; v < style.variantNames.length; v++) {
                    if (style.hasEmblem) art.add(style.emblemAsset(v));
                    if (style.hasPip) art.add(style.pipAsset(v));
                }
            }
            for (String path : art)
                try (var in = KillBannerArtTest.class.getResourceAsStream(path)) { assertNotNull(in, path + " missing"); }
            assertTrue(style.soundCount >= 1, style.id + " has sounds");
            for (int k = 1; k <= 5; k++) {
                String event = style.id + "_kill_" + Math.min(k, style.soundCount);
                assertTrue(sounds.has(event), event + " is in sounds.json");
                for (var sound : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
                    String path = "/assets/theladscore/sounds/" + sound.getAsString().substring("theladscore:".length()) + ".ogg";
                    try (var in = KillBannerArtTest.class.getResourceAsStream(path)) { assertNotNull(in, event + ": " + path + " missing"); }
                }
            }
        }
    }

    private static int opaque(byte[] rgba) {
        int n = 0;
        for (int i = 3; i < rgba.length; i += 4) if ((rgba[i] & 255) > 128) n++;
        return n;
    }
}
