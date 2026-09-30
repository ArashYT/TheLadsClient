package com.thelads.core.v1_21_1.mixin.chrome;
import java.util.Collection;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** replaceEntries is protected on 1.21.1 (public from 1.21.2); the controls search swaps the key list rows with it. */
@Mixin(AbstractSelectionList.class)
public interface SelectionListInvoker { @Invoker("replaceEntries") void ladsReplaceEntries(Collection<?> entries); }
