package com.thelads.core.v1_21_1.mixin.chrome;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.stream.Stream;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/**
 * The 26.x pack filter and sort. 1.21.x Resourcify draws its icons in the top-right corner, so the buttons get a header row.
 * 1.21.1 has no pack search (filterEntries); both lists are filled through updateList, which a button re-runs via populateLists.
 */
@Mixin(PackSelectionScreen.class)
public abstract class PackFilterMixin extends Screen {
    @Shadow @Final private HeaderAndFooterLayout layout;
    @Shadow protected abstract void populateLists();
    @Unique private int ladsFilter;
    @Unique private boolean ladsSort;
    protected PackFilterMixin(){super(Component.empty());}
    @Inject(method="init",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToFooter(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"),require=1)
    private void ladsFilters(CallbackInfo ci,@Local LinearLayout header){
        var row=header.addChild(LinearLayout.horizontal().spacing(8));
        row.addChild(Button.builder(Component.literal(ladsFilterLabel()),b->{ladsFilter=(ladsFilter+1)%3;b.setMessage(Component.literal(ladsFilterLabel()));populateLists();}).width(112).build());
        row.addChild(Button.builder(Component.literal(ladsSort?"Sort: name":"Sort: default"),b->{ladsSort=!ladsSort;b.setMessage(Component.literal(ladsSort?"Sort: name":"Sort: default"));populateLists();}).width(112).build());
        layout.setHeaderHeight(layout.getHeaderHeight()+25);
    }
    @Unique private String ladsFilterLabel(){return switch(ladsFilter){case 1->"Version: compatible";case 2->"Version: incompatible";default->"Version: all";};}
    @ModifyVariable(method="updateList",at=@At("HEAD"),argsOnly=true,require=1)
    private Stream<PackSelectionModel.Entry> ladsFilter(Stream<PackSelectionModel.Entry> entries){
        Stream<PackSelectionModel.Entry> filtered=entries.filter(e->e.isSelected()||ladsFilter==0||e.getCompatibility().isCompatible()==(ladsFilter==1));
        // Selected pack ordering is precedence, so never sort that list.
        return ladsSort?filtered.sorted((a,b)->a.isSelected()||b.isSelected()?0:String.CASE_INSENSITIVE_ORDER.compare(a.getTitle().getString(),b.getTitle().getString())):filtered;
    }
}
