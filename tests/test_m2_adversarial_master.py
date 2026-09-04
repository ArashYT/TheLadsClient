#!/usr/bin/env python3
"""
The Lads Client - Milestone 2 Adversarial Verification & Stress Suite
Executes comprehensive empirical tests across:
  1. 0-Warning Launcher Compilation (Debug & Release)
  2. Zero Hardcoded Personal Paths Audit
  3. ServiceTestSuite Execution (7/7 tests)
  4. Adoptium REST API Live Endpoints (Java 21 & Java 25) + Fallback CDN URLs
  5. In-Memory C# Services Edge Cases (15/15 tests in test_m2_csharp_services.ps1)
"""

import os
import sys
import json
import subprocess
import urllib.request
from pathlib import Path

# UTF-8 output formatting
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent
LAUNCHER_DIR = REPO_ROOT / "TheLadsLauncher"
CSPROJ_PATH = LAUNCHER_DIR / "TheLadsLauncher.csproj"

total_tests = 0
passed_tests = 0
failed_tests = 0
failures = []


def run_test(name, fn):
    global total_tests, passed_tests, failed_tests
    total_tests += 1
    print(f"[{total_tests:02d}] {name} ... ", end="", flush=True)
    try:
        fn()
        print("PASS")
        passed_tests += 1
    except Exception as e:
        print(f"FAIL: {e}")
        failed_tests += 1
        failures.append((name, str(e)))


