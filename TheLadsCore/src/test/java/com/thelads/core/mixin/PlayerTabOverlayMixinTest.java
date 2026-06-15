package com.thelads.core.mixin;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.PingViewModule;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerTabOverlayMixinTest {

    @Test
    public void testModifyPingWidth() throws Exception {
        PlayerTabOverlayMixin mixin = new PlayerTabOverlayMixin();
        
        Method method11 = PlayerTabOverlayMixin.class.getDeclaredMethod("modifyPingWidth11", int.class);
        method11.setAccessible(true);
        
        Method method13 = PlayerTabOverlayMixin.class.getDeclaredMethod("modifyPingWidth13", int.class);
        method13.setAccessible(true);
        
        PingViewModule module = (PingViewModule) ModuleManager.getInstance().getModule("PingView");
        
        // Test when disabled
        module.setEnabled(false);
        int result11Disabled = (int) method11.invoke(mixin, 11);
        int result13Disabled = (int) method13.invoke(mixin, 13);
        
        assertEquals(11, result11Disabled, "Should return original when disabled");
        assertEquals(13, result13Disabled, "Should return original when disabled");
        
        // Test when enabled
        module.setEnabled(true);
        int result11Enabled = (int) method11.invoke(mixin, 11);
        int result13Enabled = (int) method13.invoke(mixin, 13);
        
        assertEquals(45, result11Enabled, "Should return 45 when enabled");
        assertEquals(45, result13Enabled, "Should return 45 when enabled");
    }
}
