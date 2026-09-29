package com.thelads.core.v26_2.gui;

import com.google.gson.JsonParser;
import com.thelads.core.shared.SharedContentPaths;
import com.thelads.core.v26_2.mixin.WorldStorageAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import java.nio.file.*;
import java.util.*;

/** Chooses an actual save root. Vanilla still owns world loading, locking, backup and version warnings. */
public final class WorldSourcesScreen extends Screen {
    private record Source(String name, String category, Path saves) {}
    private static final String[] CATEGORIES={"All locations","Global .minecraft","Specific Version","Custom Instances"};
    private final Screen parent;
    private final List<Source> sources=new ArrayList<>();
    private final List<Button> rows=new ArrayList<>();
    private EditBox search;
    private String query="",error="";
    private int category,page;
    public WorldSourcesScreen(Screen parent){super(Component.literal("World folders"));this.parent=parent;load();}

    private void add(String name,String category,Path saves)throws java.io.IOException{
        if(!Files.isDirectory(saves))return;
        Path canonical=saves.toRealPath();
        if(sources.stream().noneMatch(s->s.saves.equals(canonical)))sources.add(new Source(name,category,canonical));
    }
    private void load(){
        var mc=Minecraft.getInstance();
        try{
            add("Global .minecraft","Global .minecraft",SharedContentPaths.savesDir());
            add("Current version / instance","Specific Version",mc.getLevelSource().getBaseDir());
            Path file=mc.gameDirectory.toPath().resolve("lads-world-sources.json");
            if(Files.isRegularFile(file)){
                if(Files.size(file)>1024*1024)throw new java.io.IOException("World folder list is too large");
                try(var reader=Files.newBufferedReader(file)){
                    for(var item:JsonParser.parseReader(reader).getAsJsonArray()){
                        var obj=item.getAsJsonObject();
                        add(obj.get("Name").getAsString(),obj.get("Category").getAsString(),
                            Path.of(obj.get("GameDirectory").getAsString()).resolve("saves"));
                    }
                }
            }
        }catch(Exception e){error="Some folders could not be read: "+e.getMessage();}
    }
    @Override protected void init(){
        addRenderableWidget(new StringWidget(width/2-150,12,300,18,title,font));
        search=new EditBox(font,width/2-150,36,300,20,Component.literal("Search world folders"));
        search.setHint(Component.literal("Search instance names or locations"));search.setValue(query);
        search.setResponder(value->{query=value;page=0;refresh();});addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal(CATEGORIES[category]),button->{
            category=(category+1)%CATEGORIES.length;button.setMessage(Component.literal(CATEGORIES[category]));page=0;refresh();
        }).bounds(width/2-150,61,300,20).build());
        addRenderableWidget(Button.builder(Component.literal("Previous"),b->{if(page>0){page--;refresh();}}).bounds(width/2-150,height-52,96,20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"),b->{int size=Math.max(1,(height-155)/26);if((page+1)*size<filtered().size()){page++;refresh();}}).bounds(width/2-48,height-52,96,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width/2+54,height-52,96,20).build());
        addRenderableWidget(new StringWidget(width/2-160,height-26,320,16,Component.literal("Add custom folders from Launcher > Worlds"),font));
        refresh();
    }
    private List<Source> filtered(){return sources.stream().filter(s->category==0||s.category.equals(CATEGORIES[category]))
        .filter(s->(s.name+" "+s.saves).toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))).toList();}
    private void refresh(){
        for(var row:rows)removeWidget(row);rows.clear();
        var filtered=filtered();int size=Math.max(1,(height-155)/26),y=89;
        for(var source:filtered.stream().skip((long)page*size).limit(size).toList()){
            var row=Button.builder(Component.literal(source.category+": "+source.name),b->choose(source)).bounds(width/2-150,y,300,20).build();
            row.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(source.saves.toString())));
            rows.add(row);addRenderableWidget(row);y+=26;
        }
        if(filtered.isEmpty()||!error.isEmpty()){
            var row=Button.builder(Component.literal(error.isEmpty()?"No folders match":error),b->{}).bounds(width/2-150,y,300,20).build();
            row.active=false;rows.add(row);addRenderableWidget(row);
        }
    }
    private void choose(Source source){
        if(minecraft.level!=null){error="Leave the current world before changing folders";refresh();return;}
        try{
            if(!Files.isDirectory(source.saves))throw new java.io.IOException("Folder is no longer available");
            ((WorldStorageAccessor)minecraft).ladsSetLevelSource(LevelStorageSource.createDefault(source.saves));
            minecraft.setScreenAndShow(new SelectWorldScreen(this));
        }catch(Exception e){error=e.getMessage();refresh();}
    }
    @Override public void onClose(){minecraft.setScreenAndShow(parent);}
}
