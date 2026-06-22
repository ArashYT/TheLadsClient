package com.thelads.core.features.alwayson.immediatelyfast;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.features.alwayson.immediatelyfast.util.IrisCompat;
import com.thelads.core.features.alwayson.immediatelyfast.feature.core.ImmediatelyFastRuntimeConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

public class ImmediatelyFast {
    public static final Logger LOGGER = LoggerFactory.getLogger("ImmediatelyFast");
    public static final String VERSION = "1.2.18";
    public static ImmediatelyFastConfig config;
    public static ImmediatelyFastRuntimeConfig runtimeConfig;

    public static boolean isEnabled() {
        try {
            com.thelads.core.config.Module m = com.thelads.core.config.ModuleManager.getInstance().getModule("ImmediatelyFast");
            return m != null && m.isEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    public static void earlyInit() {
        if (config != null) {
            return;
        }
        config = new ImmediatelyFastConfig();
        runtimeConfig = new ImmediatelyFastRuntimeConfig(config);
    }

    public static void onRenderSystemInit() {
        String gpuVendor = RenderSystem.getDevice().getDeviceInfo().vendorName();
        String gpuModel = RenderSystem.getDevice().getDeviceInfo().name();
        String backendName = RenderSystem.getDevice().getDeviceInfo().backendName();
        String backendVersion = RenderSystem.getDevice().getDeviceInfo().driverInfo();
        LOGGER.info("Initializing ImmediatelyFast " + VERSION + " on " + gpuModel + " (" + gpuVendor + ") with " + backendName + " " + backendVersion);
        
        boolean isApple = false;
        if (gpuVendor != null) {
            String gpuVendorLower = gpuVendor.toLowerCase();
            isApple = gpuVendorLower.startsWith("apple");
        }
        Objects.requireNonNull(config, "Config not loaded yet");
        Objects.requireNonNull(runtimeConfig, "Runtime config not created yet");
        if (config.fix_slow_buffer_upload_on_apple_gpu && isApple && !RenderSystem.getDevice().getDeviceInfo().underlyingExtensions().contains("GL_ARB_direct_state_access") && !RenderSystem.getDevice().getDeviceInfo().underlyingExtensions().contains("GL_ARB_buffer_storage")) {
            runtimeConfig.disable_fast_buffer_upload = true;
        }
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            LOGGER.info("Found Iris. Enabling compatibility.");
            IrisCompat.init();
        }
    }

    public static void lateInit() {
    }

    public static void onLevelChange() {
    }
}
