package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Camera, fx, lock, waypoints, telegraphs: pure "value at time t" functions with an explicit clock, no sleeping. */
class MinigameViewStatesTest {
    private static final Gson GSON = new Gson();
    private static final String ENTITY = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final CameraState.Own OWN = new CameraState.Own(0, 64, 0, 10, 5, 70);

    @AfterEach
    void tearDown() {
        Hooks.reset();
    }

    private static JsonObject j(String s) {
        return GSON.fromJson(s, JsonObject.class);
    }

    // ---- camera ----

    private static final String SPLINE = "{\"t\":\"camera\",\"mode\":\"spline\",\"ms\":5000,\"ease\":\"%s\",\"bars\":true,\"hide_hud\":true,"
        + "\"points\":[{\"x\":0,\"y\":64,\"z\":0,\"yaw\":0,\"pitch\":0},{\"x\":10,\"y\":64,\"z\":0,\"yaw\":90,\"pitch\":20}]}";

    @Test
    void splineEndsAndMidpoint() {
        CameraState c = new CameraState();
        c.apply(j(SPLINE.formatted("linear")), 1000);
        assertTrue(c.active(1000));
        CameraState.Pose p0 = c.poseAt(1000, OWN, null);
        assertEquals(0, p0.x(), 1e-9);
        assertEquals(70, p0.fov(), 1e-9); // the player's own fov
        CameraState.Pose mid = c.poseAt(3500, OWN, null);
        assertEquals(5, mid.x(), 1e-9);
        assertEquals(45, mid.yaw(), 1e-9);
        assertEquals(10, mid.pitch(), 1e-9);
        CameraState.Pose late = c.poseAt(5999, OWN, null);
        assertEquals(10, late.x(), 0.05);
        assertNull(c.poseAt(6000, OWN, null)); // the view returns at once
        assertFalse(c.active(6000));
    }

    @Test
    void smoothEasingStartsSlower() {
        CameraState lin = new CameraState(), smooth = new CameraState();
        lin.apply(j(SPLINE.formatted("linear")), 0);
        smooth.apply(j(SPLINE.formatted("smooth")), 0);
        assertTrue(smooth.poseAt(1250, OWN, null).x() < lin.poseAt(1250, OWN, null).x());
        assertEquals(lin.poseAt(2500, OWN, null).x(), smooth.poseAt(2500, OWN, null).x(), 1e-9);
    }

    @Test
    void splineYawTakesTheShorterArc() {
        CameraState c = new CameraState();
        c.apply(j("{\"mode\":\"spline\",\"ms\":1000,\"points\":[{\"x\":0,\"y\":0,\"z\":0,\"yaw\":350},{\"x\":1,\"y\":0,\"z\":0,\"yaw\":10}]}"), 0);
        double yaw = c.poseAt(500, OWN, null).yaw();
        assertTrue(Math.abs(((yaw % 360) + 360) % 360) < 1e-6 || Math.abs(yaw - 360) < 1e-6, "yaw " + yaw);
    }

    @Test
    void splineMissingYawRepeatsThePreviousPointsAndTheFirstTakesTheplayers() {
        CameraState c = new CameraState();
        c.apply(j("{\"mode\":\"spline\",\"ms\":1000,\"points\":[{\"x\":0,\"y\":0,\"z\":0},{\"x\":1,\"y\":0,\"z\":0}]}"), 0);
        assertEquals(10, c.poseAt(500, OWN, null).yaw(), 1e-9);
    }

