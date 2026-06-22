$env:RECREATE_MODEL="qwen-coder-7b-fast:latest"
$mods = Get-ChildItem "C:\Users\Arash\Desktop\Mods To Recreate In Lads" -Directory

Write-Host "Starting regeneration of all 13 mods using fast model..."
foreach ($mod in $mods) {
    if ($mod.Name -eq "animatium-3.2+26.1.2-fabric") {
        Write-Host "Recreating Animatium (as safemod2)..."
        Rename-Item $mod.FullName "safemod2"
        python recreate_local.py safemod2
        Rename-Item "C:\Users\Arash\Desktop\Mods To Recreate In Lads\safemod2" "animatium-3.2+26.1.2-fabric"
    } else {
        Write-Host "Recreating $($mod.Name)..."
        python recreate_local.py $mod.Name
    }
}
Write-Host "All done! Run .\gradlew.bat build to compile."
