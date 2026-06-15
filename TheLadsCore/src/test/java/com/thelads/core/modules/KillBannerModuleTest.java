package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Module;
import com.thelads.core.modules.killbanner.KillTracker;
import com.thelads.core.modules.killbanner.KillBannerRenderer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;

public class KillBannerModuleTest {

    static {
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testKillTrackingTrigger() throws Exception {
        // Enable module
        Module module = ModuleManager.getInstance().getModule("KillBanner");
        assertNotNull(module, "KillBanner module should be registered");
        module.setEnabled(true);

        // Reset trigger time to -1 via reflection
        Field triggerTimeField = KillBannerRenderer.class.getDeclaredField("killTriggerTime");
        triggerTimeField.setAccessible(true);
        triggerTimeField.set(null, -1L);

        // Mock entities
        Entity deadEntity = mock(Entity.class);
        when(deadEntity.getId()).thenReturn(200);

        // Set last attacked entity
        KillTracker.setLastAttackedEntity(deadEntity);

        // Simulate death of the same entity -> should trigger kill banner
        KillTracker.onEntityDeath(deadEntity);

        long triggerTime = (long) triggerTimeField.get(null);
        assertTrue(triggerTime > 0, "Kill banner should have been triggered");

        // Reset again
        triggerTimeField.set(null, -1L);

        // Set last attacked entity
        KillTracker.setLastAttackedEntity(deadEntity);

        // Simulate death of a different entity -> should NOT trigger
        Entity differentEntity = mock(Entity.class);
        when(differentEntity.getId()).thenReturn(300);
        KillTracker.onEntityDeath(differentEntity);

        triggerTime = (long) triggerTimeField.get(null);
        assertEquals(-1L, triggerTime, "Kill banner should NOT have been triggered for a different entity");
    }

    @Test
    public void testPvpTargetSwitchingRaceCondition() throws Exception {
        Module module = ModuleManager.getInstance().getModule("KillBanner");
        assertNotNull(module, "KillBanner module should be registered");
        module.setEnabled(true);

        Field triggerTimeField = KillBannerRenderer.class.getDeclaredField("killTriggerTime");
        triggerTimeField.setAccessible(true);
        triggerTimeField.set(null, -1L);

        Entity entityA = mock(Entity.class);
        when(entityA.getId()).thenReturn(101);
        Entity entityB = mock(Entity.class);
        when(entityB.getId()).thenReturn(102);

        KillTracker.setLastAttackedEntity(entityA);
        KillTracker.setLastAttackedEntity(entityB);

        KillTracker.onEntityDeath(entityA);

        long triggerTime = (long) triggerTimeField.get(null);
        assertTrue(triggerTime > 0, "Kill banner should trigger for Entity A even if Entity B was attacked afterwards");

        triggerTimeField.set(null, -1L);

        KillTracker.onEntityDeath(entityB);

        triggerTime = (long) triggerTimeField.get(null);
        assertTrue(triggerTime > 0, "Kill banner should trigger for Entity B");
    }
}
