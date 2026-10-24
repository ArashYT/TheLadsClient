# Lane SRV-A Report: Server Core + Paper + Velocity

## Scope & Ownership
- Worktree: `C:\Users\Arash\AppData\Local\Temp\wla-srva`
- Branch: `agy/ladsapi-srva`
- Base commit: `e4a49715`
- Final commit hash: `ea2e113f`
- Owned subprojects:
  - `LadsClientAPI/common`
  - `LadsClientAPI/platform-paper`
  - `LadsClientAPI/platform-velocity`

## Implementation Summary

### 1. Common (`LadsCore` bodies + `LadsClientAPI`/`LadsPlayer` impl)
- **Configuration & Rules**:
  - `LadsConfig`: parses and writes `config.yml` according to PLAN 6.2 defaults (`enabled`, `unlisted-modules`, `notify-players`, `let-players-refuse-changes`, `require-lads-client`, `min-client-version`, `badge.presence`, `badge.placeholder`, `debug`, etc.).
  - `LadsSchema`: parses embedded `/ladsapi-schema.json`. Added schema with Fullbright, XaeroMinimap, Crosshair Tweaks, and 1.7 Animations.
  - `RulesGenerator`: generates initial `rules.yml` with comments for all module options (type, default, range, choices), and regenerates existing `rules.yml` adding missing entries without altering owner values or comments.
  - `LadsRules`: loads `rules.yml` with SnakeYAML 2.2 (`org.yaml:snakeyaml:2.2`).
- **Policy Merge Order & Wire Format**:
  - `PolicyBuilder`:
    1. Base `rules.yml` (`modules`)
    2. `worlds.<world>` overrides
    3. `groups.<g>` overrides for each permission `ladsapi.group.<g>`
    4. API overrides (from `LadsPlayer`)
    5. `ladsapi.bypass` permission -> returns empty policy snapshot JSON.
  - JSON wire format strictly matching `PROTOCOL.md`:
    - Full policy snapshot JSON: `t="policy"`, `rev` (starts at 1, increments per send), `unlisted`, `modules` map, nested `options` map, and optional `notice`.
    - Tested to match PROTOCOL.md example verbatim.
- **Session Lifecycle & Rate Limiting**:
  - `SessionManager` & `LadsSession`:
    - Sends hello at +1 tick.
    - Resends hello at +60 ticks if no reply, never again.
    - If `require-lads-client` is true and no hello received within 5s (100 ticks), kicks player.
    - When client sends hello with `v >= 1`: marks player as Lads, stores Lads/MC version, checks `min-client-version` (kicking older client versions), sends full policy (rev=1), and sends full presence if `badge.presence` is true.
    - Incoming message size limit: dropped if > 32,000 bytes.
    - Rate limit: 8 msgs/s per player via sliding-window `RateLimiter`.
    - Malformed JSON safely dropped without error/kick.
    - Ack logged when `debug: true`.
    - On quit: sends presence remove to other Lads players.
- **Commands**:
  - `LadsCommandHandler`: implements `/ladsapi` subcommands: `reload`, `status`, `inspect <player>`, `set <player> <Module> <state>`, `set <player> <Module.Option> <rule>`, `clear <player>`.
- **API Implementation**:
  - `LadsClientAPI` and `LadsPlayerImpl` implementing `LadsPlayer` (`block`, `allow`, `forceOn`, `limit`, `force`, `clear`, `resend`).

### 2. Platform Paper
- `plugin.yml` configured with `api-version: '1.20'`, softdepend `[PlaceholderAPI]`, command `/ladsapi` and permissions `ladsapi.admin`, `ladsapi.bypass`.
- Plugin messaging on channel `lads:api`.
- `PaperPlayerHandle.send`: reflectively accesses `CraftPlayer.channels()` to add `lads:api` to the live set before calling `sendPluginMessage`.
- `PaperEventListener`: handles `PlayerJoinEvent`, `PlayerQuitEvent`, and `PlayerChangedWorldEvent`.
- `PaperCommandHandler` & `PaperTabCompleter`: tab completes subcommands, player names, modules, and option rules.
- `LadsPlaceholderExpansion`: provides `%ladsapi_badge%`, `%ladsapi_is_lads%`, and `%ladsapi_version%`.
- `LadsPlayerReadyEvent`: Bukkit event fired when handshake completes.