    @Test
    void orbitStartsOnPlusZAndTurnsCounterClockwiseOrClockwise() {
        CameraState c = new CameraState();
        c.apply(j("{\"mode\":\"orbit\",\"x\":0,\"y\":65,\"z\":0,\"radius\":6,\"height\":2,\"period_ms\":8000,\"ms\":10000}"), 0);
        CameraState.Pose p = c.poseAt(0, OWN, null);
        assertEquals(0, p.x(), 1e-9);
        assertEquals(6, p.z(), 1e-9);
        assertEquals(67, p.y(), 1e-9);
        assertEquals(180, Math.abs(p.yaw()), 1e-6); // looks toward -Z, at the centre
        CameraState.Pose q = c.poseAt(2000, OWN, null);
        assertEquals(6, q.x(), 1e-9);
        assertEquals(0, q.z(), 1e-9);
        assertEquals(90, q.yaw(), 1e-6); // looks west
        assertTrue(q.pitch() > 0); // looks down at the centre
        CameraState cw = new CameraState();
        cw.apply(j("{\"mode\":\"orbit\",\"x\":0,\"y\":65,\"z\":0,\"clockwise\":true,\"ms\":10000}"), 0);
        assertTrue(cw.poseAt(2000, OWN, null).x() < 0);
        assertEquals(c.poseAt(0, OWN, null).x(), c.poseAt(8000, OWN, null).x(), 1e-6); // one turn per period
    }

    @Test
    void orbitOfAnEntityEndsWhenTheClientDoesNotHaveIt() {
        CameraState c = new CameraState();
        c.apply(j("{\"mode\":\"orbit\",\"entity\":\"" + ENTITY + "\",\"radius\":4,\"ms\":6000}"), 0);
        assertNull(c.poseAt(100, OWN, null)); // no lookup: nothing revealed
        assertFalse(c.active(100)); // and the shot is gone

        c.apply(j("{\"mode\":\"orbit\",\"entity\":\"" + ENTITY + "\",\"radius\":4,\"ms\":6000}"), 0);
        UUID id = UUID.fromString(ENTITY);
        CameraState.Pose p = c.poseAt(0, OWN, u -> u.equals(id) ? new double[] {10, 70, 10} : null);
        assertEquals(10, p.x(), 1e-9);
        assertEquals(14, p.z(), 1e-9);
        assertEquals(73, p.y(), 1e-9); // centre = feet + 1, height 2
        assertNull(c.poseAt(500, OWN, u -> null)); // unloaded mid-shot: ends
        assertFalse(c.active(500));
    }

    @Test
    void fixedLooksAtAPointOrKeepsTheDirectionOfAnUnloadedEntity() {
        CameraState c = new CameraState();
        c.apply(j("{\"mode\":\"fixed\",\"x\":10,\"y\":70,\"z\":10,\"look_at\":{\"x\":0,\"y\":70,\"z\":0},\"ms\":3000,\"fov\":200}"), 0);
        CameraState.Pose p = c.poseAt(10, OWN, null);
        assertEquals(135, p.yaw(), 1e-6);
        assertEquals(0, p.pitch(), 1e-6);
        assertEquals(110, p.fov(), 1e-9); // clamped

        c.apply(j("{\"mode\":\"fixed\",\"x\":0,\"y\":64,\"z\":0,\"look_entity\":\"" + ENTITY + "\",\"ms\":3000}"), 0);
        double[] at = {0, 64, 10};
        double yawLoaded = c.poseAt(10, OWN, u -> at).yaw();
        assertEquals(yawLoaded, c.poseAt(20, OWN, u -> null).yaw(), 1e-9); // kept
        c.apply(j("{\"mode\":\"fixed\",\"x\":0,\"y\":64,\"z\":0,\"yaw\":90,\"pitch\":20,\"ms\":3000}"), 0);
        assertEquals(90, c.poseAt(10, OWN, null).yaw(), 1e-9);
    }

    @Test
    void aPointFarFromThePlayerIsPulledInTo512() {
        CameraState c = new CameraState();
        c.apply(j("{\"mode\":\"fixed\",\"x\":5000,\"y\":64,\"z\":0,\"ms\":3000}"), 0);
        assertEquals(512, c.poseAt(0, OWN, null).x(), 1e-6);
    }

