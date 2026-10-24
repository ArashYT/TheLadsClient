package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CustomSkyTest {
    private static final String FOLDER = "optifine/sky/world0/";

    private static CustomSky.Layer layer(String text) {
        return CustomSky.parse(text, FOLDER, 1, texture -> true);
    }

    @Test
    void defaultsAreTheSourceBesideItAlwaysShownAddedAndTurning() {
        CustomSky.Layer layer = layer("");
        assertNotNull(layer);
        assertEquals("minecraft:optifine/sky/world0/sky1.png", layer.texture);
        assertEquals(CustomSky.Blend.ADD, layer.blend);
        assertTrue(layer.rotate);
        assertEquals(1F, layer.brightness(6000, 0, 0), 0);
        assertEquals(90F, layer.angle(0.25F), 1e-4);
        assertArrayEquals(new float[]{1, 0, 0}, new float[]{layer.axisX, layer.axisY, layer.axisZ}, 1e-6F, "the sun's axis");
    }

    @Test
    void sourcesResolveLikeOptiFine() {
        assertEquals("minecraft:optifine/sky/world0/stars.png", CustomSky.texture("./stars.png", FOLDER, 2));
        assertEquals("minecraft:optifine/sky/night.png", CustomSky.texture("~/sky/night.png", FOLDER, 2));
        assertEquals("minecraft:mcpatcher/sky/world0/a.png", CustomSky.texture("mcpatcher/sky/world0/a.png", FOLDER, 2));
        assertEquals("minecraft:optifine/sky/world0/a.png", CustomSky.texture("a.png", FOLDER, 2));
        assertEquals("pack:sky/a.png", CustomSky.texture("pack:sky/a.png", FOLDER, 2));
        assertNull(CustomSky.texture("Pack:Sky.png", FOLDER, 2), "not a valid resource location");
    }

    @Test
    void optiFineFolderWinsAndLayersKeepTheirOrder() {
        java.util.Map<String, String> files = new java.util.HashMap<>();
        files.put("minecraft:mcpatcher/sky/world0/sky1.properties", "blend=alpha");
        files.put("minecraft:optifine/sky/world0/sky3.properties", "blend=screen");
        files.put("minecraft:optifine/sky/world0/sky1.properties", "blend=replace");
        java.util.List<CustomSky.Layer> layers = CustomSky.load(files::get, texture -> true);
        assertEquals(2, layers.size());
        assertEquals(CustomSky.Blend.REPLACE, layers.get(0).blend);
        assertEquals("minecraft:optifine/sky/world0/sky3.png", layers.get(1).texture);
        files.remove("minecraft:optifine/sky/world0/sky1.properties");
        files.remove("minecraft:optifine/sky/world0/sky3.properties");
        assertEquals(CustomSky.Blend.ALPHA, CustomSky.load(files::get, texture -> true).get(0).blend, "mcpatcher when optifine has none");
        assertTrue(CustomSky.load(name -> null, texture -> true).isEmpty());
    }

    @Test
    void missingTextureMeansNoLayer() {
        assertNull(CustomSky.parse("source=./gone.png", FOLDER, 1, texture -> false));
    }

    @Test
    void nightLayerFadesInAtDuskAndOutAtDawn() {
        CustomSky.Layer night = layer("startFadeIn=18:00\nendFadeIn=19:00\nstartFadeOut=5:00\nendFadeOut=6:00");
        assertEquals(0.5F, night.brightness(12500, 0, 0), 1e-3); // 18:30
        assertEquals(1F, night.brightness(18000, 0, 0), 0);       // midnight
        assertEquals(0.5F, night.brightness(23500, 0, 0), 1e-3);  // 05:30
        assertEquals(0F, night.brightness(6000, 0, 0), 0);        // noon
        assertEquals(1F, night.brightness(18000 + 24000 * 5, 0, 0), 0, "any day");
    }

    @Test
    void fadeOutDefaultsToTheFadeInLength() {
        CustomSky.Layer layer = layer("startFadeIn=18:00\nendFadeIn=20:00\nstartFadeOut=4:00");
        assertEquals(0.5F, layer.brightness(23000, 0, 0), 1e-3); // 05:00, half way through 04:00-06:00
    }

    @Test
    void weatherDecidesWhenItShows() {
        assertEquals(0.25F, layer("").brightness(0, 0.75F, 0), 1e-6, "clear only, by default");
        CustomSky.Layer storm = layer("weather=rain thunder");
        assertEquals(0F, storm.brightness(0, 0, 0), 0);
        assertEquals(1F, storm.brightness(0, 1, 1), 1e-6);
    }

    @Test
    void blendAxisSpeedAndRotate() {
        CustomSky.Layer layer = layer("blend=Alpha\nrotate=false\nspeed=2\naxis=1 0 0");
        assertEquals(CustomSky.Blend.ALPHA, layer.blend);
        assertFalse(layer.rotate);
        assertEquals(0F, layer.angle(0.5F), 0);
        assertArrayEquals(new float[]{0, 0, -1}, new float[]{layer.axisX, layer.axisY, layer.axisZ}, 1e-6F);
        assertEquals(CustomSky.Blend.ADD, CustomSky.blend("nonsense"));
    }

    @Test
    void brightnessReachesTheDrawColor() {
        float[] rgba = new float[4];
        CustomSky.Blend.ADD.color(0.4F, rgba);
        assertArrayEquals(new float[]{1, 1, 1, 0.4F}, rgba, 1e-6F);
        CustomSky.Blend.SCREEN.color(0.4F, rgba);
        assertArrayEquals(new float[]{0.4F, 0.4F, 0.4F, 1}, rgba, 1e-6F);
        CustomSky.Blend.MULTIPLY.color(0.4F, rgba);
        assertArrayEquals(new float[]{0.4F, 0.4F, 0.4F, 0.4F}, rgba, 1e-6F);
        assertEquals(0x302, CustomSky.Blend.ALPHA.source);
        assertEquals(0x303, CustomSky.Blend.ALPHA.destination);
    }

    @Test
    void timesAreTicksFromSixInTheMorning() {
        assertEquals(0, CustomSky.time("6:00"));
        assertEquals(18000, CustomSky.time("00:00"));
        assertEquals(12500, CustomSky.time("18:30"));
        assertEquals(-1, CustomSky.time("dusk"));
    }

    @Test
    void sixFacesOnTheCube() {
        assertEquals(6 * 4 * 5, CustomSky.FACES.length);
        for (int face = 0; face < 6; face++) {
            float u = 1, v = 1;
            for (int corner = 0; corner < 4; corner++) {
                int i = (face * 4 + corner) * 5;
                for (int axis = 0; axis < 3; axis++) assertEquals(100F, Math.abs(CustomSky.FACES[i + axis]), 0);
                u = Math.min(u, CustomSky.FACES[i + 3]);
                v = Math.min(v, CustomSky.FACES[i + 4]);
            }
            assertEquals((face % 3) / 3F, u, 1e-6, "face " + face + " column");
            assertEquals((face / 3) / 2F, v, 1e-6, "face " + face + " row");
        }
    }
}
