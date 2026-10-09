package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ParticleReductionOption;
import com.thelads.core.modules.ParticleCoreModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ParticleCoreTest {
    /** A 90 degree, 16:9 perspective projection (near 0.05, far 256) in column-major order, as OpenGL and JOML store it. */
    private static float[] projection() {
        float n = 0.05f, f = 256, aspect = 16 / 9f;
        float[] m = new float[16];
        m[0] = 1 / aspect; m[5] = 1; m[10] = -(f + n) / (f - n); m[11] = -1; m[14] = -2 * f * n / (f - n);
        return m;
    }
    private static float[] identity() { return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }

    private final ParticleCoreModule core = (ParticleCoreModule) com.thelads.core.config.ModuleManager.getInstance().getModule(ParticleCore.MODULE);

    @AfterEach void restore() {
        core.rates.reset(); core.hideOwn.set(false); core.hideOthers.set(false); core.limit.set(false); core.setEnabled(true);
        ParticleCore.standDown(false); ParticleCore.worldChanged(); ParticleCore.resetStats(); ParticleCore.refresh();
    }

    @Test void frustumKeepsWhatIsInViewAndDropsWhatIsNot() {
        var frustum = new ParticleFrustum();
        assertTrue(frustum.visible(0, 0, 50, 0), "without a frustum nothing is culled");
        frustum.set(projection(), identity());
        assertTrue(frustum.isValid());
        assertTrue(frustum.visible(0, 0, -5, 0.1), "straight ahead");
        assertFalse(frustum.visible(0, 0, 5, 0.1), "behind the camera");
        assertFalse(frustum.visible(0, 0, -300, 0.1), "past the far plane");
        assertFalse(frustum.visible(0, 8, -5, 0.1), "above the 90 degree vertical view");
        double edge = 5 * 16 / 9.0; // the horizontal half-width of the view at 5 blocks
        assertTrue(frustum.visible(edge - 0.1, 0, -5, 0.01), "just inside the edge");
        assertFalse(frustum.visible(edge + 0.5, 0, -5, 0.1), "just outside the edge");
        assertTrue(frustum.visible(edge + 0.5, 0, -5, 1), "a big particle whose centre is outside but whose quad reaches in");
        frustum.clear();
        assertTrue(frustum.visible(0, 0, 5, 0.1), "cleared: nothing is culled");
    }

    @Test void weatherColumnsBehindOrBesideTheViewAreCulled() {
        var frustum = new ParticleFrustum();
        assertTrue(frustum.visibleCylinder(0, 0, 5, 1, 11), "without a frustum nothing is culled");
        frustum.set(projection(), identity());
        assertTrue(frustum.visibleCylinder(0.5, 0, -6.5, 1, 11), "ahead");
        assertTrue(frustum.visibleCylinder(0.5, 0, 0.5, 1, 11), "the camera's own column");
        assertFalse(frustum.visibleCylinder(0.5, 0, 6.5, 1, 11), "behind");
        double edge = 5 * 16 / 9.0;
        assertTrue(frustum.visibleCylinder(edge + 0.5, 0, -5, 1, 11), "centre outside the edge, quad reaching in");
        assertFalse(frustum.visibleCylinder(edge + 3, 0, -5, 1, 11), "wholly outside the edge");
        assertTrue(frustum.visibleCylinder(0, 30, -20, 1, 11), "below the column top in view: the tall column reaches in");
        assertFalse(frustum.visibleCylinder(0, 30, -5, 1, 11), "wholly above the 90 degree view");
    }

    @Test void frustumFollowsTheViewMatrix() {
        var frustum = new ParticleFrustum();
        float[] turnedAround = {-1, 0, 0, 0, 0, 1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1}; // yaw 180 degrees
        frustum.set(projection(), turnedAround);
        assertTrue(frustum.visible(0, 0, 5, 0.1));
        assertFalse(frustum.visible(0, 0, -5, 0.1));
    }

    @Test void aDegenerateMatrixNeverCulls() {
        var frustum = new ParticleFrustum();
        frustum.set(new float[16], identity());
        assertFalse(frustum.isValid());
        assertTrue(frustum.visible(1e6, 0, 0, 0));
    }

    @Test void theShareThatSpawnsMatchesThePercentage() {
        assertTrue(ParticleCore.pass(100, 99));
        assertFalse(ParticleCore.pass(0, 0));
        assertTrue(ParticleCore.pass(1, 0));
        assertFalse(ParticleCore.pass(1, 1));
        for (int percent : new int[] {10, 33, 50, 90}) {
            int passed = 0, draws = 200_000;
            for (int i = 0; i < draws; i++) if (ParticleCore.pass(percent, ParticleCore.roll())) passed++;
            assertEquals(percent / 100.0, passed / (double) draws, 0.01, percent + "%");
        }
    }

    @Test void perTypeRatesAndPotionSwirlsDecideAtSpawn() {
        ParticleCore.refresh();
        assertTrue(ParticleCore.allowSpawn("flame", false, 0, 1), "vanilla by default");
        core.rates.set("minecraft:flame", 0);
        core.rates.set("smoke", 50);
        ParticleCore.refresh();
        for (int i = 0; i < 100; i++) assertFalse(ParticleCore.allowSpawn("flame", false, 0, 1), "0% is off");
        int smoke = 0;
        for (int i = 0; i < 20_000; i++) if (ParticleCore.allowSpawn("smoke", false, 0, 1)) smoke++;
        assertEquals(10_000, smoke, 400);
        assertTrue(ParticleCore.allowSpawn("crit", false, 0, 1), "types never edited are untouched");
        assertTrue(ParticleCore.allowSpawn("entity_effect", true, 0, 1));
        core.hideOwn.set(true); ParticleCore.refresh();
        assertFalse(ParticleCore.allowSpawn("entity_effect", true, 0, 1), "own swirls hidden");
        assertTrue(ParticleCore.allowSpawn("entity_effect", false, 0, 1), "others' swirls stay");
        assertFalse(ParticleCore.allowSpawn("ambient_entity_effect", true, 0, 1));
        core.hideOthers.set(true); ParticleCore.refresh();
        assertFalse(ParticleCore.allowSpawn("mobspell", false, 0, 1), "1.8.9 names count too");
        assertTrue(ParticleCore.allowSpawn("crit", true, 0, 1), "only potion swirls are hidden");
    }

    @Test void theDecorativeQuotaOnlyLimitsDecorations() {
        core.limit.set(true);
        core.perTick.setValue(8);
        ParticleCore.refresh();
        int smoke = 0;
        for (int i = 0; i < 20; i++) if (ParticleCore.allowSpawn("smoke", false, 0, 7)) smoke++;
        assertEquals(8, smoke);
        for (int i = 0; i < 20; i++) assertTrue(ParticleCore.allowSpawn("crit", false, 0, 7), "gameplay particles never count");
        assertFalse(ParticleCore.allowSpawn("ash", false, 49.0 * 49.0, 8), "past the distance");
        assertTrue(ParticleCore.allowSpawn("smoke", false, 0, 8), "a new tick, a new quota");
    }

    @Test void standingDownOrSwitchingOffLeavesEveryParticleAlone() {
        core.rates.set("flame", 0);
        ParticleCore.standDown(true);
        assertTrue(ParticleCore.allowSpawn("flame", false, 0, 1));
        assertFalse(ParticleCore.culling());
        ParticleCore.standDown(false);
        core.setEnabled(false); ParticleCore.refresh();
        assertTrue(ParticleCore.allowSpawn("flame", false, 0, 1));
        assertFalse(ParticleCore.optimized());
        core.setEnabled(true); ParticleCore.refresh();
        assertFalse(ParticleCore.allowSpawn("flame", false, 0, 1));
        assertTrue(ParticleCore.culling());
        assertTrue(ParticleCore.optimized());
    }

    @Test void ratesSaveOnlyWhatChangedAndNormaliseIds() {
        var rates = new ParticleReductionOption("Spawn rates");
        rates.set("minecraft:Flame", 40);
        rates.set("particlerain:rain", 0);
        rates.set("smoke", 100);
        rates.set("crit", 250);
        assertEquals(2, rates.changed());
        assertEquals(40, rates.percent("flame"));
        assertEquals(0, rates.percent("particlerain:rain"));
        assertEquals(100, rates.percent("smoke"));
        assertEquals(100, rates.percent("crit"), "above 100 is 100, so it is not kept");
        JsonObject saved = rates.save().getAsJsonObject();
        assertEquals(2, saved.size());
        var loaded = new ParticleReductionOption("Spawn rates");
        loaded.load(saved);
        assertEquals(40, loaded.percent("flame"));
        assertEquals(0, loaded.percent("particlerain:rain"));
        JsonObject messy = new JsonObject();
        messy.addProperty("minecraft:Heart", 20);
        messy.addProperty("bad", "text");
        messy.addProperty("full", 100);
        messy.addProperty("", 5);
        loaded.load(messy);
        assertEquals(1, loaded.changed());
        assertEquals(20, loaded.percent("heart"));
        loaded.setCatalog(() -> java.util.List.of("flame", "heart"));
        assertEquals(java.util.List.of("flame", "heart"), loaded.ids());
        loaded.set("gone", 10);
        assertEquals(java.util.List.of("flame", "heart", "gone"), loaded.ids(), "a saved id the game no longer lists can still be reset");
        loaded.reset();
        assertEquals(0, loaded.changed());
    }

    @Test void particleBudgetMigratesIntoParticleCore() {
        JsonObject modules = new JsonObject();
        JsonObject budget = new JsonObject(), options = new JsonObject();
        budget.addProperty("enabled", true);
        options.addProperty("Particles per tick", 128);
        options.addProperty("Distance", 32);
        budget.add("options", options);
        modules.add("ParticleBudget", budget);
        ConfigManager.migrateParticleBudget(modules);
        assertFalse(modules.has("ParticleBudget"));
        JsonObject migrated = modules.getAsJsonObject(ParticleCore.MODULE).getAsJsonObject("options");
        assertTrue(migrated.get(ParticleCore.LIMIT).getAsBoolean());
        assertEquals(128, migrated.get(ParticleCore.PER_TICK).getAsInt());
        assertEquals(32, migrated.get(ParticleCore.DISTANCE).getAsInt());
        assertFalse(modules.getAsJsonObject(ParticleCore.MODULE).has("enabled"), "Particle Core stays on by default");

        // The old default (off) migrates to quota off; whatever Particle Core already saved wins.
        modules = new JsonObject();
        modules.add("ParticleBudget", new JsonObject());
        JsonObject saved = new JsonObject(), savedOptions = new JsonObject();
        savedOptions.addProperty(ParticleCore.LIMIT, true);
        saved.add("options", savedOptions);
        modules.add(ParticleCore.MODULE, saved);
        ConfigManager.migrateParticleBudget(modules);
        assertTrue(modules.getAsJsonObject(ParticleCore.MODULE).getAsJsonObject("options").get(ParticleCore.LIMIT).getAsBoolean());
        ConfigManager.migrateParticleBudget(modules); // nothing left to migrate
    }
}
