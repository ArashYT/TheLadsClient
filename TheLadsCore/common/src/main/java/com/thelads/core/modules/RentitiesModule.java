package com.thelads.core.modules;

import com.thelads.core.config.ActionOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Rentities (experimental, Minecraft 26.3 on NVIDIA only): draws every entity model of one kind in a single instanced
 * GPU call. Remake of the MIT-licensed Rentities mod, see THIRD_PARTY_NOTICES.txt. Version code sets the two actions.
 */
public final class RentitiesModule extends Module {
    public static final String NAME = "Rentities";

    public final BoolOption scanMode;
    public final ActionOption deleteCache;
    /** Read-only status line: a button whose label is the live state, pressing it does nothing. */
    public final ActionOption status;

    public RentitiesModule() {
        super(NAME, "Experimental: draws all entities of one model type in one instanced GPU call. Turn on Entity Scan Mode once and look at mobs to build the mesh cache, then turn it off. Needs an NVIDIA GPU.");
        scanMode = addOption(new BoolOption("Entity Scan Mode", false));
        deleteCache = addOption(new ActionOption("Delete Mesh Cache", "Delete").confirm("Delete all saved Rentities meshes?"));
        status = addOption(new ActionOption("Status", "Not loaded"));
        setEnabled(false);
    }
}
