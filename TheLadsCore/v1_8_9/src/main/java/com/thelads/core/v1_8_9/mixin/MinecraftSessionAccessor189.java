package com.thelads.core.v1_8_9.mixin;

import com.mojang.authlib.properties.PropertyMap;
import com.thelads.core.v1_8_9.gui.LiveSession189;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** What Minecraft keeps of the logged-in account, for an account switch without a restart. A class: this Mixin has no @Mutable accessors. */
@Mixin(Minecraft.class)
public abstract class MinecraftSessionAccessor189 implements LiveSession189.Game {
    @Shadow @Final @Mutable private Session session;
    @Shadow @Final private PropertyMap profileProperties;

    @Override
    public PropertyMap lads$profileProperties() {
        return profileProperties;
    }

    @Override
    public void lads$setSession(Session session) {
        this.session = session;
    }
}
