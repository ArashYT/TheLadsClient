package com.thelads.core.client.title;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonPrimitive;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.modules.TitleScreenModule;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Manifest parsing, the host rule and the late-loading dropdown; nothing here touches the network. */
class PanoramaPickerTest {
    private static final String MANIFEST = "{\"latest\":{},\"versions\":["
        + "{\"id\":\"26.3\",\"type\":\"release\",\"url\":\"https://piston-meta.mojang.com/v1/packages/a/26.3.json\"},"
        + "{\"id\":\"26w14a\",\"type\":\"snapshot\",\"url\":\"https://piston-meta.mojang.com/v1/packages/b/26w14a.json\"},"
        + "{\"id\":\"1.12.2\",\"type\":\"release\",\"url\":\"https://piston-meta.mojang.com/v1/packages/c/1.12.2.json\"},"
        + "{\"id\":\"../evil\",\"type\":\"release\",\"url\":\"https://piston-meta.mojang.com/x.json\"},"
        + "{\"id\":\"b1.8.1\",\"type\":\"old_beta\",\"url\":\"https://piston-meta.mojang.com/d.json\"}]}";

    @Test void manifestGivesReleasesNewestFirstWithTheirUrls() {
        Map<String, String> urls = new HashMap<>();
        assertEquals(List.of("26.3", "1.12.2"), PanoramaPicker.releases(MANIFEST, urls));
        assertEquals("https://piston-meta.mojang.com/v1/packages/c/1.12.2.json", urls.get("1.12.2"));
        assertFalse(urls.containsKey("26w14a"));
    }

    @Test void onlyHttpsOnMojangHosts() {
        assertTrue(PanoramaPicker.mojang("https://piston-data.mojang.com/v1/objects/abc/client.jar"));
        assertTrue(PanoramaPicker.mojang(PanoramaPicker.MANIFEST));
        assertFalse(PanoramaPicker.mojang("http://piston-data.mojang.com/client.jar"));
        assertFalse(PanoramaPicker.mojang("https://piston-data.mojang.com.evil.net/client.jar"));
        assertFalse(PanoramaPicker.mojang("https://evilmojang.com/client.jar"));
        assertFalse(PanoramaPicker.mojang("not a url"));
        assertTrue(PanoramaPicker.mojang("https://resources.download.minecraft.net/f5/f5e0a5cfbc2e7b1e89094d7882dde106b030ec26"));
        assertFalse(PanoramaPicker.mojang("https://download.minecraft.net/f5/f5e0"));
    }

    @Test void pickSavedByNameSurvivesTheListLoadingLater() {
        DropdownOption option = (DropdownOption) new TitleScreenModule().getOption(TitleScreenModule.PANORAMA_VERSION);
        assertEquals(PanoramaPicker.DEFAULT, option.getChoices()[option.getOwnIndex()]);
        option.load(new JsonPrimitive("1.12.2")); // saved before the manifest loaded
        assertEquals("1.12.2", option.getChoices()[option.getOwnIndex()]);
        option.setChoices(PanoramaPicker.DEFAULT, "26.3", "1.12.2", "1.8.9");
        assertEquals("1.12.2", option.getChoices()[option.getOwnIndex()]);
        assertEquals(4, option.getChoices().length);
        assertEquals(new JsonPrimitive("1.12.2"), option.save());
        option.setChoices(PanoramaPicker.DEFAULT, "26.3");
        assertEquals("1.12.2", option.getChoices()[option.getOwnIndex()], "a pick the new list lacks stays");
        assertEquals(Arrays.asList(PanoramaPicker.DEFAULT, "26.3", "1.12.2"), Arrays.asList(option.getChoices()));
    }

    @Test void imageLookupIsOnlyForTheSixPanoramaPaths() {
        assertNull(PanoramaPicker.image("textures/gui/title/background/panorama_0.png"), "nothing picked: the normal panorama");
    }

    @Test void changePanoramaStepsOneReleaseFamilyAtATime() {
        String[] choices = {PanoramaPicker.DEFAULT, "26.3", "26.2.1", "26.2", "1.21.11", "1.21.4", "1.20.6"};
        assertEquals("26.2.1", PanoramaPicker.nextChoice(choices, PanoramaPicker.DEFAULT), "the newest family is what Default shows: it comes last");
        assertEquals("1.21.11", PanoramaPicker.nextChoice(choices, "26.2.1"));
        assertEquals("1.21.11", PanoramaPicker.nextChoice(choices, "26.2"), "a hotfix picked in the settings steps like its family");
        assertEquals("1.20.6", PanoramaPicker.nextChoice(choices, "1.21.4"));
        assertEquals("26.3", PanoramaPicker.nextChoice(choices, "1.20.6"));
        assertEquals(PanoramaPicker.DEFAULT, PanoramaPicker.nextChoice(choices, "26.3"));
        assertEquals(PanoramaPicker.DEFAULT, PanoramaPicker.nextChoice(new String[] {PanoramaPicker.DEFAULT}, PanoramaPicker.DEFAULT), "no list yet");
        assertEquals(PanoramaPicker.DEFAULT, PanoramaPicker.nextChoice(choices, "1.2.5"), "a pick the list lost goes back to Default");
    }

    /** Newer client jars hold 1x1 placeholder panoramas (26.2 showed as flat grey): the real images are in the asset index. */
    @Test void newerReleasesTakeThePanoramaFromTheAssetIndexNotTheJarsPlaceholders() throws Exception {
        String hash = "0123456789abcdef0123456789abcdef0123456";
        StringBuilder objects = new StringBuilder();
        for (int i = 0; i < 6; i++) objects.append(i == 0 ? "" : ",").append("\"minecraft/textures/gui/title/background/panorama_").append(i)
            .append(".png\":{\"hash\":\"").append(hash).append(i).append("\",\"size\":547239}");
        assertEquals(hash + 5, PanoramaPicker.assetHashes("{\"objects\":{" + objects + "}}")[5]);
        assertNull(PanoramaPicker.assetHashes("{\"objects\":{\"minecraft/lang/en_gb.json\":{\"hash\":\"ab\",\"size\":1}}}"), "1.13 and older: in the jar");
        assertNull(PanoramaPicker.assetHashes("{\"objects\":{" + objects.toString().replace(hash + 3, "../../evil") + "}}"), "a hash is 40 hex digits");
        assertNull(PanoramaPicker.assetHashes("{}"));

        assertFalse(PanoramaPicker.decodes(new byte[][] {png(1), png(1), png(1), png(1), png(1), png(1)}), "the jar's 1x1 placeholders");
        assertTrue(PanoramaPicker.decodes(new byte[][] {png(8), png(8), png(8), png(8), png(8), png(8)}));
        assertFalse(PanoramaPicker.decodes(new byte[][] {png(8), png(8), png(4), png(8), png(8), png(8)}), "one size for all six");
    }

    private static byte[] png(int size) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
