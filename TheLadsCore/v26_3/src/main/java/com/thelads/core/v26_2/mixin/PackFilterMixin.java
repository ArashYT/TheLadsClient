package com.thelads.core.v26_2.mixin;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PackSelectionScreen.class)
public abstract class PackFilterMixin extends Screen {
    @Shadow private EditBox search;
    @Shadow protected abstract void updateFilteredEntries(String value);
    @Unique private int ladsFilter;
    @Unique private boolean ladsSort;
    protected PackFilterMixin(){super(Component.empty());}
    @Inject(method="init",at=@At("TAIL"),require=1)
    private void ladsFilters(CallbackInfo ci){
        addRenderableWidget(Button.builder(Component.literal(ladsFilterLabel()),b->{ladsFilter=(ladsFilter+1)%3;b.setMessage(Component.literal(ladsFilterLabel()));updateFilteredEntries(search.getValue());}).bounds(6,5,112,20).build());
        addRenderableWidget(Button.builder(Component.literal(ladsSort?"Sort: name":"Sort: default"),b->{ladsSort=!ladsSort;b.setMessage(Component.literal(ladsSort?"Sort: name":"Sort: default"));updateFilteredEntries(search.getValue());}).bounds(width-118,5,112,20).build());
    }
    @Unique private String ladsFilterLabel(){return switch(ladsFilter){case 1->"Version: compatible";case 2->"Version: incompatible";default->"Version: all";};}
    @ModifyVariable(method="filterEntries",at=@At("HEAD"),argsOnly=true,require=1)
    private Stream<PackSelectionModel.Entry> ladsFilter(Stream<PackSelectionModel.Entry> entries){
        Stream<PackSelectionModel.Entry> filtered=entries.filter(e->e.isSelected()||ladsFilter==0||e.getCompatibility().isCompatible()==(ladsFilter==1));
        // Selected pack ordering is precedence, so never sort that list.
        return ladsSort?filtered.sorted((a,b)->a.isSelected()||b.isSelected()?0:String.CASE_INSENSITIVE_ORDER.compare(a.getTitle().getString(),b.getTitle().getString())):filtered;
    }
}
