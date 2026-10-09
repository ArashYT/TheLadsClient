package com.thelads.core.v1_20_1.feature.screenshots.config;

/** Told when the settings screen has been left (saved or discarded), so a screen or cache can re-read the options. */
@FunctionalInterface
public interface ConfigListener {
   void configUpdated();
}
