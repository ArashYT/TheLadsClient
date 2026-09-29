package com.thelads.core.config;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in QA only. Call on the client thread after the engines have initialized. */
public final class IntegrationRuntimeProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static boolean ran;
    private IntegrationRuntimeProbe() {}

    public static synchronized void run() {
        if (!Boolean.getBoolean("thelads.verifyIntegrationWrites") || ran) return;
        try {
            Class<?> api = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = api.getMethod("getInstance").invoke(null);
            Path game = (Path) api.getMethod("getGameDir").invoke(loader);
            Path config = (Path) api.getMethod("getConfigDir").invoke(loader);
            Path directory = validateDirectory(System.getenv("THELADS_DIR"), game, config);
            ran = true;
            int passed = 0, skipped = 0, failed = 0;
            LOGGER.info("Lads integration write probe BEGIN: {}", directory);
            for (String id : new TreeSet<>(ModuleSupport.externalIds())) {
                try {
                    IntegratedSettings.Page page = BuiltInIntegrations.open(id);
                    List<Option> controls = mutableOptions(page);
                    if (!Boolean.getBoolean("thelads.verifyAllIntegrationWrites") && !controls.isEmpty())
                        controls = List.of(selectOption(page));
                    if (controls.isEmpty()) {
                        skipped++;
                        LOGGER.info("Lads integration write SKIP: {} (no available mutable control)", id);
                    } else {
                        for (Option selected : controls) {
                            try {
                                String control = roundTrip(id, BuiltInIntegrations::open, selected);
                                passed++;
                                LOGGER.info("Lads integration write PASS: {} ({}, changed and restored)", id, control);
                            } catch (Exception | LinkageError failure) {
                                failed++;
                                LOGGER.error("Lads integration write ERROR: " + id + "/" + selected.getName()
                                    + " (see restoration result/suppressed errors)", failure);
                            }
                        }
                    }
                } catch (Exception | LinkageError failure) {
                    failed++;
                    LOGGER.error("Lads integration write ERROR: " + id + " (see restoration result/suppressed errors)", failure);
                }
            }
            LOGGER.info("Lads integration write probe END: {} passed, {} skipped, {} failed", passed, skipped, failed);
        } catch (Exception | LinkageError failure) {
            LOGGER.error("Lads integration write probe REFUSED: isolated QA guard or runtime API failed", failure);
        }
    }

    static Path validateDirectory(String configuredDirectory, Path gameDirectory, Path configDirectory) throws IOException {
        if (configuredDirectory == null || configuredDirectory.isBlank())
            throw new IOException("THELADS_DIR is required");
        Path requested = Path.of(configuredDirectory);
        if (!requested.isAbsolute()) throw new IOException("THELADS_DIR must be absolute");
        requested = requested.normalize();
        Path directory = requested.toRealPath();
        if (!directory.equals(requested)) throw new IOException("QA directory must not redirect through a link");
        String name = directory.getFileName().toString();
        if (!(name.equals("1.21.11-settings") || name.equals("26.2-settings"))
            || directory.getParent() == null || !directory.getParent().getFileName().toString().equals("verification")
            || directory.getParent().getParent() == null
            || !directory.getParent().getParent().getFileName().toString().equals("artifacts"))
            throw new IOException("Writes require artifacts/verification/{1.21.11,26.2}-settings");
        Path repository = directory.getParent().getParent().getParent();
        if (repository == null || !Files.isRegularFile(repository.resolve("TheLadsCore/settings.gradle")))
            throw new IOException("QA directory must belong to the development repository");
        if (!directory.equals(gameDirectory.toRealPath()))
            throw new IOException("Fabric's actual game directory must equal THELADS_DIR");
        Path config = configDirectory.toRealPath();
        if (!config.startsWith(directory)) throw new IOException("Native config directory is outside isolated QA");
        // A copied QA config must not contain junctions/symlinks into a user's real profile.
        try (var paths = Files.walk(config)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path entry = iterator.next();
                if (!entry.toRealPath().startsWith(directory))
                    throw new IOException("Config entry redirects outside isolated QA: " + entry.getFileName());
            }
        }
        return directory;
    }

    /** Null means skipped; success requires both live read-backs. Restoration runs after any attempted write. */
    static String roundTrip(String id, Function<String, IntegratedSettings.Page> open) throws Exception {
        IntegratedSettings.Page initial = open.apply(id);
        if (initial == null) return null;
        Option selected = selectOption(initial);
        if (selected == null) return null;
        return roundTrip(id, open, initial, selected);
    }

    static String roundTrip(String id, Function<String, IntegratedSettings.Page> open, Option requested) throws Exception {
        IntegratedSettings.Page initial = open.apply(id);
        return roundTrip(id, open, initial, option(initial, requested));
    }

    private static String roundTrip(String id, Function<String, IntegratedSettings.Page> open,
                                   IntegratedSettings.Page initial, Option selected) throws Exception {
        if (initial.hasChanges()) throw new IllegalStateException("Adapter stages changes merely by opening: " + id);
        String name = selected.getName();
        JsonElement original = selected.save().deepCopy();
        Throwable primaryFailure = null;
        try {
            mutate(selected);
            JsonElement expected = selected.save().deepCopy();
            if (original.equals(expected)) throw new IllegalStateException("Control has no distinct valid value: " + name);
            if (initial.apply() != 1) throw new IllegalStateException("Expected one changed control: " + name);
            if (!option(open.apply(id), selected).save().equals(expected))
                throw new IllegalStateException("Reopened value did not reflect change: " + name);
        } catch (Exception | LinkageError failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                IntegratedSettings.Page restore = open.apply(id);
                Option option = option(restore, selected);
                if (restore.hasChanges()) throw new IllegalStateException("Restoration page stages unrelated changes on open: " + id);
                option.load(original.deepCopy());
                restore.apply();
                if (!option(open.apply(id), selected).save().equals(original))
                    throw new IllegalStateException("Reopened value did not restore: " + name);
            } catch (Exception | LinkageError restoreFailure) {
                if (primaryFailure != null) primaryFailure.addSuppressed(restoreFailure);
                else throw restoreFailure;
            }
        }
        return name;
    }

    private static Option selectOption(IntegratedSettings.Page page) {
        for (Option option : page.options()) if (option instanceof BoolOption) return option;
        for (Option option : page.options())
            if (option instanceof DropdownOption dropdown && dropdown.getChoices().length > 1) return option;
        for (Option option : page.options())
            if (option instanceof SliderOption slider && slider.getMax() > slider.getMin() && slider.getStep() > 0) return option;
        return null;
    }

    static List<Option> mutableOptions(IntegratedSettings.Page page) {
        if (page == null) return List.of();
        return page.options().stream().filter(option -> option instanceof BoolOption
            || option instanceof DropdownOption dropdown && dropdown.getChoices().length > 1
            || option instanceof SliderOption slider && slider.getMax() > slider.getMin() && slider.getStep() > 0).toList();
    }

    private static void mutate(Option option) {
        if (option instanceof BoolOption bool) bool.toggle();
        else if (option instanceof DropdownOption dropdown) dropdown.cycle();
        else if (option instanceof SliderOption slider) {
            double original = slider.getValue();
            slider.setValue(Math.min(slider.getMax(), original + slider.getStep()));
            if (slider.getValue() == original)
                slider.setValue(Math.max(slider.getMin(), original - slider.getStep()));
        }
    }

    private static Option option(IntegratedSettings.Page page, Option selected) {
        String name = selected.getName();
        if (page == null) throw new IllegalStateException("Adapter unavailable while verifying " + name);
        return page.options().stream().filter(o -> o.getClass() == selected.getClass() && o.getName().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Control unavailable while verifying " + name));
    }
}
