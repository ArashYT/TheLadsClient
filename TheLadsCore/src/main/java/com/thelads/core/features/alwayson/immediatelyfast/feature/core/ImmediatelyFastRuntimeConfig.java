/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.immediatelyfast.feature.core;

import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFastConfig;

public class ImmediatelyFastRuntimeConfig {
    public boolean font_atlas_resizing;
    public boolean disable_fast_buffer_upload;

    public ImmediatelyFastRuntimeConfig(ImmediatelyFastConfig config) {
        this.font_atlas_resizing = config.font_atlas_resizing;
        this.disable_fast_buffer_upload = false;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return switch (key) {
            case "font_atlas_resizing" -> this.font_atlas_resizing;
            case "disable_fast_buffer_upload" -> this.disable_fast_buffer_upload;
            default -> defaultValue;
        };
    }

    public int getInt(String key, int defaultValue) {
        return defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        return defaultValue;
    }

    public String getString(String key, String defaultValue) {
        return defaultValue;
    }
}

