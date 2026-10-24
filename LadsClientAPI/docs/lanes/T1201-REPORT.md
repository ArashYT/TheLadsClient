# Lane T1201 Report: lads:api transport for Minecraft 1.20.1 (Fabric & Forge-via-Connector)

## Summary
- **Worktree**: `C:\Users\Arash\AppData\Local\Temp\wla-t1201`
- **Branch**: `agy/ladsapi-t1201`
- **Base Commit**: `e4a49715`
- **Status**: COMPLETE
- **Final Commit**: `396b5026b4d3f56ec560773d5a443a67d712f551`

### Git Diff Stat (`e4a49715..HEAD`):
```text
 LadsClientAPI/docs/lanes/T1201-REPORT.md           | 183 +++++++++++++++++++++
 .../core/v1_20_1/TheLadsCoreClient1201.java        |   1 +
 .../v1_20_1/mixin/LadsApiPacketListenerMixin.java  |  74 +++++++++
 .../core/v1_20_1/net/LadsApiTransport1201.java     |  89 ++++++++++
 .../main/resources/theladscore.v1_20_1.mixins.json |   3 +-
 5 files changed, 349 insertions(+), 1 deletion(-)
```

---

## 1. Custom Payload Interception (HEAD Mixin & Fabric API Preemption)

### 1.1 Mixin Target Verification
- **Class**: `net.minecraft.client.multiplayer.ClientPacketListener`
- **Method**: `handleCustomPayload(ClientboundCustomPayloadPacket packet)`
- **Descriptor**: `(Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;)V`
- **Verified on**: `C:\Users\Arash\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2\minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar` via `javap -s`.
- **Refmap Mapping (in compiled jar `v1_20_1-refmap.json`)**:
  `"handleCustomPayload": "Lnet/minecraft/class_634;method_11152(Lnet/minecraft/class_2658;)V"`

### 1.2 Fabric API 0.92.x Behavior & Priority Citation
- **Source Inspection**: Inspected `fabric-networking-api-v1-1.3.11+1802ada577-sources.jar` in Gradle cache (`net/fabricmc/fabric/mixin/networking/client/ClientPlayNetworkHandlerMixin.java`):
  ```java
  // We want to apply a bit earlier than other mods which may not use us in order to prevent refCount issues
  @Mixin(value = class_634.class, priority = 999)
  abstract class ClientPlayNetworkHandlerMixin implements NetworkHandlerExtensions {
      @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
      private void handleCustomPayload(class_2658 packet, CallbackInfo ci) {
          if (this.addon.handle(packet)) {
              ci.cancel();
          }
      }
  }
  ```
- **Buffer Release Behavior in Fabric Addon**:
  Inspected `net/fabricmc/fabric/impl/networking/client/ClientPlayNetworkAddon.java:76-85`:
  ```java
  public boolean handle(class_2658 packet) {
      class_2540 buf = packet.method_11458();
      try {
          return this.handle(packet.method_11456(), buf);
      } finally {
          buf.release();
      }
  }
  ```
  `ClientPlayNetworkAddon` unconditionally calls `buf.release()` in its `finally` block even when `this.handle` returns `false` (i.e. channel unhandled by Fabric receivers).
- **Our Preemption & Netty Safety**:
  We set `@Mixin(value = ClientPacketListener.class, priority = 500)` in `LadsApiPacketListenerMixin`. Because priority 500 < 999, our mixin runs before Fabric API. When `packet.getIdentifier()` equals `lads:api`, we copy the payload buffer up to `LadsApi.MAX_IN`, dispatch `LadsApiClient.receive` on the Minecraft client game thread (`mc.isSameThread() ? receive : mc.execute(receive)`), release the Netty buffer if `buf.refCnt() > 0`, and call `ci.cancel()`.
  This guarantees that Fabric API's handler is completely bypassed and never releases or drops `lads:api` packets.

---

