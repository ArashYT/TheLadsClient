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

    @Test void theOldIntensityChoiceBecomesAPercentage() {
        Module module = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        SliderOption intensity = (SliderOption) module.getOption(DamageTilt.INTENSITY);
        try {
            for (int[] choice : new int[][]{{0, 50}, {1, 100}, {2, 100}}) {
                ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"OldDamageTilt\":{\"enabled\":true,\"options\":{\"Intensity\":"
                    + choice[0] + "}}}}").getAsJsonObject());
                assertEquals(choice[1], intensity.getValue(), "1.7.1 choice " + choice[0]);
            }
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"OldDamageTilt\":{\"options\":{\"Directional\":false,\"Intensity\":35}}}}")
                .getAsJsonObject());
            assertEquals(35, intensity.getValue(), "a 1.7.2 save is a percentage already");
        } finally {
            module.getOptions().forEach(Option::reset);
            module.setEnabled(false);
        }
    }
}
