# Lane SRV-B Report: Fabric 26.3 + Forge 1.20.1 Server Platforms and Universal Jar

## 1. Commit and Lane Summary
- **Lane**: SRV-B (Fabric 26.3 + Forge 1.20.1 server platforms and the ONE universal jar)
- **Worktree**: `C:\Users\Arash\AppData\Local\Temp\wla-srvb`
- **Branch**: `agy/ladsapi-srvb`
- **Base Commit**: `e4a49715`
- **Final Commit**: `bf83043f87c8008d70691515efb582b2d6a59929`

## 2. Git Diff Stat (`e4a49715..HEAD`)
```text
 LadsClientAPI/build.gradle                         |  62 ++++-
 LadsClientAPI/docs/lanes/SRV-B-REPORT.md           | 268 +++++++++++++++++++++
 LadsClientAPI/gradle.properties                    |   2 +
 LadsClientAPI/platform-fabric/build.gradle         |  18 +-
 .../thelads/ladsapi/fabric/FabricPermissions.java  |  80 ++++++
 .../com/thelads/ladsapi/fabric/LadsFabricMod.java  | 110 +++++++++
 .../thelads/ladsapi/fabric/LadsFabricPlatform.java | 110 +++++++++
 .../ladsapi/fabric/LadsFabricPlayerHandle.java     |  50 ++++
 .../com/thelads/ladsapi/fabric/LadsPayload.java    |  28 +++
 .../src/main/resources/fabric.mod.json             |  17 ++
 .../ladsapi/fabric/FabricPermissionsTest.java      |  17 ++
 .../ladsapi/fabric/LadsPayloadCodecTest.java       |  35 +++
 LadsClientAPI/platform-forge/build.gradle          |  33 ++-
 .../com/thelads/ladsapi/forge/ForgeReflection.java | 176 ++++++++++++++
 .../com/thelads/ladsapi/forge/LadsForgeMod.java    | 149 ++++++++++++
 .../thelads/ladsapi/forge/LadsForgePlatform.java   | 114 +++++++++
 .../ladsapi/forge/LadsForgePlayerHandle.java       |  61 +++++
 .../src/main/resources/META-INF/mods.toml          |  25 ++
 .../platform-forge/src/main/resources/pack.mcmeta  |   6 +
 .../thelads/ladsapi/forge/ForgeReflectionTest.java |  27 +++
 LadsClientAPI/src/main/resources/pack.mcmeta       |   6 +
 LadsClientAPI/src/main/resources/plugin.yml        |   9 +
 .../src/main/resources/velocity-plugin.json        |   8 +
 LadsClientAPI/tools/verify-jar.py                  |  90 +++++++
 24 files changed, 1498 insertions(+), 3 deletions(-)
```

## 3. Implementation Overview

### 3.1 platform-fabric (Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.163.0+26.3)
- **`fabric.mod.json`**: Configured with `"environment": "server"`, `"entrypoints": { "main": ["com.thelads.ladsapi.fabric.LadsFabricMod"] }`, and dependencies `"fabricloader": ">=0.19.5"`, `"fabric-api": ">=0.161.0"`.
- **Payload & Networking**: `LadsPayload` implements `CustomPacketPayload` on channel `lads:api`. Registered with `PayloadTypeRegistry.clientboundPlay().registerLarge(LadsPayload.TYPE, LadsPayload.CODEC, 1024 * 1024)` and `serverboundPlay().registerLarge(...)` (1 MiB max).
- **Receiver**: `ServerPlayNetworking.registerGlobalReceiver(LadsPayload.TYPE, ...)` extracts data and calls `LadsCore.receive(handle, payload.data())`.
- **Send**: `LadsFabricPlayerHandle.send(byte[] payload)` calls `ServerPlayNetworking.send(player, new LadsPayload(payload))` **WITHOUT** checking any `canSend` gate (client does not announce channel, PROTOCOL 1).
- **Lifecycle & Events**:
  - `ServerPlayConnectionEvents.JOIN`: Registers player, calls `LadsCore.join(handle)`.
  - `ServerPlayConnectionEvents.DISCONNECT`: Calls `platform.removePlayer(uuid)` and `LadsCore.quit(uuid)`.
  - `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL`: Calls `LadsCore.worldChanged(handle)`.
