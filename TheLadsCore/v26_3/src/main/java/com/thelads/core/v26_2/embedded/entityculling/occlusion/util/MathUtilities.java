// OcclusionCulling by LogisticsCraft (MIT, commit 7a346ea), relocated by The Lads; see META-INF/lads-sources/entityculling/LICENSE.
package com.thelads.core.v26_2.embedded.entityculling.occlusion.util;

/**
 * Contains MathHelper methods
 */
public final class MathUtilities {

    private MathUtilities() {
    }

    public static int floor(double d) {
        int i = (int) d;
        return d < (double) i ? i - 1 : i;
    }

    public static int fastFloor(double d) {
        return (int) (d + 1024.0) - 1024;
    }

    public static int ceil(double d) {
        int i = (int) d;
        return d > (double) i ? i + 1 : i;
    }

}
