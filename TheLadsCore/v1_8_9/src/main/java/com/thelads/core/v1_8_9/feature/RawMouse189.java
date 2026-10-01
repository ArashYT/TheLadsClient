package com.thelads.core.v1_8_9.feature;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Raw Input: mouse deltas read from JInput instead of LWJGL's accelerated cursor. Every JInput reference lives in this class,
 * which loads only when the module is on (after TheLadsCore189 moved JInput to the parent class loader).
 */
public final class RawMouse189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    private static boolean installed;

    private RawMouse189() {}

    public static void ensureInstalled(Minecraft mc) {
        if (installed || mc.mouseHelper == null) return;
        installed = true; // one attempt: a missing mouse or native would fail again every tick
        try {
            for (Controller controller : ControllerEnvironment.getDefaultEnvironment().getControllers()) {
                if (controller.getType() != Controller.Type.MOUSE) continue;
                Component x = controller.getComponent(Component.Identifier.Axis.X);
                Component y = controller.getComponent(Component.Identifier.Axis.Y);
                if (x == null || y == null) continue;
                mc.mouseHelper = new MouseHelper() {
                    @Override public void mouseXYChange() {
                        controller.poll();
                        deltaX = (int) x.getPollData();
                        deltaY = -(int) y.getPollData();
                    }
                };
                LOGGER.info("Raw mouse input initialized via JInput");
                return;
            }
            LOGGER.warn("Raw mouse input: JInput found no mouse; using the normal mouse input.");
        } catch (Throwable t) {
            LOGGER.warn("Could not initialize raw mouse input: " + t);
        }
    }
}
