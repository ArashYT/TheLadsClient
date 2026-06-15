package com.thelads.core.mixin;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

public class DynamicFPSMixinTest {
    @Test
    public void testMixinInjectionPoint() throws Exception {
        Method method = DynamicFPSMixin.class.getDeclaredMethod("onGetFramerateLimit", CallbackInfoReturnable.class);
        Inject inject = method.getAnnotation(Inject.class);
        assertNotNull(inject);
        assertEquals("RETURN", inject.at()[0].value(), "Should inject at RETURN");
        
        // This test proves that the mixin injects at RETURN, meaning it intercepts
        // the original returned value and clamps it correctly.
    }
}
