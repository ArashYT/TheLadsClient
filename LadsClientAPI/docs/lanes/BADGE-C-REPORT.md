# Lane BADGE-C Report: 26.3 Tab + Nametag Badge Rendering

## 1. Summary
Implemented Lads Client badge rendering for Minecraft 26.3 (Fabric) in both player tab overlay (`PlayerTabOverlay`) and world-space nametags (`AvatarRenderer` / `SubmitNodeCollection`).

- **Tab**:
  - Intercepted `Font.width(FormattedText)` at ordinal 0 in `PlayerTabOverlay.extractRenderState` to add `LadsBadge.TAB_SIZE + LadsBadge.GAP` (8 + 1 = 9 px) when `LadsBadge.showInTab(player)` is true. This ensures the player's row width, column slot width (`slotWidth`), total width (`maxLineWidth`), ping icon offset, and scoreboard objective text offset (`left = xo + maxNameWidth + 1`) automatically expand.
  - Intercepted player name text extraction in `removeBodyShadow` (`GuiGraphicsExtractor.text`): blits the 8x8 badge texture using `RenderPipelines.GUI_TEXTURED` at `(x, y)` with spectator translucency support (`0x90FFFFFF` when in spectator mode, `-1` otherwise) and shifts the name text right by `TAB_SIZE + GAP` (9 px).
- **Nametag**:
  - Extended `AvatarRenderState` via duck interface `LadsAvatarRenderState` and mixin `AvatarRenderStateMixin` to capture player `UUID` during `AvatarRenderer.extractRenderState`.
  - In `AvatarRendererMixin`, tracked the active player UUID and nametag component in `NametagBadgeContext` during `submitNameDisplay`.
  - In `NametagBackgroundMixin` (`SubmitNodeCollection.submitNameTag`):
    - Shifted text X by `+(NAMETAG_SIZE + GAP) / 2.0F` (+5.0F) to center the combined badge + gap + name block symmetrically at `x = 0`.
    - Submitted custom billboard geometry via `SubmitNodeCollection.submitCustomGeometry` at `(x - 10.0F, offset)` in the nametag pose stack:
      - Normal pass: `RenderTypes.text(BADGE_TEXTURE)` with full opacity (`-1`) and full light (`0xF000F0`).
      - Sneaking pass: `RenderTypes.text(BADGE_TEXTURE)` with faded alpha (`0x80FFFFFF`, 50% opacity) matching vanilla sneaking nametag text.
      - See-through pass: When `seeThrough` is true (non-sneaking behind walls), submitted `RenderTypes.textSeeThrough(BADGE_TEXTURE)` with faded alpha (`0x80FFFFFF`).
- **Performance**: Zero allocations per frame. All renderers, identifiers, and context references are static final or static singletons without lambdas or objects allocated per entity/frame.

---

## 2. Architectural Analysis: Custom Submit vs. Extending Render State

The task asked to evaluate the two options for 26.3 nametag badge rendering:
1. **Extending the name tag's render state (`TextFeatureRenderer` / `Content`)**:
   In 26.3, nametag text is rendered through `TextFeatureRenderer.buildGroup`, which iterates over `TextFeatureRenderer.Submit(Matrix4fc pose, Font.DisplayMode displayMode, int lightCoords, TextFeatureRenderer.Content content)`. The method uses Java type-switch pattern matching (`SwitchBootstraps.typeSwitch`) over the sealed/closed interface `Content`, permitting strictly `Content.Text` and `Content.StandaloneBackground`. Any other type throws `MatchException`. Extending this pipeline requires modifying `TextFeatureRenderer`'s switch bootstrap or injecting a synthetic `TextRenderable` into the `Font.prepareText` visitor.
   - *Limits*: Fragile compiler-generated type-switch bytecode in `TextFeatureRenderer.buildGroup`; touches global font rendering paths used throughout GUI and world text.
