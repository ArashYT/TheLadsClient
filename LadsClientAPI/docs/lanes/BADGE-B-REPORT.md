# Lane BADGE-B Report: Tab and Nametag Badge Rendering (1.8.9 & 1.20.1)

## Summary
- **Base Commit**: `e4a497157a552553755355cf3ae71b9cf271701a`
- **Lane Branch**: `agy/ladsapi-badgeb`
- **Worktree**: `C:\Users\Arash\AppData\Local\Temp\wla-badgeb`
- **Scope**: Implemented badge rendering (`LadsBadge.TEXTURE_NAMESPACE/PATH`, `TAB_SIZE 8`, `NAMETAG_SIZE 9`, `GAP 1`) in tab list and nametags across Minecraft 1.8.9 and 1.20.1 with zero frame allocations, linear filtering, and proper centering/width calculations.

---

## Changed Files & Diff Stat
```text
TheLadsCore/v1_20_1/src/main/java/com/thelads/core/v1_20_1/hud/overlay/mixin/NametagRenderMixin.java      | 80 ++++++++++++++++---
TheLadsCore/v1_20_1/src/main/java/com/thelads/core/v1_20_1/hud/overlay/mixin/TabOverlayMixin.java         | 56 +++++++++++++
TheLadsCore/v1_8_9/src/main/java/com/thelads/core/v1_8_9/mixin/GuiPlayerTabOverlayMixin.java             | 56 ++++++++++++-
TheLadsCore/v1_8_9/src/main/java/com/thelads/core/v1_8_9/mixin/NametagLivingMixin.java              | 71 +++++++++++++++++
TheLadsCore/v1_8_9/src/main/java/com/thelads/core/v1_8_9/mixin/NametagRenderMixin.java              | 91 +++++++++++++++++++++-
LadsClientAPI/docs/lanes/BADGE-B-REPORT.md                                                                | [this report]
```

---

## Changed Mixins & Bytecode Verification

All bytecode targets and descriptors were verified against mapped jars using `javap -c -p -v`.

### 1. Minecraft 1.8.9
**Mapped Jar**: `C:\Users\Arash\.gradle\caches\essential-loom\1.8.9\de.oceanlabs.mcp.mcp_stable.1_8_9.22-1.8.9-forge-1.8.9-11.15.1.2318-1.8.9\minecraft-mapped.jar`

#### A. `GuiPlayerTabOverlayMixin` (`TheLadsCore/v1_8_9/src/main/java/com/thelads/core/v1_8_9/mixin/GuiPlayerTabOverlayMixin.java`)
- **Target Class**: `net.minecraft.client.gui.GuiPlayerTabOverlay`
- **Target Method**: `renderPlayerlist(ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreObjective;)V`
- **Injections / Redirects**:
  - `@Inject(method = RENDER, at = @At("RETURN"), require = 1)`: Cleans up thread-local/instance fields `ladsWidthInfo` and `ladsRowInfo` when rendering ends.
  - `@Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;", ordinal = 0), require = 1)`: Captures `NetworkPlayerInfo` during the width calculation loop.
  - `@Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 0), require = 1)`: If `LadsBadge.showInTab(uuid)` is true, expands row width by `LadsBadge.TAB_SIZE + LadsBadge.GAP` (8 + 1 = 9px) to prevent column overflow.
  - `@Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;", ordinal = 1), require = 1)`: Captures `NetworkPlayerInfo` during the player draw loop.
  - Redirect on `FontRenderer.drawStringWithShadow(Ljava/lang/String;FFI)I` via `ladsName`: Binds `LADS_BADGE`, sets `GL_LINEAR` filtering, draws an 8x8 textured rect (`Gui.drawScaledCustomSizeModalRect`), shifts text position right by 9px, and handles shadows/TabTweaks.

