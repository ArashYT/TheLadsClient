# Lane T189 Report: lads:api transport for 1.8.9

**Lane:** T189  
**Branch:** `agy/ladsapi-t189`  
**Base commit:** `e4a49715`  
**Commit:** `85de7291`  
**Worktree:** `C:\Users\Arash\AppData\Local\Temp\wla-t189`  

---

## 1. Summary of Changes

Implemented the `lads:api` transport adapter for Minecraft 1.8.9 (Forge 1.8.9, TheLadsCore `v1_8_9`):

1. **Receive (`NetworkManagerMixin` & `LadsApi189.receive`):**
   - Hooked `NetworkManager.channelRead0` (`@At("HEAD")`) where `S3FPacketCustomPayload` arrives on the Netty I/O thread.
   - When channel name equals `LadsApi.CHANNEL` (`"lads:api"`):
     - Validates payload readable byte length against `LadsApi.MAX_IN` (524,288 bytes).
     - Copies bytes using `buf.getBytes(buf.readerIndex(), payload)` without advancing `readerIndex`.
     - Dispatches payload to `LadsApiClient.receive(payload)` on the Minecraft client thread via `Minecraft.getMinecraft().addScheduledTask(...)`.
   - The packet continues downstream untouched. Vanilla Minecraft 1.8.9 ignores unrecognized channels, so no packet cancellation is required.

2. **Zero FML/REGISTER Leak (Anti-Ban Guarantee):**
   - Verified that nowhere in `TheLadsCore/v1_8_9` is `NetworkRegistry` (e.g. `NetworkRegistry.INSTANCE.newChannel` or `newSimpleChannel`) used.
   - FML's `REGISTER` channel handshake only registers channels declared via `NetworkRegistry`. Because `lads:api` is never registered with Forge/FML, the client sends zero channel registration packets (`REGISTER`), zero brand alterations, and zero outbound packets before receiving a valid server hello.

3. **Send (`LadsApi189.send` & Registration):**
   - Installed the transport sender at client init in `TheLadsCore189.init()` via `LadsApiClient.install(LadsApi189::send, "1.8.9")`.
   - Outbound packets are wrapped as `new C17PacketCustomPayload("lads:api", new PacketBuffer(Unpooled.wrappedBuffer(payload)))` and queued via `NetHandlerPlayClient.addToSendQueue(...)`.
   - Outbound size is strictly validated against `LadsApi.MAX_OUT` (32,000 bytes) and dropped if empty or exceeding `MAX_OUT`.

4. **Lifecycle Hooks (`NetHandlerPlayClientMixin`):**
   - `handleJoinGame`: Injected at `@At("TAIL")` to invoke `LadsApiClient.joinGame(0L)`. Runs on the client thread after the game and world controller are initialized.
   - `cleanup` and `onDisconnect`: Injected at `@At("HEAD")` (`ladsResetConnection`) to invoke `LadsApiClient.reset()`.
   - No hook on respawn (1.8.9 packets carry no hashed seed; respawn hook would wipe active server policies on death or dimension switch).

5. **Unit Tests (`LadsApi189Test`):**
   - Unit tests covering pure logic:
     - Exact channel matching ("lads:api" matches; null, empty, uppercase, subchannels, and other mod channels rejected).
     - Payload inbound size gating (0, negative, >524,288 rejected; 1..524,288 accepted).
     - Outbound size gating (null, 0, >32,000 rejected; 1..32,000 accepted).
     - Non-destructive `ByteBuf` read verification (readerIndex remains untouched after `buf.getBytes`).
     - Registration and transport invocation.

---

## 2. Javap Verification of Targets

All mixin targets verified against the MCP stable_22 mapped jar:
`C:\Users\Arash\.gradle\caches\essential-loom\1.8.9\de.oceanlabs.mcp.mcp_stable.1_8_9.22-1.8.9-forge-1.8.9-11.15.1.2318-1.8.9\minecraft-mapped.jar`

- `NetworkManager.channelRead0`:
  - Signature: `protected void channelRead0(io.netty.channel.ChannelHandlerContext, net.minecraft.network.Packet) throws java.lang.Exception;`
  - Descriptor: `(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V`
- `NetHandlerPlayClient.handleJoinGame`:
  - Signature: `public void handleJoinGame(net.minecraft.network.play.server.S01PacketJoinGame);`
  - Descriptor: `(Lnet/minecraft/network/play/server/S01PacketJoinGame;)V`
- `NetHandlerPlayClient.cleanup`:
  - Signature: `public void cleanup();`
  - Descriptor: `()V`
