# Lads Client — Agent Guide

This file gives the local AI agent (OpenCode) context about this repository so you
can give it high-level instructions without naming files yourself.

## What this project is
- **TheLadsCore/** — A Fabric (Minecraft) mod in Java. Built with Gradle.
  - Build: `cd TheLadsCore && .\gradlew.bat build deploy -x test` (CRITICAL: You must include `deploy` so the Launcher gets the new jar, and `-x test` to skip broken cosmetics tests)
  - Output jar: `TheLadsCore/build/libs/` (and deployed to `C:/The Lads Client/mods/`)
- **TheLadsLauncher/** — A C# Avalonia desktop launcher (.NET). Builds to an .exe.
  - Build: `cd TheLadsLauncher && dotnet build`
  - Publish exe: `dotnet publish -c Release -r win-x64 --self-contained`

## Anti-Ban Rule (PERMANENT, owner 2026-10-18)

- The Lads Client must always have the **least possible chance of getting a player banned** on any server.
- Any feature that sends extra/unusual packets, clicks inventory slots with no screen open, automates input, or shows information the vanilla client would not (radar, container peek, reach) must be gated by `HypixelSafetyManager` server rules: OFF on servers that ban it AND on servers whose rules we don't know; allowed only in singleplayer/LAN, on our own servers, or on servers known to allow it.
- Players can re-enable a blocked module only per server, through the big red "use at your own risk" bypass warning screen. Never silently.
- Visual-only client-side features are fine everywhere. Every review (Claude, Antigravity, local AI) checks this rule.

## How to work
- Explore the repo before editing; figure out the right files yourself.
- After Java changes, run the Gradle build to confirm it compiles.
- After C# changes, run `dotnet build` to confirm it compiles.
- Keep changes minimal and match surrounding code style.
- Do not delete files you did not create.

## Build prerequisites
- Java/Gradle for the mod (wrapper included: `gradlew.bat`).
- .NET SDK for the launcher.