    @Test
    void cameraBadMessagesAreIgnoredOffAndUnknownModeEnd() {
        CameraState c = new CameraState();
        c.apply(j("{\"mode\":\"fixed\",\"x\":1,\"y\":2,\"z\":3,\"ms\":3000}"), 0);
        c.apply(j("{\"mode\":\"fixed\",\"x\":1,\"y\":2,\"ms\":3000}"), 0); // no z: ignored, the running shot stays
        c.apply(j("{\"mode\":\"fixed\",\"x\":1,\"y\":2,\"z\":\"3\",\"ms\":3000}"), 0); // a string is not a number
        c.apply(j("{\"mode\":\"fixed\",\"x\":1,\"y\":2,\"z\":3}"), 0); // no ms
        c.apply(j("{\"mode\":\"spline\",\"ms\":3000,\"points\":[]}"), 0);
        c.apply(j("{\"ms\":3000}"), 0); // no mode
        assertTrue(c.active(1));
        c.apply(j("{\"mode\":\"cinema\",\"ms\":3000}"), 0); // unknown: off
        assertFalse(c.active(1));
        c.apply(j("{\"mode\":\"fixed\",\"x\":1,\"y\":2,\"z\":3,\"ms\":3000}"), 0);
        c.apply(j("{\"mode\":\"off\"}"), 0);
        assertFalse(c.active(1));
    }

    @Test
    void barsFadeAndHudHides() {
        CameraState c = new CameraState();
        c.apply(j(SPLINE.formatted("linear")), 0);
        assertEquals(0, c.barsAt(0), 1e-9);
        assertEquals(0.5, c.barsAt(150), 1e-9);
        assertEquals(1, c.barsAt(2500), 1e-9);
        assertEquals(0.5, c.barsAt(4850), 1e-9);
        assertEquals(0, c.barsAt(5000), 1e-9);
        assertTrue(c.hidesHud(100));
        assertFalse(c.hidesHud(5000));
        assertTrue(CameraState.BARS_HEIGHT > 0 && CameraState.BARS_HEIGHT < 0.5);
    }

    @Test
    void shotLengthIsClampedTo30Seconds() {
        CameraState c = new CameraState();
        c.apply(j("{\"mode\":\"fixed\",\"x\":1,\"y\":2,\"z\":3,\"ms\":999999999}"), 0);
        assertTrue(c.active(29_999));
        assertFalse(c.active(30_000));
    }

    // ---- fx ----

    @Test
    void fxFadePulseAndEnd() {
        FxState f = new FxState();
        f.apply(j("{\"t\":\"fx\",\"ms\":4000,\"fade_ms\":400,\"shader\":\"blur\",\"vignette\":{\"color\":\"#FF0000\",\"intensity\":0.6,"
            + "\"pulse_ms\":800},\"tint\":{\"color\":\"#2244FF\",\"intensity\":0.95}}"), 1000);
        assertEquals(FxState.Shader.BLUR, f.shader(1000));
        assertEquals(0, f.fadeAt(1000), 1e-9);
        assertEquals(0.5, f.fadeAt(1200), 1e-9);
        assertEquals(1, f.fadeAt(3000), 1e-9);
        assertEquals(0.5, f.fadeAt(4800), 1e-9);
        assertEquals(0.6, f.vignette(1000 + 1600).alpha(), 1e-9); // pulse at its peak
        assertEquals(0.3, f.vignette(1000 + 2000).alpha(), 1e-9); // pulse at 50 %
        assertEquals(0xFF0000, f.vignette(3000).rgb());
        assertEquals(0.8, f.tint(3000).alpha(), 1e-9); // never more than 80 %
        assertEquals(FxState.Shader.NONE, f.shader(5000));
        assertNull(f.vignette(5000));
        assertNull(f.tint(5000));
    }