### 3. Platform Velocity
- `velocity-plugin.json` generated via annotation processor `@Plugin(id = "ladsclientapi", ...)`.
- Proxy mode (`isProxy() == true`).
- `ServerPostConnectEvent`: sends `{"t":"reset"}` on every connection switch.
- Only when `proxy-rules: true`: registers `lads:api` channel with `ChannelRegistrar`, processes incoming plugin messages, and runs sessions with `world()` returning the connected backend server name. When `proxy-rules: false`, `lads:api` is NOT registered on Velocity so backend messages pass through untouched.
- `VelocityCommandHandler`: implements `/ladsapi` command with tab completion.

## Mixins
- **No mixins**: Lane SRV-A owns `common`, `platform-paper`, and `platform-velocity`. Paper and Velocity platforms use Bukkit and Velocity plugin APIs only; client/Fabric/Forge mixins belong to other lanes.

## Exact Build & Test Commands Executed

### Command:
`cd LadsClientAPI; .\gradlew.bat :common:test :platform-paper:build :platform-velocity:build`

```text
> Task :common:compileJava UP-TO-DATE
> Task :common:processResources UP-TO-DATE
> Task :common:classes UP-TO-DATE
> Task :common:jar UP-TO-DATE
> Task :common:compileTestJava UP-TO-DATE
> Task :common:processTestResources NO-SOURCE
> Task :common:testClasses UP-TO-DATE
> Task :common:test UP-TO-DATE
> Task :platform-paper:compileJava UP-TO-DATE
> Task :platform-paper:processResources UP-TO-DATE
> Task :platform-paper:classes UP-TO-DATE
> Task :platform-paper:jar UP-TO-DATE
> Task :platform-paper:assemble UP-TO-DATE
> Task :platform-paper:compileTestJava UP-TO-DATE
> Task :platform-paper:processTestResources NO-SOURCE
> Task :platform-paper:testClasses UP-TO-DATE
> Task :platform-paper:test UP-TO-DATE
> Task :platform-paper:check UP-TO-DATE
> Task :platform-paper:build UP-TO-DATE
> Task :platform-velocity:compileJava UP-TO-DATE
> Task :platform-velocity:processResources NO-SOURCE
> Task :platform-velocity:classes UP-TO-DATE
> Task :platform-velocity:jar UP-TO-DATE
> Task :platform-velocity:assemble UP-TO-DATE
> Task :platform-velocity:compileTestJava UP-TO-DATE
> Task :platform-velocity:processTestResources NO-SOURCE
> Task :platform-velocity:testClasses UP-TO-DATE
> Task :platform-velocity:test UP-TO-DATE
> Task :platform-velocity:check UP-TO-DATE
> Task :platform-velocity:build UP-TO-DATE

BUILD SUCCESSFUL in 3s
14 actionable tasks: 14 up-to-date
```

### Unit Tests Executed (9 test suites, all PASS):
- `com.thelads.ladsapi.test.ConfigRulesTest` (PASSED)
- `com.thelads.ladsapi.test.HelloResendAckFlowTest` (PASSED)
- `com.thelads.ladsapi.test.MergeOrderTest` (PASSED)
- `com.thelads.ladsapi.test.PresenceTest` (PASSED)
- `com.thelads.ladsapi.test.ProtocolPolicyJsonTest` (PASSED)
- `com.thelads.ladsapi.test.RateLimitTest` (PASSED)
- `com.thelads.ladsapi.test.VersionCompareTest` (PASSED)
- `com.thelads.ladsapi.paper.PaperPlayerHandleTest` (PASSED)
- `com.thelads.ladsapi.velocity.VelocityPlayerHandleTest` (PASSED)

## Artifacts Produced
- `LadsClientAPI/common/build/libs/common-1.0.0.jar`
- `LadsClientAPI/platform-paper/build/libs/platform-paper-1.0.0.jar` (contains `plugin.yml`)
- `LadsClientAPI/platform-velocity/build/libs/platform-velocity-1.0.0.jar` (contains `velocity-plugin.json`)

## What Could NOT Be Done or Verified in This Run
- Running live Minecraft client, Paper server, or Velocity proxy processes (strictly prohibited by AGY-LADSAPI-BUILD-RULES.md rule 33: "Do NOT launch Minecraft, servers, the launcher, QA or benchmarks. Claude runs QA on the Legion laptop later.").
- Universal jar packaging: merging into `universalJar` is owned by Lane SRV-B per task file.

