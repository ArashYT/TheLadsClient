# Lane T26 Report: lads:api transport for 26.3 (TheLadsCore v26_3)

- Branch: `agy/ladsapi-t26`
- Base commit: `e4a49715`
- Commit: `e19c909065cdf1a3f844b19237219d5d8624799b`
- Diff stat (`git diff --stat e4a49715..HEAD`):
```text
 LadsClientAPI/docs/lanes/T26-REPORT.md             | 261 +++++++++++++++++++++
 .../thelads/core/v26_2/TheLadsCoreClient26.java    |   1 +
 .../thelads/core/v26_2/feature/LadsApiPayload.java |  37 +++
 .../thelads/core/v26_2/feature/NativeLadsApi.java  |  53 +++++
 .../core/v26_2/mixin/LadsApiDisconnectMixin.java   |  20 ++
 .../v26_2/mixin/LadsApiPacketListenerMixin.java    |  48 ++++
 .../main/resources/theladscore.v26_2.mixins.json   |   4 +-
 .../core/v26_2/feature/LadsApiCodecChecks.java     |  94 ++++++++
 8 files changed, 517 insertions(+), 1 deletion(-)
```

---

## 1. Fabric API `minecraft:register` Channel Announcement Audit

### Question
Does Fabric API put only global/registered receivers in `minecraft:register`, or also registered payload types from `PayloadTypeRegistry`?

### Evidence & File + Line Citations
Inspected from Gradle cache artifact:
`fabric-networking-api-v1-6.3.8+fcdff87f5d.jar` (bundled in `fabric-api-0.161.0+26.3.jar`):

1. **Initial Channel Registration Packet Emission**:
   In `net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon`:
   - `onServerReady()` (line 56-66):
     ```java
     // The client cannot send any packets, including `minecraft:register` until after GameJoinS2CPacket is received.
     this.sendInitialChannelRegistrationPacket();
     super.onServerReady();
     ```
   - Inherited implementation in `net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon`:
     ```java
     // lines 113-119:
     protected void sendInitialChannelRegistrationPacket() {
         final RegistrationPayload payload = createRegistrationPayload(RegistrationPayload.REGISTER, this.getReceivableChannels());
         if (payload != null) {
             this.sendPacket(payload);
         }
     }
     ```

2. **Source of Receivable Channels**:
   In `net.fabricmc.fabric.impl.networking.AbstractNetworkAddon`:
   - `getReceivableChannels()` (lines 141-150):
     ```java
     public Set<Identifier> getReceivableChannels() {
         Lock lock = this.lock.readLock();
         lock.lock();
         try {
             return new HashSet<>(this.handlers.keySet());
         } finally {
             lock.unlock();
         }
     }
     ```
   - `this.handlers` stores registered payload *handlers* (receivers), NOT registered payload types.
   - At connection initialization, `lateInit()` (`AbstractNetworkAddon.java:54`) calls `this.receiver.startSession(this)`.
   - In `net.fabricmc.fabric.impl.networking.GlobalReceiverRegistry` (`startSession`, line 155):
     `addon.registerChannels(handlers);` where `handlers` contains ONLY handlers registered via `ClientPlayNetworking.registerGlobalReceiver(Identifier, PlayPayloadHandler)` (`GlobalReceiverRegistry.java:75-99`).

3. **Incremental Registration on Dynamic Receiver Addition**:
   In `net.fabricmc.fabric.impl.networking.client.ClientCommonNetworkAddon`:
   - `handleRegistration(Identifier channelName)` (lines 46-55):
     ```java
     if (this.isServerReady) {
         final RegistrationPayload payload = this.createRegistrationPayload(RegistrationPayload.REGISTER, Collections.singleton(channelName));
         if (payload != null) {
             this.sendPacket(payload);
         }
     }
     ```
   - This is triggered only when `registerChannel` is invoked on the addon.

4. **PayloadTypeRegistry Isolation**:
   In `net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl`:
   - `register(CustomPacketPayload.Type<T> id, StreamCodec<? super B, T> codec)` (lines 66-78):
     ```java
     final CustomPacketPayload.TypeAndCodec<B, T> payloadType = new CustomPacketPayload.TypeAndCodec<>(id, codec.cast());
     if (packetTypes.containsKey(id.id())) {
         throw new IllegalArgumentException("Packet type " + id + " is already registered!");
     }
     packetTypes.put(id.id(), payloadType);
     return payloadType;
     ```
   - `PayloadTypeRegistryImpl` maintains an internal map `packetTypes` used solely for packet encoding and decoding (`CustomPayloadStreamCodecMixin`).
   - It has NO reference to `GlobalReceiverRegistry`, NO reference to `AbstractNetworkAddon.handlers`, and NEVER triggers `handleRegistration` or channel registration packets.

