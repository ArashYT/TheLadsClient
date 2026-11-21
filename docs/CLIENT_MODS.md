# Bundled client mod pack

Current 26.3 development build: see [CLIENT_26_3.md](CLIENT_26_3.md) and [MOD_COVERAGE.md](MOD_COVERAGE.md) for the 26.3 pack, native replacements, and missing upstream releases. Per-version pin tables for the dropped 1.21.11 and 26.2 packs were removed; the lockfile is TheLadsLauncher/game-mods/<version>/client-mods.json.

Dynamic FPS, Raised, Custom Crosshair and Durability Tooltip have been replaced by native Lads features on 26.2; Modern Advancements and Resourcify are added as retained engines. See [NATIVE_MODULES_26_2.md](NATIVE_MODULES_26_2.md) and [REFERENCE_ENGINES_26_2.md](REFERENCE_ENGINES_26_2.md). The version table below retains the original upstream attribution for both packs. Nested library mods may make the in-game Mod Menu count higher. The launcher downloads original artifacts from Modrinth and verifies their SHA-512, size, and Fabric mod ID; it does not repackage them. Each upstream project retains its own license and authorship. The JSON manifests include exact version IDs, original filenames, source repositories, sizes and hashes.


## Settings and compatibility

Built-in Lads controls apply immediately. Cards marked MOD open the external mod configuration or its Mod Menu entry when no configuration screen is provided. Disable external jars in the launcher and restart Minecraft; required dependencies must remain enabled while their dependents are active. Existing custom jars are preserved, and conflicting versions produce an actionable error.

The default pack includes Sodium, Lithium, FerriteCore, ImmediatelyFast, Entity Culling, Dynamic FPS, and ScalableLux. These are actual installed implementations; no controlled FPS benchmark was performed. Clumps operates only where the server or integrated world runs it. Capes displays existing supported capes and does not grant ownership. JEI does not grant server operator privileges.

Exordium is excluded after its own first-run warning reported that the current release is unmaintained and can cause visual issues. Tab Tweaks is pinned for the narrow tab-list integration, but its upstream project is archived; reassess it before future Minecraft upgrades. Full online reconnect/server behavior remains unverified pending Microsoft registration.

## Updating the pins

`tools/sync_instance_mods.py` is the only manifest generator; see [CLIENT_26_3.md](CLIENT_26_3.md) for how to run it. Use `--write` only as a deliberate developer update, review every JSON diff and project license, then run the installer regression suite and actual game/world checks for each version. Launching the client never queries for arbitrary latest mod versions. The older `tools/resolve_client_mods.py` was removed: its `--lock` overwrote the 1.21.11 and 26.2 manifests with an outdated, hard-coded mod set.
