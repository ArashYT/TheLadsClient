# Autonomous batch: local AI recreates every remaining mod, back-to-back, unattended.
# Each mod is isolated: AI writes files -> compile -> keep if it builds, discard if not.
$ErrorActionPreference = 'Continue'
$root = 'C:\Users\Arash\Desktop\Lads Client'
$modsRoot = 'C:\Users\Arash\Desktop\Mods To Recreate In Lads'
$env:OLLAMA_API_BASE = 'http://127.0.0.1:11434'
# Force UTF-8 so aider's progress-bar chars don't crash with UnicodeEncodeError when redirected
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
Set-Location $root
$log = Join-Path $root 'recreate_all_log.txt'
"=== Recreate-all run $(Get-Date) ===" | Out-File $log

$mods = @(
  'farblockentityrendering-2.1',
  'shulkerboxutils-1.3.0',
  'retromod-1.1.0-rc.1+26.2',
  'Kerria-1.3.0+1.21.1-fabric',
  'obe+26.2-1.0.10',
  'ModernAdvancementsScreen-1.9.0-1.26.2',
  'Resourcify (26.2-fabric)-1.8.4',
  'animatium-3.2+26.1.2-fabric'
)

foreach ($m in $mods) {
  $dir = Join-Path $modsRoot $m
  $fj = Join-Path $dir 'fabric.mod.json'
  if (-not (Test-Path $fj)) { "SKIP $m (no fabric.mod.json)" | Out-File $log -Append; continue }

  $id = ($m -replace '[^a-zA-Z0-9]', '').ToLower()
  $pkgDir = Join-Path $root "TheLadsCore\src\main\java\com\thelads\core\mixin\auto\$id"

  $name = $m; $desc = ''; $mixins = ''
  try {
    $j = Get-Content $fj -Raw | ConvertFrom-Json
    if ($j.name) { $name = $j.name }
    if ($j.description) { $desc = $j.description }
  } catch {}
  $mset = @()
  foreach ($mj in (Get-ChildItem -Path $dir -Filter '*.mixins.json' -ErrorAction SilentlyContinue)) {
    try {
      $k = Get-Content $mj.FullName -Raw | ConvertFrom-Json
      if ($k.mixins) { $mset += $k.mixins }
      if ($k.client) { $mset += $k.client }
    } catch {}
  }
  $mixins = (($mset | Select-Object -Unique) -join ', ')
  if ($mixins.Length -gt 1200) { $mixins = $mixins.Substring(0, 1200) }

  $task = @"
OUTPUT RULES: Output ONLY the code blocks for the new files you create. No commentary.
TASK: Recreate the "$name" mod natively inside this Fabric mod (Minecraft 26.1.2, Java).
Description: $desc
Create NEW Mixin classes under package com.thelads.core.mixin.auto.$id
(path: TheLadsCore/src/main/java/com/thelads/core/mixin/auto/$id/).
The original mod hooked these classes/features as a guide: $mixins
For each feature, write a Mixin targeting the matching net.minecraft.* class.
HARD RULES:
- Put require = 0 on EVERY @Inject / @ModifyArg / @Redirect (a wrong target must be a harmless no-op).
- Only create files under com.thelads.core.mixin.auto.$id . Do NOT modify any existing file.
- Match the style in TheLadsCore/src/main/java/com/thelads/core/mixin/GuiMixin.java .
- Use only real Minecraft / Java / Mixin APIs. If unsure, use a minimal safe @Inject at HEAD.
"@
  Set-Content -Path (Join-Path $root 'AI_TASK_AUTO.txt') -Value $task -Encoding utf8

  "--- $(Get-Date -Format HH:mm) | $name : AI working..." | Out-File $log -Append
  $args = @('-m','aider','--model','ollama_chat/qwen-coder-7b-fast','--no-show-model-warnings','--yes-always','--no-auto-commits','--map-tokens','512','--message-file','AI_TASK_AUTO.txt')
  $p = Start-Process -FilePath 'python' -ArgumentList $args -RedirectStandardOutput (Join-Path $root 'ai_auto_out.txt') -RedirectStandardError (Join-Path $root 'ai_auto_err.txt') -NoNewWindow -PassThru -Wait

  Push-Location (Join-Path $root 'TheLadsCore')
  $b = & .\gradlew.bat compileJava --console=plain 2>&1 | Out-String
  Pop-Location
  if ($b -match 'BUILD SUCCESSFUL') {
    "    $name : COMPILES OK (kept in mixin/auto/$id)" | Out-File $log -Append
  } else {
    if (Test-Path $pkgDir) { Remove-Item -Recurse -Force $pkgDir }
    "    $name : COMPILE FAILED -> discarded" | Out-File $log -Append
  }
}
"=== DONE $(Get-Date) ===" | Out-File $log -Append
