package com.thelads.client.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class MissingFieldTest {
    @Test
    public void testGsonMissingField() {
        Gson gson = new Gson();
        ModConfig config = gson.fromJson("{}", ModConfig.class);
        assertTrue(config.isCapesEnabled());
        assertFalse(config.isUiScalingEnabled());
    }
}
