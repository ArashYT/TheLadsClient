/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Lists
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonElement
 *  org.jspecify.annotations.Nullable
 */
package fuzs.resourcepackoverrides.common.config;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import fuzs.resourcepackoverrides.common.ResourcePackOverrides;
import fuzs.resourcepackoverrides.common.services.ClientAbstractions;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

@Deprecated
public class JsonConfigFileUtil {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int SEARCH_DEPTH = 3;

    public static boolean mkdirs(String modId) {
        return JsonConfigFileUtil.mkdirs(JsonConfigFileUtil.getConfigPath(modId));
    }

    public static boolean mkdirs(@Nullable File dir) {
        if (dir != null && !dir.exists()) {
            return dir.mkdirs();
        }
        return false;
    }

    public static void getAndLoad(String jsonName, Consumer<File> serializer, Consumer<FileReader> deserializer) {
        File jsonFile = JsonConfigFileUtil.getConfigPath(jsonName);
        JsonConfigFileUtil.load(jsonFile, serializer, deserializer);
    }

    public static void getAndLoad(String jsonName, String modId, Consumer<File> serializer, Consumer<FileReader> deserializer) {
        File jsonFileInDir = JsonConfigFileUtil.getSpecialConfigPath(jsonName, modId);
        JsonConfigFileUtil.load(jsonFileInDir, serializer, deserializer);
    }

    public static void getAllAndLoad(String jsonName, Consumer<File> serializer, Consumer<FileReader> deserializer, Runnable prepareForLoad) {
        File jsonDir = JsonConfigFileUtil.getConfigPath(jsonName);
        ArrayList files = Lists.newArrayList();
        JsonConfigFileUtil.createAllIfAbsent(jsonDir, serializer, files);
        JsonConfigFileUtil.loadAllFiles(jsonDir, deserializer, prepareForLoad, files);
    }

    private static void createAllIfAbsent(File jsonDir, Consumer<File> serializer, List<File> files) {
        JsonConfigFileUtil.mkdirs(jsonDir);
        JsonConfigFileUtil.getAllFilesRecursive(jsonDir, 3, files, name -> name.endsWith(".json"));
        if (files.isEmpty()) {
            serializer.accept(jsonDir);
        }
    }

    private static void loadAllFiles(File jsonDir, Consumer<FileReader> deserializer, Runnable prepareForLoad, List<File> files) {
        if (files.isEmpty()) {
            JsonConfigFileUtil.getAllFilesRecursive(jsonDir, 3, files, name -> name.endsWith(".json"));
        }
        prepareForLoad.run();
        files.forEach(file -> JsonConfigFileUtil.loadFromFile(file, deserializer));
    }

    private static void load(File jsonFile, Consumer<File> serializer, Consumer<FileReader> deserializer) {
        JsonConfigFileUtil.createIfAbsent(jsonFile, serializer);
        JsonConfigFileUtil.loadFromFile(jsonFile, deserializer);
    }

    private static void createIfAbsent(File jsonFile, Consumer<File> serializer) {
        if (!jsonFile.exists()) {
            serializer.accept(jsonFile);
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public static boolean copyToFile(File jsonFile) {
        JsonConfigFileUtil.mkdirs(jsonFile.getParentFile());
        try (InputStream input = JsonConfigFileUtil.class.getResourceAsStream("/" + jsonFile.getName());
             FileOutputStream output = new FileOutputStream(jsonFile);){
            if (input == null) return false;
            jsonFile.createNewFile();
            byte[] buffer = new byte[16384];
            int lengthRead = input.read(buffer);
            while (lengthRead > 0) {
                output.write(buffer, 0, lengthRead);
                lengthRead = input.read(buffer);
            }
            boolean bl = true;
            return bl;
        }
        catch (Exception e) {
            ResourcePackOverrides.LOGGER.error("Failed to copy {} in config directory: {}", (Object)jsonFile.getName(), (Object)e);
        }
        return false;
    }

    public static boolean saveToFile(File jsonFile, JsonElement jsonElement) {
        boolean bl;
        JsonConfigFileUtil.mkdirs(jsonFile.getParentFile());
        FileWriter writer = new FileWriter(jsonFile);
        try {
            GSON.toJson(jsonElement, (Appendable)writer);
            bl = true;
        }
        catch (Throwable throwable) {
            try {
                try {
                    writer.close();
                }
                catch (Throwable throwable2) {
                    throwable.addSuppressed(throwable2);
                }
                throw throwable;
            }
            catch (Exception e) {
                ResourcePackOverrides.LOGGER.error("Failed to create {} in config directory: {}", (Object)jsonFile.getName(), (Object)e);
                return false;
            }
        }
        writer.close();
        return bl;
    }

    private static void loadFromFile(File file, Consumer<FileReader> deserializer) {
        try (FileReader reader = new FileReader(file);){
            deserializer.accept(reader);
        }
        catch (Exception e) {
            ResourcePackOverrides.LOGGER.error("Failed to read {} in config directory: {}", (Object)file.getName(), (Object)e);
        }
    }

    private static void getAllFilesRecursive(File directory, int searchLayers, List<File> fileList, Predicate<String> fileNamePredicate) {
        File[] allFilesAndDirs = directory.listFiles();
        if (allFilesAndDirs != null) {
            for (File file : allFilesAndDirs) {
                if (file.isDirectory()) {
                    if (searchLayers <= 0) continue;
                    JsonConfigFileUtil.getAllFilesRecursive(file, searchLayers - 1, fileList, fileNamePredicate);
                    continue;
                }
                if (fileList.size() >= 128 || !fileNamePredicate.test(file.getName())) continue;
                try {
                    fileList.add(file);
                }
                catch (Exception e) {
                    ResourcePackOverrides.LOGGER.error("Failed to locate files in {} directory: {}", (Object)directory.getName(), (Object)e);
                }
            }
        }
    }

    public static File getConfigPath(String jsonName) {
        return ClientAbstractions.INSTANCE.getConfigDirectory().resolve(jsonName).toFile();
    }

    public static File getSpecialConfigPath(String jsonName, String modId) {
        return ClientAbstractions.INSTANCE.getConfigDirectory().resolve(modId).resolve(jsonName).toFile();
    }
}

