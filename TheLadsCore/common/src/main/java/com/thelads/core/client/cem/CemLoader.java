package com.thelads.core.client.cem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clean-room Custom Entity Models (CEM) JSON loader (OptiFine / MCPatcher format).
 * Parses .jem entity model files and maps custom model parts, box dimensions,
 * texture offsets, and pivot positions.
 */
public final class CemLoader {
    public static final String[] FOLDERS = {"optifine/cem/", "mcpatcher/cem/"};

    public static final class Part {
        public final String partName;
        public final String id;
        public final float[] translate;
        public final float[] rotate;
        public final List<Box> boxes;
        public final List<Part> submodels;

        public Part(String partName, String id, float[] translate, float[] rotate, List<Box> boxes, List<Part> submodels) {
            this.partName = partName;
            this.id = id;
            this.translate = translate;
            this.rotate = rotate;
            this.boxes = boxes;
            this.submodels = submodels;
        }
    }

    public static final class Box {
        public final float[] coordinates; // [x, y, z, width, height, depth]
        public final float[] textureOffset; // [u, v]
        public final float sizeAdd;

        public Box(float[] coordinates, float[] textureOffset, float sizeAdd) {
            this.coordinates = coordinates;
            this.textureOffset = textureOffset;
            this.sizeAdd = sizeAdd;
        }
    }

    public static final class Model {
        public final String entityName;
        public final String texture;
        public final int textureWidth;
        public final int textureHeight;
        public final Map<String, Part> parts;

        public Model(String entityName, String texture, int textureWidth, int textureHeight, Map<String, Part> parts) {
            this.entityName = entityName;
            this.texture = texture;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.parts = parts;
        }

        public Part getPart(String name) {
            return parts != null ? parts.get(name) : null;
        }
    }

    private CemLoader() {}

    public static Model parseJem(String entityName, String jsonText) {
        if (jsonText == null || jsonText.trim().isEmpty()) return null;

        try {
            JsonElement rootElem = new JsonParser().parse(jsonText);
            if (!rootElem.isJsonObject()) return null;
            JsonObject root = rootElem.getAsJsonObject();

            String texture = root.has("texture") ? root.get("texture").getAsString() : null;
            int textureWidth = 64;
            int textureHeight = 32;

            if (root.has("textureSize") && root.get("textureSize").isJsonArray()) {
                JsonArray size = root.getAsJsonArray("textureSize");
                if (size.size() >= 2) {
                    textureWidth = size.get(0).getAsInt();
                    textureHeight = size.get(1).getAsInt();
                }
            }

            Map<String, Part> parts = new HashMap<String, Part>();
            if (root.has("models") && root.get("models").isJsonArray()) {
                for (JsonElement elem : root.getAsJsonArray("models")) {
                    if (!elem.isJsonObject()) continue;
                    Part part = parsePart(elem.getAsJsonObject());
                    if (part != null && part.partName != null) {
                        parts.put(part.partName, part);
                    }
                }
            }

            return new Model(entityName, texture, textureWidth, textureHeight, parts);
        } catch (Exception e) {
            return null;
        }
    }

    private static Part parsePart(JsonObject obj) {
        String partName = obj.has("part") ? obj.get("part").getAsString() : null;
        String id = obj.has("id") ? obj.get("id").getAsString() : partName;

        float[] translate = parseVec3(obj.get("translate"));
        float[] rotate = parseVec3(obj.get("rotate"));

        List<Box> boxes = new ArrayList<Box>();
        if (obj.has("boxes") && obj.get("boxes").isJsonArray()) {
            for (JsonElement bElem : obj.getAsJsonArray("boxes")) {
                if (!bElem.isJsonObject()) continue;
                JsonObject bObj = bElem.getAsJsonObject();
                float[] coords = parseCoordinates(bObj.get("coordinates"));
                float[] texOffset = parseVec2(bObj.get("textureOffset"));
                float sizeAdd = bObj.has("sizeAdd") ? bObj.get("sizeAdd").getAsFloat() : 0.0f;
                boxes.add(new Box(coords, texOffset, sizeAdd));
            }
        }

        List<Part> submodels = new ArrayList<Part>();
        if (obj.has("submodels") && obj.get("submodels").isJsonArray()) {
            for (JsonElement sElem : obj.getAsJsonArray("submodels")) {
                if (sElem.isJsonObject()) {
                    Part sub = parsePart(sElem.getAsJsonObject());
                    if (sub != null) submodels.add(sub);
                }
            }
        }

        return new Part(partName, id, translate, rotate, boxes, submodels);
    }

    private static float[] parseVec3(JsonElement elem) {
        if (elem != null && elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            if (arr.size() >= 3) {
                return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
            }
        }
        return new float[]{0.0f, 0.0f, 0.0f};
    }

    private static float[] parseVec2(JsonElement elem) {
        if (elem != null && elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            if (arr.size() >= 2) {
                return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat()};
            }
        }
        return new float[]{0.0f, 0.0f};
    }

    private static float[] parseCoordinates(JsonElement elem) {
        if (elem != null && elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            if (arr.size() >= 6) {
                return new float[]{
                        arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat(),
                        arr.get(3).getAsFloat(), arr.get(4).getAsFloat(), arr.get(5).getAsFloat()
                };
            }
        }
        return new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    }
}
