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
    private static final String[] CATEGORIES={"All locations","Global","This Instance","Other Instance","Custom Folders"};
    private final Screen parent;
    private final List<Source> sources=new ArrayList<>();
    private final List<Button> rows=new ArrayList<>();
    private EditBox search;
    private Button previous,next,addFolder;
    private StringWidget pageLabel;
    private String query="",error="";
    private int category,page;
    public WorldSourcesScreen(Screen parent){super(Component.literal("World folders"));this.parent=parent;load();}

    private void add(String name,String category,Path saves)throws java.io.IOException{
        if(!Files.isDirectory(saves))return;
        Path canonical=saves.toRealPath();
        if(sources.stream().noneMatch(s->s.saves.equals(canonical)&&s.category.equals(category)))sources.add(new Source(name,category,canonical));
    }
    private void load(){
        var mc=Minecraft.getInstance();
        try{
            add("Global .minecraft","Global",SharedContentPaths.savesDir());
            add("Current instance","This Instance",mc.gameDirectory.toPath().resolve("saves"));
            Path file=mc.gameDirectory.toPath().resolve("lads-world-sources.json");
            if(Files.isRegularFile(file)){
                if(Files.size(file)>1024*1024)throw new java.io.IOException("World folder list is too large");
                try(var reader=Files.newBufferedReader(file)){
                    for(var item:JsonParser.parseReader(reader).getAsJsonArray()){
                        var obj=item.getAsJsonObject();
                        Path instance=Path.of(obj.get("GameDirectory").getAsString());
                        if(Files.isDirectory(instance)&&!Files.isSameFile(instance,mc.gameDirectory.toPath()))
                            add(obj.get("Name").getAsString(),"Other Instance",instance.resolve("saves"));
                    }
                }
            }
            Path custom=mc.gameDirectory.toPath().resolve("lads-world-custom-sources.json");
            if(Files.isRegularFile(custom)&&Files.size(custom)<=1024*1024)try(var reader=Files.newBufferedReader(custom)){
                for(var item:JsonParser.parseReader(reader).getAsJsonArray()){
                    Path folder=Path.of(item.getAsString());
                    try{add(folder.getFileName()==null?folder.toString():folder.getFileName().toString(),"Custom Folders",folder);}catch(Exception ignored){}
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
        previous=addRenderableWidget(Button.builder(Component.literal("Previous"),b->{if(page>0){page--;refresh();}}).bounds(width/2-150,height-52,96,20).build());
        next=addRenderableWidget(Button.builder(Component.literal("Next"),b->{int size=pageSize();if((page+1)*size<filtered().size()){page++;refresh();}}).bounds(width/2-48,height-52,96,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width/2+54,height-52,96,20).build());
        addFolder=addRenderableWidget(Button.builder(Component.literal("Add Folder"),b->addCustomFolder()).bounds(width/2-150,height-27,146,20).build());
        pageLabel=addRenderableWidget(new StringWidget(width/2+4,height-27,146,20,Component.empty(),font));
        refresh();
    }
    private List<Source> filtered(){return sources.stream().filter(s->category==0||s.category.equals(CATEGORIES[category]))
        .filter(s->(s.name+" "+s.saves).toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))).toList();}
    private void refresh(){
        for(var row:rows)removeWidget(row);rows.clear();
        var filtered=filtered();int size=pageSize(),y=89;
        int pages=Math.max(1,(filtered.size()+size-1)/size);page=Math.clamp(page,0,pages-1);
        previous.active=page>0;next.active=page+1<pages;pageLabel.setMessage(Component.literal("Page "+(page+1)+" / "+pages));
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
    private int pageSize(){return Math.max(1,(height-155)/26);}
    private void addCustomFolder(){
        addFolder.active=false;
        NativeFileDialogs.choose(true).whenComplete((folder,failure)->minecraft.execute(()->{
            addFolder.active=true;
            if(failure!=null){error="Folder selection failed: "+failure.getMessage();refresh();return;}
            if(folder==null)return;
            try{
                // Mount the chosen save root directly; worlds are never copied, moved or deleted.
                add(folder.getFileName()==null?folder.toString():folder.getFileName().toString(),"Custom Folders",folder);
                var data=new com.google.gson.JsonArray();
                sources.stream().filter(s->s.category.equals("Custom Folders")).forEach(s->data.add(s.saves.toString()));
                Path file=minecraft.gameDirectory.toPath().resolve("lads-world-custom-sources.json");
                Path temp=Files.createTempFile(file.getParent(),"lads-folders-",".tmp");
                Files.writeString(temp,data.toString());
                try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException ignored){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
                error="";category=4;page=0;rebuildWidgets();
            }catch(Exception e){error="Could not add folder: "+e.getMessage();refresh();}
        }));
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
