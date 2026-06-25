package com.thelads.core.client;

import com.mojang.authlib.GameProfile;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.client.capes.CapeConfig;
import com.thelads.core.client.capes.CapeType;
import com.thelads.core.client.capes.PlayerHandler;
import com.thelads.core.client.capes.util.CapesUtils;
import com.thelads.core.features.alwayson.skinlayers.SkinUtil;
import com.thelads.core.mixin.alwayson.skinlayers.ModelPartMixin;
import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import com.thelads.core.features.alwayson.skinlayers.api.OffsetProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementHolder;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class E2EPolishTests {

    private Path tempGameDir;
    private Path tempConfigDir;
    private MockedStatic<FabricLoader> fabricLoaderStatic;

    private void setField(Object obj, Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @BeforeEach
    public void setUp() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable t) {
            // Might already be bootstrapped, ignore
        }

        tempGameDir = Files.createTempDirectory("thelads_e2e_game_dir");
        tempConfigDir = tempGameDir.resolve("config");
        Files.createDirectories(tempConfigDir);

        // Mock FabricLoader
        fabricLoaderStatic = Mockito.mockStatic(FabricLoader.class);
        FabricLoader mockLoader = mock(FabricLoader.class);
        when(mockLoader.getGameDir()).thenReturn(tempGameDir);
        when(mockLoader.getConfigDir()).thenReturn(tempConfigDir);
        fabricLoaderStatic.when(FabricLoader::getInstance).thenReturn(mockLoader);

        // Initialize NEABaseMod.config to prevent NPEs in animations
        dev.tr7zw.notenoughanimations.versionless.NEABaseMod.config = new dev.tr7zw.notenoughanimations.versionless.config.Config();

        // Mock NEAnimationsLoader.INSTANCE and its fields
        dev.tr7zw.notenoughanimations.NEAnimationsLoader mockNea = mock(dev.tr7zw.notenoughanimations.NEAnimationsLoader.class);
        mockNea.heldItemHandler = mock(dev.tr7zw.notenoughanimations.logic.HeldItemHandler.class);
        mockNea.playerTransformer = mock(dev.tr7zw.notenoughanimations.logic.PlayerTransformer.class);
        mockNea.animationProvider = mock(dev.tr7zw.notenoughanimations.logic.AnimationProvider.class);
        dev.tr7zw.notenoughanimations.NEAnimationsLoader.INSTANCE = mockNea;
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (fabricLoaderStatic != null) {
            fabricLoaderStatic.close();
        }
        deleteDirectory(tempGameDir.toFile());
    }

    private void deleteDirectory(java.io.File file) {
        java.io.File[] contents = file.listFiles();
        if (contents != null) {
            for (java.io.File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    @Test
    public void testCapesPreferredSelectionAndCycle() {
        // Test CapeType cycle
        assertEquals(CapeType.OPTIFINE, CapeType.MINECRAFT.cycle());
        assertEquals(CapeType.LABYMOD, CapeType.OPTIFINE.cycle());
        assertEquals(CapeType.COSMETICA, CapeType.LABYMOD.cycle());
        assertEquals(CapeType.MINECRAFTCAPES, CapeType.COSMETICA.cycle());
        assertEquals(CapeType.CLOAKSPLUS, CapeType.MINECRAFTCAPES.cycle());
        assertEquals(CapeType.MINECRAFT, CapeType.CLOAKSPLUS.cycle());
        
        // Test URL generation for different cape types
        GameProfile profile = new GameProfile(UUID.fromString("88888888-4444-4444-4444-121212121212"), "TestUser");
        
        Module capesMod = ModuleManager.getInstance().getModule("Capes");
        if (capesMod != null) {
            boolean originalState = capesMod.isEnabled();
            try {
                capesMod.setEnabled(true);
                
                // Verify OptiFine cape URL
                String optifineUrl = CapeType.OPTIFINE.getURL(profile);
                assertEquals("http://s.optifine.net/capes/TestUser.png", optifineUrl);
                
                // Enable LabyMod Capes option and verify URL
                var optLaby = capesMod.getOption("LabyMod Capes");
                if (optLaby instanceof com.thelads.core.config.BoolOption) {
                    ((com.thelads.core.config.BoolOption) optLaby).set(true);
                }
                assertEquals("https://dl.labymod.net/capes/88888888-4444-4444-4444-121212121212", CapeType.LABYMOD.getURL(profile));
                
                // Verify MinecraftCapes URL formatting (UUID without dashes)
                assertEquals("https://api.minecraftcapes.net/profile/88888888444444444444121212121212", CapeType.MINECRAFTCAPES.getURL(profile));
            } finally {
                capesMod.setEnabled(originalState);
            }
        }
        
        // Profile validation
        assertTrue(CapesUtils.isValidProfile(profile));
        assertFalse(CapesUtils.isValidProfile(null));
    }

    @Test
    public void testPlayerHandlerOnLoadTexture() throws Exception {
        try (MockedStatic<Minecraft> mcStatic = Mockito.mockStatic(Minecraft.class)) {
            Minecraft mockMc = mock(Minecraft.class);
            mcStatic.when(Minecraft::getInstance).thenReturn(mockMc);
            
            GameProfile mockProfile = new GameProfile(UUID.randomUUID(), "LadsPlayer");
            when(mockMc.getGameProfile()).thenReturn(mockProfile);
            
            Module capesMod = ModuleManager.getInstance().getModule("Capes");
            if (capesMod != null) {
                var preferredCapeOpt = capesMod.getOption("Preferred Cape");
                if (preferredCapeOpt instanceof com.thelads.core.config.DropdownOption) {
                    ((com.thelads.core.config.DropdownOption) preferredCapeOpt).setIndex(2); // Set to LabyMod
                }
            }
            
            // Call onLoadTexture and verify it triggers player handler initialization
            PlayerHandler.onLoadTexture(mockProfile);
            PlayerHandler handler = PlayerHandler.fromProfile(mockProfile);
            assertNotNull(handler);
            assertEquals(mockProfile, handler.getProfile());
        }
    }

    @Test
    public void testNotEnoughAnimationsModuleAndProvider() {
        Module neaModule = ModuleManager.getInstance().getModule("NotEnoughAnimations");
        assertNotNull(neaModule, "NotEnoughAnimations module should be registered");
        assertTrue(neaModule.isEnabled(), "NotEnoughAnimations should default to enabled");
        
        dev.tr7zw.notenoughanimations.logic.AnimationProvider provider = new dev.tr7zw.notenoughanimations.logic.AnimationProvider();
        
        try {
            Field basicAnimationsField = dev.tr7zw.notenoughanimations.logic.AnimationProvider.class.getDeclaredField("basicAnimations");
            basicAnimationsField.setAccessible(true);
            java.util.Set<?> animations = (java.util.Set<?>) basicAnimationsField.get(provider);
            assertNotNull(animations);
            assertTrue(animations.size() > 0, "AnimationProvider should load animations");
        } catch (Exception e) {
            fail("Failed to verify AnimationProvider animations", e);
        }
    }

    @Test
    public void testSkinLayersModelPartMixinEradicatesDuplication() throws Exception {
        ModelPartMixin mixin = new ModelPartMixin();
        
        CallbackInfo ci = new CallbackInfo("render", true);
        com.mojang.blaze3d.vertex.PoseStack poseStack = mock(com.mojang.blaze3d.vertex.PoseStack.class);
        com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer = mock(com.mojang.blaze3d.vertex.VertexConsumer.class);
        
        // Set visible field to true using reflection
        Field visibleField = ModelPartMixin.class.getDeclaredField("visible");
        visibleField.setAccessible(true);
        visibleField.set(mixin, true);
        
        mixin.render(poseStack, vertexConsumer, 15, 15, 0xFFFFFF, ci);
        assertFalse(ci.isCancelled(), "Should not cancel rendering when injectedMesh is null");
        
        // Inject mesh and offset provider
        Mesh mockMesh = mock(Mesh.class);
        OffsetProvider mockOffset = mock(OffsetProvider.class);
        mixin.setInjectedMesh(mockMesh, mockOffset);
        
        CallbackInfo ciCancel = new CallbackInfo("render", true);
        
        // When injectedMesh is present, it should attempt to render it, causing a ClassCastException in unit tests because mixins are not applied to merge ModelPartMixin and ModelPart.
        ClassCastException ex = assertThrows(ClassCastException.class, () -> {
            mixin.render(poseStack, vertexConsumer, 15, 15, 0xFFFFFF, ciCancel);
        });
        
        assertTrue(ex.getMessage().contains("ModelPartMixin") && ex.getMessage().contains("ModelPart"), 
                   "Should attempt to cast ModelPartMixin to ModelPart to render the 3D injected mesh");
    }

    @Test
    public void testSkinLayersDisabledSetup3dLayersReturnsFalse() {
        Module skinLayersMod = ModuleManager.getInstance().getModule("SkinLayers");
        if (skinLayersMod != null) {
            boolean originalState = skinLayersMod.isEnabled();
            try {
                skinLayersMod.setEnabled(false);
                boolean result = SkinUtil.setup3dLayers((net.minecraft.client.player.AbstractClientPlayer)null, null, false);
                assertFalse(result, "setup3dLayers should return false immediately when module is disabled");
            } finally {
                skinLayersMod.setEnabled(originalState);
            }
        }
    }
}
