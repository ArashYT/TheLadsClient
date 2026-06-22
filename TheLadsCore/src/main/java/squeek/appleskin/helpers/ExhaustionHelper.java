/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.player.Player
 */
package squeek.appleskin.helpers;

import net.minecraft.world.entity.player.Player;

public class ExhaustionHelper {
    public static float getExhaustion(Player player) {
        return ((ExhaustionManipulator)player.getFoodData()).getExhaustion();
    }

    public static void setExhaustion(Player player, float exhaustion) {
        ((ExhaustionManipulator)player.getFoodData()).setExhaustion(exhaustion);
    }

    public static interface ExhaustionManipulator {
        public float getExhaustion();

        public void setExhaustion(float var1);
    }
}

