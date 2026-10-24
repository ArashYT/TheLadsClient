package com.thelads.core.client;

/**
 * Automatically adjusts perspective (third person) while riding vehicles/mounts or gliding.
 */
public final class AutoPerspectiveHelper {
    private static boolean wasRiding = false;
    private static int previousPerspective = 0; // 0 = first person

    private AutoPerspectiveHelper() {}

    /**
     * Ticks perspective state. Returns desired perspective (0=first person, 1=third person back), or -1 if no change.
     */
    public static int checkPerspectiveChange(boolean isRiding, int currentPerspective, boolean moduleEnabled) {
        if (!moduleEnabled) {
            wasRiding = isRiding;
            return -1;
        }

        if (isRiding && !wasRiding) {
            // Just mounted! Save perspective and switch to third person
            previousPerspective = currentPerspective;
            wasRiding = true;
            return 1; // Third person back
        } else if (!isRiding && wasRiding) {
            // Just dismounted! Restore previous perspective
            wasRiding = false;
            return previousPerspective;
        }
        wasRiding = isRiding;
        return -1;
    }

    public static void reset() {
        wasRiding = false;
        previousPerspective = 0;
    }
}
