package com.thelads.core.client;

import com.thelads.core.modules.AutoTextModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AutoTextTest {
    @Test
    public void testMacros() {
        AutoTextModule.bindMacro(100, "/hello");
        assertEquals("/hello", AutoTextModule.getMacro(100));
        AutoTextModule.bindMacro(100, null);
        assertNull(AutoTextModule.getMacro(100));
    }
}
