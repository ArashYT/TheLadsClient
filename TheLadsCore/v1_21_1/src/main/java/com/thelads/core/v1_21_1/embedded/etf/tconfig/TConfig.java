package com.thelads.core.v1_21_1.embedded.etf.tconfig;

import net.minecraft.resources.ResourceLocation;
import com.thelads.core.v1_21_1.embedded.etf.tconfig.gui.entries.TConfigEntryCategory;

public abstract class TConfig {

    public abstract TConfigEntryCategory getGUIOptions();

    public abstract ResourceLocation getModIcon();

    public boolean doesGUI() {
        return true;
    }


    public static class NoGUI extends TConfig {
        @Override
        public TConfigEntryCategory getGUIOptions() {
            return null;
        }

        @Override
        public ResourceLocation getModIcon() {
            return null;
        }

        @Override
        public boolean doesGUI() {
            return false;
        }
    }

}
