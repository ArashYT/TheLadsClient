# Microsoft login setup for The Lads Client

The launcher owner needs one Microsoft application registration for The Lads Client. Players then sign in with their own Microsoft accounts. The public application ID is safe to put in the launcher. Do not put a client secret, password, or refresh token in its settings.

## Before registering: an accessible directory

Signing into the Azure portal with a personal Microsoft account does not by itself create a developer directory. If **Manage tenants** reports **No tenants found**, or Entra reports that the account is not a member of **Microsoft Services**, this is a directory-access issue, not a Windows password issue.

Use an existing directory where your account can register applications, or finish [Azure account setup](https://azure.microsoft.com/en-us/pricing/purchase-options/azure-account). Microsoft documents that initial Azure account creation provides a tenant. Free-account signup can require phone/card verification and agreement to Microsoft's terms; the account owner must supply those details. Creating additional workforce tenants has separate eligibility restrictions. [Microsoft's tenant prerequisites](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-create-new-tenant).

Once the directory is accessible, complete this registration:

1. Open the [Microsoft Entra admin center](https://entra.microsoft.com/). In **App registrations**, choose **New registration**. Name it **The Lads Client** and choose an account type that includes **personal Microsoft accounts**. The launcher uses Microsoft's `consumers` authority, so a work/school-only app is not suitable. If you cannot create applications in your directory, its administrator must enable that access. [Microsoft account-type documentation](https://learn.microsoft.com/en-us/entra/identity-platform/supported-accounts-validation).
2. Under **Authentication**, add the **Mobile and desktop applications** platform. Add `http://localhost` for a possible system-browser flow. Under **Advanced settings**, set **Allow public client flows** to **Yes** and save. The implemented login uses device code. Do not create a client secret for this desktop client. [Microsoft desktop configuration](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-configuration).
3. Copy **Application (client) ID** from the app's Overview page. It looks like a GUID. Do not copy the directory/tenant ID or object ID.
4. Request access to the Minecraft APIs using [Microsoft's Minecraft application review form](https://aka.ms/mce-reviewappid). Provide your own application ID and the requested launcher details. App registration, user consent, and Minecraft API approval are separate. The form observed on 27 September 2026 says reviews occur weekly; this does not guarantee an approval date or acceptance. [Launcher library's approval guidance](https://minecraft-launcher-lib.readthedocs.io/en/stable/tutorial/microsoft_login.html#apply-for-permission).
5. Current source builds include The Lads Client's registered application ID by default. Fresh settings and older missing/null/blank IDs use it; individual players do not register separate applications. To use a custom registration, open **Settings → Microsoft sign-in**, paste its application ID, and click **Save Settings**. Clearing that field restores the Lads default. Explicit custom IDs are preserved, and invalid nonblank IDs still fail validation. Settings are saved under `%APPDATA%\.theladsclient\settings.json`, or the `THELADS_DIR` override if set.
6. Open **Accounts → Add Microsoft Account**. Copy the displayed code, open Microsoft's verification page, and complete sign-in yourself. The launcher requests Xbox access, obtains the Xbox/XSTS and Minecraft session, and checks the Java profile and license. [Microsoft device code flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code).

## Passkeys

Passkeys belong to your Microsoft account and are created on Microsoft's site. Open [Advanced security options](https://account.live.com/proofs/manage), complete Microsoft's identity verification, choose **Add a new way to sign in or verify**, and follow the passkey option. Select your device or security key and personally complete the Windows Hello, fingerprint, face, or device prompt. The available labels and storage choices depend on your device. [Microsoft's passkey instructions](https://support.microsoft.com/en-us/accounts-billing/security/create-save-passkey).

Afterward, choose the passkey sign-in option on Microsoft's verification page when adding the account in the launcher. The launcher does not need your Windows PIN or the private passkey. A passkey does not replace the Lads application registration or Minecraft API approval.

## Final account verification

After Microsoft approves the app, verify this sequence with an account that can play Java Edition:

- Add the account and confirm the Minecraft username, not the Microsoft email, appears.
- Close and reopen the launcher. Confirm that the account remains listed.
- Launch 1.21.11, then 26.2. Confirm the correct username and version in each game.
- Join a server that requires authenticated accounts. Verify joining again after restarting the launcher and after token refresh.
- Cancel a new sign-in and retry. Remove an account and confirm it stays removed after restarting.

Offline accounts can play local worlds and servers that allow offline identities. They do not authenticate to Microsoft, Realms, or servers that require online accounts. An expired online session never silently becomes an offline account.

In-game account selection applies to the **next launch**. Exit the game and relaunch from the launcher to establish the new account's complete session. The current Minecraft process retains its original identity.

The previous code used Prism Launcher's application ID. The updated launcher has no borrowed default. [Prism's source configuration](https://github.com/PrismLauncher/PrismLauncher/blob/develop/CMakeLists.txt) identifies that ID and asks rebranded builds to replace its API keys.

## Troubleshooting

- **Azure free signup cannot continue:** if Step 1's verification widget is blank or says "Something went wrong. Please reload the challenge to try again." while **Next** stays disabled, record the visible message and attempts made. If the issue persists, use [Microsoft Azure's contact options](https://azure.microsoft.com/en-us/contact/) for signup assistance. This symptom alone does not establish its cause or a service outage.
- **Application ID missing or invalid:** configure the application (client) ID in Settings.
- **Microsoft rejects the registration:** check personal-account support and public client flows.
- **Microsoft sign-in succeeds but Minecraft verification fails:** check Minecraft API approval, Xbox account setup/family permissions, and Java Edition access. An HTTP 403 alone does not identify which requirement failed.
- **No Java profile:** use the correct owning account and finish creating its Minecraft profile/username.
- **Network failure:** retry after connectivity is restored. The launcher keeps the saved account instead of opening repeated sign-in prompts.

Setup verified on 26 September 2026: Azure confirmed creation of **The Lads Client** in **Default Directory**. The application supports **personal Microsoft accounts only**, with the **Mobile and desktop applications** platform and `http://localhost`. **Allow public client flows** is enabled and remained enabled after a browser reload. No client secret was created. Application (client) ID `c8ca54dc-01e3-4bb3-824a-35e09bb3aa13` is saved as `MicrosoftClientId` in the launcher's `%APPDATA%\.theladsclient\settings.json`; the previous settings were backed up before the atomic update. The Azure signup challenge failure recorded on 6 September is historical and no longer the current blocker.

The official Minecraft application review request was **submitted on 27 September 2026 at approximately 04:40 UTC**, after the owner reviewed and authorized submission. It includes the registered IDs and [project repository](https://github.com/ArashYT/TheLadsClient). The form confirmed **"Thank you for contacting Mojang Studios."** No reference number was shown. See [submission evidence](../artifacts/login-setup/MINECRAFT_APPID_REVIEW_SUBMITTED_2026-09-27.json), which preserves the status at submission.

**Approval email received and verified on 28 September 2026:** the Gmail message **"AppID Review Complete (09.28.2026)"**, from **Minecraft Enforcement Notification <MCENotify@microsoft.com>**, is timestamped **5:38 PM America/Toronto** and confirms that the applications in the batch met the criteria and were approved for the allow list. The message names no application ID. Its association with The Lads Client's ID `c8ca54dc-01e3-4bb3-824a-35e09bb3aa13` is based on the sole known new-application review submitted on 27 September. See [approval evidence](../artifacts/login-setup/MINECRAFT_APPID_APPROVAL_2026-09-28.json). Live launcher authentication remains unverified pending a runtime check.

In the previously installed launcher, an attempt at **23:52:34** returned a generic Minecraft account-verification failure; its exact stage and cause are unknown. A second attempt at **23:53:02** was declined. Neither result proves an application-approval failure or successful authentication. Sign-in through the new application, live refresh, both game versions and authenticated multiplayer remain unverified. See [registration evidence](../artifacts/login-setup/MICROSOFT_REGISTRATION_2026-09-26.md).

At **00:06:33 on 27 September**, Refresh in the updated installed launcher reported **Minecraft Services authentication/profile failed**, with guidance to check Java Edition access, Xbox profile/family permissions and Minecraft API approval. **No HTTP status was supplied.** This does not establish HTTP 403 or application approval as the sole cause. No device-code challenge was active at that handoff. The subsequent approval email does not establish successful live authentication; end-to-end verification remains pending. Do not submit a duplicate review request.

Latest source validation: the Release build passed with zero warnings/errors and all **125 launcher tests passed** with zero failures/skips. New coverage includes nine configuration cases and eleven authentication status/redaction cases. Diagnostics now report broad authentication stages and validated HTTP/Xbox codes without exposing raw response fields. A self-contained Windows x64 launcher was published to `artifacts/login-setup/published-20260926` and **installed and verified on 27 September**. The installed EXE/DLL match the published SHA-256 hashes. Only those two files changed; all four installed game-mod files and the live settings file were preserved by hash comparison. Source game-mod files were excluded from the update to prevent rolling back the installed versions. The previous launcher closed normally and the updated launcher restarted successfully. See [installation evidence](../artifacts/login-setup/installed-20260927.json). These checks do not establish live authentication.

The 6 September refresh fix, retained in current source, explicitly renews Xbox/XSTS and Minecraft credentials when a manual refresh is requested or the cached Minecraft token has less than two minutes remaining. Regression checks exercise the real CmlLib orchestration with synthetic service responses; these checks do not establish that a real account can sign in.
