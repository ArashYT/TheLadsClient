package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import org.junit.jupiter.api.Test;

class DamageTiltTest {
    /** Facing south (yaw 0): +X is the player's left, +Z ahead. Knockback pushes away from the attacker. */
    @Test void knockbackGivesTheSideTheHitCameFrom() {
        assertEquals(0, DamageTilt.sourceYaw(-0.4, 0, 0), 1e-3, "pushed right: hit from the left, Minecraft's fixed tilt");
        assertEquals(180, Math.abs(DamageTilt.sourceYaw(0.4, 0, 0)), 1e-3, "pushed left: hit from the right");
        assertEquals(90, DamageTilt.sourceYaw(0, -0.4, 0), 1e-3, "pushed back: hit from the front");
        assertEquals(-90, DamageTilt.sourceYaw(0, 0.4, 0), 1e-3, "pushed forward: hit from behind");
        assertEquals(0, DamageTilt.sourceYaw(0, -0.4, 90), 1e-3, "facing west (yaw 90), an attacker in the south is on the left");
        // The same as the server's attackedAtYaw: atan2(dz, dx) towards the attacker minus the yaw.
        double dx = 3, dz = -1, length = Math.hypot(dx, dz);
        float yaw = 37;
        assertEquals(Math.toDegrees(Math.atan2(dz, dx)) - yaw, DamageTilt.sourceYaw(-dx / length * 0.4, -dz / length * 0.4, yaw), 1e-3);
        assertTrue(Float.isNaN(DamageTilt.sourceYaw(0.05, 0.05, 0)), "a slow push (fall damage, fire) is no hit's");
    }

    @Test void aDirectionCountsForTheHurtItArrivesWith() {
        DamageTilt tilt = new DamageTilt();
        assertTrue(Float.isNaN(tilt.yaw(0)), "nothing yet");
        tilt.hurt(1000);
        tilt.direction(90, 1020); // 1.8.9: status 2, then the velocity of the same tick
        assertEquals(90, tilt.yaw(1100));
        tilt.direction(-90, 2000);
        tilt.hurt(2030);          // a server that sends the velocity first
        assertEquals(-90, tilt.yaw(2100));
        tilt.hurt(4000);          // fall damage: no direction
        assertTrue(Float.isNaN(tilt.yaw(4100)), "an old direction never carries over");
        tilt.direction(45, 4500); // a push long after the hurt (a launch pad) is not the hit's
        assertTrue(Float.isNaN(tilt.yaw(4510)));
        tilt.hurt(6000);
        tilt.direction(30, 6010);
        tilt.direction(Float.NaN, 6020); // a slow push in the same tick keeps it
        assertEquals(30, tilt.yaw(6500));
        assertTrue(Float.isNaN(tilt.yaw(6000 + DamageTilt.KEEP_MS + 1)), "the direction ends with its hurt");
    }

