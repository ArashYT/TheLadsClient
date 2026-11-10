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
}