2. **Custom Submit next to / within name tag submit (`submitCustomGeometry`) (Chosen)**:
   We capture player `UUID` on `AvatarRenderState` and activate `NametagBadgeContext` during `AvatarRenderer.submitNameDisplay`. Inside `SubmitNodeCollection.submitNameTag`, we shift the text by `+5.0F` and call `SubmitNodeCollection.submitCustomGeometry` at `(x - 10.0F, offset)` before `poseStack.popPose()`.
   - *Advantages*: Reuses the exact billboarded, scaled, and translated modelview matrix calculated by vanilla `submitNameTag`. Renders the 64x64 PNG cleanly via `RenderTypes.text(BADGE_TEXTURE)` and `RenderTypes.textSeeThrough(BADGE_TEXTURE)` with full mipmapping, bilinear sampling, and alpha blending.
   - *Limits*: `submitCustomGeometry` routes into `translucentCustomGeometry`. In 26.3's default phase list, `translucentCustomGeometry` executes in the translucent phase after `nameTags`, which correctly blends with transparency over nametags, but cannot be reordered into `nameTags`'s `SimpleFeatureRenderPhase` without modifying `SubmitNodeCollection.allPhases`.

---

## 3. Mixin Targets & Javap Bytecode Verification

All bytecode descriptors and targets were verified via `javap -s` against the 26.3 deobfuscated Loom jar:
`C:\Users\Arash\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar`

### 1. `PlayerTabOverlay` (Tab Overlay)
- **Target Class**: `net.minecraft.client.gui.components.PlayerTabOverlay`
- **Method**: `extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V`
- **Injection 1**: `@WrapOperation` at `INVOKE net/minecraft/client/gui/Font.width(Lnet/minecraft/network/chat/FormattedText;)I` (ordinal 0). Adds `TAB_SIZE + GAP` (8 + 1 = 9 px) when `LadsBadge.showInTab(uuid)` is true.
- **Injection 2**: `@WrapOperation` at `INVOKE net/minecraft/client/gui/GuiGraphicsExtractor.text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V` (ordinal 0). Blits `RenderPipelines.GUI_TEXTURED` at `(x, y)` and shifts name to `x + 9`.

### 2. `AvatarRenderer` (Avatar Nametag Display)
- **Target Class**: `net.minecraft.client.renderer.entity.player.AvatarRenderer`
- **Method 1**: `extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V`
  - Injects at `RETURN` to store `entity.getUUID()` into `AvatarRenderState`.
- **Method 2**: `submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V`
  - Injects at `HEAD` and `RETURN` to manage `NametagBadgeContext.CURRENT_PLAYER` and `CURRENT_NAMETAG`.

### 3. `AvatarRenderState` (Render State Duck)
- **Target Class**: `net.minecraft.client.renderer.entity.state.AvatarRenderState`
- **Duck Interface**: `com.thelads.core.v26_2.feature.LadsAvatarRenderState`
- Adds `UUID lads$getUuid()`, `void lads$setUuid(UUID)`, and `boolean lads$hasBadge()`.

### 4. `SubmitNodeCollection` (Nametag Submit & Custom Geometry)
- **Target Class**: `net.minecraft.client.renderer.SubmitNodeCollection`
- **Method**: `submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V`
  - Injects at `HEAD` (`lads$rename`) to maintain nametag reference identity.
  - Injects at `STORE 0` (`lads$shiftTextX`) to shift text X position by `+(NAMETAG_SIZE + GAP) / 2.0F` (+5.0F).
  - Injects at `INVOKE popPose()` (`lads$submitBadge`) to call `submitCustomGeometry` with `BADGE_NORMAL_GEOMETRY` or `BADGE_SNEAKING_GEOMETRY`.

---

## 4. Hot Paths and Performance (Zero Allocations)
- No objects, strings, streams, or lambdas allocated per frame or per entity.
- Geometry renderers `BADGE_NORMAL_GEOMETRY` and `BADGE_SNEAKING_GEOMETRY` are `static final` instances of `SubmitNodeCollector.CustomGeometryRenderer`.
- Texture identifier `LADS$BADGE_TEXTURE` is `static final`.
- `NametagBadgeContext` uses static references on the render thread.

---

## 5. Build and Test Evidence

