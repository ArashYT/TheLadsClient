package com.thelads.core.v26_2.feature.dynamicfps.feature.battery;
import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Original Lads vector battery icon, independent of upstream's separately licensed bitmap art. */
public final class BatteryDrawing {
    private BatteryDrawing() {}
    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int charge, boolean charging) {
        int color = charge <= 10 ? 0xfff17979 : charging ? 0xff86d8ba : 0xff8eaff0;
        graphics.fill(x, y + 3, x + 14, y + 13, 0xffd7e0ef);
        graphics.fill(x + 1, y + 4, x + 13, y + 12, 0xff172231);
        graphics.fill(x + 14, y + 6, x + 16, y + 10, 0xffd7e0ef);
        int fill = Math.round(Math.clamp(charge, 0, 100) * 10f / 100f);
        if (fill > 0) graphics.fill(x + 2, y + 5, x + 2 + fill, y + 11, color);
        if (charging) {
            graphics.fill(x + 7, y + 1, x + 9, y + 6, 0xffffffff);
            graphics.fill(x + 6, y + 5, x + 8, y + 9, 0xffffffff);
        }
    }
}
