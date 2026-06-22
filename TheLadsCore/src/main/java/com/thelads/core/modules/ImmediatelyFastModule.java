package com.thelads.core.modules;

import com.thelads.core.config.Module;

public class ImmediatelyFastModule extends Module {
    public ImmediatelyFastModule() {
        super("ImmediatelyFast", "Optimizes core rendering bottlenecks.");
        setEnabled(true);
    }
}
