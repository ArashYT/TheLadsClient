package com.thelads.core.client.cosmetics;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import com.thelads.core.client.gui.cosmetics.CosmeticsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.util.UUID;

/**
 * STRESS TEST HARNESS for M6.3 - Model Application Memory Leak.
 * 
 * To empirically verify this memory leak, you can invoke `StressTestHarness.runLeakStressTest()` 
 * from any in-game command or debug keybind.
 */
public class StressTestHarness {

    public static void runLeakStressTest(File testImageFile) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        UUID playerUuid = mc.player.getUUID();
        
        System.out.println("Starting Memory Leak Stress Test...");
        
        for (int i = 0; i < 50; i++) {
            // 1. Simulate opening the Cosmetics Screen
            CosmeticsScreen screen = new CosmeticsScreen(null);
            mc.setScreenAndShow(screen);
            
            // 2. Fetch a new texture
            Identifier newTexture = CosmeticsBackend.fetchByFile(testImageFile).join();
            
            // 3. Simulate UI preview texture update
            try {
                java.lang.reflect.Method updatePreview = CosmeticsScreen.class.getDeclaredMethod("updatePreviewTexture", Identifier.class);
                updatePreview.setAccessible(true);
                updatePreview.invoke(screen, newTexture);
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // 4. User clicks 'Apply'
            CosmeticsBackend.setActiveSkin(playerUuid, newTexture);
            
            // 5. User closes screen
            screen.removed();
            
            // At this point, the PREVIOUS active skin (from iteration i-1) is permanently leaked
            // because CosmeticsBackend.setActiveSkin replaced it without releasing,
            // and CosmeticsScreen did not release it because it was "in use" when the screen was closed.
        }
        
        System.out.println("Stress Test Complete. 49 DynamicTexture instances have been leaked in TextureManager.");
        System.out.println("Check memory usage or heap dump to confirm NativeImage memory leak.");
    }
}
