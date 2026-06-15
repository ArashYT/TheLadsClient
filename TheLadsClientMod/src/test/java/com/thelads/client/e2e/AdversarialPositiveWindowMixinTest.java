package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.client.mixin.WindowMixin;
import com.thelads.client.config.ConfigManager;
import java.lang.reflect.Method;

public class AdversarialPositiveWindowMixinTest {
    @BeforeEach
    public void setupMocks() throws Exception { 
        ReflectionHelper.setupMocks(); 
        ConfigManager.getConfig().setUiScalingEnabled(true);
    }
    
    @AfterEach
    public void teardownMocks() { 
        ReflectionHelper.teardownMocks(); 
    }

    @Test
    public void testWindowMixinModifiesGuiScaleWhenEnabled() throws Exception {
        WindowMixin mixin = new WindowMixin();
        
        CallbackInfoReturnable<Integer> cir = org.mockito.Mockito.mock(CallbackInfoReturnable.class);
        org.mockito.Mockito.when(cir.getReturnValue()).thenReturn(3);
        
        Method m = WindowMixin.class.getDeclaredMethod("onCalculateScale", CallbackInfoReturnable.class);
        m.setAccessible(true);
        m.invoke(mixin, cir);
        
        // When uiScalingEnabled is true, we expect the UI scale to be genuinely modified.
        // For example, it might force the return value to 1.
        org.mockito.Mockito.verify(cir, org.mockito.Mockito.atLeastOnce()).setReturnValue(1);
    }
}
