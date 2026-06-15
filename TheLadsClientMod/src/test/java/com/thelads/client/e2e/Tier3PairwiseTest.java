package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class Tier3PairwiseTest {
    @BeforeEach
    public void setupMocks() throws Exception { com.thelads.client.e2e.ReflectionHelper.setupMocks(); }
    @AfterEach
    public void teardownMocks() { com.thelads.client.e2e.ReflectionHelper.teardownMocks(); }


    @Test
    public void testCapeOnScaleOn() throws Exception {
        ReflectionHelper.setCapesEnabled(true);
        ReflectionHelper.setUiScalingEnabled(true);
        if (!ReflectionHelper.isCapesEnabled() || !ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Pairwise: Cape On, Scale On failed");
        }
    }

    @Test
    public void testCapeOffScaleOn() throws Exception {
        ReflectionHelper.setCapesEnabled(false);
        ReflectionHelper.setUiScalingEnabled(true);
        if (ReflectionHelper.isCapesEnabled() || !ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Pairwise: Cape Off, Scale On failed");
        }
    }

    @Test
    public void testCapeOnScaleOff() throws Exception {
        ReflectionHelper.setCapesEnabled(true);
        ReflectionHelper.setUiScalingEnabled(false);
        if (!ReflectionHelper.isCapesEnabled() || ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Pairwise: Cape On, Scale Off failed");
        }
    }

    @Test
    public void testCapeOffScaleOff() throws Exception {
        ReflectionHelper.setCapesEnabled(false);
        ReflectionHelper.setUiScalingEnabled(false);
        if (ReflectionHelper.isCapesEnabled() || ReflectionHelper.isUiScalingEnabled()) {
            throw new RuntimeException("Pairwise: Cape Off, Scale Off failed");
        }
    }

    @Test
    public void testGuiToggleWithDifferentConfigStates() throws Exception {
        ReflectionHelper.setCapesEnabled(false);
        ReflectionHelper.setUiScalingEnabled(true);
        ReflectionHelper.simulateRightShiftKeyPress();
        Object currentScreen = ReflectionHelper.getCurrentScreen();
        if (currentScreen == null) {
            throw new RuntimeException("GUI did not open with non-default config states");
        }
    }
}
