# MC 26.1.2 → 26.2 Migration (Workstream E)

**Status: BLOCKED ON UPSTREAM — cannot complete now (26.2 is brand new; ~1/3 of the mod
stack has no 26.2 build yet).** Assessment done 2026-06-17.

## What's ready
- **Fabric is buildable on 26.2:** `fabric-api 0.152.1+26.2` exists. Loom will fetch 26.2
  mappings. Pin changes (in `gradle.properties`) when proceeding:
  - `minecraft_version=26.2`
  - `fabric_api_version=0.152.1+26.2`
  - `loader_version` — keep 0.19.2 (or bump if a newer build is required), `loom_version` may need a bump.

## The wall: shaded + source-ported mods need 26.2 builds
`build.gradle` shades **28 hardcoded 26.1.x jars** from `shading_libs/`; ~12 more mods are
**source-ported** into `src/main/java/.../features/alwayson/*` (no jar to swap — they need
manual 26.2 API fixing, ideally with the upstream 26.2 source as reference).

### Mod 26.2 readiness (Modrinth, 2026-06-17)
| Ready ✅ | NOT ready ❌ (no 26.2 build) |
|---|---|
| sodium, scalablelux, xaeros-minimap, lambdynamiclights, entityculling, clientsort, quick-pack, better-render-distance, ixeris | **immediatelyfast**, **3dskinlayers**, **jei**, **betterstats**, **advancements-reloaded**, appleskin, not-enough-animations, xaeros-world-map, raised, enhanced-tooltips |

The ❌ list includes **core, deeply source-ported mods** (ImmediatelyFast ~17 mixins,
3dSkinLayers ~14 mixins, BetterStats, AdvancementsReloaded). Hand-porting these to 26.2
without an upstream 26.2 reference is impractical and unverifiable.

## Why we can't just "do it now"
1. The build fails immediately — `build.gradle`'s `zipTree(...)` references 26.1.x jar
   filenames that don't exist for 26.2.
2. Even after swapping the ✅ jars, the ❌ mods would have to be **dropped** (major feature
   loss) or hand-ported blind.
3. Every custom mixin + every source-ported mod must be re-checked against the 26.2 API
   (method renames / signature changes) — hundreds of potential breaks.
4. It would break the working 26.1.2 build that's in active use.

## Recommended path
1. **Wait** for the ❌ mods (esp. ImmediatelyFast, 3dSkinLayers, JEI, BetterStats,
   AdvancementsReloaded) to publish 26.2 builds. Re-run the readiness check; this doc is the tracker.
2. When ready, do the migration **on a branch**: bump the pins above, download 26.2 jars
   into `shading_libs/` + update the 28 filenames in `build.gradle`, then iterate
   `gradlew compileJava` fixing API breaks until green, then in-game test.
3. Launcher side (small): `LauncherSettings.FabricVersion` / `minecraft_version` strings,
   packwiz pack, and any "26.1.2" UI labels → "26.2".

## Periodic re-check (one-liner)
```bash
for s in immediatelyfast 3dskinlayers jei betterstats advancements-reloaded appleskin not-enough-animations xaeros-world-map; do
  r=$(curl -s -A x "https://api.modrinth.com/v2/project/$s/version?game_versions=%5B%2226.2%22%5D&loaders=%5B%22fabric%22%5D"); echo "$(echo "$r"|grep -q version_number && echo ✅ || echo ❌) $s"; done
```