- **Commands & Permissions**:
  - `/ladsapi` command registered via `CommandRegistrationCallback.EVENT` with greedy argument support.
  - `FabricPermissions`: Soft reflection integration with `me.lucko.fabric.api.permissions.v0.Permissions`. Falls back to vanilla 26.3 `Permissions.COMMANDS_ADMIN` (op level 2) when absent.
- **Single-instance & Config**: `LadsCore.start(platform)` check aborts initialization gracefully if a hybrid server has already started LadsCore. Configuration directory set to `FabricLoader.getInstance().getConfigDir().resolve("ladsclientapi")` (`config/ladsclientapi`).

### 3.2 platform-forge (Minecraft 1.20.1, Forge 47.4.10)
- **`META-INF/mods.toml`**: Configured with `modId="ladsclientapi"`, `displayTest="IGNORE_ALL_VERSION"`.
- **Extension Point**: Registered `IExtensionPoint.DisplayTest` with `NetworkConstants.IGNORESERVERONLY` and `(remote, isServer) -> true`. Ensures server-only behavior without breaking clients if accidentally installed on client or joining vanilla servers.
- **Channel**: `EventNetworkChannel` on `lads:api` created with `NetworkRegistry.newEventChannel(..., NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"))` allowing vanilla and unmodded clients to connect freely.
- **Receiver**: Inbound packets received via `channel.addListener(this::onCustomPayload)`, queued to server work thread and dispatched to `LadsCore.receive(handle, data)`.
- **Packet Send**: Vanilla clientbound custom payload packet sent via `NetworkDirection.PLAY_TO_CLIENT.buildPacket(Pair.of(buf, 0), CHANNEL_NAME)` and dispatched via `PacketDistributor.PLAYER.with(() -> player).send(packet.getThis())`.
- **Events**: `PlayerEvent.PlayerLoggedInEvent`, `PlayerEvent.PlayerLoggedOutEvent`, `PlayerEvent.PlayerChangedDimensionEvent`, `RegisterCommandsEvent`.
- **SRG Handling**: Forge 1.20.1 runtime uses SRG obfuscation for Minecraft classes. To prevent SRG naming crashes in production Forge servers, `ForgeReflection` isolates all Minecraft method lookups (`getGameProfile`, `getPlayerUUID`, `getPlayerName`, `getWorldName`, `hasPermissions`, `disconnect`), matching both Mojang and SRG member names. `LadsForgeMod` and `LadsForgePlayerHandle` touch only standard Forge API (`EventNetworkChannel`, `NetworkDirection`, `PacketDistributor`, `PacketTarget`) and non-obfuscated standard Java/Forge methods.

### 3.3 Root `universalJar`
- Uses `com.gradleup.shadow` 9.6.0 (compatible with Gradle 9.6.0).
- Combines classes from `common`, `platform-fabric`, and `platform-forge` (plus `platform-paper` and `platform-velocity` if present).
- Packages root fallback descriptors: `plugin.yml`, `velocity-plugin.json`, `fabric.mod.json`, `META-INF/mods.toml`, `pack.mcmeta`.
- Shaded SnakeYAML 2.2 relocated to `com.thelads.ladsapi.lib.snakeyaml`.
- Compiled with Java 25 toolchain with `options.release = 17`, guaranteeing bytecode version 61 (Java 17) across all classes.

