$base = "C:\Users\Arash\Desktop\Lads Client\TheLadsCore\src\main\java\com\thelads\core\mixin\auto"
$classes = @{
    "shulkerboxutils130\ClientLevelMixin.java" = "package com.thelads.core.mixin.auto.shulkerboxutils130;`n`npublic class ClientLevelMixin {}"
    "resourcify262fabric184\MixinGuiGraphics.java" = "package com.thelads.core.mixin.auto.resourcify262fabric184;`n`npublic class MixinGuiGraphics {}"
    "obe2621010\BlockEntityRendererMixin.java" = "package com.thelads.core.mixin.auto.obe2621010;`n`npublic class BlockEntityRendererMixin {}"
    "decentscreenshot10262\ScreenshotGalleryScreen.java" = "package com.thelads.core.mixin.auto.decentscreenshot10262;`n`npublic class ScreenshotGalleryScreen {}"
    "modernadvancementsscreen1901262\AdvancementScreenMixin.java" = "package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;`n`npublic class AdvancementScreenMixin {}"
    "kerria1301211fabric\AnimatedTextureMixin.java" = "package com.thelads.core.mixin.auto.kerria1301211fabric;`n`npublic class AnimatedTextureMixin {}"
}

foreach ($key in $classes.Keys) {
    $path = Join-Path $base $key
    $dir = Split-Path $path
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force
    }
    Set-Content -Path $path -Value $classes[$key]
}
Write-Host "Fixed quarantined files!"
