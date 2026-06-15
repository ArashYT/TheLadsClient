package com.thelads.core.client.gui.cosmetics;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.Mockito.*;

public class CosmeticsLeakTest {

    @BeforeAll
    public static void setup() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    public void testTextureLeakOnMultipleApplies() {
        // Setup mocks
        Minecraft mockMinecraft = mock(Minecraft.class);
        TextureManager mockTextureManager = mock(TextureManager.class);
        when(mockMinecraft.getTextureManager()).thenReturn(mockTextureManager);
        
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockMinecraft).execute(any(Runnable.class));

        // Mock Player instead of LocalPlayer, or just use reflection to bypass player
        net.minecraft.client.player.LocalPlayer mockPlayer = Mockito.mock(net.minecraft.client.player.LocalPlayer.class, Mockito.withSettings().withoutAnnotations());
        UUID playerUuid = UUID.randomUUID();
        when(mockPlayer.getUUID()).thenReturn(playerUuid);
        mockMinecraft.player = mockPlayer;
        
        try (MockedStatic<Minecraft> mcStatic = mockStatic(Minecraft.class)) {
            mcStatic.when(Minecraft::getInstance).thenReturn(mockMinecraft);
            
            // In a real scenario, fetching registers a texture with TextureManager
            Identifier textureA = Identifier.fromNamespaceAndPath("thelads", "tex_a");
            Identifier textureB = Identifier.fromNamespaceAndPath("thelads", "tex_b");
            
            // Assume textureA is the current active skin (e.g. from previous fetch & apply)
            CosmeticsBackend.setActiveSkin(playerUuid, textureA);
            
            // Now a player wants to change their skin to textureB
            // The screen is opened and they fetch texture B
            CosmeticsScreen screen = new CosmeticsScreen(null);
            
            // We use reflection since `minecraft` is protected in `Screen`
            try {
                java.lang.reflect.Field mcField = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("minecraft");
                mcField.setAccessible(true);
                mcField.set(screen, mockMinecraft);
                
                java.lang.reflect.Method updatePreview = CosmeticsScreen.class.getDeclaredMethod("updatePreviewTexture", Identifier.class);
                updatePreview.setAccessible(true);
                
                // User previews texture B
                updatePreview.invoke(screen, textureB);
                
                // User applies texture B
                CosmeticsBackend.setActiveSkin(playerUuid, textureB);
                
                // User closes the screen
                screen.removed();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // After applying B and closing, textureA is NO LONGER in use.
            // We expect the backend/screen to release texture A to prevent a GPU memory leak.
            verify(mockTextureManager, atLeastOnce()).release(textureA);
        }
    }
}
