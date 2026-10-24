package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ContainerQoLModule;

public final class ContainerQoL {
    private static ContainerQoLModule cachedModule;

    private ContainerQoL() {}

    public static ContainerQoLModule module() {
        ContainerQoLModule m = cachedModule;
        if (m == null) {
            if (ModuleManager.getInstance().getModule(ContainerQoLModule.NAME) instanceof ContainerQoLModule found) {
                cachedModule = m = found;
            }
        }
        return m;
    }

    public static boolean isClickOutsideToCloseEnabled() {
        ContainerQoLModule m = module();
        return m != null && m.isEnabled() && m.clickOutsideToClose.get();
    }

    public static boolean isClickOutside(int mouseX, int mouseY, int guiLeft, int guiTop, int xSize, int ySize) {
        return mouseX < guiLeft || mouseX >= guiLeft + xSize || mouseY < guiTop || mouseY >= guiTop + ySize;
    }
}
