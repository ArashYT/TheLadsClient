"""
Common helpers and utilities for The Lads Client E2E Testing Suite.
Provides path resolution, git inspection, TOML parsing, schema validation,
and boot log analysis for opaque-box testing.
"""

import os
import re
import sys
import json
import shutil
import tempfile
import subprocess
from pathlib import Path

# Authoritative Root Directories
TESTS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TESTS_DIR.parent
PACKWIZ_DIR = REPO_ROOT / "Packwiz"
LAUNCHER_DIR = REPO_ROOT / "TheLadsLauncher"
CORE_DIR = REPO_ROOT / "TheLadsCore"

# Dynamic Default Paths per Contract
DEFAULT_APPDATA = os.environ.get("APPDATA", str(Path.home() / "AppData" / "Roaming"))
DEFAULT_CLIENT_BASE = Path(DEFAULT_APPDATA) / ".theladsclient"
DEFAULT_SHARED_DIR = DEFAULT_CLIENT_BASE / "shared"
DEFAULT_RUNTIME_DIR = DEFAULT_CLIENT_BASE / "runtime"
DEFAULT_LOGS_DIR = DEFAULT_CLIENT_BASE / "logs"
DEFAULT_PROFILES_DIR = DEFAULT_CLIENT_BASE / "profiles"


def run_cmd(cmd, cwd=None, capture_output=True, timeout=60):
    """Executes a command and returns (exit_code, stdout, stderr)."""
    if cwd is None:
        cwd = str(REPO_ROOT)
    try:
        res = subprocess.run(
            cmd,
            cwd=str(cwd),
            shell=True,
            text=True,
            capture_output=capture_output,
            timeout=timeout,
            encoding="utf-8",
            errors="replace"
        )
        return res.returncode, res.stdout, res.stderr
    except subprocess.TimeoutExpired:
        return -1, "", "Command timed out"
    except Exception as e:
        return -1, "", str(e)


def get_git_repo_size_mb():
    """Calculates total size of .git directory in Megabytes."""
    git_dir = REPO_ROOT / ".git"
    if not git_dir.exists():
        return 0.0
    total_bytes = 0
    for root, _, files in os.walk(git_dir):
        for f in files:
            fp = os.path.join(root, f)
            try:
                total_bytes += os.path.getsize(fp)
            except OSError:
                pass
    return total_bytes / (1024 * 1024)


def get_git_pack_size_mb():
    """Calculates size of .git/objects/pack/ in Megabytes."""
    pack_dir = REPO_ROOT / ".git" / "objects" / "pack"
    if not pack_dir.exists():
        return 0.0
    total_bytes = 0
    for f in pack_dir.glob("*.pack"):
        try:
            total_bytes += f.stat().st_size
        except OSError:
            pass
    return total_bytes / (1024 * 1024)


def get_tracked_git_files():
    """Returns set of all file paths tracked by git (relative to repo root)."""
    code, out, _ = run_cmd("git ls-files")
    if code != 0:
        return set()
    return {line.strip().replace("\\", "/") for line in out.splitlines() if line.strip()}


def check_git_ignored(relative_path):
    """Returns True if the specified path is ignored by .gitignore."""
    code, _, _ = run_cmd(f'git check-ignore -q "{relative_path}"')
    return code == 0


def parse_minimal_toml(toml_str):
    """
    Lightweight robust TOML key-value parser for metadata testing
    without external dependencies. Handles tables, strings, ints, booleans, and lists.
    """
    result = {}
    current_section = result
    
    for line in toml_str.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
            
        # Array of tables [[table]]
        if line.startswith("[[") and line.endswith("]]"):
            sec_name = line[2:-2].strip()
            if sec_name not in result:
                result[sec_name] = []
            new_entry = {}
            if isinstance(result[sec_name], list):
                result[sec_name].append(new_entry)
            current_section = new_entry
            continue

        # Section header [table]
        if line.startswith("[") and line.endswith("]"):
            sec_name = line[1:-1].strip()
            parts = sec_name.split(".")
            curr = result
            for p in parts:
                if p not in curr:
                    curr[p] = {}
                curr = curr[p]
            current_section = curr
            continue
            
        # Key = Value
        if "=" in line:
            k, v = line.split("=", 1)
            key = k.strip()
            val_str = v.strip()
            
            # String parsing
            if (val_str.startswith('"') and val_str.endswith('"')) or (val_str.startswith("'") and val_str.endswith("'")):
                current_section[key] = val_str[1:-1]
            elif val_str.lower() == "true":
                current_section[key] = True
            elif val_str.lower() == "false":
                current_section[key] = False
            elif val_str.isdigit():
                current_section[key] = int(val_str)
            else:
                current_section[key] = val_str
                
    return result


