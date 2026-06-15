package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thelads.client.config.ModConfig;

public class GsonBehaviorTest {
    @Test
    public void testGson() throws Exception {
        Gson gson = new Gson();
        JsonObject obj = gson.fromJson("{\"capesEnabled\":null}", JsonObject.class);
        ModConfig config = gson.fromJson(obj, ModConfig.class);
        System.out.println("capes: " + config.isCapesEnabled());
        System.out.println("scaling: " + config.isUiScalingEnabled());
    }
}
