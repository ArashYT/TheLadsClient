// Adapted from Searchables 1.0.2 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core with Controlling.
package com.thelads.core.v1_21_1.embedded.controlling.mixin.searchables;

import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;
import java.util.function.Predicate;

@Mixin(EditBox.class)
public interface AccessEditBox {
    
    @Accessor("filter")
    Predicate<String> searchables$getFilter();
    
    @Nullable
    @Accessor("responder")
    Consumer<String> lads$searchablesResponder();
    
}
