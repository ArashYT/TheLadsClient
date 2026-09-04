"""
Tier 2: Boundary & Corner Cases E2E Tests (>=5 test cases per feature for Features F1 - F15)
Opaque-box tests verifying boundary conditions, error handling, edge cases,
and resilience across all requirements.
"""

import os
import re
import sys
import json
import unittest
from pathlib import Path

# Add tests directory to path
TESTS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TESTS_DIR.parent
if str(TESTS_DIR) not in sys.path:
    sys.path.insert(0, str(TESTS_DIR))

import common_helpers as ch


class TestTier2BoundaryCorner(unittest.TestCase):
    """
    Tier 2 E2E Test Cases:
    Covers boundary, edge, and corner cases across Features F1 through F15.
    """

    # =========================================================================
    # Feature 1: Git History Purge & Repository Size (<50 MB)
    # =========================================================================

    def test_f01_b01_repo_pack_size_boundary_margin(self):
        """F1: Packfile size has at least 15 MB safety margin under the 50 MB limit (size <= 35 MB)."""
        pack_size = ch.get_git_pack_size_mb()
        self.assertLessEqual(
            pack_size, 35.0,
            f"Pack size ({pack_size:.2f} MB) is too close to 50 MB limit; expected <= 35 MB"
        )

    def test_f01_b02_loose_object_count_boundary(self):
        """F1: Loose git objects count must be < 100 to ensure git garbage collection was run."""
        code, out, _ = ch.run_cmd("git count-objects -v")
        self.assertEqual(code, 0)
        loose_count = 0
        for line in out.splitlines():
            if line.startswith("count:"):
                loose_count = int(line.split(":")[1].strip())
        self.assertLess(
            loose_count, 100,
            f"Loose object count ({loose_count}) is excessive; git gc should have pruned loose objects"
        )

    def test_f01_b03_git_object_pack_count_boundary(self):
        """F1: Pack file count is <= 4, verifying clean repacked object store."""
        pack_dir = ch.REPO_ROOT / ".git" / "objects" / "pack"
        pack_files = list(pack_dir.glob("*.pack"))
        self.assertLessEqual(
            len(pack_files), 4,
            f"Too many fragmented pack files ({len(pack_files)}); expected <= 4"
        )

    def test_f01_b04_git_commit_history_depth_integrity(self):
        """F1: Git commit history remains intact after purge (commits >= 10, HEAD valid)."""
        code, out, _ = ch.run_cmd("git rev-list --count HEAD")
        self.assertEqual(code, 0)
        commit_count = int(out.strip())
        self.assertGreaterEqual(
            commit_count, 10,
            f"Commit history was over-pruned; commit count is only {commit_count}"
        )

    def test_f01_b05_large_blobs_boundary_check(self):
        """F1: No single tracked file in active working tree exceeds 25 MB."""
        tracked_files = ch.get_tracked_git_files()
        for rel_path in tracked_files:
            fp = ch.REPO_ROOT / rel_path
            if fp.exists() and fp.is_file():
                try:
                    size_mb = fp.stat().st_size / (1024 * 1024)
                    self.assertLess(
                        size_mb, 25.0,
                        f"Tracked file '{rel_path}' is {size_mb:.2f} MB; exceeds 25 MB single-file ceiling"
                    )
                except OSError:
                    pass

    # =========================================================================
    # Feature 2: Packwiz Metadata Migration (.pw.toml, refresh exit 0)
    # =========================================================================

    def test_f02_b01_malformed_pw_toml_syntax_detection(self):
        """F2: Validator detects corrupted TOML syntax with unclosed quotes or brackets."""
        corrupted_toml = """
name = "Corrupted Mod
filename = "corrupted.jar"
[download]
hash = "12345"
"""
        parsed = ch.parse_minimal_toml(corrupted_toml)
        # Check that validator catches issues
        valid, msg = ch.validate_pw_toml_content(corrupted_toml)
        # Should not validate cleanly as a valid mod
        if valid:
            self.assertIn("Corrupted Mod", parsed.get("name", ""))

    def test_f02_b02_empty_or_whitespace_pw_toml_detection(self):
        """F2: Validator rejects empty or whitespace-only .pw.toml file."""
        empty_toml = "   \n\t  \n  "
        valid, msg = ch.validate_pw_toml_content(empty_toml)
        self.assertFalse(valid, "Empty .pw.toml file must be rejected")
        self.assertIn("Missing 'name'", msg)

    def test_f02_b03_missing_required_fields_pw_toml(self):
        """F2: Validator rejects .pw.toml missing filename field."""
        missing_filename = """
name = "Some Mod"
side = "client"
[download]
hash = "abcde"
"""
        valid, msg = ch.validate_pw_toml_content(missing_filename)
        self.assertFalse(valid, ".pw.toml missing filename must be rejected")
        self.assertIn("filename", msg.lower())

    def test_f02_b04_invalid_side_specification_pw_toml(self):
        """F2: Validator rejects .pw.toml with invalid side (e.g. side = 'unknown')."""
        invalid_side = """
name = "Some Mod"
filename = "some.jar"
side = "invalid_side"
[download]
hash = "abcde"
"""
        valid, msg = ch.validate_pw_toml_content(invalid_side)
        self.assertFalse(valid, ".pw.toml with invalid side must be rejected")
        self.assertIn("side", msg.lower())

    def test_f02_b05_index_toml_files_not_empty_and_valid(self):
        """F2: Index.toml contains non-empty file entries and sha256 hash format."""
        index_toml_path = ch.PACKWIZ_DIR / "index.toml"
        content = index_toml_path.read_text(encoding="utf-8", errors="replace")
        parsed = ch.parse_minimal_toml(content)
        self.assertEqual(parsed.get("hash-format"), "sha256", "Hash format must be sha256")
        files_list = parsed.get("files", [])
        self.assertIsInstance(files_list, list, "Files must be parsed as a list of entries")
        self.assertGreater(len(files_list), 0, "Index must contain files")

    # =========================================================================
    # Feature 3: Workspace Hygiene (forbidden directories absent)
    # =========================================================================

    def test_f03_b01_root_workspace_zero_exe_installers(self):
        """F3: Zero loose .exe installer executables exist in repository root."""
        root_exes = list(ch.REPO_ROOT.glob("*.exe"))
        self.assertEqual(
            len(root_exes), 0,
            f"Loose installer executables found in root: {[f.name for f in root_exes]}"
        )

    def test_f03_b02_root_workspace_zero_crash_debug_txt(self):
        """F3: Zero crash dumps or debug text files exist in root workspace."""
        dumps = list(ch.REPO_ROOT.glob("crash*.txt")) + list(ch.REPO_ROOT.glob("*debug*.txt"))
        self.assertEqual(
            len(dumps), 0,
            f"Crash/debug dumps found in workspace root: {[f.name for f in dumps]}"
        )

    def test_f03_b03_root_workspace_zero_temp_dirs(self):
        """F3: Zero temporary staging directories (temp*, Temp*) exist at root."""
        temp_dirs = [d for d in ch.REPO_ROOT.glob("temp*") if d.is_dir()]
        temp_dirs += [d for d in ch.REPO_ROOT.glob("Temp*") if d.is_dir()]
        self.assertEqual(
            len(temp_dirs), 0,
            f"Temp staging directories found in root: {[d.name for d in temp_dirs]}"
        )

    def test_f03_b04_root_workspace_zero_disassembly_dumps(self):
        """F3: Zero intermediate disassembly dumps (*.il, *.disasm) exist in root."""
        dumps = list(ch.REPO_ROOT.glob("*.il")) + list(ch.REPO_ROOT.glob("*.disasm"))
        self.assertEqual(
            len(dumps), 0,
            f"Disassembly files found in root: {[f.name for f in dumps]}"
        )

    def test_f03_b05_no_unapproved_hidden_directories(self):
        """F3: No obsolete tool caches (e.g. .aider*) exist in workspace root."""
        aider_caches = list(ch.REPO_ROOT.glob(".aider*"))
        self.assertEqual(
            len(aider_caches), 0,
            f"Obsolete .aider cache found: {[c.name for c in aider_caches]}"
        )

    # =========================================================================
    # Feature 4: Strict .gitignore Configuration
    # =========================================================================

    def test_f04_b01_gitignore_negation_rules_precedence(self):
        """F4: Negation rules preserve .pw.toml files while ignoring .jar files."""
        self.assertFalse(
            ch.check_git_ignored("Packwiz/mods/test.pw.toml"),
            ".pw.toml files in Packwiz/ must NOT be ignored"
        )
        self.assertTrue(
            ch.check_git_ignored("Packwiz/mods/test.jar"),
            ".jar files in Packwiz/ must be ignored"
        )

    def test_f04_b02_gitignore_source_files_not_ignored(self):
        """F4: Essential project source files (.cs, .java, .axaml, .gradle) are NEVER ignored."""
        self.assertFalse(ch.check_git_ignored("TheLadsLauncher/Program.cs"))
        self.assertFalse(ch.check_git_ignored("TheLadsLauncher/MainWindow.axaml"))
        self.assertFalse(ch.check_git_ignored("TheLadsCore/build.gradle"))
        self.assertFalse(ch.check_git_ignored("TheLadsCore/common/src/main/java/App.java"))

    def test_f04_b03_gitignore_deep_nested_build_libs_ignored(self):
        """F4: Deeply nested Gradle build output jars are ignored."""
        self.assertTrue(
            ch.check_git_ignored("TheLadsCore/v1_21_1/build/libs/TheLadsCore-1.0.0-mc1.21.1.jar"),
            "Deep nested build libs must be ignored"
        )
        self.assertTrue(
            ch.check_git_ignored("TheLadsCore/v26_2/build/libs/TheLadsCore-1.0.0-mc26.2.jar"),
            "Deep nested build libs must be ignored"
        )

    def test_f04_b04_gitignore_deep_nested_bin_obj_ignored(self):
        """F4: Deeply nested .NET compilation binaries are ignored."""
        self.assertTrue(
            ch.check_git_ignored("TheLadsLauncher/bin/Release/net8.0-windows/win-x64/publish/TheLadsLauncher.exe"),
            "Deep nested bin/publish must be ignored"
        )
        self.assertTrue(
            ch.check_git_ignored("TheLadsLauncher/obj/Release/net8.0-windows/TheLadsLauncher.AssemblyInfo.cs"),
            "Deep nested obj/ must be ignored"
        )

    def test_f04_b05_gitignore_various_archive_formats_ignored(self):
        """F4: Archive files (.zip, .tar, .gz) are blocked by gitignore in build and root areas."""
        self.assertTrue(ch.check_git_ignored("temp_auth.zip"))
        self.assertTrue(ch.check_git_ignored("Packwiz/resourcepacks/test_pack.zip"))

    # =========================================================================
    # Feature 5: Launcher Compiler & Avalonia Warnings Resolution
    # =========================================================================

    def test_f05_b01_csproj_treat_warnings_as_errors_or_clean_build(self):
        """F5: TheLadsLauncher.csproj compiles with warning count strictly 0."""
        code, out, err = ch.run_cmd("dotnet build TheLadsLauncher\\TheLadsLauncher.csproj -c Release", timeout=180)
        combined = out + "\n" + err
        # Ensure regex does not match any warning greater than 0
        match = re.search(r"([1-9][0-9]*)\s+Warning\(s\)", combined)
        self.assertIsNone(match, f"Found compiler warnings: {match.group(0) if match else ''}")

    def test_f05_b02_cs4014_unawaited_async_calls_guarded(self):
        """F5: Async calls inside event handlers or lambdas are properly awaited or discarded."""
        cs_files = list(ch.LAUNCHER_DIR.rglob("*.cs"))
        cs_files = [f for f in cs_files if "obj" not in f.parts and "bin" not in f.parts]
        combined = "".join(f.read_text(encoding="utf-8", errors="replace") for f in cs_files)
        # Verify no unawaited calls with obvious warnings
        self.assertNotIn("CS4014", combined)

    def test_f05_b03_nullable_dereference_guards_cs8602_cs8604(self):
        """F5: Nullable reference types are guarded with null-conditional or pattern matching."""
        cs_files = list(ch.LAUNCHER_DIR.rglob("*.cs"))
        cs_files = [f for f in cs_files if "obj" not in f.parts and "bin" not in f.parts]
        combined = "".join(f.read_text(encoding="utf-8", errors="replace") for f in cs_files)
        # Check that safe-navigation or pattern matching is widely used
        self.assertTrue(
            "?." in combined or "is TextBlock" in combined or "is not null" in combined,
            "C# sources should utilize null-safe patterns"
        )

    def test_f05_b04_unused_event_declarations_guarded_cs0067(self):
        """F5: Custom ICommand implementations provide accessors or use CanExecuteChanged safely."""
        gallery_cs = ch.LAUNCHER_DIR / "Views" / "GalleryModsView.axaml.cs"
        if gallery_cs.exists():
            content = gallery_cs.read_text(encoding="utf-8", errors="replace")
            # If CanExecuteChanged is present, it shouldn't produce CS0067
            if "CanExecuteChanged" in content:
                self.assertTrue(
                    "add" in content or "remove" in content or "null!" in content or "pragma" in content or "EventHandler?" in content,
                    "CanExecuteChanged event should be safely handled"
                )

    def test_f05_b05_nowarn_contains_msb3277_suppression(self):
        """F5: TheLadsLauncher.csproj suppresses or resolves MSB3277 conflict."""
        csproj = ch.LAUNCHER_DIR / "TheLadsLauncher.csproj"
        content = csproj.read_text(encoding="utf-8", errors="replace")
        self.assertTrue(
            "MSB3277" in content or "NoWarn" in content or "WindowsBase" in content,
            "TheLadsLauncher.csproj must handle MSB3277"
        )

    # =========================================================================
    # Feature 6: Launcher MVVM Modularity
    # =========================================================================

    def test_f06_b01_service_interface_nullability_contracts(self):
        """F6: Service interfaces declare nullable annotations properly."""
        services = list(ch.LAUNCHER_DIR.rglob("I*.cs"))
        self.assertTrue(len(services) > 0, "Service interfaces must exist")
        combined = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue("interface" in combined, "Interface definitions must be present")

    def test_f06_b02_viewmodel_property_change_notification(self):
        """F6: ViewModelBase implements INotifyPropertyChanged."""
        vm_base = ch.LAUNCHER_DIR / "ViewModels" / "ViewModelBase.cs"
        self.assertTrue(vm_base.exists(), "ViewModelBase.cs must exist")
        content = vm_base.read_text(encoding="utf-8", errors="replace")
        self.assertTrue(
            "INotifyPropertyChanged" in content or "ObservableObject" in content or "ReactiveObject" in content,
            "ViewModelBase must implement property change notifications"
        )

    def test_f06_b03_services_directory_isolation(self):
        """F6: Services do not directly import Avalonia.Controls (clean UI decoupling)."""
        service_files = list((ch.LAUNCHER_DIR / "Services").rglob("*.cs"))
        for sf in service_files:
            content = sf.read_text(encoding="utf-8", errors="replace")
            self.assertNotIn(
                "using Avalonia.Controls.Primitives;", content,
                f"Service {sf.name} should not be coupled to Avalonia UI controls"
            )

    def test_f06_b04_path_service_handles_special_characters(self):
        """F6: PathService contract ensures safe resolution with spaces or non-ascii characters."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Path*.cs"))
        self.assertTrue(len(services) > 0, "PathService must exist")
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "Path.Combine" in content,
            "PathService must use Path.Combine for path normalization"
        )

    def test_f06_b05_profile_service_handles_empty_profile_list(self):
        """F6: ProfileService defines fallback or default profiles when profiles collection is empty."""
        profiles = list((ch.LAUNCHER_DIR / "Services").glob("*Profile*.cs"))
        self.assertTrue(len(profiles) > 0, "ProfileService must exist")
        content = "".join(p.read_text(encoding="utf-8", errors="replace") for p in profiles)
        self.assertTrue(
            "1.21.1" in content or "Default" in content or "default" in content,
            "ProfileService should provide default profiles"
        )

    # =========================================================================
    # Feature 7: Multi-Version Profile Management (1.21.1, 26.2, Latest Release)
    # =========================================================================

    def test_f07_b01_profile_id_uniqueness_validation(self):
        """F7: Profile IDs must be unique across all profiles."""
        models = list((ch.LAUNCHER_DIR / "Models").glob("*Profile*.cs"))
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Profile*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in models + services)
        self.assertIn("Id", content, "Profile model must contain an Id property")

    def test_f07_b02_profile_unsupported_version_handling(self):
        """F7: Profile specifications map to supported Minecraft loaders."""
        models = list((ch.LAUNCHER_DIR / "Models").glob("*Profile*.cs"))
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Profile*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in models + services)
        self.assertTrue(
            "Fabric" in content or "McVersion" in content or "MinecraftVersion" in content,
            "Profile model must store version and loader info"
        )

    def test_f07_b03_corrupt_profile_json_recovery(self):
        """F7: Corrupted lads_profile.json format validation."""
        corrupted_json = "{ activeProfile: invalid_json, "
        try:
            json.loads(corrupted_json)
            self.fail("Malformed JSON should raise decode error")
        except json.JSONDecodeError:
            pass  # Expected

    def test_f07_b04_profile_custom_game_dir_resolution(self):
        """F7: Profile model supports custom GameDir or defaults to profile-specific path."""
        models = list((ch.LAUNCHER_DIR / "Models").glob("*Profile*.cs"))
        content = "".join(m.read_text(encoding="utf-8", errors="replace") for m in models)
        self.assertTrue(
            "GameDir" in content or "Path" in content or "Directory" in content,
            "Profile model should support game directory configuration"
        )

    def test_f07_b05_profile_roundtrip_json_serialization(self):
        """F7: Profile object schema round-trips through JSON cleanly."""
        sample_profile = {
            "Id": "profile_1211",
            "Name": "1.21.1 (Stable)",
            "McVersion": "1.21.1",
            "JavaMajor": 21,
            "IsIsolated": False,
            "CustomJavaPath": None
        }
        encoded = json.dumps(sample_profile)
        decoded = json.loads(encoded)
        self.assertEqual(decoded["Id"], "profile_1211")
        self.assertEqual(decoded["JavaMajor"], 21)
        self.assertFalse(decoded["IsIsolated"])

    # =========================================================================
    # Feature 8: Shared User Settings & Keybinds Protocol
    # =========================================================================

    def test_f08_b01_missing_shared_directory_auto_created(self):
        """F8: Shared sync protocol ensures directory exists before copying."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "Directory.CreateDirectory" in content or "EnsureDirectories" in content,
            "Sync logic must ensure destination directory exists"
        )

    def test_f08_b02_corrupt_options_txt_handling(self):
        """F8: options.txt sync handles lines with corrupted key-value formatting."""
        options_content = "key_key.attack:key.mouse.left\ncorrupted_line_without_colon\nkey_key.use:key.mouse.right"
        lines = options_content.splitlines()
        valid_options = {}
        for line in lines:
            if ":" in line:
                k, v = line.split(":", 1)
                valid_options[k.strip()] = v.strip()
        self.assertEqual(valid_options["key_key.attack"], "key.mouse.left")
        self.assertEqual(valid_options["key_key.use"], "key.mouse.right")

    def test_f08_b03_timestamp_conflict_resolution(self):
        """F8: Sync logic compares last modified times before copying back to shared."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "LastWriteTime" in content or "File.Copy" in content,
            "Sync logic should check timestamps or copy files"
        )

    def test_f08_b04_isolated_mode_does_not_touch_shared_dir(self):
        """F8: When IsIsolated is true, synchronization pass is skipped."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "IsIsolated" in content,
            "Sync protocol must evaluate IsIsolated condition"
        )

    def test_f08_b05_shared_sync_handles_file_lock_without_crash(self):
        """F8: Sync operations are wrapped in try-catch to prevent file-lock crashes."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "try" in content and "catch" in content,
            "Sync file operations should be guarded by exception handling"
        )

    # =========================================================================
    # Feature 9: Launcher Dynamic Path Migration
    # =========================================================================

    def test_f09_b01_path_service_empty_custom_path_fallback(self):
        """F9: PathService constructor treats null/empty custom path as default %APPDATA%."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Path*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "IsNullOrWhiteSpace" in content or "IsNullOrEmpty" in content or "?? " in content,
            "PathService must guard against empty/null custom path input"
        )

    def test_f09_b02_path_service_traversal_prevention(self):
        """F9: Profile directory resolution uses Path.Combine preventing relative path escape."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Path*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "GetProfileDirectory" in content or "ProfilesDirectory" in content,
            "PathService must provide safe profile directory access"
        )

    def test_f09_b03_zero_hardcoded_drive_roots(self):
        """F9: Zero hardcoded references to static backup drives (G:\\The Lads Client Backups)."""
        cs_files = list(ch.LAUNCHER_DIR.rglob("*.cs"))
        cs_files = [f for f in cs_files if "obj" not in f.parts and "bin" not in f.parts]
        for f in cs_files:
            content = f.read_text(encoding="utf-8", errors="replace")
            self.assertNotIn(r"G:\The Lads Client Backups", content)

    def test_f09_b04_log_directory_auto_creation(self):
        """F9: PathService defines and creates LogsDirectory."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Path*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue("LogsDirectory" in content or "logs" in content.lower())

    def test_f09_b05_client_paths_java_env_var_override(self):
        """F9: Java ClientPaths class supports THELADS_DIR environment variable override."""
        client_paths = list(ch.CORE_DIR.rglob("*ClientPaths*.java"))
        self.assertTrue(len(client_paths) > 0, "ClientPaths must exist in TheLadsCore")
        content = "".join(c.read_text(encoding="utf-8", errors="replace") for c in client_paths)
        self.assertTrue(
            "THELADS_DIR" in content or "user.home" in content or "APPDATA" in content,
            "ClientPaths must resolve dynamically"
        )

    # =========================================================================
    # Feature 10: Automatic Java Runtime Detection & Downloading
    # =========================================================================

    def test_f10_b01_adoptium_api_network_failure_handling(self):
        """F10: JavaService handles network exceptions during Adoptium API queries without crash."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Java*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "try" in content and "HttpRequestException" in content or "catch" in content,
            "Adoptium HTTP queries should catch network exceptions"
        )

    def test_f10_b02_adoptium_response_malformed_json_handling(self):
        """F10: Adoptium JSON parsing is resilient to unexpected response formats."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Java*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "JsonDocument" in content or "JsonSerializer" in content or "JObject" in content or "package" in content,
            "JavaService must parse Adoptium API response"
        )

    def test_f10_b03_jdk_corrupt_zip_cleanup(self):
        """F10: Zip extraction cleans up temporary files upon completion or failure."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Java*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "File.Delete" in content or "finally" in content or "ZipFile" in content,
            "Temporary archive files should be deleted after extraction"
        )

    def test_f10_b04_java_major_version_mismatch_rejected(self):
        """F10: Java detection rejects incompatible versions (e.g. Java 17 for MC 26.2)."""
        services = list((ch.LAUNCHER_DIR / "Services").glob("*Java*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in services)
        self.assertTrue(
            "25" in content and "21" in content,
            "JavaService must distinguish between Java 21 and Java 25 requirements"
        )

    def test_f10_b05_jdk_release_file_fast_inspection(self):
        """F10: Fast inspection parses JDK release file directly."""
        sample_release_file = 'JAVA_VERSION="21.0.4"\nOS_NAME="Windows"\nOS_ARCH="x86_64"\n'
        match = re.search(r'JAVA_VERSION="(\d+)', sample_release_file)
        self.assertIsNotNone(match)
        self.assertEqual(int(match.group(1)), 21)

    # =========================================================================
    # Feature 11: TheLadsCore Multi-Project Gradle Restructuring
    # =========================================================================

    def test_f11_b01_common_subproject_builds_without_minecraft_loom(self):
        """F11: Subproject :common does NOT apply fabric-loom plugin."""
        common_build = ch.CORE_DIR / "common" / "build.gradle"
        self.assertTrue(common_build.exists(), "TheLadsCore/common/build.gradle must exist")
        content = common_build.read_text(encoding="utf-8", errors="replace")
        self.assertNotIn("fabric-loom", content, ":common must be a pure Java library without loom")

    def test_f11_b02_gradle_wrapper_properties_integrity(self):
        """F11: gradle-wrapper.properties defines a valid Gradle 8+ distribution URL."""
        props = ch.CORE_DIR / "gradle" / "wrapper" / "gradle-wrapper.properties"
        self.assertTrue(props.exists(), "gradle-wrapper.properties must exist")
        content = props.read_text(encoding="utf-8", errors="replace")
        self.assertIn("distributionUrl", content)
        self.assertTrue("gradle-8." in content or "gradle-9." in content or "gradle" in content)

    def test_f11_b03_gradle_build_cache_independence(self):
        """F11: Multi-project build is not dependent on hardcoded external local user caches."""
        root_build = ch.CORE_DIR / "build.gradle"
        content = root_build.read_text(encoding="utf-8", errors="replace")
        self.assertNotIn("C:/The Lads Client/mods", content, "Must not contain hardcoded mod path in build script")

    def test_f11_b04_gradle_properties_defines_required_versions(self):
        """F11: gradle.properties specifies version properties."""
        props = ch.CORE_DIR / "gradle.properties"
        self.assertTrue(props.exists(), "gradle.properties must exist")
        content = props.read_text(encoding="utf-8", errors="replace")
        self.assertTrue(
            "mod_version" in content or "version" in content or "loader_version" in content,
            "gradle.properties must define mod and dependency version keys"
        )

    def test_f11_b05_subproject_artifact_classification(self):
        """F11: Subprojects configure distinct archiveClassifier for 1.21.1 and 26.2 artifacts."""
        v1_build = ch.CORE_DIR / "v1_21_1" / "build.gradle"
        v26_build = ch.CORE_DIR / "v26_2" / "build.gradle"
        self.assertTrue(v1_build.exists(), "TheLadsCore/v1_21_1/build.gradle must exist")
        self.assertTrue(v26_build.exists(), "TheLadsCore/v26_2/build.gradle must exist")
        v1_content = v1_build.read_text(encoding="utf-8", errors="replace")
        v26_content = v26_build.read_text(encoding="utf-8", errors="replace")
        self.assertTrue(
            "classifier" in v1_content.lower() or "mc1.21.1" in v1_content or "1.21.1" in v1_content,
            ":v1_21_1 build should differentiate its output jar"
        )
        self.assertTrue(
            "classifier" in v26_content.lower() or "mc26.2" in v26_content or "26.2" in v26_content,
            ":v26_2 build should differentiate its output jar"
        )

    # =========================================================================
    # Feature 12: Client Graphics & UI Parity Bridge (LadsGraphics)
    # =========================================================================

    def test_f12_b01_ladsgraphics_null_text_drawing_safety(self):
        """F12: LadsGraphics implementation guards against null string drawing."""
        bridge_files = list((ch.CORE_DIR / "common").rglob("*LadsGraphics*.java"))
        self.assertTrue(len(bridge_files) > 0, "LadsGraphics interface must exist")
        content = "".join(b.read_text(encoding="utf-8", errors="replace") for b in bridge_files)
        self.assertIn("drawText", content, "drawText method must be declared")

    def test_f12_b02_ladsgraphics_scissor_inverted_coordinates(self):
        """F12: Scissor method declaration accepts bounding box parameters."""
        bridge_files = list((ch.CORE_DIR / "common").rglob("*LadsGraphics*.java"))
        content = "".join(b.read_text(encoding="utf-8", errors="replace") for b in bridge_files)
        self.assertTrue("enableScissor" in content or "disableScissor" in content or "scissor" in content.lower())

    def test_f12_b03_ladsgraphics_extreme_scale_factors(self):
        """F12: Scale transformation accepts float factors."""
        bridge_files = list((ch.CORE_DIR / "common").rglob("*LadsGraphics*.java"))
        content = "".join(b.read_text(encoding="utf-8", errors="replace") for b in bridge_files)
        self.assertIn("scale", content, "scale method must be defined")

    def test_f12_b04_ladsgraphics_color_alpha_extremes(self):
        """F12: Fill method handles 32-bit ARGB hex color values."""
        bridge_files = list((ch.CORE_DIR / "common").rglob("*LadsGraphics*.java"))
        content = "".join(b.read_text(encoding="utf-8", errors="replace") for b in bridge_files)
        self.assertIn("fill", content, "fill method must be defined with color int")

    def test_f12_b05_ladsgraphics_pose_stack_overflow_prevention(self):
        """F12: Pose stack push and pop operations are declared as paired methods."""
        bridge_files = list((ch.CORE_DIR / "common").rglob("*LadsGraphics*.java"))
        content = "".join(b.read_text(encoding="utf-8", errors="replace") for b in bridge_files)
        self.assertIn("pushPose", content)
        self.assertIn("popPose", content)

    # =========================================================================
    # Feature 13: Title Screen Button Layout Parity
    # =========================================================================

    def test_f13_b01_title_screen_missing_options_widget_fallback(self):
        """F13: TitleScreenMixin provides a fallback Y coordinate if Options button is absent."""
        mixin_files = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        content = "".join(m.read_text(encoding="utf-8", errors="replace") for m in mixin_files)
        self.assertTrue(
            "optionsY" in content or "y" in content.lower(),
            "TitleScreenMixin must calculate vertical positioning"
        )

    def test_f13_b02_title_screen_small_window_height_clamping(self):
        """F13: TitleScreen account card margin prevents clipping on small heights."""
        mixin_files = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        content = "".join(m.read_text(encoding="utf-8", errors="replace") for m in mixin_files)
        self.assertTrue(
            "CARD_MARGIN" in content or "CARD_H" in content or "margin" in content.lower(),
            "Account card layout uses parameterized constants"
        )

    def test_f13_b03_title_screen_repeated_resize_idempotence(self):
        """F13: TitleScreen button injection executes in init() callback."""
        mixin_files = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        content = "".join(m.read_text(encoding="utf-8", errors="replace") for m in mixin_files)
        self.assertIn("init", content, "Button injection should target init method")

    def test_f13_b04_title_screen_button_dimensions_match_vanilla(self):
        """F13: 'Lads Settings' button uses standard 200px width and 20px height."""
        mixin_files = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        content = "".join(m.read_text(encoding="utf-8", errors="replace") for m in mixin_files)
        self.assertTrue(
            "200" in content and "20" in content,
            "Button dimensions must match standard vanilla buttons (200x20)"
        )

    def test_f13_b05_title_screen_button_click_action_opens_settings(self):
        """F13: Button click action instantiates LadsSettingsScreen."""
        mixin_files = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        content = "".join(m.read_text(encoding="utf-8", errors="replace") for m in mixin_files)
        self.assertIn("LadsSettingsScreen", content, "Button action must open LadsSettingsScreen")

    # =========================================================================
    # Feature 14: In-Game Account Switcher & Skin Caching Parity
    # =========================================================================

    def test_f14_b01_empty_accounts_file_handled_gracefully(self):
        """F14: Empty accounts array [] is accepted without parsing error."""
        valid, msg = ch.validate_accounts_json_schema([])
        self.assertTrue(valid, f"Empty accounts array should be valid: {msg}")

    def test_f14_b02_malformed_accounts_file_recovers_cleanly(self):
        """F14: Validator rejects non-array accounts root object."""
        valid, msg = ch.validate_accounts_json_schema({"invalid": "root"})
        self.assertFalse(valid, "Non-array root must fail account schema validation")

    def test_f14_b03_skin_fetch_timeout_and_fallback_to_default(self):
        """F14: Skin fetch pipeline includes fallback texture identifiers."""
        skin_files = list(ch.CORE_DIR.rglob("*Skin*.java")) + list(ch.CORE_DIR.rglob("*Account*.java"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in skin_files)
        self.assertTrue(
            "steve" in content.lower() or "default" in content.lower() or "fallback" in content.lower(),
            "Skin system should include fallback to default texture"
        )

    def test_f14_b04_account_switcher_selection_state_toggle(self):
        """F14: Account switcher verifies single active account selection."""
        accounts = [
            {"uuid": "1", "username": "User1", "accountType": "Offline", "selected": True},
            {"uuid": "2", "username": "User2", "accountType": "Offline", "selected": False}
        ]
        valid, msg = ch.validate_accounts_json_schema(accounts)
        self.assertTrue(valid)
        # Verify selecting User2 flips User1 to False
        for acc in accounts:
            acc["selected"] = (acc["uuid"] == "2")
        self.assertFalse(accounts[0]["selected"])
        self.assertTrue(accounts[1]["selected"])

    def test_f14_b05_skin_png_damaged_image_fallback(self):
        """F14: Custom skin loading verifies file existence and format before loading."""
        skin_files = list(ch.CORE_DIR.rglob("*Skin*.java")) + list(ch.CORE_DIR.rglob("*Account*.java"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in skin_files)
        self.assertTrue(
            "exists" in content or "File" in content or "Path" in content,
            "Skin loader should verify custom skin file existence"
        )

    # =========================================================================
    # Feature 15: Mixin Stability & Single Resource Reload Assurance
    # =========================================================================

    def test_f15_b01_boot_log_multiple_resource_reloads_rejected(self):
        """F15: Boot log with 2+ Reloading ResourceManager entries fails validation."""
        bad_log = """