## 2. Forge 1.20.1 & Sinytra Connector Compatibility Audit

### 2.1 Forge 1.20.1 Patch Analysis
- **Forge Patch**: `patches/minecraft/net/minecraft/client/multiplayer/ClientPacketListener.java.patch` in MinecraftForge `1.20.1` branch:
  ```diff
  @@ -1855,6 +_,7 @@
      }
   
      public void m_7413_(ClientboundCustomPayloadPacket p_105004_) {
  +      if (net.minecraftforge.network.NetworkHooks.onCustomPayload(p_105004_, this.f_104885_)) return;
         PacketUtils.m_131363_(p_105004_, this, this.f_104888_);
  ```
- **Forge `NetworkHooks.onCustomPayload` Implementation**:
  Source: `net/minecraftforge/network/NetworkHooks.java`:
  ```java
  public static boolean onCustomPayload(final ICustomPacket<?> packet, final Connection manager) {
      return NetworkRegistry.findTarget(packet.getName()).
              filter(ni->validateSideForProcessing(packet, ni, manager)).
              map(ni->ni.dispatch(packet.getDirection(), packet, manager)).orElse(Boolean.FALSE);
  }
  ```
- **Findings**:
  1. For channels not registered with Forge's `NetworkRegistry` (such as `lads:api`), `NetworkRegistry.findTarget(packet.getName())` returns `Optional.empty()`.
  2. `NetworkHooks.onCustomPayload` therefore evaluates to `false`.
  3. Forge's `if (NetworkHooks.onCustomPayload(...)) return;` statement evaluates to `if (false) return;` and does NOT swallow or drop the unknown payload.
  4. Furthermore, our Mixin injects at `@At("HEAD")`, which executes before any Forge method body instructions.
  5. Connector remaps `@Mixin(ClientPacketListener.class)` to Forge's SRG name `m_7413_`.
  6. Conclusion: No secondary hook is required; the single HEAD mixin cleanly intercepts `lads:api` on both Fabric and Forge-via-Connector.

---

## 3. Outbound Transport (`LadsApiClient.Transport`)

- **Class**: `com.thelads.core.v1_20_1.net.LadsApiTransport1201`
- **Installation**: `LadsApiClient.install(LadsApiTransport1201::send, "1.20.1")` called at client initialization via `TheLadsCoreClient1201.onInitializeClient()` and safeguarded by static classloading in `LadsApiPacketListenerMixin`.
- **Packet**: Vanilla `net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket` with `CHANNEL = new ResourceLocation("lads", "api")` and `FriendlyByteBuf(Unpooled.wrappedBuffer(payload))`.
- **Validation**:
  - `isValidPayload`: `payload != null && payload.length > 0 && payload.length <= LadsApi.MAX_OUT` (32,000 bytes).
  - Outbound packets are only transmitted if `mc.getConnection()` is active and connected.
- **Anti-Ban Compliance**:
  - Zero channel registration calls (`ClientPlayNetworking.registerGlobalReceiver` or Forge `NetworkRegistry` are never called).
  - Zero channel registration packets (`minecraft:register` or `REGISTER`) are sent.
  - Zero outbound packets sent until a server hello triggers client response via `LadsApiClient`.

---

## 4. Connection Lifecycle Hooks

Verified on `ClientPacketListener` in `minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar`:
1. **Login**:
   - Method: `handleLogin(ClientboundLoginPacket)`
   - Descriptor: `(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V`
   - Accessor: `packet.seed()` -> `long`
   - Action: `@Inject(method = "handleLogin", at = @At("TAIL"))` calls `LadsApiClient.joinGame(packet.seed())`.
   - Refmap: `"handleLogin": "Lnet/minecraft/class_634;method_11120(Lnet/minecraft/class_2678;)V"`
