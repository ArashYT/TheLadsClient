package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Freelook, ViewModel, Time Changer, Weather Changer and Fog Customizer: the logic every version's hooks share. */
class PvpViewModulesTest {
    private static final class Server extends DefaultGameBridge {
        String address = "Singleplayer";
        @Override public String getServerAddress() { return address; }
        @Override public boolean isIngame() { return true; }
    }

    @TempDir java.nio.file.Path tmp;
    private final Server server = new Server();

    @BeforeEach
    void setUp() {
        HypixelSafetyManager.setBypassFile(tmp.resolve("server_bypasses.json").toFile());
        LadsGameBridge.set(server);
    }

    @AfterEach
    void tearDown() {
        LadsGameBridge.set(new DefaultGameBridge());
        HypixelSafetyManager.setBypassFile(null);
    }

    @Test
    void freelookOrbitsFromTheAimAndGivesThePlayersPerspectiveBack() {
        FreelookModule look = new FreelookModule();
        look.setEnabled(true);
        assertEquals(FreelookModule.FIRST_PERSON, look.perspective(FreelookModule.FIRST_PERSON, 30, 10), "no key: the player's own view");
        assertFalse(look.turn(5, 5), "not detached: the mouse turns the player");

        look.key(true);
        assertEquals(FreelookModule.BACK, look.perspective(FreelookModule.FIRST_PERSON, 30, 10));
        assertTrue(look.detached());
        assertEquals(30, look.yaw());
        assertEquals(10, look.pitch(), "the camera starts where the player aims");
        assertTrue(look.turn(20, 100));
        assertEquals(50, look.yaw());
        assertEquals(90, look.pitch(), "pitch stops straight down");
        assertEquals(FreelookModule.BACK, look.perspective(FreelookModule.BACK, 0, 0), "held: nothing changes");

        look.snapKey(true);
        assertEquals(FreelookModule.FRONT, look.perspective(FreelookModule.BACK, 30, 10), "Snaplook wins while held");
        assertFalse(look.detached(), "the front view follows the player");
        look.snapKey(false);
        look.key(false);
        assertEquals(FreelookModule.FIRST_PERSON, look.perspective(FreelookModule.FRONT, 30, 10), "both up: back to first person");
        assertFalse(look.detached());
    }

    @Test
    void freelookToggleInvertAndServerRules() {
        FreelookModule look = new FreelookModule();
        look.setEnabled(true);
        look.mode.setIndex(1); // Toggle
        look.invertPitch.set(true);
        look.key(true);
        look.key(false);
        assertEquals(FreelookModule.BACK, look.perspective(FreelookModule.FRONT, 0, 0), "toggle: on after a press and release");
        look.turn(0, 30);
        assertEquals(-30, look.pitch(), "Invert Pitch turns the camera up for a downward mouse move");

        server.address = "play.somesmp.net"; // unknown rules: off at once, the player's own perspective back
        assertEquals(FreelookModule.FRONT, look.perspective(FreelookModule.BACK, 0, 0));
        assertFalse(look.detached());
        assertEquals(HypixelSafetyManager.Block.BANNED, blockAt("mc.hypixel.net", FreelookModule.NAME));
        assertEquals(HypixelSafetyManager.Block.NONE, blockAt("localhost", FreelookModule.NAME));
    }

    @Test
    void viewModelScalesTheHandFovOnlyWhileOn() {
        ViewModelModule vm = new ViewModelModule();
        vm.handFov.setValue(105);
        assertEquals(80, vm.handFov(80), 1e-4, "off: the game's own");
        assertFalse(vm.posed());
        vm.setEnabled(true);
        assertEquals(120, vm.handFov(80), 1e-4, "105 of vanilla's 70: 1.5x, on top of zoom or water");
        assertFalse(vm.posed(), "default sliders: no matrix work");
        vm.scale.setValue(50);
        assertTrue(vm.posed());
    }