#### B. `NametagRenderMixin` (`TheLadsCore/v1_8_9/src/main/java/com/thelads/core/v1_8_9/mixin/NametagRenderMixin.java`)
- **Target Class**: `net.minecraft.client.renderer.entity.Render`
- **Target Method**: `renderLivingLabel(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V`
- **Injections / Redirects**:
  - `@Inject(method = "renderLivingLabel", at = @At("HEAD"))`: Captures the rendering `Entity`.
  - `@Inject(method = "renderLivingLabel", at = @At("RETURN"))`: Clears the captured `Entity`.
  - `@Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 0))`: Expands background box width by `NAMETAG_SIZE + GAP` (9 + 1 = 10px).
  - `@Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 0))`: See-through pass. Centers badge + text as one unit by shifting text right by `(NAMETAG_SIZE + GAP) / 2 = 5px`, drawing a 9x9 badge quad at `textX - 10px` with see-through alpha (`0.2F`), and `GL_LINEAR` filtering.
  - `@Inject(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1))`: Shadow pass. Shifts text shadow offset by +5px to align with shifted text.
  - `@Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1))`: Normal pass. Draws 9x9 badge quad at `textX - 10px` with opaque alpha (`1.0F`), `GL_LINEAR` filtering, and shifts text right by 5px.

#### C. `NametagLivingMixin` (`TheLadsCore/v1_8_9/src/main/java/com/thelads/core/v1_8_9/mixin/NametagLivingMixin.java`)
- **Target Class**: `net.minecraft.client.renderer.entity.RendererLivingEntity`
- **Target Method**: `renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V`
- **Injections / Redirects**:
  - In 1.8.9, sneaking nametags bypass `Render.renderLivingLabel` and are drawn directly inside `RendererLivingEntity.renderName`.
  - `@Redirect(method = "renderName", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I", ordinal = 0))`: Expands background box width by 10px.
  - `@Redirect(method = "renderName", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 0))`: Shifts text right by 5px, draws faded 9x9 badge quad with linear filtering and matching sneaking alpha immediately to the left.

---

### 2. Minecraft 1.20.1
**Mapped Jar**: `C:\Users\Arash\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2\minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar`

#### A. `TabOverlayMixin` (`TheLadsCore/v1_20_1/src/main/java/com/thelads/core/v1_20_1/hud/overlay/mixin/TabOverlayMixin.java`)
- **Target Class**: `net.minecraft.client.gui.components.PlayerTabOverlay`
- **Target Method**: `render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V`
- **Injections / WrapOperations**:
  - `@Inject(method = RENDER, at = @At("RETURN"))`: Clears `lads$widthInfo` and `lads$renderInfo`.
  - `@WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;", ordinal = 0))`: Captures `PlayerInfo` in `lads$widthInfo`.
  - `@WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I", ordinal = 0))`: Expands column width by `TAB_SIZE + GAP` (8 + 1 = 9px) when `LadsBadge.showInTab(uuid)` is true.
  - `@WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;", ordinal = 1))`: Captures `PlayerInfo` in `lads$renderInfo`.
  - `@WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = NAME, ordinal = 0))`: Sets linear texture filter on `AbstractTexture` (cached by texture ID to avoid redundant OpenGL calls), draws 8x8 badge via `GuiGraphics.blit`, and shifts name right by 9px.

#### B. `NametagRenderMixin` (`TheLadsCore/v1_20_1/src/main/java/com/thelads/core/v1_20_1/hud/overlay/mixin/NametagRenderMixin.java`)
- **Target Class**: `net.minecraft.client.renderer.entity.EntityRenderer`
- **Target Method**: `renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V`
- **Injections / WrapOperations**:
  - `@Inject(method = "renderNameTag", at = @At("HEAD"))`: Captures the `Entity`.
  - `@Inject(method = "renderNameTag", at = @At("RETURN"))`: Clears the captured `Entity`.
  - `@WrapOperation(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))`:
    - Centers badge + text as one unit by shifting text right by `(NAMETAG_SIZE + GAP) / 2 = 5px`.
    - Computes badge quad bounds: `[textX - 10, textX - 1]`.
    - If background is enabled (`bg != 0`), emits a background quad into `RenderType.textBackground()` / `RenderType.textBackgroundSeeThrough()` using format `POSITION_COLOR_LIGHTMAP` (including `.uv2(light)`).
    - Renders 9x9 badge quad into `RenderType.text(LADS_BADGE)` or `RenderType.textSeeThrough(LADS_BADGE)` using format `POSITION_COLOR_TEX_LIGHTMAP` with linear texture filtering.
    - Honors `Nametags1201.background()` and `Nametags1201.shadow()`.
    - Seamlessly supports standing players, sneaking players (faded alpha), and see-through passes via `Font.DisplayMode`.

