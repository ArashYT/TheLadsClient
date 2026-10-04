# AutoReconnect and the other multiplayer remakes (1.7.0)

1.7.0 replaced the earlier AutoReconnect port, whose screen and connection hooks were adapted from the
LGPL AutoReconnect mod, with Lads code written from the feature description only (the mod's Modrinth page:
reconnect after an unintended disconnect, by default after 3, 10, 30 and 60 seconds, configurable, with
messages or commands after an automatic reconnect). No upstream source, class or file was used. The LGPL
notice, its licence copy and the `META-INF/lads-sources/reconnect` source bundle were removed with it.

## AutoReconnect (module `AutoReconnect`, 1.8.9, 26.2, 26.3)

- `common` `ReconnectSession` is the whole state machine: the target (server address or world folder), the
  account it was joined with, the countdown (`ReconnectPlan`), the reason filter (`ReconnectFilters`) and the
  join actions (`ReconnectActions`). Unit tests: `ReconnectSessionTest`, `ReconnectBehaviorTest`.
- 26.x (`NativeReconnect`): servers are remembered in `ConnectScreen.startConnecting` (server list, direct
  connect, quick play, transfers); local worlds on Fabric's join event. Fabric's screen events add Reconnect
  and Cancel reconnect under the disconnect screen's last button; Escape cancels a running countdown.
- 1.8.9 (`AutoReconnect189`): servers as their `GuiConnecting` opens, local worlds once joined, buttons and
  Escape through Forge's screen events. Realms do not accept 1.8.9; its chat is unsigned.
- Realms on 26.x: `RealmsConnect.connect` (ReconnectRealmsMixin) remembers the Realm by name; a retry joins it again
  through `RealmsMainScreen.play`, as the Realms screen does (it wakes the Realm and asks for its current address).
- Kicks and bans never count down by default: the reason key list skips kicks, bans, whitelist, duplicate
  login, outdated client/server, spam and idle kicks, and the default text patterns skip reasons containing
  "banned", "kicked" or "white-list" (servers and 1.8.9 send most kicks as plain text). Login, session,
  consent and transfer reasons never retry. Timeouts, lost connections, full or closed servers do retry.
- Settings keep their module options (Retry Initial Failures, Repeat Last Delay, Reason Filter, Enable Reconnect
  Actions, Match Action IDs as Regex, Sign Configured Commands) and the lists in
  `config/theladscore/reconnect.json`. 1.6.0 files load (old key names are read) and are saved with the new
  names. The import of the external mod's own `autoreconnectrf.json` and the `/ladsreconnect` command were
  dropped.
- Stands down when `autoreconnectrf` is loaded.

## IgnorePacketErrors (module, on by default; 1.8.9, 26.2, 26.3)

Replaces Network Protocol Disconnect (`netprodis`). A play packet from the server that can't be decoded is
dropped with the rest of its frame (`PacketDecodeGuardMixin`, 1.8.9 `MessageDeserializerMixin`), and a play
packet whose handler throws is logged and skipped (`PacketErrorGuardMixin`, 1.8.9 `NetworkManagerMixin` for
handlers on the network thread; 1.8.9 already only logs handler errors on the game thread). Timeouts, closed
or reset connections, kicks, frame/decompression errors and any `Error` still disconnect
(`PacketErrorPolicy`, unit-tested). The first three skips log a stack trace, later ones one line. The module
description warns that a skipped packet can leave the world out of sync. Stands down when `netprodis` is loaded.

## Chat signing (Chat > Hide Signing Indicators, 26.2 and 26.3)

Not a module. On by default and independent of the Chat module's own switch: chat lines show no signing
indicator or "not secure" icon, and the "Chat messages can't be verified" toast is dropped. Signatures and
reports are untouched. Stands down when `chatsigninghider` is loaded. 1.8.9 chat has no signing.

## Ctrl+R refreshes the server list (1.8.9, 26.2, 26.3)

Not a module. Ctrl+R on the multiplayer screen runs the screen's own refresh (as its Refresh button and F5),
never while a text field has focus (26.x; 1.8.9's list has none). 26.3 matches the R by keyboard layout.

## QA

- 26.x title probe (`NativeReconnectProbe`, `-Dthelads.verifyIntegrations`): disconnect screen buttons,
  countdown, Escape, kick reasons, Cancel and the delays editor with a mock target.
- 26.x in-world capture (`ServerFeaturesCapture`, `LADS_VERIFY_CAPTURE_SERVER=1` with `LADS_VERIFY_AUTO_WORLD=1`)
  and the 1.8.9 self-test (`ProbeServer170`): an undecodable frame through the real decoder, a failing
  packet through the real connection while staying connected, then with IgnorePacketErrors off the same
  packet disconnects the QA world and AutoReconnect counts down and reopens it; a refused local port is
  re-dialled twice and stops at the retry limit; Ctrl+R rebuilds the server list. Evidence for 1.7.0 is in
  `artifacts/1.7.0/remake/server/`.