    @Test
    void timeChangerKeepsTheDayAndPicksTheHour() {
        assertEquals(6000, TimeChangerModule.fixed(0, 12), "noon");
        assertEquals(18000, TimeChangerModule.fixed(0, 0), "midnight");
        assertEquals(0, TimeChangerModule.fixed(0, 6), "Minecraft's day starts at 06:00");
        assertEquals(18000, TimeChangerModule.fixed(0, 24), "24:00 is midnight");
        assertEquals(5 * 24000 + 12000, TimeChangerModule.fixed(5 * 24000 + 23999, 18), "the real day stays (moon phase)");
        assertEquals(-24000 + 6000, TimeChangerModule.fixed(-1, 12), "negative times round down to their day");
        TimeChangerModule changer = new TimeChangerModule();
        changer.hour.setValue(13.5);
        assertEquals("13:30", changer.hour.display());
        assertEquals(24000 + 7500, changer.time(24000 + 100));
    }

    @Test
    void weatherChangerForcesLevelsAndWhatFalls() {
        int none = WeatherChangerModule.FALLS_NONE, rain = WeatherChangerModule.FALLS_RAIN, snow = WeatherChangerModule.FALLS_SNOW;
        assertEquals(0, WeatherChangerModule.rain(WeatherChangerModule.CLEAR));
        assertEquals(1, WeatherChangerModule.rain(WeatherChangerModule.THUNDER));
        assertEquals(1, WeatherChangerModule.thunder(WeatherChangerModule.THUNDER));
        assertEquals(0, WeatherChangerModule.thunder(WeatherChangerModule.SNOW));
        assertEquals(snow, WeatherChangerModule.precipitation(WeatherChangerModule.SNOW, none), "snow in a desert too");
        assertEquals(snow, WeatherChangerModule.precipitation(WeatherChangerModule.SNOW, rain));
        assertEquals(rain, WeatherChangerModule.precipitation(WeatherChangerModule.RAIN, none), "rain in a dry biome");
        assertEquals(snow, WeatherChangerModule.precipitation(WeatherChangerModule.THUNDER, snow), "a snowy biome keeps its snow");
        assertEquals(none, WeatherChangerModule.precipitation(-1, none), "off: the biome's own");
        WeatherChangerModule changer = new WeatherChangerModule();
        changer.weather.setIndex(WeatherChangerModule.SNOW);
        assertEquals(-1, changer.forced());
        changer.setEnabled(true);
        assertEquals(WeatherChangerModule.SNOW, changer.forced());
    }

    @Test
    void fogCustomizerScalesAndGatesTheSeeThroughOptions() {
        FogCustomizerModule fog = new FogCustomizerModule();
        fog.disable.set(true);
        assertFalse(fog.clears(FogCustomizerModule.TERRAIN), "off: vanilla fog");
        assertEquals(96, fog.start(96));
        fog.setEnabled(true);
        assertTrue(fog.clears(FogCustomizerModule.TERRAIN));
        fog.fogStart.setValue(50);
        fog.fogEnd.setValue(10);
        float start = fog.start(96);
        assertEquals(48, start, 1e-4);
        assertEquals(48.1f, fog.end(128, start), 1e-4, "the end never comes before the start");

        fog.clearWater.set(true);
        fog.clearLava.set(true);
        fog.clearEffects.set(true);
        assertTrue(fog.clears(FogCustomizerModule.LAVA), "singleplayer: allowed");
        server.address = "play.somesmp.net";
        assertTrue(fog.clears(FogCustomizerModule.WATER), "clear water is visual only (OptiFine's Clear Water)");
        assertFalse(fog.clears(FogCustomizerModule.LAVA), "unknown rules: lava fog stays");
        assertFalse(fog.clears(FogCustomizerModule.EFFECT), "unknown rules: Blindness/Darkness fog stays");
        assertTrue(fog.isEnabled(), "the module itself is visual only");

        assertEquals(FogCustomizerModule.LAVA, FogCustomizerModule.medium(true, true, true));
        assertEquals(FogCustomizerModule.EFFECT, FogCustomizerModule.medium(false, true, true), "Blindness under water is Blindness fog");
        assertEquals(FogCustomizerModule.WATER, FogCustomizerModule.medium(false, false, true));
        assertEquals(FogCustomizerModule.TERRAIN, FogCustomizerModule.medium(false, false, false));
    }

    private HypixelSafetyManager.Block blockAt(String address, String module) {
        server.address = address;
        return HypixelSafetyManager.blockOf(module);
    }
}
