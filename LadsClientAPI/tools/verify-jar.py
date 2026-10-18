#!/usr/bin/env python3
"""
verify-jar.py - Verifies universal LadsClientAPI jar requirements:
1. Every metadata file is present (plugin.yml, velocity-plugin.json, fabric.mod.json, META-INF/mods.toml, pack.mcmeta).
2. Every .class file major version <= 61 (Java 17).
3. No unrelocated org/yaml/snakeyaml classes exist.
4. Relocated com/thelads/ladsapi/lib/snakeyaml classes exist.
"""

import sys
import zipfile
from pathlib import Path

REQUIRED_METADATA = [
    "plugin.yml",
    "velocity-plugin.json",
    "fabric.mod.json",
    "META-INF/mods.toml",
    "pack.mcmeta"
]

def verify_jar(jar_path_str: str) -> bool:
    jar_path = Path(jar_path_str)
    if not jar_path.exists():
        print(f"FAIL: Jar file not found: {jar_path}")
        return False

    print(f"Verifying jar: {jar_path.resolve()} ({jar_path.stat().st_size:,} bytes)")

    with zipfile.ZipFile(jar_path, 'r') as zf:
        namelist = set(zf.namelist())

        # 1. Metadata check
        missing_meta = [m for m in REQUIRED_METADATA if m not in namelist]
        if missing_meta:
            print(f"FAIL: Missing metadata files: {missing_meta}")
            return False
        print(f"PASS: All required metadata files present: {REQUIRED_METADATA}")

        # 2. Bytecode version check and snakeyaml check
        class_count = 0
        max_major = 0
        unrelocated_yaml = []
        high_version_classes = []
        relocated_yaml_count = 0

        for name in zf.namelist():
            if name.startswith("org/yaml/snakeyaml/"):
                unrelocated_yaml.append(name)

            if name.startswith("com/thelads/ladsapi/lib/snakeyaml/"):
                relocated_yaml_count += 1

            if name.endswith(".class"):
                class_count += 1
                data = zf.read(name)
                if len(data) >= 8 and data[:4] == b'\xca\xfe\xba\xbe':
                    major = int.from_bytes(data[6:8], 'big')
                    if major > max_major:
                        max_major = major
                    if major > 61:
                        high_version_classes.append((name, major))
                else:
                    print(f"WARNING: Invalid class header for {name}")

        if unrelocated_yaml:
            print(f"FAIL: Found {len(unrelocated_yaml)} unrelocated SnakeYAML classes, e.g.: {unrelocated_yaml[:5]}")
            return False
        print(f"PASS: No unrelocated org/yaml/snakeyaml classes found.")

        if relocated_yaml_count == 0:
            print("FAIL: No relocated com/thelads/ladsapi/lib/snakeyaml classes found in jar!")
            return False
        print(f"PASS: Found {relocated_yaml_count} relocated SnakeYAML entries.")

        if high_version_classes:
            print(f"FAIL: Found {len(high_version_classes)} classes with bytecode major version > 61:")
            for name, major in high_version_classes[:10]:
                print(f"  {name}: major version {major}")
            return False

        print(f"PASS: Verified {class_count} class files. Maximum bytecode major version: {max_major} (Java 17 <= 61).")

    print("\nALL CHECKS PASSED: Jar is universally compatible.")
    return True

if __name__ == "__main__":
    jar_file = sys.argv[1] if len(sys.argv) > 1 else "build/libs/LadsClientAPI-1.0.0.jar"
    success = verify_jar(jar_file)
    sys.exit(0 if success else 1)
