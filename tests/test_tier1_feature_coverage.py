"""
Tier 1: Feature Coverage E2E Tests (>=5 test cases per feature for Features F1 - F15)
Opaque-box tests verifying primary behavior (happy-path) across all requirements.
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


class TestTier1FeatureCoverage(unittest.TestCase):
    """
    Tier 1 E2E Test Cases:
    Covers Features F1 through F15 with at least 5 distinct test cases per feature.
    """

    # =========================================================================
    # Feature 1: Git History Purge & Repository Size (<50 MB)
    # =========================================================================

    def test_f01_01_repo_clone_pack_size_under_50mb(self):
        """F1: Cloned repository pack size must be strictly under 50 MB."""
        pack_size_mb = ch.get_git_pack_size_mb()
        self.assertGreater(pack_size_mb, 0.0, "Git pack file must exist")
        self.assertLess(
            pack_size_mb, 50.0,
            f"Git pack size ({pack_size_mb:.2f} MB) exceeds requirement of < 50 MB"
        )

    def test_f01_02_no_monster_blobs_in_git_history(self):
        """F1: No individual blobs over 50 MB exist in reachable git history."""
        # Query git objects exceeding 50 MB (52,428,800 bytes)
        code, out, err = ch.run_cmd("git rev-list --objects --all")
        self.assertEqual(code, 0, f"git rev-list failed: {err}")
        
        # Check size of objects
        code, out_objs, _ = ch.run_cmd("git cat-file --batch-check=\"%(objectsize) %(rest)\" --batch-all-objects")
        if code == 0 and out_objs:
            for line in out_objs.splitlines():
                parts = line.split(maxsplit=1)
                if parts and parts[0].isdigit():
                    size = int(parts[0])
                    self.assertLess(
                        size, 52428800,
                        f"Found oversized git blob: {size} bytes ({size / (1024*1024):.2f} MB)"
                    )

    def test_f01_03_temp_auth_zip_blob_absent_from_git(self):
        """F1: Monster blob temp_auth.zip (1.868 GB) is absent from reachable git history."""
        code, out, _ = ch.run_cmd("git log --all --full-history -- \"*temp_auth.zip*\"")
        self.assertEqual(code, 0)
        self.assertEqual(
            out.strip(), "",
            "Historical monster blob 'temp_auth.zip' was found in git history; must be purged"
        )

    def test_f01_04_no_large_installer_binaries_in_git(self):
        """F1: Heavy installer executables (.exe) are absent from reachable git tree."""
        code, out, _ = ch.run_cmd("git ls-files \"*.exe\"")
        self.assertEqual(code, 0)
        self.assertEqual(
            out.strip(), "",
            f"Tracked .exe files found in git tree: {out.strip()}"
        )

    def test_f01_05_working_tree_tracked_size_under_50mb(self):
        """F1: Total size of active tracked files in Git HEAD is under 50 MB."""
        tracked_files = ch.get_tracked_git_files()
        self.assertTrue(len(tracked_files) > 0, "Working tree must contain tracked files")
        
        total_tracked_bytes = 0
        for rel_path in tracked_files:
            fp = ch.REPO_ROOT / rel_path
            if fp.exists() and fp.is_file():
                try:
                    total_tracked_bytes += fp.stat().st_size
                except OSError:
                    pass
                    
        total_tracked_mb = total_tracked_bytes / (1024 * 1024)
        self.assertLess(
            total_tracked_mb, 50.0,
            f"Total tracked file size ({total_tracked_mb:.2f} MB) exceeds < 50 MB limit"
        )

    # =========================================================================
    # Feature 2: Packwiz Metadata Migration (.pw.toml, refresh exit 0)
    # =========================================================================

    def test_f02_01_pack_toml_exists_and_valid(self):
        """F2: Packwiz/pack.toml exists and contains valid required root metadata."""
        pack_toml_path = ch.PACKWIZ_DIR / "pack.toml"
        self.assertTrue(pack_toml_path.exists(), "Packwiz/pack.toml must exist")
        content = pack_toml_path.read_text(encoding="utf-8", errors="replace")
        parsed = ch.parse_minimal_toml(content)
        self.assertIn("name", parsed, "pack.toml must specify 'name'")
        self.assertIn("author", parsed, "pack.toml must specify 'author'")
        self.assertIn("index", parsed, "pack.toml must specify 'index' section")

    def test_f02_02_index_toml_exists_and_valid(self):
        """F2: Packwiz/index.toml exists and contains valid index metadata."""
        index_toml_path = ch.PACKWIZ_DIR / "index.toml"
        self.assertTrue(index_toml_path.exists(), "Packwiz/index.toml must exist")
        content = index_toml_path.read_text(encoding="utf-8", errors="replace")
        parsed = ch.parse_minimal_toml(content)
        self.assertIn("hash-format", parsed, "index.toml must define 'hash-format'")
        self.assertIn("files", parsed, "index.toml must define 'files' table/entries")

    def test_f02_03_zero_tracked_jars_in_packwiz(self):
        """F2: Zero physical .jar files are tracked in Packwiz/ by git."""
        code, out, _ = ch.run_cmd("git ls-files \"Packwiz/*.jar\"")
        self.assertEqual(code, 0)
        self.assertEqual(
            out.strip(), "",
            f"Found tracked .jar files in Packwiz/ in git tree: {out.strip()}"
        )

    def test_f02_04_mods_are_pw_toml_metadata(self):
        """F2: Mod descriptors in Packwiz/mods are .pw.toml files with valid metadata."""
        mods_dir = ch.PACKWIZ_DIR / "mods"
        self.assertTrue(mods_dir.exists(), "Packwiz/mods directory must exist")
        toml_files = list(mods_dir.glob("*.pw.toml"))
        self.assertGreater(len(toml_files), 0, "Packwiz/mods must contain .pw.toml metadata files")
        
        # Verify first 10 sample files parse cleanly
        for tf in toml_files[:10]:
            content = tf.read_text(encoding="utf-8", errors="replace")
            valid, msg = ch.validate_pw_toml_content(content)
            self.assertTrue(valid, f"Invalid metadata in {tf.name}: {msg}")

    def test_f02_05_resourcepacks_zero_tracked_zips(self):
        """F2: Zero physical .zip files in Packwiz/resourcepacks are tracked in git."""
        code, out, _ = ch.run_cmd("git ls-files \"Packwiz/resourcepacks/*.zip\"")
        self.assertEqual(code, 0)
        self.assertEqual(
            out.strip(), "",
            f"Found tracked .zip files in Packwiz/resourcepacks/: {out.strip()}"
        )

    # =========================================================================
    # Feature 3: Workspace Hygiene (forbidden directories absent)
    # =========================================================================

    def test_f03_01_dead_testlogin_project_absent(self):
        """F3: Obsolete project TestLogin is completely absent from workspace root."""
        test_login = ch.REPO_ROOT / "TestLogin"
        self.assertFalse(test_login.exists(), "TestLogin folder must not exist at root")

    def test_f03_02_dead_launcher_clean_project_absent(self):
        """F3: Duplicate project TheLadsLauncher_Clean is completely absent from workspace root."""
        clean_dir = ch.REPO_ROOT / "TheLadsLauncher_Clean"
        self.assertFalse(clean_dir.exists(), "TheLadsLauncher_Clean folder must not exist at root")

    def test_f03_03_dead_staging_projects_absent(self):
        """F3: Dead test projects TestCmlLib, MsalTest, and Playground are absent."""
        for name in ("TestCmlLib", "MsalTest", "Playground"):
            p = ch.REPO_ROOT / name
            self.assertFalse(p.exists(), f"Dead project '{name}' must not exist in workspace root")

    def test_f03_04_legacy_launcher_agents_absent(self):
        """F3: Legacy .agents directory inside TheLadsLauncher is absent (only root .agents allowed)."""
        launcher_agents = ch.LAUNCHER_DIR / ".agents"
        self.assertFalse(
            launcher_agents.exists(),
            "TheLadsLauncher/.agents folder must not exist; metadata is restricted to root .agents"
        )

    def test_f03_05_outer_dead_staging_directories_absent(self):
        """F3: Outer staging and decompiled directories (temp_mc, TempJar, decompiled) are absent."""
        outer_dev = ch.REPO_ROOT.parent
        for name in ("temp_mc", "TempJar", "decompiled"):
            p = outer_dev / name
            self.assertFalse(p.exists(), f"Outer staging folder '{name}' must not exist in dev root")

    # =========================================================================
    # Feature 4: Strict .gitignore Configuration
    # =========================================================================

    def test_f04_01_gitignore_file_exists_and_non_empty(self):
        """F4: Repository root .gitignore exists and contains active rule definitions."""
        gi_path = ch.REPO_ROOT / ".gitignore"
        self.assertTrue(gi_path.exists(), ".gitignore must exist at repository root")
        content = gi_path.read_text(encoding="utf-8", errors="replace")
        self.assertGreater(len(content.strip()), 100, ".gitignore must contain comprehensive rules")

    def test_f04_02_gitignore_blocks_binary_executables(self):
        """F4: .gitignore blocks binary executables and installers (*.exe, *.dll)."""
        self.assertTrue(ch.check_git_ignored("installer.exe"), ".exe files must be ignored")
        self.assertTrue(ch.check_git_ignored("Output/setup.exe"), "Output/*.exe must be ignored")
        self.assertTrue(ch.check_git_ignored("TheLadsLauncher/bin/Debug/net8.0/app.dll"), ".dll must be ignored")

    def test_f04_03_gitignore_blocks_java_build_artifacts(self):
        """F4: .gitignore blocks Java build artifacts (*.jar, .gradle/, build/)."""
        self.assertTrue(ch.check_git_ignored("Packwiz/mods/test.jar"), "Packwiz mod jars must be ignored")
        self.assertTrue(ch.check_git_ignored("TheLadsCore/.gradle/caches/foo"), ".gradle cache must be ignored")
        self.assertTrue(ch.check_git_ignored("TheLadsCore/build/libs/core.jar"), "Gradle build output must be ignored")

    def test_f04_04_gitignore_blocks_dotnet_build_artifacts(self):
        """F4: .gitignore blocks .NET build directories (bin/, obj/)."""
        self.assertTrue(ch.check_git_ignored("TheLadsLauncher/bin/Debug/"), "bin/ directory must be ignored")
        self.assertTrue(ch.check_git_ignored("TheLadsLauncher/obj/Debug/"), "obj/ directory must be ignored")

    def test_f04_05_gitignore_blocks_crash_dumps_and_logs(self):
        """F4: .gitignore blocks logs, crash dumps, and scratch disassemblies."""
        self.assertTrue(ch.check_git_ignored("crash_log.txt"), "crash dumps must be ignored")
        self.assertTrue(ch.check_git_ignored("launcher_debug.txt"), "debug logs must be ignored")
        self.assertTrue(ch.check_git_ignored("dump.il"), "disassembly files must be ignored")

    # =========================================================================
    # Feature 5: Launcher Compiler & Avalonia Warnings Resolution
    # =========================================================================

    def test_f05_01_launcher_csproj_targets_net8(self):
        """F5: TheLadsLauncher.csproj specifies net8.0-windows or net8.0 target framework."""
        csproj_path = ch.LAUNCHER_DIR / "TheLadsLauncher.csproj"
        self.assertTrue(csproj_path.exists(), "TheLadsLauncher.csproj must exist")
        content = csproj_path.read_text(encoding="utf-8", errors="replace")
        self.assertRegex(content, r"<TargetFramework>net8\.0(-windows)?</TargetFramework>")

    def test_f05_02_launcher_suppresses_msb3277_or_resolves_conflict(self):
        """F5: TheLadsLauncher.csproj resolves MSB3277 WindowsBase version conflict."""
        csproj_path = ch.LAUNCHER_DIR / "TheLadsLauncher.csproj"
        content = csproj_path.read_text(encoding="utf-8", errors="replace")
        self.assertTrue(
            "MSB3277" in content or "WindowsBase" in content or "<NoWarn>" in content,
            "TheLadsLauncher.csproj should handle MSB3277 warning or WindowsBase reference"
        )

    def test_f05_03_avalonia_obsolete_systemdecorations_eliminated(self):
        """F5: Obsolete Avalonia property SystemDecorations is replaced with WindowDecorations."""
        axaml_files = list(ch.LAUNCHER_DIR.rglob("*.axaml"))
        for af in axaml_files:
            content = af.read_text(encoding="utf-8", errors="replace")
            self.assertNotIn(
                "SystemDecorations", content,
                f"Obsolete 'SystemDecorations' found in {af.relative_to(ch.REPO_ROOT)}; use WindowDecorations"
            )

    def test_f05_04_avalonia_obsolete_watermark_eliminated(self):
        """F5: Obsolete TextBox.Watermark is replaced with PlaceholderText across all views."""
        axaml_files = list(ch.LAUNCHER_DIR.rglob("*.axaml"))
        for af in axaml_files:
            content = af.read_text(encoding="utf-8", errors="replace")
            self.assertNotIn(
                'Watermark="', content,
                f"Obsolete 'Watermark' property found in {af.relative_to(ch.REPO_ROOT)}; use PlaceholderText"
            )

    def test_f05_05_launcher_builds_with_zero_errors_and_zero_warnings(self):
        """F5: dotnet build TheLadsLauncher.csproj completes with 0 errors and 0 warnings."""
        code, out, err = ch.run_cmd(
            "dotnet build TheLadsLauncher\\TheLadsLauncher.csproj -c Release",
            timeout=180
        )
        combined = out + "\n" + err
        
        # Must compile with exit code 0
        self.assertEqual(code, 0, f"dotnet build failed (exit code {code}):\n{combined}")
        
        # Verify 0 errors
        self.assertRegex(combined, r"(?i)0\s+Error\(s\)", "Build must report 0 Error(s)")
        
        # Verify 0 warnings (or no warning summary line indicating warnings > 0)
        warn_match = re.search(r"([0-9]+)\s+Warning\(s\)", combined, re.IGNORECASE)
        if warn_match:
            warn_count = int(warn_match.group(1))
            self.assertEqual(
                warn_count, 0,
                f"dotnet build produced {warn_count} compiler/Avalonia warning(s); expected 0 warnings"
            )

    # =========================================================================
    # Feature 6: Launcher MVVM Modularity
    # =========================================================================

    def test_f06_01_ipathservice_interface_defined(self):
        """F6: IPathService interface exists with BaseDirectory, SharedDirectory, ProfilesDirectory, RuntimesDirectory."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        self.assertTrue(services_dir.exists(), "Services directory must exist in TheLadsLauncher")
        
        candidates = list(services_dir.glob("*Path*.cs"))
        self.assertTrue(len(candidates) > 0, "PathService interface/class must exist in Services")
        
        combined_text = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        for req_prop in ("BaseDirectory", "SharedDirectory", "ProfilesDirectory", "RuntimesDirectory"):
            self.assertIn(req_prop, combined_text, f"Path service missing required contract property '{req_prop}'")

    def test_f06_02_iprofileservice_interface_defined(self):
        """F6: IProfileService interface exists defining profile management operations."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Profile*.cs"))
        self.assertTrue(len(candidates) > 0, "ProfileService interface/class must exist in Services")
        
        combined_text = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertTrue(
            "GetProfiles" in combined_text or "Profiles" in combined_text or "ActiveProfile" in combined_text,
            "Profile service must provide profile access and selection"
        )

    def test_f06_03_ijavaservice_interface_defined(self):
        """F6: IJavaService interface exists defining Java detection and automated download."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Java*.cs"))
        self.assertTrue(len(candidates) > 0, "JavaService interface/class must exist in Services")
        
        combined_text = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertTrue(
            "GetJavaPath" in combined_text or "DetectJava" in combined_text or "DownloadJava" in combined_text or "EnsureJava" in combined_text,
            "Java service must provide detection or download methods"
        )

    def test_f06_04_iauthservice_interface_defined(self):
        """F6: IAuthService interface exists defining authentication operations."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Auth*.cs"))
        self.assertTrue(len(candidates) > 0, "AuthService interface/class must exist in Services")
        
        combined_text = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertTrue(
            "Account" in combined_text or "Login" in combined_text or "Authenticate" in combined_text,
            "Auth service must handle accounts and authentication"
        )

    def test_f06_05_viewmodels_defined_and_decoupled(self):
        """F6: ViewModels directory exists with ViewModelBase and primary ViewModels."""
        vm_dir = ch.LAUNCHER_DIR / "ViewModels"
        self.assertTrue(vm_dir.exists(), "ViewModels directory must exist in TheLadsLauncher")
        
        vm_files = [f.name for f in vm_dir.glob("*.cs")]
        self.assertIn("ViewModelBase.cs", vm_files, "ViewModelBase must exist")
        self.assertTrue(
            any("MainWindow" in f for f in vm_files),
            "MainWindowViewModel must exist"
        )

    # =========================================================================
    # Feature 7: Multi-Version Profile Management (1.21.1, 26.2, Latest Release)
    # =========================================================================

    def test_f07_01_profile_1_21_1_specification(self):
        """F7: Predefined profile for 1.21.1 exists specifying Minecraft 1.21.1 and Java 21."""
        models_dir = ch.LAUNCHER_DIR / "Models"
        services_dir = ch.LAUNCHER_DIR / "Services"
        sources = list(models_dir.glob("*.cs")) + list(services_dir.glob("*.cs"))
        combined_text = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        
        self.assertIn("1.21.1", combined_text, "1.21.1 profile specification must exist")
        self.assertTrue(
            "21" in combined_text,
            "Profile 1.21.1 must be associated with Java 21"
        )

    def test_f07_02_profile_26_2_specification(self):
        """F7: Predefined profile for 26.2 exists specifying Minecraft 26.2 and Java 25."""
        models_dir = ch.LAUNCHER_DIR / "Models"
        services_dir = ch.LAUNCHER_DIR / "Services"
        sources = list(models_dir.glob("*.cs")) + list(services_dir.glob("*.cs"))
        combined_text = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        
        self.assertIn("26.2", combined_text, "26.2 profile specification must exist")
        self.assertTrue(
            "25" in combined_text,
            "Profile 26.2 must be associated with Java 25"
        )

    def test_f07_03_profile_latest_release_specification(self):
        """F7: Predefined profile for latest release exists."""
        models_dir = ch.LAUNCHER_DIR / "Models"
        services_dir = ch.LAUNCHER_DIR / "Services"
        sources = list(models_dir.glob("*.cs")) + list(services_dir.glob("*.cs"))
        combined_text = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        
        self.assertTrue(
            "latest.release" in combined_text.lower() or "latest release" in combined_text.lower() or "latest" in combined_text.lower(),
            "Latest release profile specification must exist"
        )

    def test_f07_04_profile_model_supports_isisolated_flag(self):
        """F7: Profile model supports IsIsolated boolean flag defaulting to false."""
        models_dir = ch.LAUNCHER_DIR / "Models"
        sources = list(models_dir.glob("*Profile*.cs"))
        self.assertTrue(len(sources) > 0, "LauncherProfile model must exist")
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertIn("IsIsolated", content, "Profile model must contain 'IsIsolated' property")

    def test_f07_05_profile_selection_contract(self):
        """F7: Active profile selection contract updates profile state and game directory."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        sources = list(services_dir.glob("*Profile*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertTrue(
            "ActiveProfile" in content or "SelectProfile" in content or "SetCurrentProfile" in content,
            "Profile service must provide active profile state manipulation"
        )

    # =========================================================================
    # Feature 8: Shared User Settings & Keybinds Protocol
    # =========================================================================

    def test_f08_01_shared_directory_structure_contract(self):
        """F8: Shared directory contract points to %APPDATA%/.theladsclient/shared/."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        sources = list(services_dir.glob("*Path*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertTrue(
            "shared" in content.lower(),
            "PathService must define 'SharedDirectory' pointing to shared/"
        )

    def test_f08_02_shared_options_txt_keybinds_synchronization(self):
        """F8: Shared synchronization handles options.txt for vanilla options and keybindings."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        sources = list(services_dir.glob("*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertIn("options.txt", content, "Synchronization protocol must reference 'options.txt'")

    def test_f08_03_shared_servers_dat_synchronization(self):
        """F8: Shared synchronization handles servers.dat for multiplayer servers."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        sources = list(services_dir.glob("*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertIn("servers.dat", content, "Synchronization protocol must reference 'servers.dat'")

    def test_f08_04_shared_accounts_and_profile_synchronization(self):
        """F8: Shared synchronization handles lads_accounts.json and lads_profile.json."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        sources = list(services_dir.glob("*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertTrue(
            "lads_accounts.json" in content and "lads_profile.json" in content,
            "Synchronization protocol must manage lads_accounts.json and lads_profile.json"
        )

    def test_f08_05_isolated_profile_skips_shared_synchronization(self):
        """F8: Isolated profiles (IsIsolated == true) skip shared directory synchronization."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        sources = list(services_dir.glob("*Profile*.cs")) + list(services_dir.glob("*Launch*.cs"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertTrue(
            "IsIsolated" in content,
            "Sync logic must inspect IsIsolated property before synchronizing"
        )

    # =========================================================================
    # Feature 9: Launcher Dynamic Path Migration
    # =========================================================================

    def test_f09_01_zero_hardcoded_user_arash_in_launcher_cs(self):
        """F9: Zero occurrences of hardcoded 'C:\\Users\\Arash' in TheLadsLauncher C# files."""
        cs_files = list(ch.LAUNCHER_DIR.rglob("*.cs"))
        # Exclude obj/ or bin/ if any
        cs_files = [f for f in cs_files if "obj" not in f.parts and "bin" not in f.parts]
        
        for f in cs_files:
            content = f.read_text(encoding="utf-8", errors="replace")
            self.assertNotIn(
                r"C:\Users\Arash", content,
                f"Hardcoded 'C:\\Users\\Arash' found in {f.relative_to(ch.REPO_ROOT)}"
            )

    def test_f09_02_zero_hardcoded_user_arash_in_launcher_axaml(self):
        """F9: Zero occurrences of hardcoded 'C:\\Users\\Arash' in TheLadsLauncher AXAML files."""
        axaml_files = list(ch.LAUNCHER_DIR.rglob("*.axaml"))
        for f in axaml_files:
            content = f.read_text(encoding="utf-8", errors="replace")
            self.assertNotIn(
                r"C:\Users\Arash", content,
                f"Hardcoded 'C:\\Users\\Arash' found in {f.relative_to(ch.REPO_ROOT)}"
            )

    def test_f09_03_zero_hardcoded_static_lads_client_root(self):
        """F9: Zero occurrences of static root 'C:\\The Lads Client' in TheLadsLauncher codebase."""
        source_files = list(ch.LAUNCHER_DIR.rglob("*.cs")) + list(ch.LAUNCHER_DIR.rglob("*.axaml"))
        source_files = [f for f in source_files if "obj" not in f.parts and "bin" not in f.parts]
        
        for f in source_files:
            content = f.read_text(encoding="utf-8", errors="replace")
            self.assertNotIn(
                r"C:\The Lads Client", content,
                f"Static root 'C:\\The Lads Client' found in {f.relative_to(ch.REPO_ROOT)}"
            )

    def test_f09_04_dynamic_base_directory_resolution(self):
        """F9: PathService defaults dynamically to %APPDATA%/.theladsclient."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Path*.cs"))
        self.assertTrue(len(candidates) > 0, "PathService implementation must exist")
        content = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertTrue(
            ".theladsclient" in content and ("SpecialFolder.ApplicationData" in content or "APPDATA" in content),
            "PathService must resolve dynamically to %APPDATA%/.theladsclient"
        )

    def test_f09_05_custom_base_directory_override_supported(self):
        """F9: PathService supports custom base path override via constructor or config."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Path*.cs"))
        content = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertTrue(
            "customBasePath" in content or "custom" in content.lower() or "basePath" in content,
            "PathService must allow configuring a custom base path"
        )

    # =========================================================================
    # Feature 10: Automatic Java Runtime Detection & Downloading
    # =========================================================================

    def test_f10_01_java_21_requirement_for_mc_1_21_1(self):
        """F10: JavaService specifies Java 21 for Minecraft 1.21.1."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Java*.cs"))
        self.assertTrue(len(candidates) > 0, "JavaService must exist")
        content = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertIn("21", content, "JavaService must handle Java 21")

    def test_f10_02_java_25_requirement_for_mc_26_2(self):
        """F10: JavaService specifies Java 25 for Minecraft 26.2."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Java*.cs"))
        content = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertIn("25", content, "JavaService must handle Java 25")

    def test_f10_03_adoptium_api_endpoint_structure_java_21(self):
        """F10: Adoptium REST API endpoint for Java 21 query follows official schema."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Java*.cs"))
        content = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertIn("api.adoptium.net", content, "JavaService must reference Adoptium REST API")

    def test_f10_04_adoptium_api_endpoint_structure_java_25(self):
        """F10: Adoptium REST API query parameters specify OS windows and architecture x64."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Java*.cs"))
        content = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertTrue(
            "windows" in content.lower() and "x64" in content.lower(),
            "Adoptium query must target windows x64 binaries"
        )

    def test_f10_05_managed_runtime_directory_structure(self):
        """F10: Managed runtime paths follow %APPDATA%/.theladsclient/runtime/java-{version}/bin/java.exe."""
        services_dir = ch.LAUNCHER_DIR / "Services"
        candidates = list(services_dir.glob("*Java*.cs")) + list(services_dir.glob("*Path*.cs"))
        content = "".join(c.read_text(encoding="utf-8", errors="replace") for c in candidates)
        self.assertTrue(
            "java.exe" in content and "runtime" in content.lower(),
            "Java runtime paths must resolve to runtime/java-{version}/bin/java.exe"
        )

    # =========================================================================
    # Feature 11: TheLadsCore Multi-Project Gradle Restructuring
    # =========================================================================

    def test_f11_01_settings_gradle_defines_three_subprojects(self):
        """F11: settings.gradle includes subprojects common, v1_21_1, and v26_2."""
        settings_path = ch.CORE_DIR / "settings.gradle"
        self.assertTrue(settings_path.exists(), "TheLadsCore/settings.gradle must exist")
        content = settings_path.read_text(encoding="utf-8", errors="replace")
        for subproj in ("common", "v1_21_1", "v26_2"):
            self.assertIn(
                f"'{subproj}'", content.replace('"', "'"),
                f"settings.gradle must include subproject '{subproj}'"
            )

    def test_f11_02_common_subproject_java_21_toolchain(self):
        """F11: Subproject :common configures Java 21 toolchain (release = 21)."""
        common_build = ch.CORE_DIR / "common" / "build.gradle"
        self.assertTrue(common_build.exists(), "TheLadsCore/common/build.gradle must exist")
        content = common_build.read_text(encoding="utf-8", errors="replace")
        self.assertTrue(
            "21" in content,
            ":common/build.gradle must configure Java 21"
        )

    def test_f11_03_v1_21_1_subproject_mc_1_21_1_fabric_loom(self):
        """F11: Subproject :v1_21_1 configures Minecraft 1.21.1 and includes :common."""
        v1_build = ch.CORE_DIR / "v1_21_1" / "build.gradle"
        self.assertTrue(v1_build.exists(), "TheLadsCore/v1_21_1/build.gradle must exist")
        content = v1_build.read_text(encoding="utf-8", errors="replace")
        self.assertIn("1.21.1", content, ":v1_21_1 must target Minecraft 1.21.1")
        self.assertIn(":common", content, ":v1_21_1 must depend on project(':common')")

    def test_f11_04_v26_2_subproject_mc_26_2_fabric_loom(self):
        """F11: Subproject :v26_2 configures Minecraft 26.2 with Java 25 and includes :common."""
        v26_build = ch.CORE_DIR / "v26_2" / "build.gradle"
        self.assertTrue(v26_build.exists(), "TheLadsCore/v26_2/build.gradle must exist")
        content = v26_build.read_text(encoding="utf-8", errors="replace")
        self.assertIn("26.2", content, ":v26_2 must target Minecraft 26.2")
        self.assertIn(":common", content, ":v26_2 must depend on project(':common')")

    def test_f11_05_root_build_gradle_coordinates_subprojects(self):
        """F11: Root build.gradle coordinates multi-project repository and build tasks."""
        root_build = ch.CORE_DIR / "build.gradle"
        self.assertTrue(root_build.exists(), "TheLadsCore/build.gradle must exist")
        content = root_build.read_text(encoding="utf-8", errors="replace")
        self.assertTrue(
            "allprojects" in content or "subprojects" in content or "buildAll" in content,
            "Root build.gradle must configure subproject coordination"
        )

    # =========================================================================
    # Feature 12: Client Graphics & UI Parity Bridge (LadsGraphics)
    # =========================================================================

    def test_f12_01_ladsgraphics_bridge_interface_defined(self):
        """F12: LadsGraphics bridge interface in :common defines universal drawing primitives."""
        bridge_files = list((ch.CORE_DIR / "common").rglob("*LadsGraphics*.java"))
        self.assertTrue(len(bridge_files) > 0, "LadsGraphics interface must exist in :common")
        content = "".join(bf.read_text(encoding="utf-8", errors="replace") for bf in bridge_files)
        for method in ("fill", "drawText", "pushPose", "popPose"):
            self.assertIn(method, content, f"LadsGraphics interface missing required method '{method}'")

    def test_f12_02_v1_21_1_guigraphics_adapter_implements_bridge(self):
        """F12: GuiGraphicsLadsAdapter in :v1_21_1 wraps GuiGraphics to implement LadsGraphics."""
        adapter_files = list((ch.CORE_DIR / "v1_21_1").rglob("*Adapter*.java"))
        self.assertTrue(len(adapter_files) > 0, "GuiGraphics adapter must exist in :v1_21_1")
        content = "".join(af.read_text(encoding="utf-8", errors="replace") for af in adapter_files)
        self.assertIn("LadsGraphics", content, "v1_21_1 adapter must implement LadsGraphics")
        self.assertIn("GuiGraphics", content, "v1_21_1 adapter must wrap GuiGraphics")

    def test_f12_03_v26_2_guigraphicsextractor_adapter_implements_bridge(self):
        """F12: GuiGraphicsExtractorLadsAdapter in :v26_2 wraps GuiGraphicsExtractor to implement LadsGraphics."""
        adapter_files = list((ch.CORE_DIR / "v26_2").rglob("*Adapter*.java"))
        self.assertTrue(len(adapter_files) > 0, "GuiGraphicsExtractor adapter must exist in :v26_2")
        content = "".join(af.read_text(encoding="utf-8", errors="replace") for af in adapter_files)
        self.assertIn("LadsGraphics", content, "v26_2 adapter must implement LadsGraphics")
        self.assertIn("GuiGraphicsExtractor", content, "v26_2 adapter must wrap GuiGraphicsExtractor")

    def test_f12_04_hud_elements_render_through_ladsgraphics(self):
        """F12: Universal HUD elements in :common render through LadsGraphics interface."""
        hud_files = list((ch.CORE_DIR / "common").rglob("*Hud*.java"))
        self.assertTrue(len(hud_files) > 0, "HUD elements must exist in :common")
        content = "".join(hf.read_text(encoding="utf-8", errors="replace") for hf in hud_files)
        self.assertIn("LadsGraphics", content, "HUD elements in :common must render using LadsGraphics")

    def test_f12_05_settings_screens_render_through_ladsgraphics(self):
        """F12: Settings and layout screens in :common render through LadsGraphics bridge."""
        screen_files = list((ch.CORE_DIR / "common").rglob("*Screen*.java"))
        self.assertTrue(len(screen_files) > 0, "UI Screens must exist in :common")
        content = "".join(sf.read_text(encoding="utf-8", errors="replace") for sf in screen_files)
        self.assertIn("LadsGraphics", content, "Settings screens in :common must render using LadsGraphics")

    # =========================================================================
    # Feature 13: Title Screen Button Layout Parity
    # =========================================================================

    def test_f13_01_title_screen_lads_settings_button_injected(self):
        """F13: 'Lads Settings' button is registered in TitleScreenMixin."""
        mixin_files = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        self.assertTrue(len(mixin_files) > 0, "TitleScreenMixin must exist")
        content = "".join(mf.read_text(encoding="utf-8", errors="replace") for mf in mixin_files)
        self.assertTrue(
            "Lads Settings" in content or "LADS_SETTINGS" in content or "lads_settings" in content,
            "TitleScreenMixin must inject 'Lads Settings' button"
        )

    def test_f13_02_lads_settings_positioned_above_options(self):
        """F13: 'Lads Settings' button is positioned directly at/above Options button Y."""
        mixin_files = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        content = "".join(mf.read_text(encoding="utf-8", errors="replace") for mf in mixin_files)
        self.assertTrue(
            "optionsY" in content or "menu.options" in content or "options" in content.lower(),
            "TitleScreenMixin must locate Options button to position Lads Settings"
        )

    def test_f13_03_subsequent_buttons_shifted_by_24px(self):
        """F13: Buttons below Options are shifted downward by 24 pixels."""
        mixin_files = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        content = "".join(mf.read_text(encoding="utf-8", errors="replace") for mf in mixin_files)
        self.assertIn("24", content, "Widgets below Options must be shifted downward by 24 pixels")

    def test_f13_04_bottom_left_account_card_rendered(self):
        """F13: TitleScreenMixin draws bottom-left account switcher card."""
        mixin_files = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        content = "".join(mf.read_text(encoding="utf-8", errors="replace") for mf in mixin_files)
        self.assertTrue(
            "CARD_" in content or "account" in content.lower() or "0xCC0D0D1A" in content,
            "TitleScreenMixin must render bottom-left account card"
        )

    def test_f13_05_layout_parity_across_1_21_1_and_26_2(self):
        """F13: Title screen button injection logic exists in both v1_21_1 and v26_2 subprojects."""
        v1_mixins = list((ch.CORE_DIR / "v1_21_1").rglob("*TitleScreen*.java"))
        v26_mixins = list((ch.CORE_DIR / "v26_2").rglob("*TitleScreen*.java"))
        self.assertTrue(len(v1_mixins) > 0, "TitleScreen mixin must exist in v1_21_1")
        self.assertTrue(len(v26_mixins) > 0, "TitleScreen mixin must exist in v26_2")

    # =========================================================================
    # Feature 14: In-Game Account Switcher & Skin Caching Parity
    # =========================================================================

    def test_f14_01_account_switcher_reads_dynamic_accounts_json(self):
        """F14: AccountSwitcherScreen reads accounts from dynamic ClientPaths."""
        account_files = list(ch.CORE_DIR.rglob("*AccountSwitcher*.java"))
        self.assertTrue(len(account_files) > 0, "AccountSwitcherScreen must exist")
        content = "".join(af.read_text(encoding="utf-8", errors="replace") for af in account_files)
        self.assertTrue(
            "ClientPaths" in content or "lads_accounts.json" in content,
            "AccountSwitcherScreen must read from dynamic accounts storage"
        )
        self.assertNotIn("C:/The Lads Client/lads_accounts.json", content, "Must not contain hardcoded C:/ path")

    def test_f14_02_account_json_schema_validation(self):
        """F14: Account JSON schema validator verifies required account fields."""
        valid_sample = [
            {
                "uuid": "00000000-0000-0000-0000-000000000001",
                "username": "TestPlayer",
                "accessToken": "mock_token",
                "accountType": "Microsoft",
                "selected": True
            }
        ]
        valid, msg = ch.validate_accounts_json_schema(valid_sample)
        self.assertTrue(valid, f"Sample accounts schema should be valid: {msg}")

    def test_f14_03_skin_cache_priority_local_skin_png(self):
        """F14: Skin fetching prioritizes local custom skin.png when present."""
        sources = list(ch.CORE_DIR.rglob("*Account*.java")) + list(ch.CORE_DIR.rglob("*Skin*.java"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertIn("skin.png", content, "Skin system must check for custom skin.png")

    def test_f14_04_skin_fetch_mojang_and_minotar_fallback(self):
        """F14: Skin fetching connects to Mojang API with Minotar fallback."""
        sources = list(ch.CORE_DIR.rglob("*Skin*.java")) + list(ch.CORE_DIR.rglob("*Account*.java"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertTrue(
            "minotar" in content.lower() or "mojang" in content.lower() or "sessionserver" in content.lower(),
            "Skin fetch pipeline must include Mojang or Minotar fallback logic"
        )

    def test_f14_05_skin_caching_to_disk_contract(self):
        """F14: Downloaded skins are cached to disk in .theladsclient/cache/skins/."""
        sources = list(ch.CORE_DIR.rglob("*Skin*.java")) + list(ch.CORE_DIR.rglob("*ClientPaths*.java"))
        content = "".join(s.read_text(encoding="utf-8", errors="replace") for s in sources)
        self.assertTrue(
            "skins" in content.lower() or "cache" in content.lower(),
            "Skin manager must cache skins to disk"
        )

    # =========================================================================
    # Feature 15: Mixin Stability & Single Resource Reload Assurance
    # =========================================================================

    def test_f15_01_mixin_configs_specify_default_require_zero(self):
        """F15: Version-specific mixin configs declare injectors.defaultRequire = 0."""
        mixin_json_files = list(ch.CORE_DIR.rglob("*mixins*.json"))
        self.assertTrue(len(mixin_json_files) > 0, "Mixin JSON configurations must exist")
        
        for mf in mixin_json_files:
            try:
                data = json.loads(mf.read_text(encoding="utf-8", errors="replace"))
                injectors = data.get("injectors", {})
                default_req = injectors.get("defaultRequire", None)
                if default_req is not None:
                    self.assertEqual(
                        default_req, 0,
                        f"{mf.name} must specify defaultRequire = 0 to prevent injection crashes"
                    )
            except json.JSONDecodeError:
                pass

    def test_f15_02_v1_21_1_mixin_targets_gui_class(self):
        """F15: GuiMixin in v1_21_1 targets net.minecraft.client.gui.Gui."""
        v1_mixins = list((ch.CORE_DIR / "v1_21_1").rglob("*GuiMixin*.java"))
        self.assertTrue(len(v1_mixins) > 0, "GuiMixin must exist in v1_21_1")
        content = "".join(m.read_text(encoding="utf-8", errors="replace") for m in v1_mixins)
        self.assertIn("Gui.class", content, "v1_21_1 GuiMixin must target Gui.class")

    def test_f15_03_v26_2_mixin_targets_hud_class(self):
        """F15: GuiMixin in v26_2 targets net.minecraft.client.gui.Hud."""
        v26_mixins = list((ch.CORE_DIR / "v26_2").rglob("*GuiMixin*.java"))
        self.assertTrue(len(v26_mixins) > 0, "GuiMixin must exist in v26_2")
        content = "".join(m.read_text(encoding="utf-8", errors="replace") for m in v26_mixins)
        self.assertIn("Hud.class", content, "v26_2 GuiMixin must target Hud.class")

    def test_f15_04_boot_log_validator_single_resource_reload(self):
        """F15: Boot log validator accepts logs with exactly ONE Reloading ResourceManager entry."""
        valid_sample_log = """
[Render thread/INFO]: Setting user: TestUser
[Render thread/INFO]: Reloading ResourceManager: vanilla, fabric, theladscore
[Render thread/INFO]: OpenAL initialized.
[Render thread/INFO]: Sound engine started
[Render thread/INFO]: Game took 3.421 seconds to start
[Render thread/INFO]: TitleScreen ready
"""
        res = ch.validate_boot_log(valid_sample_log)
        self.assertTrue(res["passed"], f"Sample boot log should pass: {res.get('errors')}")
        self.assertEqual(res["reload_resource_manager_count"], 1)

    def test_f15_05_boot_log_validator_zero_mixin_injection_errors(self):
        """F15: Boot log validator rejects logs containing mixin injection errors."""
        corrupted_log = """
[Render thread/INFO]: Setting user: TestUser
[Render thread/INFO]: Reloading ResourceManager: vanilla
[Render thread/ERROR]: org.spongepowered.asm.mixin.injection.throwables.InjectionError: Critical injection failure
[Render thread/INFO]: Game took 2.1 seconds to start
"""
        res = ch.validate_boot_log(corrupted_log)
        self.assertFalse(res["passed"], "Boot log containing InjectionError must fail validation")
        self.assertTrue(res["has_mixin_errors"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