## Git Diff Stat (`e4a49715..HEAD`)
```text
 LadsClientAPI/common/build.gradle                  |   2 +
 .../main/java/com/thelads/ladsapi/LadsCore.java    |  65 +++++-
 .../com/thelads/ladsapi/api/LadsPlayerImpl.java    |  75 +++++++
 .../ladsapi/command/LadsCommandHandler.java        | 194 ++++++++++++++++
 .../com/thelads/ladsapi/config/LadsConfig.java     | 159 +++++++++++++
 .../com/thelads/ladsapi/policy/PolicyBuilder.java  | 153 +++++++++++++
 .../java/com/thelads/ladsapi/rules/LadsRules.java  | 183 +++++++++++++++
 .../java/com/thelads/ladsapi/rules/ModuleRule.java |  80 +++++++
 .../java/com/thelads/ladsapi/rules/RuleOption.java |  68 ++++++
 .../com/thelads/ladsapi/rules/RulesGenerator.java  | 209 +++++++++++++++++
 .../com/thelads/ladsapi/schema/LadsSchema.java     | 102 +++++++++
 .../com/thelads/ladsapi/session/LadsSession.java   | 151 +++++++++++++
 .../thelads/ladsapi/session/SessionManager.java    | 248 +++++++++++++++++++++
 .../java/com/thelads/ladsapi/util/RateLimiter.java |  32 +++
 .../java/com/thelads/ladsapi/util/VersionUtil.java |  63 ++++++
 .../common/src/main/resources/ladsapi-schema.json  |  93 ++++++++
 .../com/thelads/ladsapi/test/ConfigRulesTest.java  | 163 ++++++++++++++
 .../com/thelads/ladsapi/test/FakePlatform.java     |  75 +++++++
 .../com/thelads/ladsapi/test/FakePlayerHandle.java |  79 +++++++
 .../ladsapi/test/HelloResendAckFlowTest.java       | 100 +++++++++
 .../com/thelads/ladsapi/test/MergeOrderTest.java   |  79 +++++++
 .../com/thelads/ladsapi/test/PresenceTest.java     |  79 +++++++
 .../ladsapi/test/ProtocolPolicyJsonTest.java       |  66 ++++++
 .../com/thelads/ladsapi/test/RateLimitTest.java    |  27 +++
 .../thelads/ladsapi/test/VersionCompareTest.java   |  57 +++++
 LadsClientAPI/docs/lanes/SRV-A-REPORT.md           | 190 ++++++++++++++++
 LadsClientAPI/platform-paper/build.gradle          |  20 ++
 .../com/thelads/ladsapi/paper/LadsPaperPlugin.java |  83 +++++++
 .../ladsapi/paper/LadsPlaceholderExpansion.java    |  65 ++++++
 .../thelads/ladsapi/paper/PaperCommandHandler.java |  29 +++
 .../thelads/ladsapi/paper/PaperEventListener.java  |  37 +++
 .../com/thelads/ladsapi/paper/PaperPlatform.java   |  61 +++++
 .../thelads/ladsapi/paper/PaperPlayerHandle.java   |  69 ++++++
 .../thelads/ladsapi/paper/PaperTabCompleter.java   |  85 +++++++
 .../ladsapi/paper/event/LadsPlayerReadyEvent.java  |  39 ++++
 .../platform-paper/src/main/resources/plugin.yml   |  19 ++
 .../ladsapi/paper/PaperPlayerHandleTest.java       |  63 ++++++
 LadsClientAPI/platform-velocity/build.gradle       |  15 ++
 .../ladsapi/velocity/LadsVelocityPlugin.java       | 126 +++++++++++
 .../ladsapi/velocity/VelocityCommandHandler.java   |  96 ++++++++
 .../thelads/ladsapi/velocity/VelocityPlatform.java |  70 ++++++
 .../ladsapi/velocity/VelocityPlayerHandle.java     |  59 +++++
 .../velocity/event/LadsPlayerReadyEvent.java       |  25 +++
 .../ladsapi/velocity/VelocityPlayerHandleTest.java |  76 +++++++
 44 files changed, 3821 insertions(+), 8 deletions(-)
```