    @Test void settingsPickTheYawAndScaleTheTilt() {
        Module module = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        BoolOption directional = (BoolOption) module.getOption(DamageTilt.DIRECTIONAL);
        SliderOption intensity = (SliderOption) module.getOption(DamageTilt.INTENSITY);
        assertTrue(module.isEnabled(), "on by default since 1.7.2");
        assertTrue(directional.get(), "directional by default");
        assertEquals(1, DamageTilt.strength(module), "100% by default: Minecraft's own tilt");
        DamageTilt tilt = new DamageTilt();
        tilt.hurt(0);
        tilt.direction(180, 0);
        try {
            assertEquals(180, tilt.cameraYaw(module, 10));
            directional.set(false);
            assertEquals(0, tilt.cameraYaw(module, 10), "Directional off: the old fixed tilt");
            directional.set(true);
            assertEquals(0, tilt.cameraYaw(module, DamageTilt.KEEP_MS + 1), "unknown direction: the fixed tilt");
            // A hurt whose direction has not come yet holds the tilt a moment instead of leaning the fixed way first.
            DamageTilt late = new DamageTilt();
            assertEquals(1, late.cameraStrength(module, 0), "nothing to wait for before any hurt");
            late.hurt(1000);
            assertEquals(0, late.cameraStrength(module, 1020), "1.8.9's knockback is still on its way");
            late.direction(90, 1030);
            assertEquals(1, late.cameraStrength(module, 1030), "it came: the tilt goes on towards it");
            late.hurt(3000);
            late.direction(Float.NaN, 3000); // fall damage: its velocity packet carries no push
            assertEquals(1, late.cameraStrength(module, 3000), "an answer without a direction: the fixed tilt at once");
            late.hurt(5000);
            assertEquals(0, late.cameraStrength(module, 5000 + DamageTilt.WAIT_MS - 1));
            assertEquals(1, late.cameraStrength(module, 5000 + DamageTilt.WAIT_MS), "no answer at all: the fixed tilt after the wait");
            directional.set(false);
            late.hurt(9000);
            assertEquals(1, late.cameraStrength(module, 9000), "Directional off never waits");
            directional.set(true);
            intensity.setValue(50);
            assertEquals(0.5f, DamageTilt.strength(module));
            intensity.setValue(0);
            assertEquals(0, DamageTilt.strength(module), "0 is no tilt");
            assertEquals(1, DamageTilt.scale(250));
            assertEquals(0, DamageTilt.scale(-5));
        } finally {
            module.getOptions().forEach(Option::reset);
        }
    }

    /** 1.7.1 configs upgrade with Directional off; the module comes on unless the player changed it; Intensity becomes a percentage. */
    @Test void olderConfigsUpgradeAsTheOwnerChose() {
        Module module = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        BoolOption directional = (BoolOption) module.getOption(DamageTilt.DIRECTIONAL);
        SliderOption intensity = (SliderOption) module.getOption(DamageTilt.INTENSITY);
        boolean enabledWas = module.isEnabled();
        long modifiedWas = module.getLastModified();
        try {
            //        saved 1.7.1 entry (enabled, Intensity choice, lastModified)        expected: on, Directional, Intensity
            Object[][] cases = {
                {"\"enabled\":true,\"options\":{\"Intensity\":1},\"lastModified\":1790000000000", true, false, 100.0}, // had it on
                {"\"enabled\":false,\"options\":{\"Intensity\":1},\"lastModified\":0", true, false, 100.0},          // never touched
                {"\"enabled\":false,\"options\":{\"Intensity\":1}", true, false, 100.0},                               // no lastModified
                {"\"enabled\":false,\"options\":{\"Intensity\":0},\"lastModified\":1790000000000", false, false, 50.0},// switched off
                {"\"enabled\":true,\"options\":{\"Intensity\":2},\"lastModified\":1790000000000", true, false, 100.0}, // Strong
                {"\"enabled\":false,\"options\":{\"Directional\":true,\"Intensity\":35},\"lastModified\":0", false, true, 35.0}, // 1.7.2 save
                {null, true, true, 100.0}};                                                                              // no entry: fresh
            for (Object[] c : cases) {
                fresh(module);
                ConfigManager.applyJson(JsonParser.parseString(c[0] == null ? "{\"modules\":{}}"
                    : "{\"modules\":{\"OldDamageTilt\":{" + c[0] + "}}}").getAsJsonObject());
                assertEquals(c[1], module.isEnabled(), "module on/off after " + c[0]);
                assertEquals(c[2], directional.get(), "Directional after " + c[0]);
                assertEquals((double) c[3], intensity.getValue(), "Intensity after " + c[0]);
            }
        } finally {
            fresh(module);
            module.setEnabled(enabledWas);
            module.setLastModified(modifiedWas);
        }
    }

    /** The module as a fresh start registers it: on, every option at its default, never changed. */
    private static void fresh(Module module) {
        module.getOptions().forEach(Option::reset);
        module.setEnabled(true);
        module.setLastModified(0);
    }
}
