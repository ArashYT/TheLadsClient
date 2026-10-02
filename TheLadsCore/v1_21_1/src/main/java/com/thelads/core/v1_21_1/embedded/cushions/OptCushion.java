// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_1.embedded.cushions;

/**
 * Marker mixed into the Cushion-Backport entity ({@code com.leclowndu93150.cushionbackport.entity.Cushion}).
 * This addon has no compile dependency on that mod, so every place where upstream tested the concrete
 * cushion type tests {@code entity instanceof OptCushion} instead.
 */
public interface OptCushion {
}
