# Standalone Lads Discord presence

The 26.2 `DiscordRPC` module now connects to the local Discord desktop app through the already bundled Discord RPC runtime. It samples Minecraft state on the game thread and runs initialization, callbacks, updates, reconnects and shutdown on a dedicated daemon worker. It does not use Prism or Microsoft authentication.

## Application setup and opt-in

The Lads project owner needs to create or identify **The Lads Client's own Discord application**, then copy its public **Application ID / Client ID** from the [Discord Developer Portal](https://discord.com/developers/applications). Discord's [official RPC library setup](https://github.com/discord/discord-rpc#basic-usage) requires that ID for initialization. This is an application ID, not a Discord user ID, bot token or client secret. No borrowed ID is included.

In Minecraft, open **RSHIFT → DiscordRPC**:

1. Paste the Lads application's ID into **Application ID**.
2. Enable the module and explicitly enable **Share activity**. Both are required before any native connection begins.
3. Keep the Discord desktop app running, signed in, with activity sharing enabled as desired in Discord.

The owner configures one application identity for the client; each player does not need a separate app registration. No bot token, OAuth secret, Microsoft account or uploaded art asset is required by this legacy local presence path. The displayed application name comes from the configured Discord application. Discord now recommends its [Social SDK for new projects](https://docs.discord.com/developers/topics/rpc#rpc-over-ipc); this implementation reuses the project's existing native binding and the documented IPC interface.

No Lads Discord application ID was found in the repository or checked local Lads configuration during this task. Read-only inspection found running Discord desktop processes and the `discord-ipc-0` Windows named pipe. That confirms local availability only: **no real Discord handshake or presence update was sent**. Live verification needs the Lads application's actual ID and explicit user opt-in to publishing test activity. Visible profile updates and clearing still need to be checked with that configuration.

## Privacy and behavior

- The module and **Share activity** both default off. An older saved `DiscordRPC` enabled flag cannot independently authorize the new integration.
- **Share server address** and **Share world name** default off. The old `Show Server IP` preference is deliberately not reused as permission to publish anything.
- Simple detail shows menu, singleplayer or multiplayer state and Minecraft version. Full detail can include explicitly selected world/server/dimension information. Minimal hides the gameplay context. No coordinates, Minecraft or Discord usernames, seeds, player UUIDs, party IDs or join secrets are sent.
- Ordinary changes are coalesced to at most one update per 15 seconds. Privacy changes clear the previous activity before submitting a filtered replacement. Disabling requests a clear and closes the IPC connection within the worker's next 500 ms cycle.
- Missing Discord, rejected IDs and native loading errors become module status text and use bounded retry backoff. They do not fall back to another application's ID or start Discord for the player.
- Minecraft shutdown requests a clear, closes the native connection, and stops the worker. A JVM shutdown hook provides a fallback. The legacy API has no per-update acknowledgement; “Presence submitted” describes the native call, not proof that Discord displayed it.

Presence data can be visible to other Discord users according to the player's Discord settings. See Discord's [Rich Presence overview](https://docs.discord.com/developers/platform/rich-presence).

## Validation

The service tests use an injected mock IPC transport, including simulated READY/disconnect/error callbacks, missing client, native-library failure, privacy changes, throttling, reconnect backoff and cleanup. A separate injected native-binding test verifies `Discord_Initialize`, callbacks, the exact presence fields, disabled auto-registration and clear-before-shutdown without contacting Discord. A real daemon-worker test verifies that a blocked mock connection does not block game-thread disable requests. Text bounds are checked in UTF-8 bytes rather than UTF-16 character count.

These tests verify service behavior and the binding contract, not a live Discord profile. No developer-portal registration or external publication was attempted.
