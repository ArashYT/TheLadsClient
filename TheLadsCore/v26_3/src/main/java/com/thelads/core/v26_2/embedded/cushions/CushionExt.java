// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions;

/** Implemented by {@code Cushion} via mixin; the flag flips via {@link CushionSectionTasks} when the rebuilt mesh is installed and is read from the render thread. */
public interface CushionExt {
    boolean optimizedcushions$isBaked();

    void optimizedcushions$setBaked(boolean baked);
}
