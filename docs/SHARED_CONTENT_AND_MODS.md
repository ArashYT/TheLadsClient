# Shared game content and mod management (1.2.3)

This page describes how The Lads Client shares worlds, packs and servers with the normal Minecraft folder, how existing
profiles were migrated, and how to switch any mod on or off.

## Shared worlds, resource packs, shader packs and servers

Every client version and every profile (including custom game directories) uses the same four canonical locations:

| Content | Location |
| --- | --- |
| Worlds | `%APPDATA%\.minecraft\saves` |
| Resource packs | `%APPDATA%\.minecraft\resourcepacks` |
| Shader packs | `%APPDATA%\.minecraft\shaderpacks` |
| Multiplayer server list | `%APPDATA%\.minecraft\servers.dat` |

Screenshots keep using `%APPDATA%\.minecraft\screenshots` (26.x writes there directly; 1.21.x screenshots are copied there
after the game closes). The launcher Gallery shows that folder.

How it works:

- **Worlds, resource packs and shader packs**: each profile's `saves`, `resourcepacks` and `shaderpacks` folders are NTFS
  directory junctions to the folders above. Junctions need no administrator rights or Developer Mode. Minecraft, Iris,
  Essential and Realms all see the shared folders, and Minecraft's per-world `session.lock` works across versions, so the
  same world cannot be opened by two games at once. The launcher checks the links before every launch and re-creates a
  missing one.
- **Server list**: Minecraft replaces `servers.dat` with a new file on every save, which would break a file link. LadsCore
  therefore reads and writes the canonical file directly (all four versions). Saves take a shared lock, and a game that
  loaded the list earlier merges servers another game added or removed meanwhile instead of overwriting them.
  If you disable LadsCore for a profile, the launcher copies the list into that profile before launch and merges it back
  (three-way, nothing is dropped) afterwards; this also happens at the next launch if the launcher was closed meanwhile.
- **Mods, game versions, libraries, per-version settings (`options.txt`, `config`) and accounts stay per profile.** The
  whole game directory is *not* the global `.minecraft`.
- Buttons on Home (**Worlds**, **Resource packs**, **Shader packs**, **Screenshots**), the Gallery, the Resource-pack
  browser and the server picker all use the canonical folders. On the Files page the three shared links are badged
  **Shared**; deleting inside them asks for confirmation because it affects every version. Deletions from Files, Gallery
  and Mods go to the Recycle Bin; if Windows cannot recycle an item (larger than the bin, or no Recycle Bin on that drive),
  Windows asks before deleting it permanently, and answering No keeps it.
- A profile with a **Packwiz URL** whose pack manages files under `resourcepacks/` or `shaderpacks/` is not updated by
  Packwiz, because Packwiz would replace or delete those files for every version. The launch stops with a message; remove
  those files from the pack or clear the profile's Packwiz URL.
- Newer worlds are never opened automatically. A world last played in a newer version is listed by older versions, and
  Minecraft's own "downgrade" confirmation is the gate. The only automatic world opens are LadsCore's auto-reconnect (the
  world already open in that session) and the isolated QA harness.

### The "Isolate profile" option

It is kept, and now means only: **keep this profile's game settings (`options.txt`, keybinds) separate**. Worlds, resource
packs, shader packs and the server list are always shared, isolated or not. The retired "Sync resource packs from global"
setting is ignored (packs are shared directly).

### Where the shared folder lives when it is not `%APPDATA%\.minecraft`

- `LADS_GLOBAL_MINECRAFT_DIR` (absolute path) chooses the shared folder explicitly. Automated tests and QA use it. A value
  that is not an absolute path stops the launcher at startup with an error message instead of guessing.
- If only `THELADS_DIR` is set (an isolated launcher data folder), the shared folder is `<THELADS_DIR>\global-minecraft`, so
  an isolated launcher never touches your real worlds. The Home status line and the log say so whenever the shared folder is
  not the normal `.minecraft`.

## Migration of existing profiles

At startup (in the background, skipping any profile whose game is running) and before every launch, the launcher migrates
each profile once:

1. It records an inventory of the profile's worlds/packs/shaders, the old `.theladsclient\shared\servers.dat` and the
   global folders (`<profile>\.lads-shared-migration\<stamp>\inventory.json`).
2. It moves the profile's real `saves`/`resourcepacks`/`shaderpacks` folder aside (an instant rename) and links the
   shared folder in its place. If linking fails, the folder is moved back.
