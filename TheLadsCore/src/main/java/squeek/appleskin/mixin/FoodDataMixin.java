/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.food.FoodData
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package squeek.appleskin.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import squeek.appleskin.helpers.ExhaustionHelper;

@Mixin(value={FoodData.class})
public class FoodDataMixin
implements ExhaustionHelper.ExhaustionManipulator {
    @Shadow
    private float exhaustionLevel;

    @Override
    public void setExhaustion(float value) {
        this.exhaustionLevel = value;
    }

    @Override
    public float getExhaustion() {
        return this.exhaustionLevel;
    }
}