    @Test
    void fxFadeIsAtMostHalfOfMsAndDefaultsApply() {
        FxState f = new FxState();
        f.apply(j("{\"t\":\"fx\",\"ms\":1000,\"fade_ms\":5000,\"vignette\":{}}"), 0);
        assertEquals(1, f.fadeAt(500), 1e-9); // fade 500: full at the middle
        assertEquals(0.5, f.fadeAt(250), 1e-9);
        FxState.Overlay v = f.vignette(500);
        assertEquals(0x000000, v.rgb());
        assertEquals(0.5, v.alpha(), 1e-9); // intensity default 0.5, steady
        FxState g = new FxState();
        g.apply(j("{\"t\":\"fx\",\"ms\":1000,\"tint\":{}}"), 0);
        assertEquals(0.3, g.tint(10).alpha(), 1e-9);
    }

    @Test
    void fxFlashFadesLinearlyAndIsNotState() {
        FxState f = new FxState();
        f.apply(j("{\"t\":\"fx\",\"flash\":{\"color\":\"#FFFFFF\",\"ms\":400}}"), 1000);
        assertNull(f.vignette(1000)); // without a valid ms only the flash plays
        assertEquals(1, f.flash(1000).alpha(), 1e-9);
        assertEquals(0.5, f.flash(1200).alpha(), 1e-9);
        assertNull(f.flash(1400));
        f.apply(j("{\"t\":\"fx\"}"), 1500);
        f.apply(j("{\"t\":\"fx\",\"flash\":{}}"), 2000);
        assertEquals(1, f.flash(2000).alpha(), 1e-9);
        f.apply(j("{\"t\":\"fx\"}"), 2100); // clears at once
        assertNull(f.flash(2100));
    }

    @Test
    void reduceFlashesDropsFlashAndPulse() {
        Hooks.reduceFlashes = () -> true;
        FxState f = new FxState();
        f.apply(j("{\"t\":\"fx\",\"ms\":4000,\"flash\":{\"ms\":400},\"vignette\":{\"intensity\":0.6,\"pulse_ms\":800}}"), 0);
        assertNull(f.flash(0));
        assertEquals(0.6, f.vignette(400).alpha(), 1e-9); // steady, whatever the pulse phase
        assertEquals(0.6, f.vignette(1000).alpha(), 1e-9);
    }

    // ---- lock ----

    @Test
    void lockHoldsForMsOnly() {
        LockState l = new LockState();
        l.apply(j("{\"t\":\"lock\",\"ms\":5000,\"move\":true,\"jump\":true,\"attack\":true,\"use\":true}"), 100);
        assertTrue(l.locked(LockState.Lock.MOVE, 100));
        assertTrue(l.locked(LockState.Lock.USE, 5099));
        assertFalse(l.locked(LockState.Lock.MOVE, 5100)); // ends by itself
        assertFalse(l.locked(LockState.Lock.SNEAK, 200));
        assertFalse(l.locked(LockState.Lock.INVENTORY, 200));
    }

    @Test
    void lockOnlyJsonTrueCountsAndNoMsOrNothingSetUnlocks() {
        LockState l = new LockState();
        l.apply(j("{\"ms\":5000,\"move\":\"true\",\"jump\":1,\"look\":true}"), 0);
        assertFalse(l.locked(LockState.Lock.MOVE, 1));
        assertFalse(l.locked(LockState.Lock.JUMP, 1));
        assertTrue(l.locked(LockState.Lock.LOOK, 1));
        l.apply(j("{\"move\":true}"), 0); // no ms
        assertFalse(l.any(1));
        l.apply(j("{\"ms\":5000,\"move\":true}"), 0);
        l.apply(j("{\"ms\":5000,\"move\":false}"), 0); // all false
        assertFalse(l.any(1));
        l.apply(j("{\"ms\":99999999,\"hotbar\":true,\"drop\":true,\"sprint\":true}"), 0);
        assertTrue(l.locked(LockState.Lock.HOTBAR, 14_999));
        assertFalse(l.locked(LockState.Lock.HOTBAR, 15_000)); // clamped to 15 s
        l.apply(j("{\"t\":\"lock\"}"), 0);
        assertFalse(l.any(1));
    }

    // ---- waypoints ----