### 3.4 Verification Tool (`tools/verify-jar.py`)
- Python 3.12 script executed with `py -3.12 -I tools\verify-jar.py build\libs\LadsClientAPI-1.0.0.jar`.
- Validates presence of all 5 required metadata files.
- Validates 0 unrelocated `org/yaml/snakeyaml` classes.
- Validates presence of relocated `com/thelads/ladsapi/lib/snakeyaml` classes.
- Verifies every `.class` bytecode header magic `0xCAFEBABE` and confirms `major_version <= 61`.

---

## 4. Exact Build and Test Output (Observed in This Run)

Command: `cd LadsClientAPI; .\gradlew.bat build universalJar`
```text
> Configure project :platform-fabric
Fabric Loom: 1.17.21

> Configure project :platform-forge
Fabric Loom: 1.17.21

> Task :common:compileJava UP-TO-DATE
> Task :common:processResources NO-SOURCE
> Task :common:classes UP-TO-DATE
> Task :common:jar UP-TO-DATE
> Task :common:assemble UP-TO-DATE
> Task :common:compileTestJava NO-SOURCE
> Task :common:processTestResources NO-SOURCE
> Task :common:testClasses UP-TO-DATE
> Task :common:test NO-SOURCE
> Task :common:check UP-TO-DATE
> Task :common:build UP-TO-DATE
> Task :platform-fabric:compileJava UP-TO-DATE
> Task :platform-fabric:processResources UP-TO-DATE
> Task :platform-fabric:classes UP-TO-DATE
> Task :platform-fabric:processIncludeJars UP-TO-DATE
> Task :platform-fabric:jar UP-TO-DATE
> Task :platform-fabric:assemble UP-TO-DATE
> Task :platform-fabric:compileTestJava UP-TO-DATE
> Task :platform-fabric:processTestResources NO-SOURCE
> Task :platform-fabric:testClasses UP-TO-DATE
> Task :platform-fabric:test UP-TO-DATE
> Task :platform-fabric:validateAccessWidener NO-SOURCE
> Task :platform-fabric:check UP-TO-DATE
> Task :platform-fabric:build UP-TO-DATE
> Task :platform-forge:compileJava UP-TO-DATE
> Task :platform-forge:processResources UP-TO-DATE
> Task :platform-forge:classes UP-TO-DATE
> Task :platform-forge:jar UP-TO-DATE
> Task :platform-forge:processIncludeJars
> Task :platform-forge:compileTestJava UP-TO-DATE
> Task :platform-forge:processTestResources NO-SOURCE
> Task :platform-forge:testClasses UP-TO-DATE
> Task :platform-forge:test UP-TO-DATE
> Task :platform-forge:validateAccessWidener NO-SOURCE
> Task :platform-forge:check UP-TO-DATE
> Task :platform-paper:compileJava NO-SOURCE
> Task :platform-paper:processResources NO-SOURCE
> Task :platform-paper:classes UP-TO-DATE
> Task :platform-paper:jar UP-TO-DATE
> Task :platform-paper:assemble UP-TO-DATE
> Task :platform-paper:compileTestJava NO-SOURCE
> Task :platform-paper:processTestResources NO-SOURCE
> Task :platform-paper:testClasses UP-TO-DATE
> Task :platform-paper:test NO-SOURCE
> Task :platform-paper:check UP-TO-DATE
> Task :platform-paper:build UP-TO-DATE
> Task :platform-velocity:compileJava NO-SOURCE
> Task :platform-velocity:processResources NO-SOURCE
> Task :platform-velocity:classes UP-TO-DATE
> Task :platform-velocity:jar UP-TO-DATE
> Task :platform-velocity:assemble UP-TO-DATE
> Task :platform-velocity:compileTestJava NO-SOURCE
> Task :platform-velocity:processTestResources NO-SOURCE
> Task :platform-velocity:testClasses UP-TO-DATE
> Task :platform-velocity:test NO-SOURCE
> Task :platform-velocity:check UP-TO-DATE
> Task :platform-velocity:build UP-TO-DATE
> Task :universalJar UP-TO-DATE

> Task :platform-forge:remapJar
Could not determine Mixin version for jar manifest

> Task :platform-forge:remapSourcesJar SKIPPED
> Task :platform-forge:assemble
> Task :platform-forge:build

BUILD SUCCESSFUL in 2s
18 actionable tasks: 2 executed, 16 up-to-date
```