- `NetHandlerPlayClient.onDisconnect`:
  - Signature: `public void onDisconnect(net.minecraft.util.IChatComponent);`
  - Descriptor: `(Lnet/minecraft/util/IChatComponent;)V`
- `S3FPacketCustomPayload.getChannelName`:
  - Signature: `public java.lang.String getChannelName();`
  - Descriptor: `()Ljava/lang/String;`
- `S3FPacketCustomPayload.getBufferData`:
  - Signature: `public net.minecraft.network.PacketBuffer getBufferData();`
  - Descriptor: `()Lnet/minecraft/network/PacketBuffer;`
- `C17PacketCustomPayload.<init>`:
  - Signature: `public net.minecraft.network.play.client.C17PacketCustomPayload(java.lang.String, net.minecraft.network.PacketBuffer);`
  - Descriptor: `(Ljava/lang/String;Lnet/minecraft/network/PacketBuffer;)V`
- `Minecraft.addScheduledTask`:
  - Signature: `public com.google.common.util.concurrent.ListenableFuture<java.lang.Object> addScheduledTask(java.lang.Runnable);`
  - Descriptor: `(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;`

---

## 3. FML / NetworkRegistry Check (Code Citing)

Searched all java sources under `TheLadsCore/v1_8_9`:
- `NetworkRegistry`: 0 occurrences.
- `FMLNetworkEvent`: 0 occurrences.
- `SimpleNetworkWrapper`: 0 occurrences.
- `REGISTER`: 0 channel registrations.

FML registers channels exclusively via `NetworkRegistry.INSTANCE.newChannel(...)` / `newSimpleChannel(...)` (which adds them to FML's internal `channels` map queried by `FMLHandshakeMessage$ServerHello` / `ClientHello`). Because `TheLadsCore/v1_8_9` performs direct Netty / packet handling and does not call `NetworkRegistry`, Forge never generates or transmits any `REGISTER` payload containing `lads:api`.

---

## 4. Build & Test Results from THIS Run

### TheLadsCore `:common:jar` and `:common:test`
Command: `.\gradlew.bat :common:jar :common:test` in `TheLadsCore`
```
> Task :common:compileJava
Note: C:\Users\Arash\AppData\Local\Temp\wla-t189\TheLadsCore\common\src\main\java\com\thelads\core\config\ShareCode.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.

> Task :common:processResources
> Task :common:classes
> Task :common:jar

> Task :common:compileTestJava
Note: C:\Users\Arash\AppData\Local\Temp\wla-t189\TheLadsCore\common\src\test\java\com\thelads\core\client\qa\QaProgressTest.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.

> Task :common:processTestResources
> Task :common:testClasses
> Task :common:test

BUILD SUCCESSFUL in 1m 27s
6 actionable tasks: 6 executed
```

### TheLadsCore `v1_8_9` `build`
Command: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'; .\gradlew.bat build` in `TheLadsCore\v1_8_9`
```
> Task :downgradeCommon
> Task :compileJava
> Task :processResources
> Task :classes
> Task :jar
> Task :shadowJar
> Task :shadeJvmdgApi
> Task :remapJar
> Task :assemble
> Task :compileTestJava
> Task :processTestResources NO-SOURCE
> Task :testClasses
> Task :test
> Task :validateAccessWidener NO-SOURCE
> Task :check
> Task :build

BUILD SUCCESSFUL in 31s
9 actionable tasks: 9 executed
```

### Unit Test Execution
`TheLadsCore\v1_8_9\build\reports\tests\test\index.html`:
`5 tests, 0 failures, 0 ignored, 100% successful (duration: 0.053s)`

---

## 5. Git Diff Stat

```
LadsClientAPI/docs/lanes/T189-REPORT.md            | 160 ++++++++++++++++++++++
TheLadsCore/v1_8_9/build.gradle                    |   4 +
.../com/thelads/core/v1_8_9/TheLadsCore189.java    |   1 +
.../thelads/core/v1_8_9/feature/LadsApi189.java    |  86 ++++++++++++
.../v1_8_9/mixin/NetHandlerPlayClientMixin.java    |  10 +-
.../core/v1_8_9/mixin/NetworkManagerMixin.java     |   7 +-
.../core/v1_8_9/feature/LadsApi189Test.java        |  74 +++++++++++
7 files changed, 340 insertions(+), 2 deletions(-)
```

---

## 6. What Could NOT Be Done or Verified

- Minecraft client / live server runtime launch: In accordance with HARD RULES ("Do NOT launch Minecraft, servers, the launcher, QA or benchmarks. Claude runs QA on the Legion laptop later"), in-game runtime behavior was not verified via an active game client. All classes, method signatures, descriptors, and bytecode targets were verified statically using `javap` against the official MCP stable_22 mapped jar.

