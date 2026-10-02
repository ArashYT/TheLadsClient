// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.playtime.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.thelads.core.v26_2.embedded.playtime.util.Color;

public class WptConfigValues {
	public abstract static class Value<T> {
		private T value;
		private final T defaultValue;

		/**
		 * Creates a config value with a default.
		 */
		protected Value(T defaultValue) {
			this.defaultValue = defaultValue;
			this.value = defaultValue;
		}

		/**
		 * Returns current value.
		 */
		public T get() {
			return this.value;
		}

		/**
		 * Sets current value.
		 */
		public void set(T value) {
			this.value = value;
		}

		/**
		 * Sets current value without generic type checks.
		 */
		@SuppressWarnings("unchecked")
		public void setUnchecked(Object obj) {
			this.value = (T) obj;
		}

		/**
		 * Returns default value.
		 */
		public T getDefault() {
			return this.defaultValue;
		}

		/**
		 * Reads this value from JSON.
		 */
		public abstract T read(JsonElement jsonElement);

		/**
		 * Writes this value to JSON.
		 */
		public abstract JsonElement write();
	}

	public static class BooleanValue extends Value<Boolean> {
		/**
		 * Creates a boolean config value.
		 */
		public BooleanValue(Boolean defaultValue) {
			super(defaultValue);
		}

		@Override
		public Boolean read(JsonElement jsonElement) {
			return jsonElement.isJsonPrimitive() ? jsonElement.getAsJsonPrimitive().getAsBoolean() : this.getDefault();
		}

		@Override
		public JsonElement write() {
			return new JsonPrimitive(this.get());
		}
	}

	public static class EnumValue<T extends Enum<?>> extends Value<T> {
		/**
		 * Creates an enum config value.
		 */
		public EnumValue(T defaultValue) {
			super(defaultValue);
		}

		@Override
		@SuppressWarnings("unchecked")
		public T read(JsonElement jsonElement) {
			String name = jsonElement.getAsString();

			for (Enum<?> enumConstant : this.getDefault().getClass().getEnumConstants()) {
				if (enumConstant.name().equalsIgnoreCase(name)) {
					return (T) enumConstant;
				}
			}

			return this.getDefault();
		}

		@Override
		public JsonElement write() {
			return new JsonPrimitive(this.get().name().toLowerCase());
		}
	}

	public static class ColorValue extends Value<Color> {
		/**
		 * Creates a color config value.
		 */
		public ColorValue(Color defaultValue) {
			super(defaultValue);
		}

		@Override
		public Color read(JsonElement jsonElement) {
			Color color = Color.fromString(jsonElement.getAsString());
			return color != null ? color : this.getDefault();
		}

		@Override
		public JsonElement write() {
			return new JsonPrimitive(this.get().toString());
		}
	}
}