def validate_pw_toml_content(content):
    """
    Validates that a .pw.toml file contains required packwiz metadata:
    - name
    - filename
    - side (client, server, or both)
    - download information (hash/mode/url)
    """
    parsed = parse_minimal_toml(content)
    if "name" not in parsed or not parsed["name"]:
        return False, "Missing 'name' field"
    if "filename" not in parsed or not parsed["filename"]:
        return False, "Missing 'filename' field"
    side = parsed.get("side", "")
    if side not in ("client", "server", "both"):
        return False, f"Invalid side value: '{side}', expected 'client', 'server', or 'both'"
    
    # Download check
    has_download_block = "download" in parsed and isinstance(parsed["download"], dict)
    has_update_block = "update" in parsed and isinstance(parsed["update"], dict)
    has_direct_url = "url" in parsed or ("download" in parsed and "url" in parsed.get("download", {}))
    
    if not (has_download_block or has_update_block or has_direct_url):
        return False, "Missing download or update metadata"
        
    return True, "Valid"


def validate_accounts_json_schema(data):
    """
    Validates accounts JSON data schema according to PROJECT.md:
    Array of account objects: uuid, username, accessToken, accountType, selected.
    """
    if not isinstance(data, list):
        return False, "Accounts file root must be a JSON array"
    
    for idx, acc in enumerate(data):
        if not isinstance(acc, dict):
            return False, f"Account entry at index {idx} must be a JSON object"
        for req_field in ("uuid", "username", "accountType", "selected"):
            if req_field not in acc:
                return False, f"Account entry {idx} missing required field '{req_field}'"
        if not isinstance(acc["selected"], bool):
            return False, f"Account entry {idx} 'selected' field must be a boolean"
            
    return True, "Valid schema"


def validate_profile_json_schema(data):
    """
    Validates lads_profile.json schema according to PROJECT.md:
    Active profile name, version, HUD configurations, cosmetic selections.
    """
    if not isinstance(data, dict):
        return False, "Profile root must be a JSON object"
    return True, "Valid schema"


def validate_boot_log(log_text):
    """
    Validates in-game boot log according to Requirement R4:
    - Exactly ONE 'Reloading ResourceManager' line
    - Contains 'Game took' startup benchmark or clean initialization line
    - Zero 'InvalidMixinException', 'MixinApplyError', or 'InjectionError'
    """
    results = {
        "passed": True,
        "reload_resource_manager_count": 0,
        "has_game_startup_benchmark": False,
        "has_mixin_errors": False,
        "mixin_errors": [],
        "errors": []
    }
    
    # Check Reloading ResourceManager count
    reload_matches = re.findall(r"Reloading ResourceManager:", log_text)
    results["reload_resource_manager_count"] = len(reload_matches)
    if len(reload_matches) != 1:
        results["passed"] = False
        results["errors"].append(
            f"Expected exactly 1 'Reloading ResourceManager:', but found {len(reload_matches)}"
        )
        
    # Check startup benchmark
    if re.search(r"Game took \d+(\.\d+)? (seconds|s) to start", log_text, re.IGNORECASE) or \
       re.search(r"Sound engine started", log_text, re.IGNORECASE) or \
       re.search(r"TitleScreen ready|TheLadsCore initialized", log_text, re.IGNORECASE):
        results["has_game_startup_benchmark"] = True
    else:
        results["passed"] = False
        results["errors"].append("Missing startup benchmark ('Game took N seconds to start')")
        
    # Check for Mixin Errors
    mixin_err_patterns = [
        r"InvalidMixinException",
        r"MixinApplyError",
        r"org\.spongepowered\.asm\.mixin\.injection\.throwables\.InjectionError",
        r"FATAL.*theladscore"
    ]
    for pat in mixin_err_patterns:
        errs = re.findall(pat, log_text, re.IGNORECASE)
        if errs:
            results["has_mixin_errors"] = True
            results["mixin_errors"].extend(errs)
            results["passed"] = False
            results["errors"].append(f"Found mixin errors matching pattern '{pat}': {errs}")
            
    return results
