package com.thelads.core.config;

import java.lang.reflect.*;
import java.util.*;

/** Staged controls backed by an engine's live configuration, never parallel Lads preferences. */
public final class IntegratedSettings {
    @FunctionalInterface public interface Read { Object get() throws Exception; }
    @FunctionalInterface public interface Write { void set(Object value) throws Exception; }
    @FunctionalInterface public interface Save { void run() throws Exception; }
    private IntegratedSettings() {}

    private static final class Binding {
        final Option option;
        final Class<?> type;
        final Read read;
        final Write write;
        Object baseline;
        Binding(Option option, Class<?> type, Read read, Write write, Object baseline) {
            this.option = option; this.type = type; this.read = read; this.write = write; this.baseline = baseline;
        }
        Object value() {
            if (option instanceof BoolOption b) return b.get();
            if (option instanceof DropdownOption d) return type.getEnumConstants()[d.getIndex()];
            if (option instanceof SliderOption s) {
                if (type == int.class || type == Integer.class) return s.getIntValue();
                if (type == float.class || type == Float.class) return (float)s.getValue();
                return s.getValue();
            }
            throw new IllegalStateException("Unsupported control");
        }
        void stage(Object value) {
            if (option instanceof BoolOption b) b.set((Boolean)value);
            else if (option instanceof DropdownOption d) d.setIndex(((Enum<?>)value).ordinal());
            else if (option instanceof SliderOption s) s.setValue(((Number)value).doubleValue());
        }
    }

    public static final class Page {
        private final String engine, help;
        private final List<Binding> bindings;
        private final List<Option> options;
        private final Save save;
        private Page(String engine, String help, List<Binding> bindings, Save save) {
            this.engine = engine; this.help = help; this.bindings = List.copyOf(bindings); this.save = save;
            this.options = bindings.stream().map(b -> b.option).toList();
        }
        public String engine() { return engine; }
        public String help() { return help; }
        public List<Option> options() { return options; }
        public boolean hasChanges() { return bindings.stream().anyMatch(b -> !Objects.equals(b.baseline, b.value())); }
        public void revert() { bindings.forEach(b -> b.stage(b.baseline)); }
        /** Validate all changed values before mutation; preserve independent changes from other config screens. */
        public int apply() throws Exception {
            List<Binding> changed = new ArrayList<>();
            for (Binding b : bindings) {
                if (Objects.equals(b.baseline, b.value())) continue;
                if (!Objects.equals(b.baseline, b.read.get()))
                    throw new IllegalStateException(b.option.getName() + " changed elsewhere. Reopen this module to reload it.");
                changed.add(b);
            }
            if (changed.isEmpty()) return 0;
            List<Binding> written = new ArrayList<>();
            try {
                for (Binding b : changed) { written.add(b); b.write.set(b.value()); }
                save.run();
            } catch (Exception failure) {
                Collections.reverse(written);
                for (Binding b : written) try { b.write.set(b.baseline); } catch (Exception rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
            // Read-back is part of applying: upstream setters may normalize values.
            for (Binding b : changed) { b.baseline = b.read.get(); b.stage(b.baseline); }
            return changed.size();
        }
    }

    public static final class Builder {
        private final String engine;
        private final Save save;
        private String help = "Changes apply when saved. Advanced opens the engine's complete editor.";
        private final List<Binding> bindings = new ArrayList<>();
        public Builder(String engine, Save save) { this.engine = engine; this.save = save; }
        public Builder help(String help) { this.help = help; return this; }
        public Builder field(Object target, String name) throws Exception { return field(target, name, label(name)); }
        public Builder field(Object target, String name, String label) throws Exception {
            Field f = fieldOf(target, name);
            return property(label, f.getType(), () -> f.get(receiver(target)), value -> f.set(receiver(target), value));
        }
        public Builder number(Object target, String name, double min, double max, double step) throws Exception {
            Field f = fieldOf(target, name);
            return number(label(name), f.getType(), () -> f.get(receiver(target)), value -> f.set(receiver(target), value), min, max, step);
        }
        public Builder property(String label, Class<?> type, Read read, Write write) throws Exception {
            Object initial = read.get(); Option option;
            if (type == boolean.class || type == Boolean.class) option = new BoolOption(label, (Boolean)initial);
            else if (type.isEnum()) {
                String[] choices = Arrays.stream(type.getEnumConstants()).map(v -> label(((Enum<?>)v).name())).toArray(String[]::new);
                option = new DropdownOption(label, ((Enum<?>)initial).ordinal(), choices);
            } else throw new IllegalArgumentException("A bounded numeric or boolean/enum binding is required: " + label);
            return add(option, type, read, write, initial);
        }
        public Builder number(String label, Class<?> type, Read read, Write write, double min, double max, double step) throws Exception {
            if (!(type == int.class || type == Integer.class || type == float.class || type == Float.class || type == double.class || type == Double.class))
                throw new IllegalArgumentException("Unsupported numeric type: " + label);
            Object initial = read.get(); double value = ((Number)initial).doubleValue();
            // Never silently clamp and overwrite a custom upstream value when opening the menu.
            if (!Double.isFinite(value) || value < min || value > max) return this;
            SliderOption option = new SliderOption(label, value, min, max, step);
            Binding candidate = new Binding(option, type, read, write, initial);
            if (!Objects.equals(initial, candidate.value())) return this;
            return add(option, type, read, write, initial);
        }
        private Builder add(Option option, Class<?> type, Read read, Write write, Object initial) {
            if (bindings.stream().anyMatch(b -> b.option.getName().equals(option.getName()))) throw new IllegalArgumentException("Duplicate setting " + option.getName());
            bindings.add(new Binding(option, type, read, write, initial)); return this;
        }
        public Page build() { return new Page(engine, help, bindings, save); }
    }

    public static Field fieldOf(Object target, String name) throws NoSuchFieldException {
        Field field = (target instanceof Class<?> c ? c : target.getClass()).getField(name);
        if (Modifier.isFinal(field.getModifiers())) throw new IllegalArgumentException("Read-only setting: " + name);
        return field;
    }
    private static Object receiver(Object target) { return target instanceof Class<?> ? null : target; }
    public static Object get(Object target, String name) throws Exception {
        return (target instanceof Class<?> c ? c : target.getClass()).getField(name).get(receiver(target));
    }
    public static Object call(Object target, String name) throws Exception {
        return (target instanceof Class<?> c ? c : target.getClass()).getMethod(name).invoke(receiver(target));
    }
    public static String label(String name) {
        String text = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT);
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
