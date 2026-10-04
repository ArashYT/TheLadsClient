package com.thelads.core.client;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicLightsTest {
    private final DynamicLights lights = new DynamicLights();
    private final List<String> rebuilt = new ArrayList<>();

    private void tick(float radius, float minMove, int everyTicks, double[]... sources) {
        lights.begin();
        for (double[] s : sources) lights.add((int) s[0], s[1], s[2], s[3], (int) s[4]);
        lights.end(radius, minMove, everyTicks, (a, b, c, d, e, f) -> rebuilt.add(a + "," + b + "," + c + ".." + d + "," + e + "," + f));
    }

    @Test void lightFadesEvenlyToNothingAtTheRadius() {
        assertEquals(0, lights.at(0, 64, 0), "no sources, no light");
        assertEquals(0, DynamicLights.WORLD.at(0, 64, 0), "the shared field starts empty too");
        tick(15, 0.25f, 1, new double[] {1, 0.5, 64.5, 0.5, 14});
        assertEquals(14, lights.at(0, 64, 0), "the source's own block gets its luminance");
        assertEquals(7, lights.at(7, 64, 0), "half way out: 14 * (1 - 7/15) = 7.47");
        assertEquals(0, lights.at(15, 64, 0), "nothing at the radius");
        assertEquals(0, lights.at(0, 64, 40));
        tick(30, 0.25f, 1, new double[] {1, 0.5, 64.5, 0.5, 14});
        assertEquals(11, lights.at(7, 64, 0), "a wider radius reaches further: 14 * (1 - 7/30) = 10.7");
        tick(15, 0.25f, 1, new double[] {1, 0.5, 64.5, 0.5, 14}, new double[] {2, 8.5, 64.5, 0.5, 10});
        assertEquals(10, lights.at(8, 64, 0), "the brightest source wins");
        tick(15, 0.25f, 1, new double[] {1, 0.5, 64.5, 0.5, 40});
        assertEquals(15, lights.at(0, 64, 0), "light never passes 15");
    }

    @Test void rebuildsOnlyAroundLightsThatChanged() {
        tick(15, 0.25f, 1, new double[] {7, 0.5, 64.5, 0.5, 14});
        assertEquals(List.of("-1,3,-1..1,5,1"), rebuilt, "a new light: its sections plus the one-block smooth-lighting margin");
        rebuilt.clear();
        tick(15, 0.25f, 1, new double[] {7, 0.6, 64.5, 0.5, 14});
        assertEquals(List.of(), rebuilt, "moved less than a quarter block: nothing rebuilt");
        assertEquals(14, lights.at(0, 64, 0));
        tick(15, 0.25f, 1, new double[] {7, 40.5, 64.5, 0.5, 14});
        assertEquals(List.of("-1,3,-1..1,5,1", "1,3,-1..3,5,1"), rebuilt, "moved: the old place and the new");
        assertEquals(0, lights.at(0, 64, 0), "the old place is dark");
        rebuilt.clear();
        tick(15, 0.25f, 1, new double[] {7, 40.5, 64.5, 0.5, 9});
        assertEquals(List.of("1,3,-1..3,5,1", "1,3,-1..3,5,1"), rebuilt, "a new luminance rebuilds in place");
        rebuilt.clear();
        tick(15, 0.25f, 1);
        assertEquals(List.of("1,3,-1..3,5,1"), rebuilt, "a light that went: its place goes dark");
        assertEquals(0, lights.at(40, 64, 0));
        rebuilt.clear();
        tick(15, 0.25f, 1);
        assertEquals(List.of(), rebuilt, "nothing left to rebuild");
    }

    @Test void newRadiusRebuildsEveryLight() {
        tick(15, 0.25f, 1, new double[] {1, 0.5, 64.5, 0.5, 14});
        rebuilt.clear();
        tick(30, 0.25f, 1, new double[] {1, 0.5, 64.5, 0.5, 14});
        assertEquals(List.of("-1,3,-1..1,5,1", "-2,2,-2..1,5,1"), rebuilt, "old reach, then the new one");
    }

    @Test void fastQualityRebuildsEveryFewTicksButLookupsFollowEachTick() {
        tick(15, 0.5f, 4, new double[] {1, 0.5, 64.5, 0.5, 14});
        assertEquals(List.of(), rebuilt, "ticks 1-3 of 4 publish without rebuilding");
        tick(15, 0.5f, 4, new double[] {1, 30.5, 64.5, 0.5, 14});
        assertEquals(14, lights.at(30, 64, 0), "entity lighting reads this tick's place at once");
        tick(15, 0.5f, 4, new double[] {1, 30.5, 64.5, 0.5, 14});
        tick(15, 0.5f, 4, new double[] {1, 30.5, 64.5, 0.5, 14});
        assertEquals(List.of("0,3,-1..2,5,1"), rebuilt, "the fourth tick rebuilds once, where the light is now");
    }

    @Test void resetForgetsWithoutRebuildingAndSourcesAreCapped() {
        tick(15, 0.25f, 1, new double[] {1, 0.5, 64.5, 0.5, 14});
        lights.reset();
        assertEquals(0, lights.at(0, 64, 0));
        rebuilt.clear();
        tick(15, 0.25f, 1);
        assertEquals(List.of(), rebuilt, "a new level is built afresh: the old light needs no rebuild");
        lights.begin();
        for (int i = 0; i < DynamicLights.MAX_SOURCES + 10; i++) lights.add(i, i * 100 + 0.5, 64.5, 0.5, 12);
        lights.add(-1, 0.5, 64.5, 0.5, 0);
        rebuilt.clear();
        lights.end(15, 0.25f, 1, (a, b, c, d, e, f) -> rebuilt.add(String.valueOf(a)));
        assertEquals(DynamicLights.MAX_SOURCES, rebuilt.size(), "only the first " + DynamicLights.MAX_SOURCES + " count");
        assertEquals(0, lights.at((DynamicLights.MAX_SOURCES + 5) * 100, 64, 0));
    }
}
