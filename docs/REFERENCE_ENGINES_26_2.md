# Reference engines for Minecraft 26.2

Verified against the sibling `Mods To Recreate In Lads` metadata and official project releases on 10 September 2026. These decisions apply only to the pinned Fabric 26.2 pack. Newer game versions require their own verified artifacts and compatibility checks.

| Reference | 26.2 decision | Exact official artifact / reason |
|---|---|---|
| Modern Advancements | Retained engine, with 20 live Lads settings and links to its toast/tracker layout editors | Project `JpXMs7ti`, version `DMgKx3eW`, `1.10.2-1.26.2`. Requires Fabric Loader >=0.19.3, Java >=25 and Fabric API, already supplied. [Official project](https://modrinth.com/mod/modern-advancement-screen), [pinned release metadata](https://api.modrinth.com/v2/version/DMgKx3eW). |
| Resourcify | Retained browser engine, with 8 live Lads settings and its full editor through Advanced | Project `RLzHAoZe`, version `6haoMa46`, `1.8.5`, specifically 26.2 Fabric. Requires Fabric Kotlin >=1.11.0 and Fabric resource loader, already supplied. Explicitly incompatible with VulkanMod, which this pack does not include. The sibling 1.8.4 lacks the CurseForge download authentication update provided by 1.8.5. [Pinned release](https://modrinth.com/mod/resourcify/version/6haoMa46). |
| Optimized Block Entities | Deferred until rendering coexistence is verified | Project `AtOSAunf`, version `houTLTxf`, `1.1.46`, supports 26.2 Fabric and Sodium. Its alternate shulker renderer can bypass the native Lads Shulker Box Utils extraction/submission hooks. Published metadata alone does not establish that those features work together. [Official project](https://modrinth.com/mod/obe), [26.2 metadata](https://github.com/maDU59/OptimisedBlockEntities/blob/26.2/src/main/resources/fabric.mod.json). |
| Kerria | No verified 26.2 release | Official project `f0ruQTF7` (`kerria-opt`) has no published Fabric 26.2 artifact. The sibling jar explicitly requires Minecraft 1.21.1. Upstream source branches for newer versions do not establish a released 26.2 build. [Official versions](https://modrinth.com/mod/kerria-opt/versions). |
| Animatium / safemod2 | Deferred until native animation conflicts are resolved | `safemod2` contains Animatium 3.2 metadata, not a separate mod, and that reference supports only 26.1–26.1.2. Official 26.2 release exists: project `zy0karK1`, version `Q5mMiKxL`, `4.3`, requiring Fabric API and YACL. It overlaps Lads OldDamageTilt, VerticalBobbing, SmoothHotbar and the retained animation suite; it is not added without real coexistence validation. [Pinned candidate](https://modrinth.com/mod/animatium/version/Q5mMiKxL). |
| Retromod | Excluded from the production pack | The sibling 1.1.0-rc.1 jar contains NeoForge metadata. A newer Fabric 26.2 beta exists: project `fUS6bo71`, version `dVFXbz67`, `1.3.0-rc.1+26.2-fabric`. It rewrites old mod bytecode and cannot establish renderer compatibility; upstream identifies direct GPU manipulation and custom rendering engines as requiring manual ports. [Official compatibility limits](https://bownlux.github.io/Retromod/incompatible-mods). |

## Installation and ownership

Both included engines are pinned by exact version ID, official Modrinth CDN URL, size and SHA-512 in `TheLadsLauncher/game-mods/26.2/client-mods.json`. They download through the normal transactional installer at installation/launch time. Their jars are not bundled in the launcher or copied into Lads Core.

Modern Advancements' [custom license](https://github.com/A5ho9999/MinecraftMods/blob/main/LICENSE.md) expressly permits modpack download entries fetched from official Modrinth at installation. It prohibits publicly bundled jars and incorporation of its code/assets into another independent project. The Lads adapter calls public configuration methods and editor constructors; it contains no copied engine code or assets.

The verified 1.10.2 jar defaults `httpApiEnabled` to **false**. Lads exposes only client display preferences and leaves that server HTTP setting unchanged. No API listener, service credential or download is enabled by opening the Lads settings page.

Changes use each engine's actual live configuration and public save method. Because the engines can swallow file errors, Lads verifies the persisted typed values before reporting success. Existing provider, GUI scale, coordinates and server settings remain owned by the upstream configuration.

The retained engines still own their complex behavior. Lads settings and an installed jar do not by themselves prove successful downloads, remote-server advancement synchronization or exhaustive runtime compatibility. Runtime tests of the final assembled pack are recorded separately.
