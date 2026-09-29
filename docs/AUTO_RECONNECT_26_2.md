# Native AutoReconnect — 26.2

## 2026-09-10 source integration and validation

Reference: AutoReconnect **3.103.0+26.2-fabric**, project `PRy8Khga`, version
`vHDagTHf`, exact upstream tag commit
`2b61223824f38d8bdbef697ed28a012acf9b7409` at
<https://github.com/TerminalMC/AutoReconnect>. The tagged source was inspected,
including configuration, all three reconnect strategies, screen lifecycle,
commands, reason filters and post-reconnect messaging. The reference checkout
is preserved under `artifacts/verification/native-mods-26.2/autoreconnect-reference-3.103.0`.

The native replacement is compiled into Lads Core and has no runtime dependency
on the external AutoReconnect classes. It registers only when `autoreconnectrf`
is absent. The production 26.2 manifest retired the upstream entry after the
41-check transformed runtime probe passed. Enable AutoReconnect in the Lads menu
to use its native engine; existing Lads module preferences are
not silently enabled.

| Reference feature | Lads native implementation |
|---|---|
| Per-attempt delay sequence `[3,10,30,60]` | Native list editor; empty disables automatic attempts; finite sequence gives the attempt limit |
| Infinite attempts | Repeat Last Delay repeats only the final configured delay |
| Retry initial failures | Separate setting, off by default |
| Reason key substring / localized regex matching | Both editable lists; Except Matches / Only Matches modes; nested translatable reason keys are inspected |
| Normal server reconnect | Captured attempted address, copied ServerData and transfer state; never writes the saved server list |
| Local-world reconnect | Actual world folder ID; verifies it still exists; uses vanilla world-opening flow and world-list return parent |
| Realms reconnect | Copied Realm metadata; invokes vanilla GetServerDetailsTask and Realm connection screens |
| Manual reconnect / countdown / cancel | Actual disconnect-screen widgets; first Escape cancels countdown, second returns to parent; no background scheduler races |
| Main-menu / identity changes | Clear countdown, target and queued actions; ordinary manual disconnect cannot trigger a retry after return to menus |
| Context-specific post-reconnect messages | Separate staged profile editor; server address, Realm name or world folder; exact or whole-regex ID matching; multiple matching profiles |
| Delay before and between messages | Connection/account-bound client-thread queue; positive fractional seconds; deterministic ordering |
| Plain chat / signed or unattended commands | Native packet-listener methods, only after successful automatic reconnect and explicit configuration plus global/profile activation |
| Client command settings / diagnostic disconnect | `/ladsreconnect` and compatibility alias `/autoreconnectrf`; explicit `disconnect` subcommand retains the reference diagnostic workflow |
| Existing configuration | Read-only import of `autoreconnectrf.json` into `theladscore/reconnect.json`; atomic native saves; source file remains byte-for-byte unchanged |

Intentional boundaries:

- Automatic attempts never retry recognized login/session/authentication,
  code-of-conduct consent, invalid-public-key, or transfer-handoff reasons, even
  if a user filter would match. No credential refresh or login automation runs.
- Global reconnect actions default off; every new and imported profile defaults
  disabled. Import preserves message contents without executing them. A user
  must explicitly configure and enable actions in Lads. Disabling the setting,
  disconnecting, changing accounts or editing configuration clears queued work.
- Config limits are 100 retry delays (1–86,400 seconds each), 128 entries per
  reason list, 64 action profiles, 100 messages per profile, and 4 MiB per native
  config file. Action delay is 0.1–3,600 seconds; vanilla chat/command length
  limits apply. Invalid oversized messages are disabled, never truncated into
  a different command. Original imported files remain available in full.
- Realms and local-world retry implementations use verified 26.2 APIs. A
  successful authenticated remote reconnect, Realms reconnect, local-world
  reopen, and actual sent post-reconnect message are **not claimed** by the
  isolated tests below. The QA harness never opens a connection/world or sends
  chat/actions; it uses a counter as the reconnect target and local mock sinks.

Validation at this source checkpoint:

- `:v26_2:compileJava` passed with all reconnect helpers/editors/mixins.
- `:common:test --tests com.thelads.core.client.ReconnectBehaviorTest` passed
  **18 tests**, including finite/infinite timing, repeated ticks, cancellation,
  negative/wrapping monotonic clocks, reason matching, protected auth reasons,
  exact/regex context matching, connection/account separation, action disabling,
  message length behavior, deterministic queue ordering, and ActionOption's
  safe non-serialized callbacks.
- `NativeReconnectProbe` is wired for `-Dthelads.verifyIntegrations=true` in the
  isolated artifacts/verification game directory. It exercises transformed
  disconnect controls/Escape/resize, mock dispatch, actual editor callbacks,
  imported-config preservation, native disk round trips, and safe malformed
  inputs. Both [native world checkpoints](NATIVE_RUNTIME_CHECKPOINT_26_2.md)
  recorded `Lads native reconnect probe END: 41 checks passed, 0 failed`.

License and source: the capture/layout mixin integration adapts LGPL-3.0-only
upstream hooks, with original author notices retained. Exact license, incorporated
GPL terms and attribution are in `assets/theladscore/licenses`; corresponding
modified/native helper source and mixin resources are embedded in
`META-INF/lads-sources/reconnect`. The implementation uses its own timing,
configuration and GUI code rather than shipping a concealed upstream mod JAR.
