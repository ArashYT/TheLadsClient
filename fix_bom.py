import sys
from pathlib import Path

files = [
    r"TheLadsCore\src\main\java\com\thelads\core\mixin\auto\resourcify262fabric184\MixinGuiGraphics.java",
    r"TheLadsCore\src\main\java\com\thelads\core\mixin\auto\shulkerboxutils130\ShulkerBoxRendererMixin.java",
    r"TheLadsCore\src\main\java\com\thelads\core\mixin\auto\shulkerboxutils130\ShulkerItemTooltipMixin.java",
    r"TheLadsCore\src\main\java\com\thelads\core\mixin\auto\shulkerboxutils130\ClientLevelMixin.java"
]

for f in files:
    p = Path(f)
    content = p.read_bytes().decode("utf-8-sig")
    
    lines = content.splitlines()
    package_line = ""
    imports_before_pkg = []
    
    # find package line
    pkg_idx = -1
    for i, line in enumerate(lines):
        if line.startswith("package "):
            package_line = line
            pkg_idx = i
            break
            
    if pkg_idx > 0:
        for i in range(pkg_idx):
            if lines[i].startswith("import "):
                imports_before_pkg.append(lines[i])
        
    final_lines = [package_line] + imports_before_pkg
    for i, line in enumerate(lines):
        if i == pkg_idx or (i < pkg_idx and line.startswith("import ")):
            continue
        final_lines.append(line)
        
    p.write_bytes(("\n".join(final_lines) + "\n").encode("utf-8"))
