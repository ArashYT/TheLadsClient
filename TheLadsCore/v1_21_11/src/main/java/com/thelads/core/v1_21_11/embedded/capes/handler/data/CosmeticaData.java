// Ported from Capes 1.5.10+1.21.11 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.capes.handler.data;

// Gson model; component names are the JSON keys.
public record CosmeticaData(CapeData cape) {
    public record CapeData(String origin, String image, int extraInfo) {
        public boolean isAnimated() {
            return extraInfo > 0;
        }
    }
}