---

## Build and Test Verification

### 1. Modern Core & 1.20.1 Build
Command: `cd TheLadsCore; .\gradlew.bat :common:test :v1_20_1:build` (run in foreground after verifying BENCH-LOCK)
```text
> Task :common:compileJava UP-TO-DATE
> Task :common:processResources UP-TO-DATE
> Task :common:classes UP-TO-DATE
> Task :common:jar UP-TO-DATE
> Task :common:compileTestJava UP-TO-DATE
> Task :common:processTestResources UP-TO-DATE
> Task :common:testClasses UP-TO-DATE
> Task :common:test UP-TO-DATE
> Task :v1_20_1:compileJava
> Task :v1_20_1:processResources
> Task :v1_20_1:classes
> Task :v1_20_1:compileTestJava NO-SOURCE
> Task :v1_20_1:jar
> Task :v1_20_1:processIncludeJars
> Task :v1_20_1:remapSourcesJar SKIPPED
> Task :v1_20_1:processTestResources NO-SOURCE
> Task :v1_20_1:testClasses UP-TO-DATE
> Task :v1_20_1:test NO-SOURCE
> Task :v1_20_1:validateAccessWidener
> Task :v1_20_1:check
> Task :v1_20_1:remapJar
> Task :v1_20_1:assemble
> Task :v1_20_1:build

BUILD SUCCESSFUL in 38s
12 actionable tasks: 6 executed, 6 up-to-date
```

### 2. 1.8.9 Forge Build
Command: `cd TheLadsCore\v1_8_9; .\gradlew.bat build` (run in foreground after verifying BENCH-LOCK)
```text
> Task :downgradeCommon
> Task :compileJava
Note: SpongePowered MIXIN Annotation Processor Version=0.8.5
Note: Supported obfuscation types: ObfuscationServiceMCP supports [searge,notch] ObfuscationServiceFabric supports [official:intermediary,official:named,intermediary:official,intermediary:named,named:official,named:intermediary]
Note: Loading named:intermediary mappings from C:\Users\Arash\.gradle\caches\essential-loom\1.8.9\de.oceanlabs.mcp.mcp_stable.1_8_9.22-1.8.9-forge-1.8.9-11.15.1.2318-1.8.9\mappings-mixin-srg.tiny
Note: Writing refmap to C:\Users\Arash\AppData\Local\Temp\wla-badgeb\TheLadsCore\v1_8_9\build\classes\java\main\theladscore.v1_8_9.refmap.json
Note: Writing named:intermediary output TinyMappings to C:\Users\Arash\AppData\Local\Temp\wla-badgeb\TheLadsCore\v1_8_9\build\loom-cache\mixin-map-de.oceanlabs.mcp.mcp_stable.1_8_9.22-1.8.9-forge-1.8.9-11.15.1.2318-1.8.9.1.tiny
> Task :processResources
> Task :classes
> Task :jar
> Task :shadowJar
> Task :shadeJvmdgApi
> Task :remapJar
> Task :assemble
> Task :compileTestJava NO-SOURCE
> Task :processTestResources NO-SOURCE
> Task :testClasses UP-TO-DATE
> Task :test NO-SOURCE
> Task :validateAccessWidener NO-SOURCE
> Task :check UP-TO-DATE
> Task :build

BUILD SUCCESSFUL in 28s
7 actionable tasks: 7 executed
```

---

## Verification & Limits
- **Static & Bytecode Verification**: All 5 mixins checked against mapped jars using `javap`; descriptor signatures and ordinals match bytecode exactly.
- **Line Endings**: Verified 100% CRLF across all 5 modified files and this report (`0` LF).
- **In-Game Verification**: As per rule 33 / instructions, client launching and in-game screenshots are deferred to Claude on the Legion laptop.
