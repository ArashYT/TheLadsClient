package com.thelads.core.mixin.betterf3;

import com.thelads.core.modules.BetterF3Module;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BetterF3MixinTest {

    @Test
    public void testFilterLeftText() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        
        List<String> original = Arrays.asList("Minecraft 1.16.5", "XYZ: 10 20 30", "Chunk-relative: 10 20 30");
        List<String> filtered = module.filterLeftText(original);
        
        assertEquals(2, filtered.size());
        assertEquals("Minecraft 1.16.5", filtered.get(0));
        assertEquals("Chunk-relative: 10 20 30", filtered.get(1));
    }

    @Test
    public void testFilterLeftTextDisabled() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(false);
        
        List<String> original = Arrays.asList("Minecraft 1.16.5", "XYZ: 10 20 30", "Chunk-relative: 10 20 30");
        List<String> filtered = module.filterLeftText(original);
        
        assertEquals(3, filtered.size());
        assertEquals("XYZ: 10 20 30", filtered.get(1));
    }

    @Test
    public void testFilterRightText() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        
        List<String> original = Arrays.asList("Java: 1.8", "Memory: 20%", "Display: 1920x1080");
        List<String> filtered = module.filterRightText(original);
        
        assertEquals(2, filtered.size());
        assertFalse(filtered.contains("Java: 1.8"));
    }
}