    @Test
    void waypointSetReplaceRemoveClear() {
        WaypointState w = new WaypointState();
        w.apply(j("{\"op\":\"set\",\"id\":\"flag_red\",\"x\":10.5,\"y\":65,\"z\":-3.5,\"label\":\"§cRed flag\",\"color\":\"#FF5555\","
            + "\"icon\":\"minecraft:red_banner\",\"beam\":true}"));
        WaypointState.Waypoint p = w.all().get(0);
        assertEquals("Red flag", p.label());
        assertEquals(0xFF5555, p.color());
        assertFalse(p.glyph());
        assertEquals("minecraft:red_banner", p.icon());
        assertTrue(p.distance()); // default true
        assertTrue(p.beam());
        assertFalse(p.throughWalls());
        w.apply(j("{\"op\":\"set\",\"id\":\"flag_red\",\"x\":1,\"y\":2,\"z\":3,\"icon\":\"diamond\"}"));
        assertEquals(1, w.all().size());
        assertTrue(w.all().get(0).glyph());
        assertEquals("diamond", w.all().get(0).icon());
        w.apply(j("{\"op\":\"remove\",\"id\":\"flag_red\"}"));
        assertTrue(w.all().isEmpty());
        w.apply(j("{\"op\":\"set\",\"id\":\"a\",\"x\":1,\"y\":2,\"z\":3,\"icon\":\"red_banner\"}"));
        assertEquals("minecraft:red_banner", w.all().get(0).icon());
        w.apply(j("{\"op\":\"clear\"}"));
        assertTrue(w.all().isEmpty());
    }

    @Test
    void waypointBadItemsAreIgnoredAndMaxIs64() {
        WaypointState w = new WaypointState();
        w.apply(j("{\"id\":\"a\",\"x\":1,\"y\":2,\"z\":3}")); // no op
        w.apply(j("{\"op\":\"set\",\"id\":\"BAD ID\",\"x\":1,\"y\":2,\"z\":3}"));
        w.apply(j("{\"op\":\"set\",\"id\":\"nopos\"}"));
        w.apply(j("{\"op\":\"teleport\",\"id\":\"a\",\"x\":1,\"y\":2,\"z\":3}"));
        w.apply(j("{\"op\":\"set\",\"id\":\"url\",\"x\":1,\"y\":2,\"z\":3,\"icon\":\"https://evil.example/x.png\"}"));
        assertEquals(1, w.all().size()); // only the url one: its bad icon falls back to the diamond glyph
        assertEquals("diamond", w.all().get(0).icon());
        w.clear();
        for (int i = 0; i < 70; i++) w.apply(j("{\"op\":\"set\",\"id\":\"w" + i + "\",\"x\":1,\"y\":2,\"z\":3}"));
        assertEquals(64, w.all().size());
        w.apply(j("{\"op\":\"set\",\"id\":\"w3\",\"x\":9,\"y\":2,\"z\":3}")); // replacing at the cap works
        assertEquals(9, w.all().stream().filter(p -> p.id().equals("w3")).findFirst().orElseThrow().x(), 1e-9);
    }

    @Test
    void entityWaypointWinsAndOnlyUsesTheClientsOwnEntityLookup() {
        WaypointState w = new WaypointState();
        w.apply(j("{\"op\":\"set\",\"id\":\"mate\",\"entity\":\"" + ENTITY + "\",\"x\":1,\"y\":2,\"z\":3,\"through_walls\":true}"));
        WaypointState.Waypoint p = w.all().get(0);
        assertEquals(UUID.fromString(ENTITY), p.entity());
        assertNull(WaypointState.position(p, null)); // no lookup: no position
        assertNull(WaypointState.position(p, u -> null)); // not loaded: draws nothing, learns nothing
        double[] at = {7, 8, 9};
        assertArrayEquals(at, WaypointState.position(p, u -> at));
        Hooks.throughWallsAllowed = () -> true;
        assertFalse(WaypointState.throughWalls(p)); // ignored for entity waypoints
        w.apply(j("{\"op\":\"set\",\"id\":\"c\",\"x\":1,\"y\":2,\"z\":3,\"through_walls\":true}"));
        WaypointState.Waypoint c = w.all().get(1);
        assertTrue(WaypointState.throughWalls(c));
        Hooks.throughWallsAllowed = () -> false; // the client's own safety rules say no
        assertFalse(WaypointState.throughWalls(c));
        assertArrayEquals(new double[] {1, 2, 3}, WaypointState.position(c, null));
    }

