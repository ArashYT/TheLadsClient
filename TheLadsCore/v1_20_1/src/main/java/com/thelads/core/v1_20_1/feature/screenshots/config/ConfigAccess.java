package com.thelads.core.v1_20_1.feature.screenshots.config;

import java.util.Optional;
import java.util.function.Supplier;

/** Read and write access to the gallery options; the settings screen edits through an unsaved copy of it. */
public interface ConfigAccess {
   /** The stored value, else the option's default; empty when it has neither. */
   <T> Optional<T> get(ConfigOption<T> option);

   /** A null value forgets the stored one, so the default applies again. */
   <T> void put(ConfigOption<T> option, T value);

   default <T> T getOrFallback(ConfigOption<T> option, T fallback) {
      return get(option).orElse(fallback);
   }

   default <T> T getOrElseGet(ConfigOption<T> option, Supplier<? extends T> fallback) {
      return get(option).orElseGet(fallback);
   }
}
