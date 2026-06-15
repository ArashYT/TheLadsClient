package com.thelads.client.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;

public class GsonUpperCaseBooleanTest {
    @Test
    public void testGsonUpperCase() {
        Gson gson = new Gson();
        ModConfig config = gson.fromJson("{\"capesEnabled\":\"TRUE\"}", ModConfig.class);
        assertTrue(config.isCapesEnabled());
        
        ModConfig config2 = gson.fromJson("{\"capesEnabled\":\"FALSE\"}", ModConfig.class);
        assertFalse(config2.isCapesEnabled());
    }
}
