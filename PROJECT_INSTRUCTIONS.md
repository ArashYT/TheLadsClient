# Project Instructions: The Lads Client

Welcome! You are assisting Arash in developing and maintaining **The Lads Client**, a custom Minecraft client, launcher, and modpack. Use this file as your source of truth for the workspace structure, building scripts, and developer instructions.

---

## 1. Workspace Directory Structure

You have full permission to read, write, and execute files within these directories:

*   **Main Code Directory:** `C:\Users\Arash\Desktop\Lads Client`
    Contains the custom Fabric mod, C# launcher, Packwiz modpack setup, and build scripts.
*   **Assets Directory:** `C:\Users\Arash\Desktop\The Lads Client Assets`
    Contains custom assets, textures, rendering scales, and capes.
*   **Minecraft Execution Instances:** `C:\Users\Arash\curseforge\minecraft\Instances`
    CurseForge directories where the modpack is run and tested.

---

## 2. Project Component Breakdown

Inside the main folder `C:\Users\Arash\Desktop\Lads Client`, the components are organized as follows:

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

### C. `TheLadsLauncher_Clean/` (Client Launcher)
*   **Language:** C#
*   **Framework:** .NET SDK (`TheLadsLauncher.csproj`)
*   **Build Tool:** `dotnet` CLI

---

## 3. Build & Deploy Commands

Instead of running manual builds, there is an automated build and deploy script: **`Build-LadsClient.ps1`**.

You can run these commands directly using your terminal/bash execution tools:

*   **Build and Deploy Mod to Packwiz (Default):**
    ```powershell
    powershell -ExecutionPolicy Bypass -File .\Build-LadsClient.ps1
    ```
    This compiles the Java code (`TheLadsCore`), copies the resulting `.jar` into the `The Lads Client Packwiz/mods` directory, and recalculates the SHA256 hashes inside `index.toml` and `pack.toml`.
    
*   **Build Mod + C# Launcher:**
    ```powershell
    powershell -ExecutionPolicy Bypass -File .\Build-LadsClient.ps1 -Launcher
    ```
    
*   **Watch Mode (Auto-rebuild on source edits):**
    ```powershell
    powershell -ExecutionPolicy Bypass -File .\Build-LadsClient.ps1 -Watch
    ```

---

## 4. How to Code & Assist Arash

When writing code or helping debug issues:
1.  **Fabric Modding (Java):** If asked to write new features for `TheLadsCore`, locate the sources in `TheLadsCore/src/main/java`. Keep edits compatible with the Fabric API version specified in `gradle.properties`.
2.  **Launcher Features (C#):** Work inside `TheLadsLauncher_Clean/` to add launcher tweaks or authentication updates.
3.  **Compile & Test Cycle:**
    - Make your code changes in the target components.
    - Execute the build script `.\Build-LadsClient.ps1` using your shell execution tool to compile the jar and update Packwiz.
    - Check the console logs for compilation errors. If a build fails, inspect `gradlew.bat` output or `dotnet build` output and correct the source.
4.  **Auto-Run / Verification:** Once built successfully, notify Arash that the mod is compiled and deployed to the Packwiz configuration, ready for Minecraft execution.
