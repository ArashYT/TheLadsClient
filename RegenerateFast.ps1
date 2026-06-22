Write-Host "Recreating remaining fast mods..."

python recreate_local.py safemod
python recreate_local.py shulkerboxutils-1.3.0
python recreate_local.py SignalLoss-1.2.1-26.2

Write-Host "All done!"
