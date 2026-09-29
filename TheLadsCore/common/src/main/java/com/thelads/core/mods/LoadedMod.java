package com.thelads.core.mods;

import java.util.List;

/**
 * One mod container Fabric Loader actually loaded in this game process.
 * parentId is the containing (jar-in-jar) mod, kind is Fabric's metadata type ("builtin" for minecraft/java/fabricloader).
 */
public record LoadedMod(String id, String name, String version, String parentId, List<String> authors, String license,
                        List<String> depends, List<String> provides, boolean libraryBadge, String kind) {
    public LoadedMod {
        authors = List.copyOf(authors);
        depends = List.copyOf(depends);
        provides = List.copyOf(provides);
    }
}
