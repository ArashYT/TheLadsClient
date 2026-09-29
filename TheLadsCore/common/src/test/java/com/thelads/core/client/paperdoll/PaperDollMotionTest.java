package com.thelads.core.client.paperdoll;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaperDollMotionTest {
    @Test void actionsRemainVisibleForExactlyTheConfiguredDelay() {
        var motion = new PaperDollMotion();
        assertFalse(motion.visible(false, 3));
        motion.tick(true, false, false, 3, true, false, 0, 30);
        assertTrue(motion.visible(false, 3));
        motion.tick(true, false, false, 3, false, false, 0, 30);
        motion.tick(true, false, false, 3, false, false, 0, 30);
        assertTrue(motion.visible(false, 3));
        motion.tick(true, false, false, 3, false, false, 0, 30);
        assertFalse(motion.visible(false, 3));
    }
    @Test void zeroDurationAndAlwaysDisplayNeedNoTrigger() {
        var motion = new PaperDollMotion();
        assertTrue(motion.visible(false, 0));
        assertTrue(motion.visible(true, 40));
        assertFalse(motion.visible(false, 40));
    }
    @Test void pauseFreezesTimerAndDisabledResetsPoseAndVisibility() {
        var motion = new PaperDollMotion();
        motion.tick(true, false, false, 2, true, false, 30, 30);
        float yaw = motion.yaw(1);
        for (int i = 0; i < 50; i++) motion.tick(true, true, false, 2, false, false, 99, 30);
        assertTrue(motion.visible(false, 2)); assertEquals(yaw, motion.yaw(1));
        motion.tick(false, true, false, 2, true, false, 99, 30);
        assertFalse(motion.visible(false, 2)); assertEquals(0, motion.yaw(1));
    }
    @Test void yawWrapClampInterpolationAndReturnDoNotSnapAtNorth() {
        var motion = new PaperDollMotion();
        motion.tick(true, false, true, 40, false, false, -358, 30);
        assertEquals(.9f, motion.yaw(1), .0001);
        assertEquals(.45f, motion.yaw(.5f), .0001);
        motion.tick(true, false, true, 40, false, false, 179, 30);
        assertEquals(27, motion.yaw(1), .0001);
        for (int i = 0; i < 200; i++) motion.tick(true, false, true, 40, false, false, 0, 30);
        assertTrue(motion.yaw(1) >= 0 && motion.yaw(1) < .001);
        motion.tick(true, false, true, 40, false, false, Float.NaN, 30);
        assertTrue(Float.isFinite(motion.yaw(Float.NaN)));
    }
    @Test void dismountSuppressionExpiresAndDoesNotAffectFreshWorld() {
        var motion = new PaperDollMotion();
        assertFalse(motion.recentlyRiding(5));
        motion.tick(true, false, false, 5, false, true, 0, 30);
        assertTrue(motion.recentlyRiding(5));
        for (int i = 0; i < 4; i++) motion.tick(true, false, false, 5, false, false, 0, 30);
        assertFalse(motion.recentlyRiding(5));
        motion.reset(); assertFalse(motion.recentlyRiding(5));
    }
}
