# The Lads SMP — Build, Configuration & Development Environment Guide

## 1. Directory & Instance Layout

| Environment | Path | Purpose |
| :--- | :--- | :--- |
| **Original Reference Instance (READ-ONLY)** | `%USERPROFILE%\curseforge\minecraft\Instances\Test For Lads SMP 26.3` | Untouched 256-mod CurseForge reference installation |
| **Lads Launcher Development Instance** | `%APPDATA%\.theladsclient\instances\lads-smp-26.3-dev` | Working development instance registered for **The Lads Client** (`instance.json` id: `lads-smp-26.3-dev`) |
| **Reversible Disabled/Consolidated Backup** | `%APPDATA%\.theladsclient\instances\lads-smp-26.3-dev\mods-disabled-by-audit` | Contains the 19 consolidated/removed `.jar` files and `README_DISABLED_MODS.json` |
| **`Lads SMP` Custom Mod Source Project** | `<repo-root>\LadsSMP` | Gradle 9.6.0 + Fabric Loom 1.17.21 source project for `ladssmp-0.2.1.jar` |

---

## 2. Building & Testing `Lads SMP` (`ladssmp-0.2.1.jar`)

### Prerequisites
- **Java Toolchain**: JDK 25 (`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot` — automatically resolved by Gradle Toolchain).
- **Minecraft / Loader**: Minecraft `26.3`, Fabric Loader `0.19.5`, Fabric API `0.163.0+26.3`, Loom `1.17.21` (`net.fabricmc.unobfuscated`).

### Build & Run Unit Tests
From PowerShell in `<repo-root>\LadsSMP`:
```powershell
.\gradlew.bat build test --no-daemon
```
- Compiled mod JAR output: `build\libs\ladssmp-0.2.1.jar`
- Sources JAR output: `build\libs\ladssmp-1.0.0-sources.jar`
- JUnit 5 HTML test report: `build\reports\tests\test\index.html`

### Deploying an Updated `ladssmp-0.2.1.jar` to the Lads Launcher Dev Instance
```powershell
Copy-Item "<repo-root>\LadsSMP\build\libs\ladssmp-0.2.1.jar" `
          "%APPDATA%\.theladsclient\instances\lads-smp-26.3-dev\minecraft\mods\ladssmp-0.2.1.jar" -Force
```
Or re-run the automated dev instance setup script at any time:
```powershell
python "%USERPROFILE%\.gemini\antigravity\brain\f5216744-17f7-4193-befa-9681cf4d742d\scratch\setup_dev_instance.py"
```

---

## 3. Configuring `Lads SMP` in the Dev Instance or Dedicated Server

All `Lads SMP` configuration and audit files live in `minecraft/config/ladssmp/`:

1. **`config/ladssmp/disabled_items.json`** — Controls the 10-pathway Item & Content Disabling System:
   ```json
   {
     "enabled": true,
     "existingItemPolicy": "QUARANTINE",
     "replacementItemId": "minecraft:emerald",
     "allowAdminBypass": false,
     "notifyPlayerOnRemoval": true,
     "inventorySweepIntervalTicks": 10,
     "disabledItems": [
       "smartbackpacks:flight_upgrade",
       "smartbackpacks:repair_upgrade",
       "usefulhats:halo",
       "usefulhats:shulker_helmet",
       "relics_artifacts:chrono_watch",
       "femboymod:*_backpack"
     ],
     "recipeOnlyDisabledItems": [
       "alexsmobs:mimicream"
     ],
     "lootOnlyDisabledItems": [
       "minecraft:nether_star",
       "minecraft:dragon_egg"
     ],
     "disabledRecipeIds": [
       "alexsmobs:mimicream_repair"
     ]
   }
   ```
   - **To ban a new item in-game without restarting**: Run `/ladssmp disable <namespace:item_id> <reason>`.
   - **To ban an entire mod namespace or pattern**: Add `"modid:*"` or `"*:*_pattern"` to `"disabledItems"`, then run `/ladssmp reload`.
   - **Quarantine & Audit Logs**: Quarantined items are stored per-player in `config/ladssmp/quarantine/<player-uuid>.json` and logged with timestamps in `config/ladssmp/audit.log`.

2. **`config/ladssmp/config.json`** — Controls `LadsDataUpgrader` and all 13 consolidated QoL features:
   - Set any feature boolean (`alwaysEat`, `noAnimalTemptDelay`, `spawnerControl5Torch`, `cryingObsidianPortals`, `despawningEggsHatch`, `doubleDoors`, `deathBackup`, `villagerNames`, `friendlyFireProtection`, `attributeFixUncap`, `skeletonAiFix`, `golemSpawnFix`) to `false` and run `/ladssmp reload` to toggle it cleanly.

---

## 4. Restoring Any Archived Mod

If you ever wish to restore one of the 19 archived mods from `%APPDATA%\.theladsclient\instances\lads-smp-26.3-dev\mods-disabled-by-audit\`:
1. Move the `.jar` back into `lads-smp-26.3-dev\minecraft\mods\`.
2. If it is one of the 13 consolidated mods, set its corresponding feature toggle in `config/ladssmp/config.json` to `false` so both implementations do not run simultaneously.
