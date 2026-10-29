# Project Instructions: The Lads Client

Welcome! You are assisting Arash in developing and maintaining **The Lads Client**, a custom Minecraft client, launcher, and modpack. Use this file as your source of truth for the workspace structure, building scripts, and developer instructions.

---

## 1. Workspace Directory Structure

You have full permission to read, write, and execute files within these directories:

*   **Main Code Directory:** `<repo-root>`
    Contains the custom Fabric mod, C# launcher, Packwiz modpack setup, and build scripts.
*   **Assets Directory:** `<assets-root>`
    Contains custom assets, textures, rendering scales, and capes.
*   **Minecraft Execution Instances:** `%USERPROFILE%\curseforge\minecraft\Instances`
    CurseForge directories where the modpack is run and tested.

---

## 2. Project Component Breakdown

Inside the main folder `<repo-root>`, the components are organized as follows:

### A. `TheLadsCore/` (Fabric Mod)
*   **Language:** Java
*   **Framework:** Fabric Modding Toolchain
*   **Build Tool:** Gradle (uses wrapper `gradlew.bat`)
*   **Output:** `TheLadsCore/build/libs/TheLadsCore-<version>.jar`

### B. `The Lads Client Packwiz/` (Modpack Configuration)
*   **Tool:** Packwiz (git-based modpack manager)
*   **Key Files:**
    - `pack.toml`: Main modpack metadata.
    - `index.toml`: List of all mods, resource packs, and configurations with SHA256 hashes.
    - `mods/`: Subfolder where all mod `.jar` files are stored.

### C. `TheLadsLauncher/` (Client Launcher)
*   **Language:** C#
*   **Framework:** .NET SDK (`TheLadsLauncher.csproj`)
*   **Build Tool:** `dotnet` CLI

---

## 3. Build & Deploy Commands

Instead of running manual builds, there is an automated build and deploy script: **`Build-LadsClient.ps1`**.

You can run these commands directly using PowerShell:

*   **Build Core Modules (Default):**
    ```powershell
    powershell -ExecutionPolicy Bypass -File .\Build-LadsClient.ps1
    ```
    Compiles the multi-version Java core (`TheLadsCore`) and deploys the production jars to the launcher's `game-mods/<version>` directories (`1.8.9`, `1.20.1`, `1.21.11`, `26.2`, `26.3`).

*   **Build Core + C# Launcher:**
    ```powershell
    powershell -ExecutionPolicy Bypass -File .\Build-LadsClient.ps1 -Launcher
    ```
    Builds the core modules and publishes the C# launcher (`TheLadsLauncher`) to `artifacts/client` (self-contained `win-x64`), validating core jars, mod manifests, and bundled documentation.

*   **Build and Install Locally:**
    ```powershell
    powershell -ExecutionPolicy Bypass -File .\Build-LadsClient.ps1 -Install
    ```
    Builds core, publishes the launcher, and runs `tools/Install-LadsRelease.ps1` to install the release locally.

---

## 4. How to Code & Assist Arash

When writing code or helping debug issues:
1.  **Fabric Modding (Java):** If asked to write new features for `TheLadsCore`, locate the sources in `TheLadsCore/common` and version-specific modules. Keep edits compatible with the supported Minecraft versions.
2.  **Launcher Features (C#):** Work inside `TheLadsLauncher/` to add launcher tweaks or authentication updates.
3.  **Compile & Test Cycle:**
    - Make your code changes in the target components.
    - Execute the build script `.\Build-LadsClient.ps1` (or `.\Build-LadsClient.ps1 -Launcher`) using PowerShell to compile and verify.
    - Check the console logs for compilation errors. If a build fails, inspect `gradlew.bat` output or `dotnet build` output and correct the source.
4.  **Auto-Run / Verification:** Once built successfully, notify Arash that the changes are compiled and ready.
