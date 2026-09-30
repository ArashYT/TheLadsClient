package com.thelads.core.v1_21_1.mixin.chrome;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.KeyMapping;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.*;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(KeyBindsScreen.class)
public abstract class KeySearchMixin extends OptionsSubScreen {
    @Shadow private KeyBindsList keyBindsList;
    @Unique private EditBox ladsSearch;
    @Unique private Button ladsModeButton, ladsStateButton;
    @Unique private List<KeyBindsList.Entry> ladsAll = List.of();
    @Unique private int ladsMode, ladsState;
    @Unique private static final String[] LADS_MODES = {"All", "Name", "Keybind", "Category", "Mod"};
    @Unique private static final String[] LADS_STATES = {"All bindings", "Conflicts", "Unbound"};
    protected KeySearchMixin(Screen parent,Options options,Component title){super(parent,options,title);}
    @Inject(method="addContents",at=@At("TAIL"),require=1)
    private void ladsSearchControls(CallbackInfo ci){
        ladsAll=List.copyOf(keyBindsList.children());
        layout.setHeaderHeight(86);
        String previous=ladsSearch==null?"":ladsSearch.getValue();
        ladsSearch=new EditBox(font,width/2-150,32,300,20,Component.literal("Search controls"));
        ladsSearch.setHint(Component.literal("Search names, keys, categories or mods"));
        ladsSearch.setMaxLength(128);
        ladsSearch.setResponder(value->ladsFilter(true));
        ladsModeButton=Button.builder(Component.literal("Search: "+LADS_MODES[ladsMode]),button->{
            ladsMode=(ladsMode+1)%LADS_MODES.length;
            button.setMessage(Component.literal("Search: "+LADS_MODES[ladsMode]));ladsFilter(true);
        }).bounds(width/2-150,57,148,20).build();
        ladsStateButton=Button.builder(Component.literal(LADS_STATES[ladsState]),button->{
            ladsState=(ladsState+1)%LADS_STATES.length;
            button.setMessage(Component.literal(LADS_STATES[ladsState]));ladsFilter(true);
        }).bounds(width/2+2,57,148,20).build();
        addRenderableWidget(ladsSearch);addRenderableWidget(ladsModeButton);addRenderableWidget(ladsStateButton);
        ladsSearch.setValue(previous);ladsFilter(true);
    }
    @Unique private void ladsFilter(boolean resetScroll){
        String query=ladsSearch.getValue().strip().toLowerCase(Locale.ROOT);
        List<KeyBindsList.Entry> filtered=new ArrayList<>();
        KeyBindsList.Entry heading=null;boolean headingAdded=false;
        for(var entry:ladsAll){
            if(!(entry instanceof KeyEntryAccessor accessor)){heading=entry;headingAdded=false;continue;}
            var key=accessor.ladsKey();
            String name=I18n.get(key.getName()),binding=key.getTranslatedKeyMessage().getString();
            String category=I18n.get(key.getCategory()),mod=ladsModName(key);
            String text=switch(ladsMode){case 1->name;case 2->binding;case 3->category;case 4->mod;
                default->name+" "+binding+" "+category+" "+mod;};
            boolean state=ladsState==0||(ladsState==2?key.isUnbound():!key.isUnbound()
                &&Arrays.stream(options.keyMappings).anyMatch(other->other!=key&&key.same(other)));
            if(state&&text.toLowerCase(Locale.ROOT).contains(query)){
                if(heading!=null&&!headingAdded){filtered.add(heading);headingAdded=true;}
                filtered.add(entry);
            }
        }
        // Key assignment and ordinary clicks update labels, not the viewport. Replacing
        // identical entries also clears the focused row, so only rebuild on a real filter change.
        double scroll=keyBindsList.getScrollAmount();
        if(!keyBindsList.children().equals(filtered))((SelectionListInvoker)keyBindsList).ladsReplaceEntries(filtered);
        if(resetScroll)keyBindsList.setScrollAmount(0);
        else if(keyBindsList.getScrollAmount()!=scroll)keyBindsList.setScrollAmount(scroll);
    }
    /** 1.21.1 categories are translation keys ("key.categories.<mod>"), not namespaced ids: any key segment may name the mod. */
    @Unique private static String ladsModName(KeyMapping key){
        String category=key.getCategory(),raw=key.getName().toLowerCase(Locale.ROOT);
        var segments=Arrays.asList(category.toLowerCase(Locale.ROOT).split("[.:]"));
        for(var mod:FabricLoader.getInstance().getAllMods()){
            String id=mod.getMetadata().getId();
            if(segments.contains(id)||raw.startsWith("key."+id+".")||raw.startsWith("key."+id+":")
                ||raw.startsWith(id+".")||raw.startsWith("keybind."+id+"."))
                return mod.getMetadata().getName()+" "+id;
        }
        return category+" "+I18n.get(category);
    }
    @Inject(method={"mouseClicked","keyPressed"},at=@At("TAIL"),require=1)
    private void ladsRefresh(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci){if(ladsSearch!=null)ladsFilter(false);}
    @Inject(method="repositionElements",at=@At("TAIL"),require=1)
    private void ladsPositionSearch(CallbackInfo ci){if(ladsSearch!=null){
        // HeaderAndFooterLayout centers its title in the taller header; keep it above our search row.
        for(var child:children()) if(child instanceof net.minecraft.client.gui.components.StringWidget label
            &&label.getMessage().equals(title)) label.setY(12);
        ladsSearch.setX(width/2-150);ladsSearch.setY(32);
        ladsModeButton.setX(width/2-150);ladsModeButton.setY(57);
        ladsStateButton.setX(width/2+2);ladsStateButton.setY(57);
    }}
}
