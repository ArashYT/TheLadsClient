package com.thelads.core.client.ladsapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thelads.core.LadsVersion;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.TextOption;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Locale;

/**
 * Exports the complete Lads Client module and option schema for LadsClientAPI server jar (frozen schema v1).
 */
public final class SchemaExport {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SchemaExport() {}

    public static String resolveLadsVersion() {
        String prop = System.getProperty("thelads.version");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        return LadsVersion.VERSION;
    }

    public static JsonObject generateSchema() {
        return generateSchema(resolveLadsVersion());
    }

    public static JsonObject generateSchema(String ladsVersion) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("lads", ladsVersion != null ? ladsVersion : "dev");

        JsonArray modulesArray = new JsonArray();
        ModuleManager mm = new ModuleManager();
        Collection<Module> registeredModules = mm.getRegisteredModules();

        // Include all registered modules. Hidden modules are retained because the server can address
        // them by name in policy rules (e.g. XaeroMinimap blocking, Jade, Flashback Settings).
        for (Module module : registeredModules) {
            modulesArray.add(serializeModule(module));
        }

        // Pseudo-module UseWhileMining (automation module without standalone options)
        JsonObject uwm = new JsonObject();
        uwm.addProperty("name", "UseWhileMining");
        uwm.addProperty("category", "MECHANIC");
        uwm.addProperty("neverForced", LadsApiRules.isNeverForced("UseWhileMining"));
        uwm.addProperty("automation", LadsApiRules.isAutomation("UseWhileMining"));
        uwm.add("options", new JsonArray());
        modulesArray.add(uwm);

        root.add("modules", modulesArray);
        return root;
    }

    public static String generateSchemaJson() {
        return generateSchemaJson(resolveLadsVersion());
    }

    public static String generateSchemaJson(String ladsVersion) {
        return GSON.toJson(generateSchema(ladsVersion)) + "\n";
    }

    private static JsonObject serializeModule(Module module) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", module.getName());
        Module.Category cat = module.getCategory();
        obj.addProperty("category", cat != null ? cat.name() : "ALL");
        obj.addProperty("neverForced", LadsApiRules.isNeverForced(module.getName()));
        obj.addProperty("automation", LadsApiRules.isAutomation(module.getName()));

        JsonArray optionsArray = new JsonArray();
        for (Option option : module.getOptions()) {
            JsonObject opt = serializeOption(option);
            if (LadsApiRules.isNeverForced(module.getName(), option.getName())) opt.addProperty("control", ServerControl.NEVER.name()); // the client ignores its rules
            optionsArray.add(opt);
        }
        obj.add("options", optionsArray);
        return obj;
    }

    private static JsonObject serializeOption(Option option) {
        // Restore to option's default value
        option.reset();

        JsonObject opt = new JsonObject();
        opt.addProperty("name", option.getName());

        if (option instanceof SliderOption s) {
            opt.addProperty("type", "slider");
            opt.add("default", toJsonNumber(s.getOwnValue()));
            opt.add("min", toJsonNumber(s.getMin()));
            opt.add("max", toJsonNumber(s.getMax()));
            opt.add("step", toJsonNumber(s.getStep()));
        } else if (option instanceof BoolOption b) {
            opt.addProperty("type", "bool");
            opt.addProperty("default", b.getOwn());
        } else if (option instanceof DropdownOption d) {
            opt.addProperty("type", "dropdown");
            opt.addProperty("default", d.getValue());
            JsonArray choices = new JsonArray();
            for (String choice : d.getChoices()) {
                choices.add(choice);
            }
            opt.add("choices", choices);
        } else if (option instanceof ColorOption c) {
            opt.addProperty("type", "color");
            opt.addProperty("default", String.format(Locale.ROOT, "#%08X", c.getOwnColor()));
        } else if (option instanceof TextOption) {
            opt.addProperty("type", "text");
        } else {
            opt.addProperty("type", "other");
        }

        opt.addProperty("control", option.serverControl().name());
        return opt;
    }

    private static JsonPrimitive toJsonNumber(double value) {
        if (Double.isFinite(value) && value == Math.floor(value) && Math.abs(value) <= Long.MAX_VALUE) {
            return new JsonPrimitive((long) value);
        }
        return new JsonPrimitive(value);
    }

    public static void exportToFile(Path targetPath) throws IOException {
        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }
        Files.writeString(targetPath, generateSchemaJson(), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        Path target;
        if (args.length > 0 && !args[0].isBlank()) {
            target = Paths.get(args[0]);
        } else {
            target = Paths.get("../LadsClientAPI/common/src/main/resources/ladsapi-schema.json");
        }
        exportToFile(target);
        System.out.println("Exported ladsapi-schema.json to: " + target.toAbsolutePath().normalize());
    }
}
