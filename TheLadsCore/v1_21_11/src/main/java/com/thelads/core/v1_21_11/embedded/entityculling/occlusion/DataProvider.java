// OcclusionCulling by LogisticsCraft (MIT, commit 7a346ea), relocated by The Lads; see META-INF/lads-sources/entityculling/LICENSE.
package com.thelads.core.v1_21_11.embedded.entityculling.occlusion;

import com.thelads.core.v1_21_11.embedded.entityculling.occlusion.util.Vec3d;

public interface DataProvider {

    /**
     * Prepares the requested chunk. Returns true if the chunk is ready, false when
     * not loaded. Should not reload the chunk when the x and y are the same as the
     * last request!
     *
     * @param chunkX
     * @param chunkZ
     * @return
     */
    boolean prepareChunk(int chunkX, int chunkZ);

    /**
     * Location is inside the chunk.
     *
     * @param x
     * @param y
     * @param z
     * @return
     */
    boolean isOpaqueFullCube(int x, int y, int z);

    default void cleanup() {
    }

    default void checkingPosition(Vec3d[] targetPoints, int size, Vec3d viewerPosition) {
    }

}
