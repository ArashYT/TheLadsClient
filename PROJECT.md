# Project: Lads Client Feature Porting and Integration

## Architecture
The workspace contains:
1. **TheLadsCore (Fabric Mod, Java 25)**: Located at `TheLadsCore/`. Contains the core Fabric mod code. We will integrate Capes and Render Scale features directly into it.
2. **Standalone Capes Source**: Decompiled files in `.agents/teamwork_preview_explorer_exploration/decompiled_capes` ported to Java 25 and placed in `TheLadsCore/src/main/java/com/thelads/core/client/capes/`.
3. **Standalone Render Scale Source**: Decompiled files in `.agents/teamwork_preview_explorer_exploration/decompiled_renderscale` integrated in `TheLadsCore/src/main/java/com/thelads/core/client/renderscale/`.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | E2E Testing Track | Design E2E test infra, inventory, write test cases, publish `TEST_READY.md` | None | PLANNED |
| 2 | Render Scale & Loading Overlay | Port Render Scale code to Java, register mixins, configure Sodium config menu, implement red/black loading animation | M1 | PLANNED |
| 3 | Capes Integration | Port Capes Kotlin code to Java 25, register mixins, copy resources, initialize from client mod initializer | M1 | PLANNED |
| 4 | Folder Cleanup & E2E Validation | Clean up mods/instance directories, update Packwiz index, run full E2E test validation | M2, M3 | PLANNED |

## Interface Contracts
### Render Scale
- `RenderScaleConfigEntryPoint` implements Sodium's `ConfigEntryPoint` to register settings UI.
- `RenderScaleManager` handles redirect and blitting logic.
- Config is saved in `config/render-scale-options.json` using Gson.

### Capes
- `Capes.INSTANCE.onInitializeClient()` called from `TheLadsCoreClient` initialization.
- Mixin accessors and injections to intercept cape rendering and skin resolving.
- Config is saved in `config/capes.json5` using Gson.

## Code Layout
- Ported Capes Java classes: `TheLadsCore/src/main/java/com/thelads/core/client/capes/`
- Ported Render Scale Java classes: `TheLadsCore/src/main/java/com/thelads/core/client/renderscale/`
- Mixins: `TheLadsCore/src/main/java/com/thelads/core/mixin/capes/` and `TheLadsCore/src/main/java/com/thelads/core/mixin/renderscale/`
- Capes assets: `TheLadsCore/src/main/resources/assets/capes/`
- Render Scale assets: `TheLadsCore/src/main/resources/assets/render-scale/`