### Conclusion
Registering the payload type in `PayloadTypeRegistry.clientboundPlay()` and `PayloadTypeRegistry.serverboundPlay()` decodes and encodes `lads:api` packets without Fabric EVER putting `lads:api` into `minecraft:register`. As long as `ClientPlayNetworking.registerGlobalReceiver` is NOT called, the client never announces the channel.

---

## 2. Payload Registration & Codec

- Class: `com.thelads.core.v26_2.feature.LadsApiPayload`
- Channel Identifier: `Identifier.fromNamespaceAndPath("lads", "api")` -> `lads:api`
- Codec: `StreamCodec<FriendlyByteBuf, LadsApiPayload> CODEC`
  - Encoder: writes raw payload bytes (`buf.writeBytes(payload.data)`)
  - Decoder: checks `buf.readableBytes() <= LadsApi.MAX_IN` (524,288 bytes). If readable bytes exceed `LadsApi.MAX_IN`, throws `IllegalArgumentException`. Reads exactly remaining bytes into `byte[]`.
- Registrations in `NativeLadsApi.initialize()`:
  - `PayloadTypeRegistry.clientboundPlay().register(LadsApiPayload.TYPE, LadsApiPayload.CODEC);`
  - `PayloadTypeRegistry.serverboundPlay().register(LadsApiPayload.TYPE, LadsApiPayload.CODEC);`

---

## 3. Custom Payload Receive Hook

### Implementation
- Target class: `net.minecraft.client.multiplayer.ClientPacketListener`
- Method: `handleCustomPayload(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V`
- Injection point: `@At("HEAD")`, `cancellable = true`
- Logic:
  ```java
  if (payload instanceof LadsApiPayload lads) {
      LadsApiClient.receive(lads.data());
      ci.cancel();
  }
  ```
- Because no Fabric global receiver was registered for `lads:api`, Fabric's `ClientCommonPacketListenerImplMixin.onCustomPayload` leaves the packet unhandled (`addon.handle(payload)` returns `false`). The packet continues to vanilla's `ClientCommonPacketListenerImpl.handleCustomPayload(ClientboundCustomPayloadPacket)`.
- Vanilla runs `PacketUtils.ensureRunningOnSameThread` (guaranteeing game/render thread execution), confirms it is not `BrandPayload`, and delegates to `ClientPacketListener.handleCustomPayload(CustomPacketPayload)`.
- Our `@At("HEAD")` mixin catches `LadsApiPayload`, forwards `lads.data()` to `LadsApiClient.receive(byte[])`, and cancels so vanilla's fallback `handleUnknownCustomPayload` (which logs an "Unknown custom packet payload" warning) is suppressed.

### javap Verification
Verified on `C:\Users\Arash\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar`:
```
public void handleCustomPayload(net.minecraft.network.protocol.common.custom.CustomPacketPayload);
  descriptor: (Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V
```

---

## 4. Send Path & Channel Check Audit

### Question
Does sending a packet require the server to have announced the channel? Does `ClientPlayNetworking.send` check?

### Evidence & Code Citation
In `net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking`:
- Lines 237-248:
  ```java
  public static void send(CustomPacketPayload payload) {
      Objects.requireNonNull(payload, "Payload cannot be null");
      Objects.requireNonNull(payload.getId(), "CustomPayload#getId() cannot return null for payload class: " + payload.getClass());

      // You cant send without a client player, so this is fine
      if (Minecraft.getInstance().getConnection() != null) {
          Minecraft.getInstance().getConnection().send(createC2SPacket(payload));
          return;
      }

      throw new IllegalStateException("Cannot send packets when not in game!");
  }
  ```
- `canSend(Identifier)` (lines 184-191) checks `sendableChannels.contains(channelName)`, but `send(CustomPacketPayload)` does NOT call `canSend`.
- In `NativeLadsApi.send(byte[])`, we send via direct raw connection call:
  ```java
  ClientPacketListener connection = Minecraft.getInstance().getConnection();
  if (connection == null) return;
  connection.send(new ServerboundCustomPayloadPacket(new LadsApiPayload(payload)));
  ```
- `ClientCommonPacketListenerImpl.send(Packet<?>)` directly puts the packet onto `Connection.send(...)`.

---

