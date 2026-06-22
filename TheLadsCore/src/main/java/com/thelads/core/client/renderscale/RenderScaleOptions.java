package com.thelads.core.client.renderscale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.fabricmc.loader.api.FabricLoader;

public class RenderScaleOptions {
    private static final String FILE_NAME = "render-scale-options.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public RenderScalePreset preset = RenderScalePreset.CUSTOM;
    public float renderScale = 1.0f;
    public ScaleAlgorithm scaleAlgorithm = ScaleAlgorithm.LINEAR;
    public boolean dynamicResolution = false;
    public int targetFps = 60;
    public float minRenderScale = 0.5f;
    private static RenderScaleOptions INSTANCE;

    public static RenderScaleOptions getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RenderScaleOptions();
        }
        return INSTANCE;
    }

    private static com.thelads.core.config.Module getModule() {
        return com.thelads.core.config.ModuleManager.getInstance().getModule("RenderScale");
    }

    public static RenderScalePreset getPreset() {
        var mod = getModule();
        if (mod != null) {
            var opt = mod.getOption("Preset");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                return RenderScalePreset.values()[((com.thelads.core.config.DropdownOption) opt).getIndex()];
            }
        }
        return RenderScalePreset.CUSTOM;
    }

    public static void setPreset(RenderScalePreset preset) {
        var mod = getModule();
        if (mod != null) {
            var opt = mod.getOption("Preset");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                ((com.thelads.core.config.DropdownOption) opt).setIndex(preset.ordinal());
                com.thelads.core.config.ConfigManager.save();
            }
        }
    }

    public static float getRenderScale() {
        var mod = getModule();
        if (mod != null) {
            if (!mod.isEnabled()) {
                return 1.0f;
            }
            RenderScalePreset preset = getPreset();
            if (preset != RenderScalePreset.CUSTOM) {
                return switch (preset) {
                    case ULTRA_PERFORMANCE -> 0.5f;
                    case BALANCED -> 0.75f;
                    case QUALITY -> 1.0f;
                    case SUPER_SAMPLING -> 1.5f;
                    default -> 1.0f;
                };
            }
            var opt = mod.getOption("Scale");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                int index = ((com.thelads.core.config.DropdownOption) opt).getIndex();
                return switch (index) {
                    case 0 -> 0.50f;
                    case 1 -> 0.75f;
                    case 2 -> 1.00f;
                    case 3 -> 1.25f;
                    case 4 -> 1.50f;
                    case 5 -> 2.00f;
                    default -> 1.0f;
                };
            }
        }
        return 1.0f;
    }

    public static void setRenderScale(float scale) {
        var mod = getModule();
        if (mod != null) {
            var opt = mod.getOption("Scale");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                int index = 2; // default 100%
                if (scale <= 0.5f) index = 0;
                else if (scale <= 0.75f) index = 1;
                else if (scale <= 1.0f) index = 2;
                else if (scale <= 1.25f) index = 3;
                else if (scale <= 1.5f) index = 4;
                else index = 5;
                ((com.thelads.core.config.DropdownOption) opt).setIndex(index);
                com.thelads.core.config.ConfigManager.save();
            }
        }
    }

    public static ScaleAlgorithm getScaleAlgorithm() {
        var mod = getModule();
        if (mod != null) {
            RenderScalePreset preset = getPreset();
            if (preset != RenderScalePreset.CUSTOM) {
                return preset == RenderScalePreset.ULTRA_PERFORMANCE ? ScaleAlgorithm.NEAREST : ScaleAlgorithm.LINEAR;
            }
            var opt = mod.getOption("Algorithm");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                return ScaleAlgorithm.values()[((com.thelads.core.config.DropdownOption) opt).getIndex()];
            }
        }
        return ScaleAlgorithm.LINEAR;
    }

    public static void setScaleAlgorithm(ScaleAlgorithm algorithm) {
        var mod = getModule();
        if (mod != null) {
            var opt = mod.getOption("Algorithm");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                ((com.thelads.core.config.DropdownOption) opt).setIndex(algorithm.ordinal());
                com.thelads.core.config.ConfigManager.save();
            }
        }
    }

    public static boolean isDynamicResolution() {
        var mod = getModule();
        if (mod != null) {
            if (!mod.isEnabled()) return false;
            RenderScalePreset preset = getPreset();
            if (preset != RenderScalePreset.CUSTOM) {
                return false;
            }
            var opt = mod.getOption("Dynamic Resolution");
            if (opt instanceof com.thelads.core.config.BoolOption) {
                return ((com.thelads.core.config.BoolOption) opt).get();
            }
        }
        return false;
    }

    public static void setDynamicResolution(boolean dynamicResolution) {
        var mod = getModule();
        if (mod != null) {
            var opt = mod.getOption("Dynamic Resolution");
            if (opt instanceof com.thelads.core.config.BoolOption) {
                ((com.thelads.core.config.BoolOption) opt).set(dynamicResolution);
                com.thelads.core.config.ConfigManager.save();
            }
        }
    }

    public static int getTargetFps() {
        var mod = getModule();
        if (mod != null) {
            var opt = mod.getOption("Target FPS");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                int index = ((com.thelads.core.config.DropdownOption) opt).getIndex();
                return switch (index) {
                    case 0 -> 30;
                    case 1 -> 60;
                    case 2 -> 90;
                    case 3 -> 120;
                    case 4 -> 144;
                    default -> 60;
                };
            }
        }
        return 60;
    }

    public static void setTargetFps(int targetFps) {
        var mod = getModule();
        if (mod != null) {
            var opt = mod.getOption("Target FPS");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                int index = 1; // default 60
                if (targetFps <= 30) index = 0;
                else if (targetFps <= 60) index = 1;
                else if (targetFps <= 90) index = 2;
                else if (targetFps <= 120) index = 3;
                else index = 4;
                ((com.thelads.core.config.DropdownOption) opt).setIndex(index);
                com.thelads.core.config.ConfigManager.save();
            }
        }
    }

    public static float getMinRenderScale() {
        var mod = getModule();
        if (mod != null) {
            var opt = mod.getOption("Min Scale");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                int index = ((com.thelads.core.config.DropdownOption) opt).getIndex();
                return switch (index) {
                    case 0 -> 0.50f;
                    case 1 -> 0.75f;
                    case 2 -> 1.00f;
                    default -> 0.50f;
                };
            }
        }
        return 0.50f;
    }

    public static void setMinRenderScale(float minScale) {
        var mod = getModule();
        if (mod != null) {
            var opt = mod.getOption("Min Scale");
            if (opt instanceof com.thelads.core.config.DropdownOption) {
                int index = 0; // default 50%
                if (minScale <= 0.5f) index = 0;
                else if (minScale <= 0.75f) index = 1;
                else index = 2;
                ((com.thelads.core.config.DropdownOption) opt).setIndex(index);
                com.thelads.core.config.ConfigManager.save();
            }
        }
    }

    public static void load() {
        Path path = RenderScaleOptions.getConfigPath();
        if (Files.exists(path, new LinkOption[0])) {
            try (FileReader reader = new FileReader(path.toFile());){
                INSTANCE = (RenderScaleOptions)GSON.fromJson((Reader)reader, RenderScaleOptions.class);
            }
            catch (Exception e) {
                INSTANCE = new RenderScaleOptions();
            }
        }
        if (INSTANCE == null) {
            INSTANCE = new RenderScaleOptions();
        }
    }

    public static void save() {
        Path path = RenderScaleOptions.getConfigPath();
        try {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent, new LinkOption[0])) {
                Files.createDirectories(parent, new FileAttribute[0]);
            }
            try (FileWriter writer = new FileWriter(path.toFile());){
                GSON.toJson((Object)RenderScaleOptions.getInstance(), (Appendable)writer);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
