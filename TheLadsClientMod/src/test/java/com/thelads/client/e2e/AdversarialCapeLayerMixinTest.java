package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.client.mixin.CapeLayerMixin;
import com.thelads.client.config.ConfigManager;
import java.lang.reflect.Method;

public class AdversarialCapeLayerMixinTest {
    @BeforeEach
    public void setupMocks() throws Exception { 
        ReflectionHelper.setupMocks(); 
    }
    
    @AfterEach
    public void teardownMocks() { 
        ReflectionHelper.teardownMocks(); 
    }

    @Test
    public void testCapeLayerMixinCancelsWhenDisabled() throws Exception {
        CapeLayerMixin mixin = new CapeLayerMixin();
        
        CallbackInfo ci = org.mockito.Mockito.mock(CallbackInfo.class);
        
        ConfigManager.getConfig().setCapesEnabled(false);
        
        Method m = CapeLayerMixin.class.getDeclaredMethod("onSubmit", CallbackInfo.class);
        m.setAccessible(true);
        m.invoke(mixin, ci);
        
        org.mockito.Mockito.verify(ci, org.mockito.Mockito.atLeastOnce()).cancel();
    }
    
    @Test
    public void testCapeLayerMixinDoesNotCancelWhenEnabled() throws Exception {
        CapeLayerMixin mixin = new CapeLayerMixin();
        
        CallbackInfo ci = org.mockito.Mockito.mock(CallbackInfo.class);
        
        ConfigManager.getConfig().setCapesEnabled(true);
        
        Method m = CapeLayerMixin.class.getDeclaredMethod("onSubmit", CallbackInfo.class);
        m.setAccessible(true);
        m.invoke(mixin, ci);
        
        org.mockito.Mockito.verify(ci, org.mockito.Mockito.never()).cancel();
    }
}
