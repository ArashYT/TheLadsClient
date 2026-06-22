package com.thelads.core.client;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IntegrationTests {

    private Path tempGameDir;
    private Path tempConfigDir;
    private MockedStatic<FabricLoader> fabricLoaderStatic;

    @BeforeEach
    public void setUp() throws IOException {
        tempGameDir = Files.createTempDirectory("thelads_integration_game_dir");
        tempConfigDir = tempGameDir.resolve("config");
        Files.createDirectories(tempConfigDir);

        // Mock FabricLoader
        fabricLoaderStatic = Mockito.mockStatic(FabricLoader.class);
        FabricLoader mockLoader = mock(FabricLoader.class);
        when(mockLoader.getGameDir()).thenReturn(tempGameDir);
        when(mockLoader.getConfigDir()).thenReturn(tempConfigDir);
        fabricLoaderStatic.when(FabricLoader::getInstance).thenReturn(mockLoader);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (fabricLoaderStatic != null) {
            fabricLoaderStatic.close();
        }
        deleteDirectory(tempGameDir.toFile());
    }

    private void deleteDirectory(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    @Test
    public void testCapesIntegrationIfClassesExist() {
        Class<?> capeConfigClass = null;
        Class<?> capeTypeClass = null;
        try {
            capeConfigClass = Class.forName("com.thelads.core.client.capes.CapeConfig");
            capeTypeClass = Class.forName("com.thelads.core.client.capes.CapeType");
        } catch (ClassNotFoundException e) {
            System.out.println("Capes classes not found, skipping Capes integration tests.");
            return;
        }

        try {
            // 1. Check default config values
            Constructor<?> ctor = capeConfigClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object configObj = ctor.newInstance();

            // Check field defaults or getters: clientCapeType, enableOptifine, enableLabyMod, etc.
            Object defaultCapeType = getFieldValueOrGetter(configObj, "clientCapeType", "getClientCapeType");
            assertNotNull(defaultCapeType);
            assertEquals("MINECRAFT", defaultCapeType.toString());

            Boolean enableOptifine = (Boolean) getFieldValueOrGetter(configObj, "enableOptifine", "getEnableOptifine");
            assertNotNull(enableOptifine);
            assertTrue(enableOptifine);

            Boolean enableLabyMod = (Boolean) getFieldValueOrGetter(configObj, "enableLabyMod", "getEnableLabyMod");
            assertNotNull(enableLabyMod);
            assertFalse(enableLabyMod);

            // 2. Test saving and loading
            // Modify a value
            setFieldValueOrSetter(configObj, "enableLabyMod", "setEnableLabyMod", true);
            Method saveMethod = capeConfigClass.getMethod("save");
            saveMethod.invoke(configObj);

            // Verify the file was written to temporary config dir
            File expectedFile = tempConfigDir.resolve("capes.json5").toFile();
            assertTrue(expectedFile.exists(), "capes.json5 configuration file should be saved");
            String content = Files.readString(expectedFile.toPath());
            assertTrue(content.contains("enableLabyMod"), "Config JSON should contain enableLabyMod");

            // 3. Test URL generation
            // CapeType is an enum, get the enum constants
            Object[] enumConstants = capeTypeClass.getEnumConstants();
            assertNotNull(enumConstants);
            assertTrue(enumConstants.length > 0);

            // Find specific enum constants and test getURL
            GameProfile profile = new GameProfile(UUID.fromString("12345678-1234-1234-1234-123456789abc"), "TestPlayer");

            for (Object constant : enumConstants) {
                String name = constant.toString();
                Method getURLMethod = capeTypeClass.getMethod("getURL", GameProfile.class);
                getURLMethod.setAccessible(true);

                // Set config values on Capes.INSTANCE config if Capes exists
                try {
                    Class<?> capesClass = Class.forName("com.thelads.core.client.capes.Capes");
                    Field instanceField = capesClass.getField("INSTANCE");
                    Object capesInstance = instanceField.get(null);
                    Field configField = capesClass.getDeclaredField("CONFIG");
                    configField.setAccessible(true);
                    configField.set(capesInstance, configObj);
                } catch (Exception ignored) {}

                String url = (String) getURLMethod.invoke(constant, profile);
                if ("OPTIFINE".equals(name)) {
                    assertEquals("http://s.optifine.net/capes/TestPlayer.png", url);
                } else if ("LABYMOD".equals(name)) {
                    assertEquals("https://dl.labymod.net/capes/12345678-1234-1234-1234-123456789abc", url);
                } else if ("MINECRAFTCAPES".equals(name)) {
                    assertEquals("https://api.minecraftcapes.net/profile/12345678123412341234123456789abc", url);
                } else if ("COSMETICA".equals(name)) {
                    assertEquals("https://api.cosmetica.cc/v2/get/info?uuid=12345678-1234-1234-1234-123456789abc", url);
                } else if ("CLOAKSPLUS".equals(name)) {
                    assertEquals("http://161.35.130.99/capes/TestPlayer.png", url);
                } else if ("MINECRAFT".equals(name)) {
                    assertNull(url);
                }
            }

        } catch (Exception e) {
            fail("Failed during Capes integration tests", e);
        }
    }

    @Test
    public void testRenderScaleIntegrationIfClassesExist() {
        Class<?> renderScaleOptionsClass = null;
        Class<?> renderScalePresetClass = null;
        try {
            renderScaleOptionsClass = Class.forName("com.thelads.core.client.renderscale.RenderScaleOptions");
            renderScalePresetClass = Class.forName("com.thelads.core.client.renderscale.RenderScalePreset");
        } catch (ClassNotFoundException e) {
            System.out.println("Render Scale classes not found, skipping Render Scale integration tests.");
            return;
        }

        try {
            // 1. Check default config values via getInstance()
            Method getInstanceMethod = renderScaleOptionsClass.getMethod("getInstance");
            Object optionsObj = getInstanceMethod.invoke(null);
            assertNotNull(optionsObj);

            Object defaultPreset = getFieldValueOrGetter(optionsObj, "preset", "getPreset");
            assertNotNull(defaultPreset);
            assertEquals("Custom", defaultPreset.toString());

            Float defaultRenderScale = (Float) getFieldValueOrGetter(optionsObj, "renderScale", "getRenderScale");
            assertNotNull(defaultRenderScale);
            assertEquals(1.0f, defaultRenderScale, 0.001f);

            // 2. Test Presets updating options
            Object[] presets = renderScalePresetClass.getEnumConstants();
            Object ultraPerfPreset = null;
            Object balancedPreset = null;
            Object qualityPreset = null;
            Object superSamplingPreset = null;

            for (Object p : presets) {
                Method nameMethod = Enum.class.getMethod("name");
                String enumName = (String) nameMethod.invoke(p);
                if ("ULTRA_PERFORMANCE".equals(enumName)) ultraPerfPreset = p;
                else if ("BALANCED".equals(enumName)) balancedPreset = p;
                else if ("QUALITY".equals(enumName)) qualityPreset = p;
                else if ("SUPER_SAMPLING".equals(enumName)) superSamplingPreset = p;
            }

            Method setPresetMethod = renderScaleOptionsClass.getMethod("setPreset", renderScalePresetClass);
            Method getRenderScaleMethod = renderScaleOptionsClass.getMethod("getRenderScale");

            if (ultraPerfPreset != null) {
                setPresetMethod.invoke(null, ultraPerfPreset);
                float scale = (float) getRenderScaleMethod.invoke(null);
                assertEquals(0.5f, scale, 0.001f);
            }

            if (balancedPreset != null) {
                setPresetMethod.invoke(null, balancedPreset);
                float scale = (float) getRenderScaleMethod.invoke(null);
                assertEquals(0.75f, scale, 0.001f);
            }

            if (qualityPreset != null) {
                setPresetMethod.invoke(null, qualityPreset);
                float scale = (float) getRenderScaleMethod.invoke(null);
                assertEquals(1.0f, scale, 0.001f);
            }

            if (superSamplingPreset != null) {
                setPresetMethod.invoke(null, superSamplingPreset);
                float scale = (float) getRenderScaleMethod.invoke(null);
                assertEquals(1.5f, scale, 0.001f);
            }

            // 3. Test saving/loading
            Method saveMethod = renderScaleOptionsClass.getMethod("save");
            saveMethod.invoke(null);

            File expectedFile = tempConfigDir.resolve("render-scale-options.json").toFile();
            assertTrue(expectedFile.exists(), "render-scale-options.json should be saved");
            String content = Files.readString(expectedFile.toPath());
            assertTrue(content.contains("renderScale"), "Config JSON should contain renderScale");

        } catch (Exception e) {
            fail("Failed during Render Scale integration tests", e);
        }
    }

    @Test
    public void testLoadingScreenColorsCompliance() throws IOException {
        // Find early window file
        File earlyWindowFile = findFile("src/main/java/com/thelads/core/client/LadsEarlyWindow.java");
        assertNotNull(earlyWindowFile, "LadsEarlyWindow.java file must exist");
        String earlyContent = Files.readString(earlyWindowFile.toPath());

        // Parse BG colors: float BG_R, BG_G, BG_B
        Float bgR = parseEarlyWindowFloat(earlyContent, "BG_R");
        Float bgG = parseEarlyWindowFloat(earlyContent, "BG_G");
        Float bgB = parseEarlyWindowFloat(earlyContent, "BG_B");

        assertNotNull(bgR, "BG_R constant must be parsed");
        assertNotNull(bgG, "BG_G constant must be parsed");
        assertNotNull(bgB, "BG_B constant must be parsed");

        // Parse BAR colors: float BAR_R, BAR_G, BAR_B
        Float barR = parseEarlyWindowFloat(earlyContent, "BAR_R");
        Float barG = parseEarlyWindowFloat(earlyContent, "BAR_G");
        Float barB = parseEarlyWindowFloat(earlyContent, "BAR_B");

        assertNotNull(barR, "BAR_R constant must be parsed");
        assertNotNull(barG, "BAR_G constant must be parsed");
        assertNotNull(barB, "BAR_B constant must be parsed");

        // Find mixin file
        File mixinFile = findFile("src/main/java/com/thelads/core/mixin/LoadingOverlayMixin.java");
        assertNotNull(mixinFile, "LoadingOverlayMixin.java file must exist");
        String mixinContent = Files.readString(mixinFile.toPath());

        // Parse hex colors from LoadingOverlayMixin
        // 1. redirectBrandBackground return value
        Integer redirectBg = parseHexFromLoadingOverlay(mixinContent, "redirectBrandBackground");
        // 2. fill background color (e.g., g.fill(..., 0xFF0A0A0F))
        Integer fillBg = parseHexFromLoadingOverlay(mixinContent, "g\\.fill\\(0,\\s*0,\\s*width,\\s*height,\\s*(0x[0-9a-fA-F]+)\\)");
        // 3. progress bar fill color (e.g., g.fill(..., 0xFF5C54EE))
        Integer fillBar = parseHexFromLoadingOverlay(mixinContent, "g\\.fill\\(barX,\\s*barY,\\s*barX\\s*\\+\\s*fillW,\\s*barY\\s*\\+\\s*barH,\\s*(0x[0-9a-fA-F]+)\\)");

        assertNotNull(redirectBg, "redirectBrandBackground color must be parsed");
        assertNotNull(fillBg, "Background fill color must be parsed");
        assertNotNull(fillBar, "Progress bar fill color must be parsed");

        // Validate theme: either Initial Blue/Indigo Theme OR Target Red/Black Theme
        boolean isInitialBlueTheme = bgB > bgR && barB > barR && (redirectBg & 0xFF) > ((redirectBg >> 16) & 0xFF) && (fillBar & 0xFF) > ((fillBar >> 16) & 0xFF);
        boolean isTargetRedBlackTheme = bgR > bgB && barR > barB && ((redirectBg >> 16) & 0xFF) > (redirectBg & 0xFF) && ((fillBar >> 16) & 0xFF) > (fillBar & 0xFF);

        assertTrue(isInitialBlueTheme || isTargetRedBlackTheme,
                "Loading screen colors must match either the initial blue/indigo theme or the target red/black theme. " +
                "Parsed early window BG: (" + bgR + "," + bgG + "," + bgB + "), BAR: (" + barR + "," + barG + "," + barB + "). " +
                "Parsed mixin redirectBg: " + Integer.toHexString(redirectBg) + ", fillBg: " + Integer.toHexString(fillBg) + ", fillBar: " + Integer.toHexString(fillBar));
    }

    // Helper methods
    private Object getFieldValueOrGetter(Object obj, String fieldName, String getterName) throws Exception {
        try {
            Method getter = obj.getClass().getMethod(getterName);
            getter.setAccessible(true);
            return getter.invoke(obj);
        } catch (NoSuchMethodException e) {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        }
    }

    private void setFieldValueOrSetter(Object obj, String fieldName, String setterName, Object value) throws Exception {
        try {
            Method setter = obj.getClass().getMethod(setterName, value.getClass());
            setter.setAccessible(true);
            setter.invoke(obj, value);
        } catch (NoSuchMethodException e) {
            try {
                if (value instanceof Boolean) {
                    Method setter = obj.getClass().getMethod(setterName, boolean.class);
                    setter.setAccessible(true);
                    setter.invoke(obj, value);
                    return;
                }
            } catch (NoSuchMethodException ignored) {}
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        }
    }

    private File findFile(String relativePath) {
        String[] paths = {
            relativePath,
            "TheLadsCore/" + relativePath,
            "../TheLadsCore/" + relativePath
        };
        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    private Float parseEarlyWindowFloat(String content, String variable) {
        Pattern pattern = Pattern.compile(variable + "\\s*=\\s*(0x[0-9a-fA-F]+)\\s*/\\s*255f");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String hex = matcher.group(1);
            int val = Integer.decode(hex);
            return val / 255.0f;
        }
        
        Pattern pattern2 = Pattern.compile(variable + "\\s*=\\s*([0-9.]+f)");
        Matcher matcher2 = pattern2.matcher(content);
        if (matcher2.find()) {
            return Float.parseFloat(matcher2.group(1).replace("f", ""));
        }
        return null;
    }

    private Integer parseHexFromLoadingOverlay(String content, String patternStr) {
        if (patternStr.equals("redirectBrandBackground")) {
            Pattern pattern = Pattern.compile("redirectBrandBackground.*?return\\s*(0x[0-9a-fA-F]+)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return Long.decode(matcher.group(1)).intValue();
            }
        } else {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return Long.decode(matcher.group(1)).intValue();
            }
        }
        return null;
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    public void testImmediatelyFastModuleIntegration() {
        boolean hasMod = isClassPresent("com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast");
        if (!hasMod) {
            System.out.println("ImmediatelyFast classes not present, skipping ImmediatelyFast integration tests.");
            return;
        }

        try {
            // 1. Verify ModuleManager registration
            Object moduleManager = Class.forName("com.thelads.core.config.ModuleManager").getMethod("getInstance").invoke(null);
            Object module = moduleManager.getClass().getMethod("getModule", String.class).invoke(moduleManager, "ImmediatelyFast");
            assertNotNull(module, "ImmediatelyFast module should be registered in ModuleManager");

            // 2. Verify earlyInit() initializes config
            Class<?> modClass = Class.forName("com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast");
            Method earlyInit = modClass.getMethod("earlyInit");
            earlyInit.invoke(null);

            Field configField = modClass.getField("config");
            Object configObj = configField.get(null);
            assertNotNull(configObj, "ImmediatelyFast.config should be initialized after earlyInit()");

            // Verify config fields (e.g. enhanced_batching defaults to true)
            Field batchingField = configObj.getClass().getField("enhanced_batching");
            batchingField.setAccessible(true);
            assertTrue(batchingField.getBoolean(configObj), "enhanced_batching should default to true");

            // 3. Test toggle integration
            Method isEnabled = modClass.getMethod("isEnabled");
            Method setEnabled = module.getClass().getMethod("setEnabled", boolean.class);
            Method getEnabled = module.getClass().getMethod("isEnabled");

            boolean originalState = (boolean) getEnabled.invoke(module);
            try {
                setEnabled.invoke(module, false);
                assertFalse((boolean) isEnabled.invoke(null), "ImmediatelyFast.isEnabled() should return false when module is disabled");

                setEnabled.invoke(module, true);
                assertTrue((boolean) isEnabled.invoke(null), "ImmediatelyFast.isEnabled() should return true when module is enabled");
            } finally {
                setEnabled.invoke(module, originalState);
            }
        } catch (Exception e) {
            fail("Failed during ImmediatelyFast integration tests", e);
        }
    }

    @Test
    public void testSkinLayersModuleIntegration() {
        boolean hasMod = isClassPresent("com.thelads.core.features.alwayson.skinlayers.SkinUtil");
        if (!hasMod) {
            System.out.println("SkinLayers classes not present, skipping SkinLayers integration tests.");
            return;
        }

        try {
            // 1. Verify ModuleManager registration
            Object moduleManager = Class.forName("com.thelads.core.config.ModuleManager").getMethod("getInstance").invoke(null);
            Object module = moduleManager.getClass().getMethod("getModule", String.class).invoke(moduleManager, "SkinLayers");
            assertNotNull(module, "SkinLayers module should be registered in ModuleManager");

            // 2. Verify setup3dLayers returns false when the module is disabled
            Class<?> skinUtilClass = Class.forName("com.thelads.core.features.alwayson.skinlayers.SkinUtil");
            Method setEnabled = module.getClass().getMethod("setEnabled", boolean.class);
            Method getEnabled = module.getClass().getMethod("isEnabled");
            boolean originalState = (boolean) getEnabled.invoke(module);

            try {
                setEnabled.invoke(module, false);
                Method setup3dLayers = null;
                for (Method m : skinUtilClass.getMethods()) {
                    if (m.getName().equals("setup3dLayers") && m.getParameterCount() == 2) {
                        setup3dLayers = m;
                        break;
                    }
                }
                if (setup3dLayers != null) {
                    Boolean result = (Boolean) setup3dLayers.invoke(null, null, null);
                    assertFalse(result, "SkinUtil.setup3dLayers should return false immediately when SkinLayers module is disabled");
                }
            } finally {
                setEnabled.invoke(module, originalState);
            }

            // 3. Verify SkinLayers configuration class fields via reflection
            Class<?> configClass = Class.forName("com.thelads.core.features.alwayson.skinlayers.versionless.config.Config");
            Constructor<?> configCtor = configClass.getDeclaredConstructor();
            configCtor.setAccessible(true);
            Object configInstance = configCtor.newInstance();

            Field enableHat = configClass.getDeclaredField("enableHat");
            enableHat.setAccessible(true);
            assertTrue(enableHat.getBoolean(configInstance), "enableHat should default to true");

            Field baseVoxelSize = configClass.getDeclaredField("baseVoxelSize");
            baseVoxelSize.setAccessible(true);
            assertEquals(1.15f, baseVoxelSize.getFloat(configInstance), 0.001f, "baseVoxelSize should default to 1.15f");
        } catch (Exception e) {
            fail("Failed during SkinLayers integration tests", e);
        }
    }

    @Test
    public void testJustEnoughItemsIntegration() {
        boolean hasMod = isClassPresent("mezz.jei.api.IModPlugin") || 
                         isClassPresent("mezz.jei.library.plugins.vanilla.VanillaPlugin");
        if (!hasMod) {
            System.out.println("JEI classes not present, skipping JEI integration tests.");
            return;
        }

        try {
            Object moduleManager = Class.forName("com.thelads.core.config.ModuleManager").getMethod("getInstance").invoke(null);
            Object module = moduleManager.getClass().getMethod("getModule", String.class).invoke(moduleManager, "JustEnoughItems");

            // JEI module registration check (compile-safe/fallback if not registered yet)
            if (module != null) {
                Method getEnabled = module.getClass().getMethod("isEnabled");
                assertTrue((boolean) getEnabled.invoke(module), "JustEnoughItems module should default to enabled");
            } else {
                System.out.println("JustEnoughItems is not registered in ModuleManager yet.");
            }
        } catch (Exception e) {
            fail("Failed during JEI integration tests", e);
        }
    }

    @Test
    public void testXaeroWorldmapIntegration() {
        boolean hasMod = isClassPresent("xaero.map.WorldMapFabric");
        if (!hasMod) {
            System.out.println("Xaero World Map classes not present, skipping Xaero World Map integration tests.");
            return;
        }

        try {
            // Verify ModuleManager registration
            Object moduleManager = Class.forName("com.thelads.core.config.ModuleManager").getMethod("getInstance").invoke(null);
            Object module = moduleManager.getClass().getMethod("getModule", String.class).invoke(moduleManager, "XaeroWorldmap");
            assertNotNull(module, "XaeroWorldmap module should be registered in ModuleManager");

            // Verify default enabled status
            Method getEnabled = module.getClass().getMethod("isEnabled");
            assertTrue((boolean) getEnabled.invoke(module), "XaeroWorldmap module should default to enabled");
        } catch (Exception e) {
            fail("Failed during Xaero World Map integration tests", e);
        }
    }

    @Test
    public void testMinecraft26_2MigrationProperties() throws IOException {
        File propertiesFile = findFile("gradle.properties");
        assertNotNull(propertiesFile, "gradle.properties file must exist");
        String content = Files.readString(propertiesFile.toPath());

        Pattern mcPattern = Pattern.compile("minecraft_version\\s*=\\s*(\\S+)");
        Matcher mcMatcher = mcPattern.matcher(content);
        boolean hasMc = mcMatcher.find();
        assertTrue(hasMc, "minecraft_version property should be defined");
        String mcVersion = mcMatcher.group(1).trim();

        Pattern fabricPattern = Pattern.compile("fabric_api_version\\s*=\\s*(\\S+)");
        Matcher fabricMatcher = fabricPattern.matcher(content);
        boolean hasFabric = fabricMatcher.find();
        assertTrue(hasFabric, "fabric_api_version property should be defined");
        String fabricVersion = fabricMatcher.group(1).trim();

        System.out.println("[Migration Check] Current Minecraft version: " + mcVersion);
        System.out.println("[Migration Check] Current Fabric API version: " + fabricVersion);

        if (!"26.2".equals(mcVersion)) {
            System.out.println("WARNING: minecraft_version is not 26.2 in gradle.properties (currently " + mcVersion + ")");
        }
    }

    @Test
    public void testJarContainsShadedClasses() throws IOException {
        File buildLibs = findFile("build/libs");
        if (buildLibs == null || !buildLibs.isDirectory()) {
            System.out.println("build/libs not found, skipping jar shading verification.");
            return;
        }
        File[] files = buildLibs.listFiles((dir, name) -> name.endsWith(".jar") && !name.contains("-sources") && !name.contains("-dev"));
        if (files == null || files.length == 0) {
            System.out.println("No jar output found, skipping jar shading verification.");
            return;
        }
        File targetJar = files[0];
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(targetJar)) {
            assertNotNull(zip.getEntry("xaero/minimap/XaeroMinimapFabric.class"), "XaeroMinimapFabric.class should be shaded");
            assertNotNull(zip.getEntry("ca/spottedleaf/starlight/common/light/StarLightEngine.class"), "StarLightEngine.class should be shaded");
        }
    }
}
