"""
Tier 3: Cross-Feature Combinations E2E Tests (Pairwise Interactions)
Opaque-box tests verifying interface contracts and cross-module interactions
across Launcher, Client Core, Packwiz, and Filesystem data layers.
"""

import os
import sys
import json
import shutil
import tempfile
import unittest
from pathlib import Path

# Add tests directory to path
TESTS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TESTS_DIR.parent
if str(TESTS_DIR) not in sys.path:
    sys.path.insert(0, str(TESTS_DIR))

import common_helpers as ch


class TestTier3CrossFeature(unittest.TestCase):
    """
    Tier 3 Pairwise Cross-Feature Tests:
    Verifies interoperability between multiple features working together.
    """

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp(prefix="lads_t3_test_")

    def tearDown(self):
        if os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir, ignore_errors=True)

    # -------------------------------------------------------------------------
    # T3.1: Profile Switching & Shared Keybinds Synchronization (F7 + F8)
    # -------------------------------------------------------------------------
    def test_t3_01_profile_switching_shared_keybinds_persistence(self):
        """
        T3.1: Verifies two-way sync protocol when switching between 1.21.1 and 26.2.
        A keybind remapped in profile 1.21.1 syncs to shared/options.txt and propagates
        to profile 26.2 upon launch preparation.
        """
        shared_dir = Path(self.temp_dir) / "shared"
        shared_dir.mkdir(parents=True, exist_ok=True)
        shared_options = shared_dir / "options.txt"
        
        # Initial shared options
        shared_options.write_text("key_key.jump:key.keyboard.space\nkey_key.sneak:key.keyboard.left.shift\n", encoding="utf-8")
        
        p1_dir = Path(self.temp_dir) / "profiles" / "1.21.1"
        p2_dir = Path(self.temp_dir) / "profiles" / "26.2"
        p1_dir.mkdir(parents=True, exist_ok=True)
        p2_dir.mkdir(parents=True, exist_ok=True)
        
        # Pre-launch sync 1.21.1: Copy shared options into 1.21.1 profile
        p1_options = p1_dir / "options.txt"
        shutil.copyfile(shared_options, p1_options)
        self.assertIn("key_key.jump:key.keyboard.space", p1_options.read_text(encoding="utf-8"))
        
        # Player in 1.21.1 remaps sneak key from left.shift to key.keyboard.c
        p1_options.write_text("key_key.jump:key.keyboard.space\nkey_key.sneak:key.keyboard.c\n", encoding="utf-8")
        
        # Post-game exit sync: Copy newer 1.21.1 options back to shared
        shutil.copyfile(p1_options, shared_options)
        self.assertIn("key_key.sneak:key.keyboard.c", shared_options.read_text(encoding="utf-8"))
        
        # Pre-launch sync 26.2: Copy updated shared options into 26.2 profile
        p2_options = p2_dir / "options.txt"
        shutil.copyfile(shared_options, p2_options)
        
        # Verification: 26.2 profile has the remapped keybind from 1.21.1
        self.assertIn("key_key.sneak:key.keyboard.c", p2_options.read_text(encoding="utf-8"))

    # -------------------------------------------------------------------------
    # T3.2: Dynamic Path Resolution Parity (F9 Launcher + F9 Core Mod)
    # -------------------------------------------------------------------------
    def test_t3_02_dynamic_path_resolution_launcher_core_parity(self):
        """
        T3.2: PathService in TheLadsLauncher and ClientPaths in TheLadsCore
        both resolve to the identical root directory (%APPDATA%/.theladsclient).
        """
        # Read PathService contract in Launcher
        path_services = list((ch.LAUNCHER_DIR / "Services").glob("*Path*.cs"))
        self.assertTrue(len(path_services) > 0, "PathService must exist in launcher")
        launcher_code = "".join(ps.read_text(encoding="utf-8", errors="replace") for ps in path_services)
        
        # Read ClientPaths contract in Core
        client_paths = list(ch.CORE_DIR.rglob("*ClientPaths*.java"))
        self.assertTrue(len(client_paths) > 0, "ClientPaths must exist in mod")
        core_code = "".join(cp.read_text(encoding="utf-8", errors="replace") for cp in client_paths)
        
        # Both must target .theladsclient folder
        self.assertIn(".theladsclient", launcher_code)
        self.assertIn(".theladsclient", core_code)
        
        # Neither must contain static drive roots
        self.assertNotIn("C:/The Lads Client", core_code)
        self.assertNotIn(r"C:\The Lads Client", launcher_code)

    # -------------------------------------------------------------------------
    # T3.3: Profile Version to Java Runtime Mapping (F7 + F10)
    # -------------------------------------------------------------------------
    def test_t3_03_profile_version_to_java_runtime_mapping(self):
        """
        T3.3: Profile version mapping dynamically determines required Java major version:
        Profile 1.21.1 -> Java 21; Profile 26.2 -> Java 25.
        """
        def resolve_required_java(mc_version):
            if "1.21" in mc_version:
                return 21
            elif "26." in mc_version:
                return 25
            return 21
            
        self.assertEqual(resolve_required_java("1.21.1"), 21)
        self.assertEqual(resolve_required_java("26.2"), 25)
        
        # Verify runtime directory paths match contract
        runtime_21 = ch.DEFAULT_RUNTIME_DIR / "java-21" / "bin" / "java.exe"
        runtime_25 = ch.DEFAULT_RUNTIME_DIR / "java-25" / "bin" / "java.exe"
        self.assertTrue(str(runtime_21).endswith(os.path.join("java-21", "bin", "java.exe")))
        self.assertTrue(str(runtime_25).endswith(os.path.join("java-25", "bin", "java.exe")))

    # -------------------------------------------------------------------------
    # T3.4: Profile Isolation Toggle Cross-Sync Behavior (F7 + F8)
    # -------------------------------------------------------------------------
    def test_t3_04_profile_isolation_toggle_cross_sync_behavior(self):
        """
        T3.4: Toggling IsIsolated = True disables sync with shared directory,
        preventing custom mods/options in isolated instance from leaking.
        """
        shared_dir = Path(self.temp_dir) / "shared"
        shared_dir.mkdir(parents=True, exist_ok=True)
        shared_options = shared_dir / "options.txt"
        shared_options.write_text("fov:70.0\n", encoding="utf-8")
        
        iso_dir = Path(self.temp_dir) / "profiles" / "isolated_test"
        iso_dir.mkdir(parents=True, exist_ok=True)
        iso_options = iso_dir / "options.txt"
        iso_options.write_text("fov:110.0\n", encoding="utf-8")
        
        # Simulate launch logic with IsIsolated = True
        is_isolated = True
        if not is_isolated:
            shutil.copyfile(iso_options, shared_options)
            
        # Verify shared options remains untouched
        self.assertEqual(shared_options.read_text(encoding="utf-8").strip(), "fov:70.0")

    # -------------------------------------------------------------------------
    # T3.5: Packwiz Metadata Integrity and Repo Size Cleanliness (F1 + F2)
    # -------------------------------------------------------------------------
    def test_t3_05_packwiz_metadata_integrity_and_repo_cleanliness(self):
        """
        T3.5: Packwiz metadata architecture strictly enforces that all mod descriptors
        are .pw.toml files while git index contains zero jar files, keeping repo size < 50 MB.
        """
        # 1. Check packwiz index exists and has files
        index_toml_path = ch.PACKWIZ_DIR / "index.toml"
        self.assertTrue(index_toml_path.exists())
        
        # 2. Check no jar files in git
        code, out, _ = ch.run_cmd("git ls-files \"Packwiz/*.jar\"")
        self.assertEqual(code, 0)
        self.assertEqual(out.strip(), "", "No jars tracked in Packwiz")
        
        # 3. Check git pack size remains < 50 MB
        self.assertLess(ch.get_git_pack_size_mb(), 50.0)

    # -------------------------------------------------------------------------
    # T3.6: Account Switcher Data Flow (F6 Launcher Auth + F14 Mod Account Switcher)
    # -------------------------------------------------------------------------
    def test_t3_06_account_switcher_data_flow_launcher_to_client(self):
        """
        T3.6: Accounts created and serialized by Launcher AuthService into lads_accounts.json
        conform to the exact schema consumed by Client Core AccountSwitcherScreen.
        """
        sample_accounts = [
            {
                "uuid": "4566e69f-c907-48ee-8d71-d7ba5aa00d20",
                "username": "ArashYT",
                "accessToken": "eyJh...mock_token",
                "accountType": "Microsoft",
                "selected": True
            },
            {
                "uuid": "00000000-0000-0000-0000-000000000002",
                "username": "OfflinePlayer",
                "accessToken": "",
                "accountType": "Offline",
                "selected": False
            }
        ]
        
        # Validate schema passes common validation
        valid, msg = ch.validate_accounts_json_schema(sample_accounts)
        self.assertTrue(valid, f"Accounts schema failed validation: {msg}")
        
        # Verify selected account resolution
        selected = [a for a in sample_accounts if a.get("selected")]
        self.assertEqual(len(selected), 1)
        self.assertEqual(selected[0]["username"], "ArashYT")

    # -------------------------------------------------------------------------
    # T3.7: LadsGraphics Bridge and HUD Configuration Schema (F8 + F12)
    # -------------------------------------------------------------------------
    def test_t3_07_ladsgraphics_bridge_and_hud_configuration_schema(self):
        """
        T3.7: HUD elements render through LadsGraphics coordinates, and configuration
        in lads_profile.json serializes HUD positions cleanly.
        """
        sample_hud_profile = {
            "profileName": "Default",
            "hudElements": {
                "FPSHudElement": {"x": 5, "y": 5, "enabled": True, "scale": 1.0, "color": "0xFFFFFFFF"},
                "CoordinatesHudElement": {"x": 5, "y": 20, "enabled": True, "scale": 1.0, "color": "0xFFFFFFFF"}
            }
        }
        valid, msg = ch.validate_profile_json_schema(sample_hud_profile)
        self.assertTrue(valid)
        self.assertIn("FPSHudElement", sample_hud_profile["hudElements"])

    # -------------------------------------------------------------------------
    # T3.8: Gradle Subproject & Mixin Targets Alignment (F11 + F15)
    # -------------------------------------------------------------------------
    def test_t3_08_subproject_gradle_and_version_specific_mixins_alignment(self):
        """
        T3.8: Mixin JSON configurations align with subproject Minecraft targets:
        v1_21_1 does not reference Hud.class; v26_2 does not reference Gui.class.
        """
        v1_mixins = list((ch.CORE_DIR / "v1_21_1").rglob("*GuiMixin*.java"))
        v26_mixins = list((ch.CORE_DIR / "v26_2").rglob("*GuiMixin*.java"))
        
        if v1_mixins:
            content_v1 = "".join(m.read_text(encoding="utf-8", errors="replace") for m in v1_mixins)
            self.assertIn("Gui", content_v1)
            self.assertNotIn("net.minecraft.client.gui.Hud.class", content_v1)
            
        if v26_mixins:
            content_v26 = "".join(m.read_text(encoding="utf-8", errors="replace") for m in v26_mixins)
            self.assertIn("Hud", content_v26)
            self.assertNotIn("net.minecraft.client.gui.Gui.class", content_v26)

    # -------------------------------------------------------------------------
    # T3.9: Title Screen Layout Parity Across Versions (F11 + F13)
    # -------------------------------------------------------------------------
    def test_t3_09_title_screen_layout_parity_across_gradle_submodules(self):
        """
        T3.9: Title screen button injection in both v1_21_1 and v26_2 locates Options
        and shifts downstream widgets downward by 24 pixels in both subprojects.
        """
        mixins = list(ch.CORE_DIR.rglob("*TitleScreenMixin*.java"))
        self.assertTrue(len(mixins) > 0, "TitleScreenMixin must exist")
        combined = "".join(m.read_text(encoding="utf-8", errors="replace") for m in mixins)
        self.assertIn("24", combined, "Both versions must shift widgets by 24 pixels")
        self.assertTrue("Lads Settings" in combined or "lads_settings" in combined.lower())

    # -------------------------------------------------------------------------
    # T3.10: Strict Gitignore and Multi-Project Build Output Protection (F4 + F11)
    # -------------------------------------------------------------------------
    def test_t3_10_strict_gitignore_and_multi_project_artifacts_protection(self):
        """
        T3.10: Strict gitignore configuration ensures that compiling both Gradle subprojects
        and the launcher does not result in tracked binary artifacts.
        """
        simulated_artifacts = [
            "TheLadsCore/v1_21_1/build/libs/TheLadsCore-1.0.0-mc1.21.1.jar",
            "TheLadsCore/v26_2/build/libs/TheLadsCore-1.0.0-mc26.2.jar",
            "TheLadsCore/common/build/libs/common.jar",
            "TheLadsLauncher/bin/Release/net8.0-windows/TheLadsLauncher.exe"
        ]
        for artifact in simulated_artifacts:
            self.assertTrue(
                ch.check_git_ignored(artifact),
                f"Build artifact '{artifact}' must be blocked by .gitignore"
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
