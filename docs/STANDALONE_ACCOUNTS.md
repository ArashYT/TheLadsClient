# Standalone Lads accounts

The Lads launcher handles its own accounts and launches Minecraft directly. Its account management follows the familiar Prism workflow: add a Microsoft account, choose a default, refresh its session, sign in again when needed, and remove accounts. The client is not installed inside Prism and does not start Prism or import its credentials.

- **Default** chooses the account used by this launcher. The Home account selector can choose an account for one launch without replacing the default.
- **Refresh** renews Microsoft/Xbox/Minecraft credentials and verifies the Minecraft Java license. If Microsoft requires another sign-in, the same-account sign-in dialog opens. Network failures do not open an unrelated browser flow. Right-click Refresh for **Sign in again…**.
- **Add local development account** creates an explicitly offline identity for singleplayer testing. A failed Microsoft login never falls back to an offline identity.
- **Sign out Microsoft** removes this launcher's Microsoft sessions; local accounts remain. Removing the default selects one remaining account consistently.

Microsoft account credentials remain in this launcher's protected cache. Only nonsecret profile information is exported to the game for account display. The actual verified Minecraft session is passed to the launched JVM.

## Current development limit

The current local setup does not contain a Microsoft application ID for The Lads Client. The device-code flow requires the application's own registered `client_id`; reproducing Prism's account workflow does not supply one. The launcher owner configures it once for the application, rather than requiring each player to register an app. See [Microsoft login setup](MICROSOFT_LOGIN_SETUP.md) for the existing setup instructions. Offline development remains available while that separate setup is deferred.

This implementation uses MSAL device-code authentication, Xbox user authentication, XSTS authorization and Minecraft token/profile/license verification through the existing CmlLib/XboxAuthNet backend. The [Microsoft device-code specification](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code) requires an app registration; [Prism's API settings](https://prismlauncher.org/wiki/help-pages/apis/) likewise expose an application-specific Microsoft client ID.

## Validation

The launcher suite includes persistent default-account selection/removal, forced Microsoft token refresh through the real backend adapter using mocked service responses, same-identity enforcement after reauthentication, cancellation, error handling, encrypted cache persistence, license validation and credential-free game exports. Those tests do not establish a live online session. Live standalone Microsoft authentication remains unverified until the Lads application registration is configured.