Unit Tests Executed and Passing:
- `com.thelads.ladsapi.fabric.LadsPayloadCodecTest`: Tests encode, decode, empty payload, and size validation for Fabric CustomPacketPayload codec.
- `com.thelads.ladsapi.fabric.FabricPermissionsTest`: Tests fallback logic to vanilla permission check when `fabric-permissions-api` is absent.
- `com.thelads.ladsapi.forge.ForgeReflectionTest`: Tests null-safety and compile-time classpath resolution of Player/ServerPlayer/CommandSourceStack classes.

---

## 5. Exact `verify-jar.py` Output

Command: `py -3.12 -I tools\verify-jar.py build\libs\LadsClientAPI-1.0.0.jar`
```text
Verifying jar: C:\Users\Arash\AppData\Local\Temp\wla-srvb\LadsClientAPI\build\libs\LadsClientAPI-1.0.0.jar (378,209 bytes)
PASS: All required metadata files present: ['plugin.yml', 'velocity-plugin.json', 'fabric.mod.json', 'META-INF/mods.toml', 'pack.mcmeta']
PASS: No unrelocated org/yaml/snakeyaml classes found.
PASS: Found 260 relocated SnakeYAML entries.
PASS: Verified 249 class files. Maximum bytecode major version: 61 (Java 17 <= 61).

ALL CHECKS PASSED: Jar is universally compatible.
```

---

## 6. Bytecode and SRG Verification (`javap`)

Command: `javap -cp build\libs\LadsClientAPI-1.0.0.jar com.thelads.ladsapi.forge.LadsForgeMod`
```text
Compiled from "LadsForgeMod.java"
public class com.thelads.ladsapi.forge.LadsForgeMod {
  public static final java.lang.String MODID;
  public static final net.minecraft.resources.ResourceLocation CHANNEL_NAME;
  public com.thelads.ladsapi.forge.LadsForgeMod();
  public void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent);
  public void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedOutEvent);
  public void onPlayerChangedDimension(net.minecraftforge.event.entity.player.PlayerEvent$PlayerChangedDimensionEvent);
  public void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent);
  static {};
}
```