    // ---- telegraphs ----

    @Test
    void telegraphDefaultsFillAndExpiry() {
        TelegraphState t = new TelegraphState();
        t.apply(j("{\"op\":\"set\",\"id\":\"slam1\",\"shape\":\"circle\",\"x\":10.5,\"y\":64,\"z\":8.5,\"radius\":4,\"color\":\"#FF0000\","
            + "\"ms\":1500,\"fill_ms\":1200}"), 1000);
        TelegraphState.Telegraph g = t.active(1000).get(0);
        assertEquals(0, g.fillAt(1000), 1e-9);
        assertEquals(0.5, g.fillAt(1600), 1e-9);
        assertEquals(1, g.fillAt(2200), 1e-9); // complete when the hit lands
        assertEquals(1, t.active(2499).size());
        assertTrue(t.active(2500).isEmpty()); // gone after ms
        t.apply(j("{\"shape\":\"ring\",\"id\":\"r\",\"x\":0,\"y\":0,\"z\":0,\"radius\":8,\"ms\":100}"), 0); // op defaults to set
        TelegraphState.Telegraph r = t.active(0).get(0);
        assertEquals(6, r.inner(), 1e-9);
        assertEquals(1, r.fillAt(0), 1e-9); // fill_ms 0 = filled at once
        t.apply(j("{\"shape\":\"cone\",\"id\":\"c\",\"x\":0,\"y\":0,\"z\":0,\"ms\":100}"), 0);
        assertEquals(90, t.active(0).get(1).angle(), 1e-9);
        t.apply(j("{\"shape\":\"line\",\"id\":\"l\",\"x\":0,\"y\":0,\"z\":0,\"yaw\":90,\"length\":200,\"ms\":100}"), 0);
        TelegraphState.Telegraph l = t.active(0).get(2);
        assertEquals(64, l.length(), 1e-9);
        assertEquals(1, l.width(), 1e-9);
    }

    @Test
    void telegraphNeedsShapeMsAndPositionAndMaxIs64() {
        TelegraphState t = new TelegraphState();
        t.apply(j("{\"id\":\"a\",\"x\":0,\"y\":0,\"z\":0,\"ms\":500}"), 0); // no shape
        t.apply(j("{\"id\":\"a\",\"shape\":\"circle\",\"x\":0,\"y\":0,\"z\":0}"), 0); // no ms
        t.apply(j("{\"id\":\"a\",\"shape\":\"circle\",\"y\":0,\"z\":0,\"ms\":500}"), 0); // no x
        t.apply(j("{\"id\":\"a\",\"shape\":\"star\",\"x\":0,\"y\":0,\"z\":0,\"ms\":500}"), 0); // unknown shape
        assertTrue(t.active(0).isEmpty());
        for (int i = 0; i < 70; i++) t.apply(j("{\"id\":\"t" + i + "\",\"shape\":\"circle\",\"x\":0,\"y\":0,\"z\":0,\"ms\":500}"), 0);
        assertEquals(64, t.active(0).size());
        t.apply(j("{\"id\":\"late\",\"shape\":\"circle\",\"x\":0,\"y\":0,\"z\":0,\"ms\":500}"), 600); // the old ones ran out first
        assertEquals(1, t.active(600).size());
        t.apply(j("{\"op\":\"remove\",\"id\":\"late\"}"), 600);
        assertTrue(t.active(600).isEmpty());
    }
}