2. **Respawn**:
   - Method: `handleRespawn(ClientboundRespawnPacket)`
   - Descriptor: `(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V`
   - Accessor: `packet.getSeed()` -> `long`
   - Action: `@Inject(method = "handleRespawn", at = @At("TAIL"))` calls `LadsApiClient.respawn(packet.getSeed())`.
   - Refmap: `"handleRespawn": "Lnet/minecraft/class_634;method_11117(Lnet/minecraft/class_2724;)V"`
3. **Disconnect**:
   - Method: `onDisconnect(Component)`
   - Descriptor: `(Lnet/minecraft/network/chat/Component;)V`
   - Action: `@Inject(method = "onDisconnect", at = @At("HEAD"))` calls `LadsApiClient.reset()`.
   - Refmap: `"onDisconnect": "Lnet/minecraft/class_634;method_10839(Lnet/minecraft/class_2561;)V"`
   - Reinforced via Fabric's `ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LadsApiClient.reset())`.

---

## 5. Build and Test Verification

### 5.1 Exact Output from `.\gradlew.bat :common:cleanTest :common:test :v1_20_1:build`
```
> Configure project :v1_20_1
Fabric Loom: 1.17.21

> Configure project :v1_21_11
Fabric Loom: 1.17.21

> Configure project :v26_2
Fabric Loom: 1.17.21

> Configure project :v26_3
Fabric Loom: 1.17.21

> Task :common:cleanTest
> Task :common:compileJava UP-TO-DATE
> Task :common:processResources UP-TO-DATE
> Task :common:classes UP-TO-DATE
> Task :common:jar UP-TO-DATE
> Task :common:compileTestJava UP-TO-DATE
> Task :common:processTestResources UP-TO-DATE
> Task :common:testClasses UP-TO-DATE
> Task :common:test
> Task :v1_20_1:compileJava UP-TO-DATE
> Task :v1_20_1:processResources UP-TO-DATE
> Task :v1_20_1:classes UP-TO-DATE
> Task :v1_20_1:compileTestJava NO-SOURCE
> Task :v1_20_1:jar UP-TO-DATE
> Task :v1_20_1:processIncludeJars UP-TO-DATE
> Task :v1_20_1:remapJar UP-TO-DATE
> Task :v1_20_1:remapSourcesJar SKIPPED
> Task :v1_20_1:assemble UP-TO-DATE
> Task :v1_20_1:processTestResources NO-SOURCE
> Task :v1_20_1:testClasses UP-TO-DATE
> Task :v1_20_1:test NO-SOURCE
> Task :v1_20_1:validateAccessWidener UP-TO-DATE
> Task :v1_20_1:check UP-TO-DATE
> Task :v1_20_1:build UP-TO-DATE

BUILD SUCCESSFUL in 40s
13 actionable tasks: 2 executed, 11 up-to-date
```

### 5.2 Artifact Verification
- Generated jar: `TheLadsCore/v1_20_1/build/libs/v1_20_1-1.9.12-mc1.20.1.jar`
- Verified packaged classes:
  - `com/thelads/core/v1_20_1/net/LadsApiTransport1201.class`
  - `com/thelads/core/v1_20_1/mixin/LadsApiPacketListenerMixin.class`
  - `com/thelads/core/client/ladsapi/LadsApi.class`
  - `com/thelads/core/client/ladsapi/LadsApiClient.class`
- Refmap entries for `LadsApiPacketListenerMixin` verified inside `v1_20_1-refmap.json`.

---

## 6. What Was NOT Done / Verified
- **In-game live testing / launching Minecraft client or servers**: Prohibited by `AGY-LADSAPI-BUILD-RULES.md` ("Do NOT launch Minecraft, servers, the launcher, QA or benchmarks. Claude runs QA on the Legion laptop later.").
- **Network capture on live server**: Prohibited per rules; verification completed via bytecode analysis, javap, and official upstream source inspection.
- **Modifying files outside v1_20_1 transport scope**: Strictly avoided touching `common/`, `v26_3/`, or `v1_8_9/` per lane rules.
