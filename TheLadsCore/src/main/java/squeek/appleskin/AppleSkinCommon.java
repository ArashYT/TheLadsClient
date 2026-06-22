/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ModInitializer
 */
package squeek.appleskin;

import net.fabricmc.api.ModInitializer;
import squeek.appleskin.network.SyncHandler;

public class AppleSkinCommon
implements ModInitializer {
    public void onInitialize() {
        SyncHandler.init();
    }
}

