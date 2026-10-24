package com.thelads.ladsapi.schema;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Representation of the embedded /ladsapi-schema.json.
 */
public final class LadsSchema {
    private int schema;
    private String lads;
    private List<SchemaModule> modules = new ArrayList<>();

    public static LadsSchema loadDefault() {
        try (InputStream in = LadsSchema.class.getResourceAsStream("/ladsapi-schema.json")) {
            if (in == null) {
                return new LadsSchema();
            }
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), LadsSchema.class);
        } catch (Exception e) {
            return new LadsSchema();
        }
    }

    public static LadsSchema fromJson(String json) {
        return new Gson().fromJson(json, LadsSchema.class);
    }

    public int getSchema() { return schema; }
    public String getLads() { return lads; }
    public List<SchemaModule> getModules() { return modules; }

    public static final class SchemaModule {
        private String name;
        private String category;
        private boolean neverForced;
        private boolean automation;
        private List<SchemaOption> options = new ArrayList<>();

        public String getName() { return name; }
        public String getCategory() { return category; }
        public boolean isNeverForced() { return neverForced; }
        public boolean isAutomation() { return automation; }
        public List<SchemaOption> getOptions() { return options; }
    }

    public static final class SchemaOption {
        private String name;
        private String type;
        @SerializedName("default")
        private Object defaultValue;
        private Double min;
        private Double max;
        private Double step;
        private List<String> choices;
        private String control;

        public String getName() { return name; }
        public String getType() { return type; }
        public Object getDefaultValue() { return defaultValue; }
        public Double getMin() { return min; }
        public Double getMax() { return max; }
        public Double getStep() { return step; }
        public List<String> getChoices() { return choices; }
        public String getControl() { return control; }

        public String formatComment() {
            String t = type == null ? "other" : type.toLowerCase();
            if ("slider".equals(t)) {
                String range = (min != null ? formatNum(min) : "?") + "-" + (max != null ? formatNum(max) : "?");
                String def = defaultValue != null ? formatNum(defaultValue) : "";
                return "# slider " + range + ", default " + def;
            } else if ("bool".equals(t)) {
                return "# bool, default " + defaultValue;
            } else if ("dropdown".equals(t)) {
                return "# dropdown " + (choices != null ? choices : "[]") + ", default " + defaultValue;
            } else if ("color".equals(t)) {
                return "# color, default " + defaultValue;
            } else if ("text".equals(t)) {
                return "# text (not server controllable)";
            } else {
                return "# " + t + " (not server controllable)";
            }
        }

        private static String formatNum(Object n) {
            if (n instanceof Number num) {
                double d = num.doubleValue();
                if (d == (long) d) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            }
            return String.valueOf(n);
        }
    }
}
