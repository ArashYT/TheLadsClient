// The Lads: Controlling and its Searchables copy stand down while the original Controlling is installed.
package com.thelads.core.v1_21_11.embedded.controlling;

import com.thelads.core.v1_21_11.embedded.EmbeddedMixinPlugin;

public final class ControllingMixinPlugin extends EmbeddedMixinPlugin {
    @Override protected String originalModId() { return "controlling"; }
}
