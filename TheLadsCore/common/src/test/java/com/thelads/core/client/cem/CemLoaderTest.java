package com.thelads.core.client.cem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CemLoaderTest {
    @Test
    void testParseJem() {
        String json = "{\n" +
                "  \"texture\": \"textures/entity/creeper/creeper.png\",\n" +
                "  \"textureSize\": [64, 32],\n" +
                "  \"models\": [\n" +
                "    {\n" +
                "      \"part\": \"head\",\n" +
                "      \"id\": \"head\",\n" +
                "      \"boxes\": [\n" +
                "        {\n" +
                "          \"coordinates\": [-4, 6, -4, 8, 8, 8],\n" +
                "          \"textureOffset\": [0, 0]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        CemLoader.Model model = CemLoader.parseJem("creeper", json);
        assertNotNull(model);
        assertEquals("creeper", model.entityName);
        assertEquals("textures/entity/creeper/creeper.png", model.texture);
        assertEquals(64, model.textureWidth);
        assertEquals(32, model.textureHeight);

        CemLoader.Part head = model.getPart("head");
        assertNotNull(head);
        assertEquals(1, head.boxes.size());
        assertEquals(8.0f, head.boxes.get(0).coordinates[3]); // width
    }
}