Command: `.\gradlew.bat :common:test :v26_3:build --console=plain` in `TheLadsCore`:

```text
> Configure project :v1_20_1
Fabric Loom: 1.17.21

> Configure project :v1_21_11
Fabric Loom: 1.17.21

> Configure project :v26_2
Fabric Loom: 1.17.21

> Configure project :v26_3
Fabric Loom: 1.17.21

> Task :common:compileJava UP-TO-DATE
> Task :common:processResources UP-TO-DATE
> Task :common:classes UP-TO-DATE
> Task :common:jar UP-TO-DATE
> Task :common:compileTestJava UP-TO-DATE
> Task :common:processTestResources UP-TO-DATE
> Task :common:testClasses UP-TO-DATE
> Task :common:test UP-TO-DATE
> Task :v26_3:compileJava UP-TO-DATE
> Task :v26_3:jadeCompatibilityIdentity UP-TO-DATE
> Task :v26_3:processResources
> Task :v26_3:classes
> Task :v26_3:processIncludeJars UP-TO-DATE
> Task :v26_3:compileTestJava UP-TO-DATE
> Task :v26_3:processTestResources NO-SOURCE
> Task :v26_3:testClasses UP-TO-DATE
> Task :v26_3:test
> Task :v26_3:validateAccessWidener UP-TO-DATE
> Task :v26_3:jar
> Task :v26_3:assemble
> Task :v26_3:verifyJadePackaging
> Task :v26_3:check
> Task :v26_3:build

BUILD SUCCESSFUL in 19s
15 actionable tasks: 4 executed, 11 up-to-date
```

Command: `.\gradlew.bat :common:test :v26_3:test --rerun-tasks --console=plain` in `TheLadsCore`:

```text
> Task :common:compileJava
> Task :common:processResources
> Task :common:classes
> Task :common:jar
> Task :common:compileTestJava
> Task :common:processTestResources
> Task :common:testClasses
> Task :common:test
> Task :v26_3:jadeCompatibilityIdentity
> Task :v26_3:compileJava
> Task :v26_3:processResources
> Task :v26_3:classes
> Task :v26_3:compileTestJava
> Task :v26_3:processTestResources NO-SOURCE
> Task :v26_3:testClasses
> Task :v26_3:test

BUILD SUCCESSFUL in 1m 20s
11 actionable tasks: 11 executed
```

---

## 6. Git Diff Stat against Base (`e4a49715`)

```text
 LadsClientAPI/docs/lanes/BADGE-C-REPORT.md         | 166 +++++++++++++++++++++
 .../core/v26_2/feature/LadsAvatarRenderState.java  |   9 ++
 .../core/v26_2/feature/NametagBadgeContext.java    |  16 ++
 .../tabtweaks/mixin/PlayerTabOverlayMixin.java     |  32 +++-
 .../core/v26_2/mixin/AvatarRenderStateMixin.java   |  33 ++++
 .../core/v26_2/mixin/AvatarRendererMixin.java      |  39 +++++
 .../core/v26_2/mixin/NametagBackgroundMixin.java   |  74 ++++++++-
 .../main/resources/theladscore.v26_2.mixins.json   |   2 +
 8 files changed, 367 insertions(+), 4 deletions(-)
```

---

## 7. UNVERIFIED / What could NOT be checked in this run

1. **In-game visual display on Minecraft client**: In accordance with hard rule 33 ("Do NOT launch Minecraft, servers, the launcher, QA or benchmarks. Claude runs QA on the Legion laptop later."), the game was not launched. In-game screenshot verification will be conducted by Claude on the Legion laptop.
2. **Badge PNG asset resolution**: Lane BADGE-A implements the PNG asset (`textures/gui/lads_badge.png`) in parallel; in base `e4a49715` the texture file is not yet merged, so runtime texture sampling was not visually inspected in-game.
3. **`LadsBadge.showInTab` / `showOnNametag` live conditions**: Implemented by lane BADGE-A (currently stubs returning `false` in base). Verified that code cleanly compiles and links against frozen interfaces and method signatures.
