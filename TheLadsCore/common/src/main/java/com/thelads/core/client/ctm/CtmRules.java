package com.thelads.core.client.ctm;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Clean-room Connected Textures (CTM) parser and index calculator (OptiFine / MCPatcher format).
 * Reads .properties rules under optifine/ctm/ and mcpatcher/ctm/ and resolves
 * sub-tile indices for CTM (47-tile), horizontal (4-tile), vertical (4-tile), repeat, and random methods.
 */
public final class CtmRules {
    public static final String[] FOLDERS = {"optifine/ctm/", "mcpatcher/ctm/"};

    public enum Method {
        CTM, HORIZONTAL, VERTICAL, REPEAT, FIXED, RANDOM
    }

    public static final class Rule {
        public final String path;
        public final Method method;
        public final Set<String> matchBlocks;
        public final Set<String> matchTiles;
        public final String[] tiles;
        public final String connect;
        public final Set<String> faces;
        public final int width;
        public final int height;
        public final int[] weights;

        public Rule(String path, Method method, Set<String> matchBlocks, Set<String> matchTiles,
                    String[] tiles, String connect, Set<String> faces, int width, int height, int[] weights) {
            this.path = path;
            this.method = method;
            this.matchBlocks = matchBlocks;
            this.matchTiles = matchTiles;
            this.tiles = tiles;
            this.connect = connect;
            this.faces = faces;
            this.width = width;
            this.height = height;
            this.weights = weights;
        }

        public boolean matchesFace(String faceName) {
            if (faces == null || faces.isEmpty() || faces.contains("all")) return true;
            return faces.contains(faceName.toLowerCase(Locale.ROOT));
        }

        public boolean matchesBlock(String blockName) {
            if (matchBlocks == null || matchBlocks.isEmpty()) return false;
            return matchBlocks.contains(blockName.toLowerCase(Locale.ROOT));
        }
    }

    private CtmRules() {}

    public static Rule parseRule(String path, String text) {
        Properties props = new Properties();
        try {
            props.load(new StringReader(text));
        } catch (IOException e) {
            return null;
        }

        String methodStr = props.getProperty("method", "ctm").toLowerCase(Locale.ROOT).trim();
        Method method;
        switch (methodStr) {
            case "horizontal": method = Method.HORIZONTAL; break;
            case "vertical": method = Method.VERTICAL; break;
            case "repeat": method = Method.REPEAT; break;
            case "fixed": method = Method.FIXED; break;
            case "random": method = Method.RANDOM; break;
            default: method = Method.CTM; break;
        }

        Set<String> matchBlocks = parseSet(props.getProperty("matchBlocks"));
        Set<String> matchTiles = parseSet(props.getProperty("matchTiles"));
        String[] tiles = parseTiles(props.getProperty("tiles"));
        String connect = props.getProperty("connect", "block").toLowerCase(Locale.ROOT).trim();
        Set<String> faces = parseSet(props.getProperty("faces"));

        int width = parseInt(props.getProperty("width"), 1);
        int height = parseInt(props.getProperty("height"), 1);

        int[] weights = null;
        String weightsStr = props.getProperty("weights");
        if (weightsStr != null) {
            String[] parts = weightsStr.trim().split("\\s+");
            weights = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                weights[i] = parseInt(parts[i], 1);
            }
        }

        return new Rule(path, method, matchBlocks, matchTiles, tiles, connect, faces, width, height, weights);
    }

    private static Set<String> parseSet(String str) {
        if (str == null || str.trim().isEmpty()) return Collections.emptySet();
        Set<String> set = new HashSet<String>();
        for (String part : str.trim().split("\\s+")) {
            if (!part.isEmpty()) set.add(part.toLowerCase(Locale.ROOT));
        }
        return set;
    }

    private static String[] parseTiles(String str) {
        if (str == null || str.trim().isEmpty()) return new String[0];
        List<String> list = new ArrayList<String>();
        for (String part : str.trim().split("\\s+")) {
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    int start = parseInt(range[0], -1);
                    int end = parseInt(range[1], -1);
                    if (start >= 0 && end >= start) {
                        for (int i = start; i <= end; i++) list.add(String.valueOf(i));
                        continue;
                    }
                }
            }
            list.add(part);
        }
        return list.toArray(new String[0]);
    }

    private static int parseInt(String val, int def) {
        if (val == null) return def;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Compute 47-tile CTM tile index from 8 neighbor connection states.
     */
    public static int calculateCtmIndex(boolean up, boolean down, boolean left, boolean right,
                                        boolean upLeft, boolean upRight, boolean downLeft, boolean downRight) {
        // Standard CTM 47-tile mapping
        if (!up && !down && !left && !right) return 0;
        if (up && down && left && right) {
            if (upLeft && upRight && downLeft && downRight) return 26; // full center
            if (!upLeft && upRight && downLeft && downRight) return 27;
            if (upLeft && !upRight && downLeft && downRight) return 28;
            if (upLeft && upRight && !downLeft && downRight) return 29;
            if (upLeft && upRight && downLeft && !downRight) return 30;
            return 26;
        }
        if (!up && !left && down && right) return downRight ? 1 : 2;
        if (!up && !right && down && left) return downLeft ? 3 : 4;
        if (!down && !left && up && right) return upRight ? 5 : 6;
        if (!down && !right && up && left) return upLeft ? 7 : 8;
        if (up && down && !left && !right) return 9;
        if (!up && !down && left && right) return 10;
        if (!up && down && left && right) return 11;
        if (up && !down && left && right) return 12;
        if (up && down && !left && right) return 13;
        if (up && down && left && !right) return 14;
        if (up && !down && !left && !right) return 15;
        if (!up && down && !left && !right) return 16;
        if (!up && !down && left && !right) return 17;
        if (!up && !down && !left && right) return 18;
        return 0;
    }

    /**
     * Compute horizontal connection index (0=single, 1=left only, 2=both, 3=right only).
     */
    public static int calculateHorizontalIndex(boolean left, boolean right) {
        if (left && right) return 2;
        if (left) return 3;
        if (right) return 1;
        return 0;
    }

    /**
     * Compute vertical connection index (0=single, 1=top only, 2=both, 3=bottom only).
     */
    public static int calculateVerticalIndex(boolean up, boolean down) {
        if (up && down) return 2;
        if (up) return 3;
        if (down) return 1;
        return 0;
    }

    /**
     * Compute repeat pattern index based on 2D coordinates and grid dimensions.
     */
    public static int calculateRepeatIndex(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return 0;
        int rx = Math.abs(x) % width;
        int ry = Math.abs(y) % height;
        return ry * width + rx;
    }
}