## 5. Lifecycle Mixin Targets (javap-verified)

All targets verified on `C:\Users\Arash\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar`:

1. **Login Packet**:
   - Target: `net.minecraft.client.multiplayer.ClientPacketListener`
   - Method: `handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V`
   - Descriptor: `(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V`
   - Injection: `@At("TAIL")`
   - Seed extraction: `packet.commonPlayerSpawnInfo().seed()`:
     - `ClientboundLoginPacket.commonPlayerSpawnInfo()` -> descriptor `()Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;`
     - `CommonPlayerSpawnInfo.seed()` -> descriptor `()J`
   - Handler: `LadsApiClient.joinGame(packet.commonPlayerSpawnInfo().seed());`

2. **Respawn Packet**:
   - Target: `net.minecraft.client.multiplayer.ClientPacketListener`
   - Method: `handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V`
   - Descriptor: `(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V`
   - Injection: `@At("TAIL")`
   - Seed extraction: `packet.commonPlayerSpawnInfo().seed()`:
     - `ClientboundRespawnPacket.commonPlayerSpawnInfo()` -> descriptor `()Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;`
     - `CommonPlayerSpawnInfo.seed()` -> descriptor `()J`
   - Handler: `LadsApiClient.respawn(packet.commonPlayerSpawnInfo().seed());`

3. **Start of Configuration Phase**:
   - Target: `net.minecraft.client.multiplayer.ClientPacketListener`
   - Method: `handleConfigurationStart(Lnet/minecraft/network/protocol/game/ClientboundStartConfigurationPacket;)V`
   - Descriptor: `(Lnet/minecraft/network/protocol/game/ClientboundStartConfigurationPacket;)V`
   - Injection: `@At("HEAD")`
   - Handler: `LadsApiClient.reset();`

4. **Disconnect**:
   - Target: `net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl`
   - Method: `onDisconnect(Lnet/minecraft/network/DisconnectionDetails;)V`
   - Descriptor: `(Lnet/minecraft/network/DisconnectionDetails;)V`
   - Injection: `@At("HEAD")`
   - Handler: `LadsApiClient.reset();`
   - Additionally backed up by Fabric event: `ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LadsApiClient.reset());`

---

## 6. Unit Testing

- Test class: `com.thelads.core.v26_2.feature.LadsApiCodecChecks`
- Runs standalone without Minecraft engine or server launch.
- Verifications executed:
  1. Channel identifier matches `"lads:api"`, `payload.type()` matches `LadsApiPayload.TYPE`.
  2. Normal JSON payload round trip (`{"t":"hello","v":1,"server":"Lads SMP"}`).
  3. Empty byte array round trip (`new byte[0]`).
  4. Boundary payload round trip at maximum permitted size (`LadsApi.MAX_IN` = 524,288 bytes).
  5. Oversized payload rejection (`LadsApi.MAX_IN + 1` bytes throws `IllegalArgumentException` with message containing "exceeds max size").
- Execution result:
  ```text
  PASS 6 lads:api codec checks: round-trip, empty, max bound, and oversize rejection
  ```

---

## 7. Build Execution Results

Exact lines copied from run in `TheLadsCore`:
```text
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
> Task :v26_3:processResources UP-TO-DATE
> Task :v26_3:classes UP-TO-DATE
> Task :v26_3:processIncludeJars UP-TO-DATE
> Task :v26_3:jar UP-TO-DATE
> Task :v26_3:assemble UP-TO-DATE
> Task :v26_3:compileTestJava UP-TO-DATE
> Task :v26_3:processTestResources NO-SOURCE
> Task :v26_3:testClasses UP-TO-DATE
> Task :v26_3:test UP-TO-DATE
> Task :v26_3:validateAccessWidener UP-TO-DATE
> Task :v26_3:verifyJadePackaging
> Task :v26_3:check
> Task :v26_3:build

BUILD SUCCESSFUL in 4s
15 actionable tasks: 1 executed, 14 up-to-date
```

Initial execution from clean:
```text
BUILD SUCCESSFUL in 41s
15 actionable tasks: 10 executed, 5 up-to-date
```

---

## 8. What Could NOT Be Done or Verified

1. Did not launch Minecraft client, multiplayer server, proxy, launcher, or QA benchmarks (per hard rule 33: "Do NOT launch Minecraft, servers, the launcher, QA or benchmarks. Claude runs QA on the Legion laptop later.").
2. In-game network transmission to a live server was therefore not tested end-to-end; verified via static bytecode examination, javap descriptors, and standalone headless codec unit test.
