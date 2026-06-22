package com.thelads.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thelads.client.config.ModConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AdversarialGsonTest {
    @Test
    public void testGsonMissingFields() {
        Gson gson = new Gson();
        JsonObject obj = new JsonObject();
        obj.addProperty("uiScalingEnabled", true);
        ModConfig config = gson.fromJson(obj, ModConfig.class);
        System.out.println("capesEnabled: " + config.isCapesEnabled());
        System.out.println("uiScalingEnabled: " + config.isUiScalingEnabled());
    }
}
