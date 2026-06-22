"""
recreate_local.py — reliable local-AI mod recreation (bypasses aider).

Talks to Ollama directly, demands a strict file-delimited output format, then
writes each emitted file into the correct mixin/auto/<id>/ package — forcing the
basename + correct `package` line so the model can't misplace files or leak them
into a mixin-owned package. Compile-gates each mod; quarantines failures for
inspection instead of silently discarding.

Usage: python recreate_local.py <modDirName> [<modDirName> ...]
"""
import json
import os
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

OLLAMA_URL = "http://127.0.0.1:11434"
MODEL = os.environ.get("RECREATE_MODEL", "qwen-coder-14b-stable:latest")
ROOT = Path(__file__).parent
MODS_ROOT = Path(r"C:\Users\Arash\Desktop\Mods To Recreate In Lads")
AUTO_PKG_ROOT = ROOT / "TheLadsCore/src/main/java/com/thelads/core/mixin/auto"
QUARANTINE = ROOT / "recreate_quarantine"


def ollama(prompt: str) -> str:
    payload = json.dumps({
        "model": MODEL,
        "prompt": prompt,
        "stream": True,
        "options": {"num_ctx": 8192, "num_predict": 8192, "temperature": 0.1},
    }).encode()
    req = urllib.request.Request(f"{OLLAMA_URL}/api/generate", data=payload,
                                 headers={"Content-Type": "application/json"})
    out = []
    with urllib.request.urlopen(req, timeout=1800) as resp:
        for raw in resp:
            raw = raw.strip()
            if not raw:
                continue
            obj = json.loads(raw)
            tok = obj.get("response", "")
            out.append(tok)
            sys.stdout.write(tok)
            sys.stdout.flush()
            if obj.get("done"):
                break
    return "".join(out)


def mod_meta(mod_dir: Path):
    name, desc, mixins = mod_dir.name, "", []
    fj = mod_dir / "fabric.mod.json"
    if fj.exists():
        try:
            j = json.loads(fj.read_text(encoding="utf-8"))
            name = j.get("name", name)
            desc = j.get("description", "")
        except Exception:
            pass
    for mj in mod_dir.glob("*.mixins.json"):
        try:
            k = json.loads(mj.read_text(encoding="utf-8"))
            mixins += k.get("mixins", []) + k.get("client", []) + k.get("server", [])
        except Exception:
            pass
    return name, desc, sorted(set(mixins))


def build_prompt(name, desc, mixins, pkg):
    name = "Example Optimization Mod"
    guide = ", ".join(m.rsplit(".", 1)[-1] for m in mixins)[:1500]
    return f"""You are an expert Java developer writing a SpongePowered Mixin for a Minecraft 26.1.2 mod.

MOD: {name}
ORIGINAL MIXIN CLASSES (names are hints to which net.minecraft.* classes to target): {guide}

Write Mixin classes that reproduce this behavior. Target real net.minecraft.* classes only.

CRITICAL — this project uses OFFICIAL (Mojang) mappings, NOT Yarn. Use Mojang package names:
- net.minecraft.client.Minecraft  (NOT MinecraftClient)
- net.minecraft.client.renderer.*  and  net.minecraft.client.renderer.blockentity.*  (NOT client.render.*)
- net.minecraft.world.level.block.entity.BlockEntity / BlockEntityRenderer / BlockEntityRenderDispatcher
- net.minecraft.world.level.Level (NOT world.World) ; net.minecraft.core.BlockPos ; net.minecraft.resources.Identifier
- net.minecraft.world.entity.Entity / LivingEntity ; net.minecraft.world.item.ItemStack ; net.minecraft.network.chat.Component
- net.minecraft.client.gui.screens.Screen ; net.minecraft.client.gui.GuiGraphics ; net.minecraft.util.Mth
Never use Yarn names like net.minecraft.client.render.*, MinecraftClient, ClientWorld, Identifier in net.minecraft.util.*, or *.method_xxxx.

HARD RULES:
- Put require = 0 on EVERY @Inject / @ModifyArg / @ModifyVariable / @Redirect (a wrong target must be a harmless no-op).
- Use ONLY real Minecraft / Java / SpongePowered Mixin APIs. If unsure how to hook something, write a minimal safe @Inject at HEAD with require = 0.
- Every class must be self-contained and compile on its own. Prefer fewer, simple, robust mixins over many fragile ones.

OUTPUT FORMAT — output ONLY files, each delimited EXACTLY like this, no prose, no markdown fences:
=== FILE: SomeClassMixin.java ===
package {pkg};
<full java source>
=== END ===

Use only a bare class-name filename (e.g. MixinFoo.java) — no directory path. Start each file's body with `package {pkg};`."""


def parse_files(text):
    files = {}
    for m in re.finditer(r"=== FILE:\s*(.+?)\s*===\s*\n(.*?)(?:\n=== END ===|\Z)", text, re.DOTALL):
        raw_name, body = m.group(1).strip(), m.group(2)
        base = os.path.basename(raw_name.replace("\\", "/")).strip()
        if not base.endswith(".java"):
            continue
        body = body.strip()
        # strip accidental ``` fences
        body = re.sub(r"^```[a-zA-Z]*\n", "", body)
        body = re.sub(r"\n```$", "", body).strip()
        files[base] = body
    return files


def force_package(code, pkg):
    code = re.sub(r"^\s*package\s+[\w.]+\s*;", "", code, count=1).lstrip()
    return f"package {pkg};\n\n{code}\n"


def compile_core():
    r = subprocess.run([str(ROOT / "TheLadsCore/gradlew.bat"), "compileJava", "--console=plain"],
                       cwd=str(ROOT / "TheLadsCore"), capture_output=True, text=True)
    return "BUILD SUCCESSFUL" in (r.stdout + r.stderr), (r.stdout + r.stderr)


def recreate(mod_name):
    mod_dir = MODS_ROOT / mod_name
    if not mod_dir.exists():
        print(f"!! {mod_name}: dir not found"); return
    mid = re.sub(r"[^a-zA-Z0-9]", "", mod_name).lower()
    pkg = f"com.thelads.core.mixin.auto.{mid}"
    pkg_dir = AUTO_PKG_ROOT / mid
    name, desc, mixins = mod_meta(mod_dir)
    print(f"\n===== {name} ({mid}) -> {pkg} =====")
    resp = ollama(build_prompt(name, desc, mixins, pkg))
    files = parse_files(resp)
    if not files:
        print(f"\n!! {name}: model produced no parseable files"); return
    pkg_dir.mkdir(parents=True, exist_ok=True)
    for base, code in files.items():
        (pkg_dir / base).write_text(force_package(code, pkg), encoding="utf-8")
    print(f"\n   wrote {len(files)} files: {', '.join(files)}")
    ok, log = compile_core()
    if ok:
        print(f"   {name}: COMPILES OK -> kept in mixin/auto/{mid} ({len(files)} files)")
    else:
        QUARANTINE.mkdir(exist_ok=True)
        dest = QUARANTINE / mid
        if dest.exists():
            import shutil; shutil.rmtree(dest)
        pkg_dir.rename(dest)
        (QUARANTINE / f"{mid}.compile.log").write_text(log[-6000:], encoding="utf-8")
        print(f"   {name}: COMPILE FAILED -> quarantined to recreate_quarantine/{mid} (see .compile.log)")


if __name__ == "__main__":
    targets = sys.argv[1:]
    if not targets:
        print("usage: python recreate_local.py <modDirName> ..."); sys.exit(1)
    for t in targets:
        recreate(t)
    print("\n=== recreate_local DONE ===")
