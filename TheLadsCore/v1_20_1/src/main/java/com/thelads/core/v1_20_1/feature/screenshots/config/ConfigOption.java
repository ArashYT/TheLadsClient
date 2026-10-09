package com.thelads.core.v1_20_1.feature.screenshots.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** One typed gallery option: its name, settings category, default and JSON form (replaces the CatConfig option types). */
public final class ConfigOption<T> {
   private final String name;
   private final String category;
   private final Class<T> type;
   private final Supplier<T> defaultValue;
   private final Function<T, JsonElement> writer;
   private final Function<JsonElement, T> reader;
   @Nullable
   private final Integer min;
   @Nullable
   private final Integer max;

   private ConfigOption(String name, String category, Class<T> type, Supplier<T> defaultValue, Function<T, JsonElement> writer,
                        Function<JsonElement, T> reader, @Nullable Integer min, @Nullable Integer max) {
      this.name = name;
      this.category = category;
      this.type = type;
      this.defaultValue = defaultValue;
      this.writer = writer;
      this.reader = reader;
      this.min = min;
      this.max = max;
   }

   public static ConfigOption<Boolean> bool(String name, String category, boolean defaultValue) {
      return new ConfigOption<>(name, category, Boolean.class, () -> defaultValue, JsonPrimitive::new, JsonElement::getAsBoolean, null, null);
   }

   public static ConfigOption<Integer> integer(String name, String category, int defaultValue, int min, int max) {
      return new ConfigOption<>(name, category, Integer.class, () -> defaultValue, JsonPrimitive::new, JsonElement::getAsInt, min, max);
   }

   public static <E extends Enum<E>> ConfigOption<E> enumeration(String name, String category, Class<E> type, E defaultValue) {
      return new ConfigOption<>(name, category, type, () -> defaultValue, value -> new JsonPrimitive(value.name()), json -> {
         String wanted = json.getAsString().toUpperCase(Locale.ROOT);
         for (E constant : type.getEnumConstants()) if (constant.name().equals(wanted)) return constant;
         return null;
      }, null, null);
   }

   public static <T> ConfigOption<T> custom(String name, String category, Class<T> type, Supplier<T> defaultValue,
                                            Function<T, JsonElement> writer, Function<JsonElement, T> reader) {
      return new ConfigOption<>(name, category, type, defaultValue, writer, reader, null, null);
   }

   public String name() {
      return name;
   }

   public String category() {
      return category;
   }

   public Class<T> type() {
      return type;
   }

   @Nullable
   public T defaultValue() {
      return defaultValue.get();
   }

   @Nullable
   public Integer min() {
      return min;
   }

   @Nullable
   public Integer max() {
      return max;
   }

   public JsonElement write(T value) {
      return writer.apply(value);
   }

   /** The parsed value, or null when the JSON does not fit this option (it then keeps its default). */
   @Nullable
   public T read(JsonElement json) {
      try {
         return json == null || json.isJsonNull() ? null : reader.apply(json);
      } catch (RuntimeException unreadable) {
         return null;
      }
   }
}