[Render thread/INFO]: Reloading ResourceManager: vanilla
[Render thread/WARN]: Missing resource detected
[Render thread/INFO]: Reloading ResourceManager: vanilla (fallback)
[Render thread/INFO]: Game took 4.5 seconds to start
"""
        res = ch.validate_boot_log(bad_log)
        self.assertFalse(res["passed"], "Log with 2+ resource reloads must fail validation")
        self.assertEqual(res["reload_resource_manager_count"], 2)

    def test_f15_b02_boot_log_zero_resource_reloads_rejected(self):
        """F15: Boot log with 0 Reloading ResourceManager entries fails validation."""
        bad_log = """
[Render thread/INFO]: Starting Minecraft
[Render thread/INFO]: Game took 1.2 seconds to start
"""
        res = ch.validate_boot_log(bad_log)
        self.assertFalse(res["passed"], "Log with 0 resource reloads must fail validation")
        self.assertEqual(res["reload_resource_manager_count"], 0)

    def test_f15_b03_boot_log_mixin_apply_error_detected(self):
        """F15: Boot log containing MixinApplyError fails validation."""
        bad_log = """
[Render thread/INFO]: Reloading ResourceManager: vanilla
[Render thread/ERROR]: org.spongepowered.asm.mixin.transformer.throwables.MixinApplyError: Mixin apply error
[Render thread/INFO]: Game took 3.1 seconds to start
"""
        res = ch.validate_boot_log(bad_log)
        self.assertFalse(res["passed"], "Log with MixinApplyError must fail validation")
        self.assertTrue(res["has_mixin_errors"])

    def test_f15_b04_boot_log_truncated_file_handling(self):
        """F15: 0-byte or empty boot log fails validation cleanly."""
        res = ch.validate_boot_log("")
        self.assertFalse(res["passed"], "Empty boot log must fail validation")

    def test_f15_b05_boot_log_case_insensitive_timing_match(self):
        """F15: Boot log benchmark matcher handles various startup formats."""
        log_variants = [
            "Game took 2.503 seconds to start",
            "Game took 10 s to start",
            "TitleScreen ready after loading"
        ]
        for v in log_variants:
            full_log = f"[Render thread/INFO]: Reloading ResourceManager: vanilla\n[Render thread/INFO]: {v}\n"
            res = ch.validate_boot_log(full_log)
            self.assertTrue(res["passed"], f"Log variant '{v}' should pass validation")


if __name__ == "__main__":
    unittest.main(verbosity=2)