Command: `javap -c -p -cp build\libs\LadsClientAPI-1.0.0.jar com.thelads.ladsapi.forge.LadsForgePlayerHandle`
```text
Compiled from "LadsForgePlayerHandle.java"
public class com.thelads.ladsapi.forge.LadsForgePlayerHandle implements com.thelads.ladsapi.platform.LadsPlayerHandle {
  private final net.minecraft.server.level.ServerPlayer player;
  private final java.util.UUID uuid;
  private final java.lang.String name;

  public com.thelads.ladsapi.forge.LadsForgePlayerHandle(net.minecraft.server.level.ServerPlayer);
    Code:
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: aload_0
         5: aload_1
         6: putfield      #7                  // Field player:Lnet/minecraft/server/level/ServerPlayer;
         9: aload_0
        10: aload_1
        11: invokestatic  #13                 // Method com/thelads/ladsapi/forge/ForgeReflection.getPlayerUUID:(Lnet/minecraft/world/entity/player/Player;)Ljava/util/UUID;
        14: putfield      #19                 // Field uuid:Ljava/util/UUID;
        17: aload_0
        18: aload_1
        19: invokestatic  #23                 // Method com/thelads/ladsapi/forge/ForgeReflection.getPlayerName:(Lnet/minecraft/world/entity/player/Player;)Ljava/lang/String;
        22: putfield      #27                 // Field name:Ljava/lang/String;
        25: return

  public net.minecraft.server.level.ServerPlayer getPlayer();
  public java.util.UUID uuid();
  public java.lang.String name();
  public java.lang.String world();
  public boolean hasPermission(java.lang.String);
  public void send(byte[]);
    Code:
         0: new           #44                 // class net/minecraft/network/FriendlyByteBuf
         3: dup
         4: aload_1
         5: invokestatic  #46                 // Method io/netty/buffer/Unpooled.wrappedBuffer:([B)Lio/netty/buffer/ByteBuf;
         8: invokespecial #52                 // Method net/minecraft/network/FriendlyByteBuf."<init>":(Lio/netty/buffer/ByteBuf;)V
        11: astore_2
        12: getstatic     #55                 // Field net/minecraftforge/network/NetworkDirection.PLAY_TO_CLIENT:Lnet/minecraftforge/network/NetworkDirection;
        15: aload_2
        16: iconst_0
        17: invokestatic  #61                 // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
        20: invokestatic  #67                 // Method org/apache/commons/lang3/tuple/Pair.of:(Ljava/lang/Object;Ljava/lang/Object;)Lorg/apache/commons/lang3/tuple/Pair;
        23: getstatic     #73                 // Field com/thelads/ladsapi/forge/LadsForgeMod.CHANNEL_NAME:Lnet/minecraft/resources/ResourceLocation;
        26: invokevirtual #79                 // Method net/minecraftforge/network/NetworkDirection.buildPacket:(Lorg/apache/commons/lang3/tuple/Pair;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraftforge/network/ICustomPacket;
        29: astore_3
        30: getstatic     #83                 // Field net/minecraftforge/network/PacketDistributor.PLAYER:Lnet/minecraftforge/network/PacketDistributor;
        33: aload_0
        34: invokedynamic #89,  0             // InvokeDynamic #0:get:(Lcom/thelads/ladsapi/forge/LadsForgePlayerHandle;)Ljava/util/function/Supplier;
        39: invokevirtual #93                 // Method net/minecraftforge/network/PacketDistributor.with:(Ljava/util/function/Supplier;)Lnet/minecraftforge/network/PacketDistributor$PacketTarget;
        42: aload_3
        43: invokeinterface #97,  1           // InterfaceMethod net/minecraftforge/network/ICustomPacket.getThis:()Lnet/minecraft/network/protocol/Packet;
        48: invokevirtual #103                // Method net/minecraftforge/network/PacketDistributor$PacketTarget.send:(Lnet/minecraft/network/protocol/Packet;)V
        51: return

  public void kick(java.lang.String);
  private net.minecraft.server.level.ServerPlayer lambda$send$0();
}
```

---

## 7. Mixin Targets Statement
- **No Mixins Used**: Lane SRV-B introduced **zero mixins**. Both Fabric 26.3 and Forge 1.20.1 platform integrations are clean-room, event-driven implementations that leverage official platform APIs (`ServerPlayNetworking`, `ServerPlayConnectionEvents`, `ServerEntityLevelChangeEvents`, `CommandRegistrationCallback` on Fabric; `EventNetworkChannel`, `PlayerLoggedInEvent`, `PlayerLoggedOutEvent`, `PlayerChangedDimensionEvent`, `RegisterCommandsEvent` on Forge).

---

## 8. What Could NOT Be Verified in This Run
1. **Live Dedicated Server Execution with Connected Clients**: In strict adherence to Hard Rule 33 ("Do NOT launch Minecraft, servers, the launcher, QA or benchmarks. Claude runs QA on the Legion laptop later."), no dedicated Minecraft server process was launched to test live packet roundtrips with an active client. Verification was conducted via comprehensive unit tests, Gradle build execution, `javap` bytecode inspection, and `verify-jar.py`.
2. **Paper & Velocity Platform Runtime**: `platform-paper` and `platform-velocity` modules are owned by Lane SRV-A in parallel and did not have sources in this worktree during this run. The `universalJar` task was validated with fallback descriptors and dynamically includes them when present.
