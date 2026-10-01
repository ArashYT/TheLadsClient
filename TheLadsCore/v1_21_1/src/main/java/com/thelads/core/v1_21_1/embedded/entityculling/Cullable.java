package com.thelads.core.v1_21_1.embedded.entityculling;

/** Culling state the async pass keeps on each entity and block entity (added by the Cullable mixins). */
public interface Cullable {
    int lads$hiddenVersion();

    long lads$checkedAt();

    void lads$cullResult(int hiddenVersion, long checkedAt);

    long lads$seenAt();

    void lads$seen(long now);

    boolean lads$tracked();

    void lads$tracked(boolean tracked);
}
