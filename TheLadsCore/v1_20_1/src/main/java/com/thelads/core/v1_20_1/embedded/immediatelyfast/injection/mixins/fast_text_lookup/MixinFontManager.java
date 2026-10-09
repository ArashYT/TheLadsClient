/*
 * This file is part of ImmediatelyFast - https://github.com/RaphiMC/ImmediatelyFast
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
// Modified by The Lads: repackaged into Lads Core (com.thelads.core.v1_20_1.embedded.immediatelyfast).
package com.thelads.core.v1_20_1.embedded.immediatelyfast.injection.mixins.fast_text_lookup;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;

@Mixin(value = FontManager.class, priority = 500)
public abstract class MixinFontManager {

    @Shadow
    @Final
    private Map<ResourceLocation, FontSet> fontSets;

    @Shadow
    private Map<ResourceLocation, ResourceLocation> renames;

    @Shadow
    protected abstract FontSet method_27542(ResourceLocation par1);

    @Unique
    private final Map<ResourceLocation, FontSet> immediatelyFast$overriddenFontStorages = new Object2ObjectOpenHashMap<>();

    @Unique
    private FontSet immediatelyFast$defaultFontStorage;

    @Unique
    private FontSet immediatelyFast$unicodeFontStorage;

    @Inject(method = "apply(Lnet/minecraft/client/gui/font/FontManager$Preparation;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("RETURN"))
    private void rebuildOverriddenFontStoragesOnReload(CallbackInfo ci) {
        this.immediatelyFast$rebuildOverriddenFontStorages();
    }

    @Inject(method = "setRenames", at = @At("RETURN"))
    private void rebuildOverriddenFontStoragesOnChange(CallbackInfo ci) {
        this.immediatelyFast$rebuildOverriddenFontStorages();
    }

    @ModifyArg(method = {"createFont", "createFontFilterFishy"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;<init>(Ljava/util/function/Function;Z)V"))
    private Function<ResourceLocation, FontSet> overrideFontStorage(Function<ResourceLocation, FontSet> original) {
        return id -> {
            // Fast path for default font
            if (Minecraft.DEFAULT_FONT.equals(id) && this.immediatelyFast$defaultFontStorage != null) {
                return this.immediatelyFast$defaultFontStorage;
            } else if (Minecraft.UNIFORM_FONT.equals(id) && this.immediatelyFast$unicodeFontStorage != null) {
                return this.immediatelyFast$unicodeFontStorage;
            }

            // Try to get the font storage from the overridden map otherwise
            final FontSet storage = this.immediatelyFast$overriddenFontStorages.get(id);
            if (storage != null) {
                return storage;
            }

            // In case some mod is doing cursed stuff call the original function
            return original.apply(id);
        };
    }

    @Unique
    private void immediatelyFast$rebuildOverriddenFontStorages() {
        this.immediatelyFast$overriddenFontStorages.clear();
        this.immediatelyFast$overriddenFontStorages.putAll(this.fontSets);
        for (ResourceLocation key : this.renames.keySet()) {
            this.immediatelyFast$overriddenFontStorages.put(key, this.method_27542(key));
        }

        this.immediatelyFast$defaultFontStorage = this.immediatelyFast$overriddenFontStorages.get(Minecraft.DEFAULT_FONT);
        this.immediatelyFast$unicodeFontStorage = this.immediatelyFast$overriddenFontStorages.get(Minecraft.UNIFORM_FONT);
    }

}
