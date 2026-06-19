package com.thelads.core.features.alwayson.immediatelyfast;

public class ImmediatelyFastConfig {
    public boolean enhanced_batching = false;
    public boolean font_atlas_resizing = false;
    public int font_atlas_size = 1024;
    public boolean map_atlas_generation = false;
    public int map_atlas_size = 2048;
    public boolean skip_text_translucency_sorting = false;
    public boolean fast_text_lookup = true;
    public boolean avoid_redundant_framebuffer_switching = false;
    public boolean fix_slow_buffer_upload_on_apple_gpu = true;
    public boolean experimental_disable_resource_pack_conflict_handling = false;
    public boolean experimental_sign_text_buffering = false;
    public boolean debug_only_and_not_recommended_disable_mod_conflict_handling = false;
    public boolean debug_only_and_not_recommended_disable_hardware_conflict_handling = false;
    public boolean debug_only_print_additional_error_information = false;
    public boolean debug_only_use_last_usage_for_batch_ordering = false;
    public boolean debug_only_detailed_memory_leak_detection = false;

    public boolean getBoolean(String key, boolean defaultValue) {
        return switch (key) {
            case "enhanced_batching" -> this.enhanced_batching;
            case "font_atlas_resizing" -> this.font_atlas_resizing;
            case "map_atlas_generation" -> this.map_atlas_generation;
            case "skip_text_translucency_sorting" -> this.skip_text_translucency_sorting;
            case "fast_text_lookup" -> this.fast_text_lookup;
            case "avoid_redundant_framebuffer_switching" -> this.avoid_redundant_framebuffer_switching;
            case "fix_slow_buffer_upload_on_apple_gpu" -> this.fix_slow_buffer_upload_on_apple_gpu;
            case "experimental_disable_resource_pack_conflict_handling" -> this.experimental_disable_resource_pack_conflict_handling;
            case "experimental_sign_text_buffering" -> this.experimental_sign_text_buffering;
            case "debug_only_and_not_recommended_disable_mod_conflict_handling" -> this.debug_only_and_not_recommended_disable_mod_conflict_handling;
            case "debug_only_and_not_recommended_disable_hardware_conflict_handling" -> this.debug_only_and_not_recommended_disable_hardware_conflict_handling;
            case "debug_only_print_additional_error_information" -> this.debug_only_print_additional_error_information;
            case "debug_only_use_last_usage_for_batch_ordering" -> this.debug_only_use_last_usage_for_batch_ordering;
            case "debug_only_detailed_memory_leak_detection" -> this.debug_only_detailed_memory_leak_detection;
            default -> defaultValue;
        };
    }

    public int getInt(String key, int defaultValue) {
        return switch (key) {
            case "font_atlas_size" -> this.font_atlas_size;
            case "map_atlas_size" -> this.map_atlas_size;
            default -> defaultValue;
        };
    }

    public long getLong(String key, long defaultValue) {
        return defaultValue;
    }

    public String getString(String key, String defaultValue) {
        return defaultValue;
    }
}