def run_cmd(cmd, cwd=None):
    res = subprocess.run(
        cmd,
        shell=True,
        cwd=cwd or str(REPO_ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace"
    )
    return res.returncode, res.stdout, res.stderr


# ------------------------------------------------------------------------------
# Test 1: Debug Build 0 Warnings & 0 Errors
# ------------------------------------------------------------------------------
def test_build_debug():
    code, out, err = run_cmd(f'dotnet build "{CSPROJ_PATH}" -c Debug --no-incremental')
    combined = out + "\n" + err
    if code != 0:
        raise AssertionError(f"Debug build failed (exit code {code}):\n{combined}")
    if "0 Warning(s)" not in combined:
        raise AssertionError(f"Debug build contains compiler/Avalonia warnings:\n{combined}")
    if "0 Error(s)" not in combined:
        raise AssertionError(f"Debug build contains compiler errors:\n{combined}")


# ------------------------------------------------------------------------------
# Test 2: Release Build 0 Warnings & 0 Errors
# ------------------------------------------------------------------------------
def test_build_release():
    code, out, err = run_cmd(f'dotnet build "{CSPROJ_PATH}" -c Release --no-incremental')
    combined = out + "\n" + err
    if code != 0:
        raise AssertionError(f"Release build failed (exit code {code}):\n{combined}")
    if "0 Warning(s)" not in combined:
        raise AssertionError(f"Release build contains compiler/Avalonia warnings:\n{combined}")
    if "0 Error(s)" not in combined:
        raise AssertionError(f"Release build contains compiler errors:\n{combined}")


# ------------------------------------------------------------------------------
# Test 3: Zero Hardcoded Personal Paths Audit
# ------------------------------------------------------------------------------
def test_path_audit():
    forbidden = ["C:\\Users\\Arash", "C:\\The Lads Client"]
    violations = []
    for ext in ["*.cs", "*.axaml", "*.json"]:
        for f in LAUNCHER_DIR.rglob(ext):
            rel = str(f.relative_to(LAUNCHER_DIR))
            # Skip build output directories
            if any(part in rel for part in ["bin\\", "bin/", "obj\\", "obj/"]):
                continue
            try:
                content = f.read_text(encoding="utf-8", errors="replace")
                for pat in forbidden:
                    if pat in content:
                        violations.append(f"{f.name} contains forbidden pattern '{pat}'")
            except Exception:
                pass

    if violations:
        raise AssertionError(f"Hardcoded path violations found:\n" + "\n".join(violations))


# ------------------------------------------------------------------------------
# Test 4: ServiceTestSuite Execution
# ------------------------------------------------------------------------------
def test_service_test_suite():
    code, out, err = run_cmd(f'dotnet run --no-build --project "{CSPROJ_PATH}" -- --test-services')
    combined = out + "\n" + err
    if code != 0:
        raise AssertionError(f"ServiceTestSuite exited with code {code}:\n{combined}")
    if "7 PASSED, 0 FAILED" not in combined:
        raise AssertionError(f"ServiceTestSuite did not report 7 PASSED, 0 FAILED:\n{combined}")


# ------------------------------------------------------------------------------
# Test 5: Adoptium REST API Live Endpoints (Java 21 & Java 25)
# ------------------------------------------------------------------------------
def test_adoptium_live_api():
    opener = urllib.request.build_opener()
    opener.addheaders = [("User-Agent", "TheLadsLauncher-Adversarial/1.0")]
    urllib.request.install_opener(opener)

    for version in [21, 25]:
        api_url = f"https://api.adoptium.net/v3/assets/latest/{version}/hotspot?os=windows&architecture=x64&image_type=jdk"
        req = urllib.request.Request(api_url)
        with urllib.request.urlopen(req, timeout=15) as resp:
            if resp.status != 200:
                raise AssertionError(f"Adoptium API for Java {version} returned HTTP {resp.status}")
            data = json.loads(resp.read().decode("utf-8"))
            if not isinstance(data, list) or len(data) == 0:
                raise AssertionError(f"Adoptium API for Java {version} did not return a non-empty list")

            first = data[0]
            link = first.get("binary", {}).get("package", {}).get("link", "")
            if not link.startswith("https://"):
                raise AssertionError(f"Invalid package link for Java {version}: {link}")

            # Verify HEAD request on binary link
            head_req = urllib.request.Request(link, method="HEAD")
            with urllib.request.urlopen(head_req, timeout=15) as head_resp:
                if head_resp.status not in (200, 302):
                    raise AssertionError(f"Binary link returned HTTP {head_resp.status}: {link}")
                content_len = int(head_resp.headers.get("Content-Length", 0))
                # Java runtime zip should be >= 100 MB
                if content_len < 100 * 1024 * 1024:
                    raise AssertionError(f"Java {version} package size unusually small: {content_len} bytes")


# ------------------------------------------------------------------------------
# Test 6: Adoptium Fallback URLs Reachability
# ------------------------------------------------------------------------------
def test_adoptium_fallback_urls():
    fallbacks = [
        ("Java 21", "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip"),
        ("Java 25", "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip")
    ]
    opener = urllib.request.build_opener()
    opener.addheaders = [("User-Agent", "TheLadsLauncher-Adversarial/1.0")]
    urllib.request.install_opener(opener)

    for name, url in fallbacks:
        req = urllib.request.Request(url, method="HEAD")
        with urllib.request.urlopen(req, timeout=15) as resp:
            if resp.status not in (200, 302):
                raise AssertionError(f"Fallback URL for {name} returned HTTP {resp.status}: {url}")


# ------------------------------------------------------------------------------
# Test 7: C# Services In-Memory Adversarial Edge Cases (15 tests)
# ------------------------------------------------------------------------------
def test_csharp_services_suite():
    script_path = REPO_ROOT / "tests" / "test_m2_csharp_services.ps1"
    code, out, err = run_cmd(f'pwsh -File "{script_path}"')
    combined = out + "\n" + err
    if code != 0:
        raise AssertionError(f"In-memory C# services suite failed (code {code}):\n{combined}")
    if "Total: 15 | Passed: 15 | Failed: 0" not in combined:
        raise AssertionError(f"C# suite did not report 15 Passed, 0 Failed:\n{combined}")


def main():
    print("=" * 70)
    print(" THE LADS LAUNCHER - MILESTONE 2 MASTER ADVERSARIAL TEST SUITE")
    print("=" * 70)

    run_test("Launcher Clean Debug Build (0 Warnings, 0 Errors)", test_build_debug)
    run_test("Launcher Clean Release Build (0 Warnings, 0 Errors)", test_build_release)
    run_test("Launcher Hardcoded Personal Paths Audit", test_path_audit)
    run_test("Launcher Built-in ServiceTestSuite Execution", test_service_test_suite)
    run_test("Adoptium REST API Live Endpoints & Binary Verification", test_adoptium_live_api)
    run_test("Adoptium Static Fallback Release URLs Reachability", test_adoptium_fallback_urls)
    run_test("In-Memory C# Services Edge Cases (15 Sub-tests)", test_csharp_services_suite)

    print("=" * 70)
    print(f" TOTAL: {total_tests} | PASSED: {passed_tests} | FAILED: {failed_tests}")
    print("=" * 70)

    if failed_tests > 0:
        print("\nFAILURE DETAILS:")
        for name, err in failures:
            print(f"[-] {name}: {err}")
        return 1
    else:
        print("\nALL MILESTONE 2 ADVERSARIAL CHALLENGES EMPIRICALLY PASSED!")
        return 0


if __name__ == "__main__":
    sys.exit(main())
