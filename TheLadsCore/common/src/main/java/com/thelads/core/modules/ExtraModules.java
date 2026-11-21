package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Modules added in the 1.14.4 batch (optimizer remakes). Kept in one holder so version code can read
 * them as static fields; {@link #registerAll} adds them to the menu.
 */
public final class ExtraModules {
    private ExtraModules() {}

    // <stream fields: one "public static final XModule X = new XModule();" per module>
    public static final MaceOptimizerModule MACE_OPTIMIZER = new MaceOptimizerModule();
    public static final PearlOptimizerModule PEARL_OPTIMIZER = new PearlOptimizerModule();
    public static final SwordOptimizerModule SWORD_OPTIMIZER = new SwordOptimizerModule();
    public static final LowLatencyModule LOW_LATENCY = new LowLatencyModule();
    public static final InputOptimizerModule INPUT_OPTIMIZER = new InputOptimizerModule();
    public static final AudioEngineModule AUDIO_ENGINE = new AudioEngineModule();
    public static final RentitiesModule RENTITIES = new RentitiesModule();

    public static void registerAll(ModuleManager manager) {
        // <stream registrations: manager.register(X, Module.Category.MECHANIC);>
        manager.register(MACE_OPTIMIZER, Module.Category.MECHANIC);
        manager.register(PEARL_OPTIMIZER, Module.Category.MECHANIC);
        manager.register(SWORD_OPTIMIZER, Module.Category.MECHANIC);
        manager.register(LOW_LATENCY, Module.Category.MECHANIC);
        manager.register(INPUT_OPTIMIZER, Module.Category.MECHANIC);
        manager.register(AUDIO_ENGINE, Module.Category.MECHANIC);
        manager.register(RENTITIES, Module.Category.MECHANIC);
    }
}
