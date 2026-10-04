package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.SliderOption;
import org.junit.jupiter.api.Test;

class CustomFovModuleTest {
    private static final double EPS = 1e-9;

    /** The game's FOV modifier for a speed (walking speed 0.1, as both 1.8.9 and 26.x compute it): (speed / walk + 1) / 2. */
    private static double speedTerm(double speed) { return (speed / 0.1 + 1) / 2; }

    @Test
    void defaultsKeepEveryVanillaChange() {
        CustomFovModule fov = new CustomFovModule();
        assertTrue(fov.isEnabled());
        for (String change : new String[] {"Sprinting", "Speed Effects", "Flying", "Bow Aiming", "Spyglass", "Underwater"})
            assertEquals(1, fov.share(change), EPS, change + " starts at 100%: the game's own FOV");
        assertEquals(1.1, CustomFovModule.scaled(1.1, 1), EPS);
        assertEquals(0.1, CustomFovModule.scaled(0.1, 1), EPS, "the spyglass");
        assertEquals(0.13, CustomFovModule.speed(0.13, 1.3, 1, 1, 1), EPS, "sprinting speed unchanged");
    }

    @Test
    void zeroPercentRemovesTheChangeAndFiftyHalvesIt() {
        assertEquals(1, CustomFovModule.scaled(1.1, 0), EPS, "flying at 0%: no wider FOV");
        assertEquals(1.05, CustomFovModule.scaled(1.1, .5), EPS);
        assertEquals(1, CustomFovModule.scaled(0.1, 0), EPS, "spyglass at 0%: no zoom");
        assertEquals(0.55, CustomFovModule.scaled(0.1, .5), EPS);
        assertEquals(6 / 7.0 + (1 - 6 / 7.0) * .5, CustomFovModule.scaled(6 / 7.0, .5), EPS, "under water");
    }

    @Test
    void speedScalesSprintAndEffectsApartAndKeepsTheRest() {
        double walk = 0.1, sprint = 1.3, speedII = 1.4, slowness = 0.85;
        double sprinting = walk * sprint;
        assertEquals(1.15, speedTerm(CustomFovModule.speed(sprinting, sprint, 1, 1, 1)), EPS, "vanilla sprint FOV");
        assertEquals(1.0, speedTerm(CustomFovModule.speed(sprinting, sprint, 1, 0, 1)), EPS, "sprinting at 0%: walking FOV");
        assertEquals(1.075, speedTerm(CustomFovModule.speed(sprinting, sprint, 1, .5, 1)), EPS);
        double both = walk * sprint * speedII;
        assertEquals(walk * speedII, CustomFovModule.speed(both, sprint, speedII, 0, 1), EPS, "Speed II kept, sprint removed");
        assertEquals(walk * sprint, CustomFovModule.speed(both, sprint, speedII, 1, 0), EPS, "sprint kept, Speed II removed");
        assertEquals(walk, CustomFovModule.speed(walk * slowness, 1, slowness, 1, 0), EPS, "Slowness counts as an effect");
        // Something else (soul speed, powder snow) stays: only the named multipliers are divided out.
        assertEquals(walk * 1.5, CustomFovModule.speed(walk * 1.5 * sprint, sprint, 1, 0, 0), EPS);
        assertEquals(0, CustomFovModule.speed(0, 1, 0, 1, 0), EPS, "Slowness VII stops the player: no division by zero");
    }

    @Test
    void staticFovAndTheModuleSwitch() {
        CustomFovModule fov = new CustomFovModule();
        ((SliderOption) fov.getOption("Flying")).setValue(40);
        assertEquals(.4, fov.share("Flying"), EPS);
        ((BoolOption) fov.getOption("Static FOV")).set(true);
        assertEquals(0, fov.share("Flying"), EPS, "Static FOV removes every change");
        assertEquals(0, fov.share("Sprinting"), EPS);
        fov.setEnabled(false);
        assertEquals(1, fov.share("Flying"), EPS, "off: the game's own FOV");
    }
}