3. Each moved item is then placed:
   - not in the shared folder yet → moved there;
   - identical to the shared copy (same files and SHA-256) → kept as a backup in `<profile>\.lads-shared-duplicates\`;
   - different from the shared copy with the same name → moved in under a new name such as
     `World (26.3 2026-09-29)` or `pack (26.3 2026-09-29).zip`. **Two different worlds are never merged; nothing is
     overwritten.**
   - a world currently open in a game → skipped and retried next time.
4. Server lists are merged by address into the canonical `servers.dat` (servers only in a profile list are added; the
   canonical list keeps its names and order; a server visible in either list stays visible). The old copies are kept in the
   migration folder. If the canonical list cannot be read, nothing is changed and an error is shown.

Everything is logged to `report.jsonl` in the migration folder. A notice on Home and Profiles lists renamed items (both
kept), waiting items and warnings, with **Open report**, **Open shared saves** and **Open backup folder**. An interrupted
migration resumes safely; running it again changes nothing. A profile folder on a drive that cannot hold junctions
(FAT32/exFAT or network) is reported, and you can choose to launch once without shared folders.

## Mod inventory

The launcher **Mods → Installed** page and the in-game **Lads menu → Installed mods** view list every relevant entry of the
selected profile and version:

- installed and disabled jars (with their upstream name, version, mod id, authors and file name),
- pack mods that will download at the next launch (**Pending download**) or that you switched off before they downloaded
  (**Not downloaded**),
- mods that are in the Lads pack for another version only (**Not in the Lads pack for 26.3 (included for …)**),
- jars that do not support this Minecraft version (**Unsupported**) or cannot be read (**Invalid**, with the reason),
- LadsCore and its native Lads modules (including modules not yet available on this version, with the reason),
- embedded libraries (jar-in-jar) under their parent, and the platform components Minecraft, Fabric Loader and Java.

Filters: **All, Lads modules, Third-party, Enabled, Disabled, Libraries & dependencies** (a library is a jar marked as a
library, an embedded jar, or a mod that other mods depend on). Search matches the name, upstream name, mod id, file name
and embedded libraries (their parents open automatically). **Reset filters** shows the full inventory again. On both
surfaces the "enabled / disabled / pending / unavailable" counts are computed from the actual jar files in `mods\` and the
selected version's pack; Lads modules are counted separately. Mods that another mod loads at runtime (for example
Essential's components) are shown under that mod in game.

Third-party mods keep their upstream names and attribution; only LadsCore and its modules are labelled as Lads. Xaero's
Minimap and World Map remain upstream engines with a Lads integration note.

## Switching mods on and off

- **Any standalone mod** (pack mods, your own mods and LadsCore) can be switched off or on. Minecraft, Fabric Loader and Java
  cannot. Embedded libraries cannot be switched independently; their row offers **Disable <parent>…** instead.
- Choices are saved **per profile** in `<profile game folder>\lads-mod-state.json`, keyed by the Fabric mod id (with the
  Modrinth project id as a fallback when a project's mod id differs between Minecraft versions). They survive relaunches,
  profile switches, pack repair, mod updates and launcher updates, and they also apply to Fabric profiles on versions without
  a Lads pack. A mod with no saved choice keeps its current on/off state (so mods switched off in 1.2.2 stay off). If the
  launcher retires a switched-off pack mod that has no saved choice, it records the choice so the mod stays off if a later
  pack brings it back.
- A switched-off jar is renamed to `<original file name>.jar.disabled`; nothing inside the jar changes. A switched-off pack
  mod that is not downloaded yet is not downloaded.
- **Dependencies**: switching off a library lists the mods that need it and offers to switch them off together; switching
  a mod on lists switched-off dependencies to enable with it. If a launch would still be invalid, the launcher offers the two
  coherent fixes instead of starting the game.
- **While Minecraft is running**, changes are saved and marked **Restart required**; the loaded state and the next-launch
  state are shown separately, and the files change before the next game starts. Renaming a loaded jar never unloads it.
- **In game**, the Installed mods view can switch third-party mods and LadsCore too (saved as a request for the next launch,
  with the same dependency confirmation). Native Lads modules switch immediately in game; in the launcher they can be
  switched while the game is not running.
- **Disabling LadsCore** removes the in-game Lads menus and features until you re-enable it. Re-enable it on the launcher's
  Mods page; **Restore default mod set** clears all saved choices after confirmation.
- Failures (a locked file, a dependency conflict) are shown on the Mods page and the list is re-read from disk.

## Original file names and GoodMC

- Third-party pack jars now use each release's original file name (for example `sodium-fabric-0.9.2+mc26.3.jar`) instead of
  `lads-<modId>.jar`. Existing managed jars are renamed on the next launch with their exact bytes and on/off state. A
  switched-off jar from an older pack version gets its original name from Modrinth's file lookup when online, otherwise it
  keeps its old name until then. Your own jars are never renamed.
- GoodMC: Old Combat & Blockhitting was removed from the 26.2 and 26.3 packs. The copy the launcher installed is moved to
  `.lads-mod-cache\retired\goodmc\` (recoverable). A GoodMC jar you added yourself is left in place and marked
  **Removed from the Lads pack**. LadsCore's separate console-style swing animation (LegacySwing) is unchanged.
- GoodMC saved a no-cooldown attack speed (base 32767) into player data. On 26.2/26.3, when a singleplayer or LAN world
  (hosted by this client) loads a player with exactly that value and GoodMC is not installed, LadsCore resets the attack speed to its normal value
  (the same as `/attribute … minecraft:attack_speed base reset`). Multiplayer servers and GoodMC's config files are untouched.

## Known limitations

- 1.21.1 does not know the `acceptedCodeOfConduct` server flag, so after a 1.21.1 save newer versions may ask for a server's
  code of conduct again. Essential's `servers.essential.dat` stays per profile.
- When two running games change the server list, additions, removals and edits are merged per server (matched by address).
  If both games edit the same server before either saves, the last save wins for that server. A server icon change alone is
  not treated as an edit.
- Migration backups (`.lads-shared-duplicates`, `.lads-shared-migration`) are kept until you delete them.
