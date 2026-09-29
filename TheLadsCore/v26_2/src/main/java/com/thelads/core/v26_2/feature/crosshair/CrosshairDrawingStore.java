package com.thelads.core.v26_2.feature.crosshair;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.thelads.core.client.CrosshairDrawing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

final class CrosshairDrawingStore {
    private static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("thelads/crosshair-drawing.json");}
    static CrosshairDrawing load(CrosshairDrawing fallback){
        try{var path=path();if(!Files.isRegularFile(path)||Files.size(path)>16384)return fallback;var data=JsonParser.parseString(Files.readString(path)).getAsJsonObject();var rows=new java.util.ArrayList<String>();for(var row:data.getAsJsonArray("rows"))rows.add(row.getAsString());return CrosshairDrawing.fromRows(data.get("width").getAsInt(),data.get("height").getAsInt(),rows);}
        catch(Exception failure){LoggerFactory.getLogger("TheLadsCore").warn("Could not read crosshair drawing; using default",failure);return fallback;}
    }
    static void save(CrosshairDrawing drawing)throws IOException{
        var path=path();Files.createDirectories(path.getParent());var json=new com.google.gson.JsonObject();json.addProperty("width",drawing.width());json.addProperty("height",drawing.height());json.add("rows",new Gson().toJsonTree(drawing.rows()));var temp=Files.createTempFile(path.getParent(),"crosshair-",".tmp");
        try{Files.writeString(temp,new Gson().toJson(json));try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
}
