// Ported from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.handler.data;

// Gson model; component names are the JSON keys.
public record CosmeticaData(CapeData cloak) {
    public record CapeData(String texture, int frames) {
        public boolean isAnimated() {
            return frames > 1;
        }
    }
}
