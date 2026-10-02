// OcclusionCulling by LogisticsCraft (MIT, commit 7a346ea), relocated by The Lads; see META-INF/lads-sources/entityculling/LICENSE.
package com.thelads.core.v1_21_1.embedded.entityculling.occlusion.cache;

public interface OcclusionCache {

    void resetCache();

    void setVisible(int x, int y, int z);

    void setHidden(int x, int y, int z);

    int getState(int x, int y, int z);

    void setLastHidden();

    void setLastVisible();

}
