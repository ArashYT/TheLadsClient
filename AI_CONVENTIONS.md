# Conventions for the local AI agent (always read this first)

You are an autonomous coding agent working in the **Lads Client** repo via Aider +
a local Ollama model. Follow these rules on every task.

## Repo layout
- **TheLadsCore/** — main Fabric mod (Java, Minecraft 26.1.2). Gradle build.
- **TheLadsClientMod/** — secondary Fabric mod (Java). Gradle build.
- **TheLadsLauncher/** — C# Avalonia desktop launcher (.NET).

## Build & verify commands (run these yourself, from the repo root)
- Compile the mod:           `cd TheLadsCore && gradlew.bat compileJava`
- Run a specific test class:  `cd TheLadsCore && gradlew.bat test --tests "fully.qualified.ClassName"`
- Compile the launcher:       `cd TheLadsLauncher && dotnet build`
Always finish a task by compiling, and run the relevant tests if the task touched tested code.

## How to work
- Only edit the files you were given. If you need another file, say so — don't guess wildly.
- Keep changes minimal and match the surrounding code style.
- Do NOT modify, reformat, or "improve" unrelated code.
- Do NOT touch the MC 26.2 migration (it is blocked on upstream — see TheLadsCore/MIGRATION_26.2.md).
- Never delete files you did not create.
- If a build/test fails, read the error and fix it; repeat until green. Do not give up silently.

## Reference material
- The folders `TheLadsCore/decompiled_verify_fresh/` and `decompiled_verify_packwiz/` are
  READ-ONLY decompiled reference copies. Use them to see how code used to look, but NEVER edit them.

## When done
Report: (1) files changed, (2) compile result, (3) test result, (4) anything you couldn't finish.
