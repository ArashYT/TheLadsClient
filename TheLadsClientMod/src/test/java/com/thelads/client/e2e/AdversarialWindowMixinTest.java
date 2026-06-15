package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.client.mixin.WindowMixin;
import com.thelads.client.config.ConfigManager;
import java.lang.reflect.Method;

public class AdversarialWindowMixinTest {
    @BeforeEach
    public void setupMocks() throws Exception { 
        ReflectionHelper.setupMocks(); 
        ConfigManager.getConfig().setUiScalingEnabled(false);
    }
    
    @AfterEach
    public void teardownMocks() { 
        ReflectionHelper.teardownMocks(); 
    }

    @Test
    public void testWindowMixinForcesGuiScale() throws Exception {
        // Instantiate the mixin to test its handler directly
        WindowMixin mixin = new WindowMixin();
        
        // We mock CallbackInfoReturnable or just use a real one since it's an instantiable class in Mixin library
        // Wait, CallbackInfoReturnable constructor is protected/package-private in some versions, but we can mock it
        CallbackInfoReturnable<Integer> cir = org.mockito.Mockito.mock(CallbackInfoReturnable.class);
        
        // Let's assume the original method would have returned 3 (GUI Scale 3)
        org.mockito.Mockito.when(cir.getReturnValue()).thenReturn(3);
        
        Method m = WindowMixin.class.getDeclaredMethod("onCalculateScale", CallbackInfoReturnable.class);
        m.setAccessible(true);
        m.invoke(mixin, cir);
        
        // The bug is that if uiScalingEnabled is false, it forces the return value to 1,
        // totally destroying the user's selected GUI scale (like 3 or 4) for the whole game.
        // We assert that it SHOULD NOT force it to 1, meaning the test will fail against the current codebase!
        org.mockito.Mockito.verify(cir, org.mockito.Mockito.never()).setReturnValue(1);
    }
}
