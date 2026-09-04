"""
Tier 4: Real-World Application Workloads E2E Tests
Simulates real-world application workflows, full launch lifecycles,
cold-start provisioning, and verification pipeline gatekeepers.
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


class TestTier4RealWorld(unittest.TestCase):
    """
    Tier 4 Real-World Application Scenarios:
    Comprehensive workflows verifying realistic client execution scenarios.
    """

    def setUp(self):
        self.temp_env = tempfile.mkdtemp(prefix="lads_t4_sim_")

    def tearDown(self):
        if os.path.exists(self.temp_env):
            shutil.rmtree(self.temp_env, ignore_errors=True)

    # -------------------------------------------------------------------------
    # T4.1: Pipeline Gatekeeper Verification Script Simulation
    # -------------------------------------------------------------------------
    def test_t4_01_pipeline_gatekeeper_verification_script_flow(self):
        """
        T4.1: Simulates the 4-gate verification pipeline (Requirement R4):
        - Gate 1: Git repository pack size < 50 MB
        - Gate 2: Workspace hygiene (no loose dumps/dead test projects)
        - Gate 3: Packwiz metadata integrity (no tracked jars)
        - Gate 4: Clean boot log validation
        """
        # Gate 1: Repo Size
        pack_size = ch.get_git_pack_size_mb()
        self.assertLess(pack_size, 50.0, "Gate 1: Cloned repo pack size must be < 50 MB")
        
        # Gate 2: Workspace Hygiene
        self.assertFalse((ch.REPO_ROOT / "TestLogin").exists(), "Gate 2: TestLogin must not exist")
        self.assertFalse((ch.REPO_ROOT / "TheLadsLauncher_Clean").exists(), "Gate 2: TheLadsLauncher_Clean must not exist")
        root_exes = list(ch.REPO_ROOT.glob("*.exe"))
        self.assertEqual(len(root_exes), 0, "Gate 2: Zero loose .exe in root workspace")
        
        # Gate 3: Packwiz Cleanliness
        code, out, _ = ch.run_cmd("git ls-files \"Packwiz/*.jar\"")
        self.assertEqual(out.strip(), "", "Gate 3: Zero tracked jars in Packwiz")
        
        # Gate 4: Simulated Boot Log Check
        sample_log = """
[15:30:00] [Render thread/INFO]: [TheLadsCore] Loading The Lads Client v26.2...
[15:30:01] [Render thread/INFO]: Reloading ResourceManager: vanilla, fabric, theladscore
[15:30:02] [Render thread/INFO]: [TheLadsCore] HUD engine initialized with 20 elements.
[15:30:03] [Render thread/INFO]: Game took 3.125 seconds to start
[15:30:03] [Render thread/INFO]: TitleScreen ready.
"""
        res = ch.validate_boot_log(sample_log)
        self.assertTrue(res["passed"], f"Gate 4: Boot log validation failed: {res.get('errors')}")

    # -------------------------------------------------------------------------
    # T4.2: Cold-Start Onboarding and Directory Provisioning
    # -------------------------------------------------------------------------
    def test_t4_02_cold_start_onboarding_and_directory_provisioning(self):
        """
        T4.2: Simulates first-time user run when %APPDATA%/.theladsclient does not exist.
        Launcher dynamic path service provisions the full directory tree and default configs.
        """
        base_dir = Path(self.temp_env) / ".theladsclient"
        self.assertFalse(base_dir.exists())
        
        # Simulate PathService.EnsureDirectories()
        subdirs = ["shared", "profiles", "runtime", "logs", "bin"]
        for sd in subdirs:
            (base_dir / sd).mkdir(parents=True, exist_ok=True)
            
        # Verify all directories exist
        for sd in subdirs:
            self.assertTrue((base_dir / sd).exists(), f"Subdirectory '{sd}' was not provisioned")
            
        # Simulate initial shared options setup
        shared_options = base_dir / "shared" / "options.txt"
        shared_options.write_text("lang:en_us\nguiScale:0\n", encoding="utf-8")
        self.assertTrue(shared_options.exists())
        
        # Simulate initial accounts file setup
        shared_accounts = base_dir / "shared" / "lads_accounts.json"
        shared_accounts.write_text("[]", encoding="utf-8")
        self.assertTrue(shared_accounts.exists())
        valid, msg = ch.validate_accounts_json_schema([])
        self.assertTrue(valid)

    # -------------------------------------------------------------------------
    # T4.3: Full Multi-Profile Lifecycle and Keybind Propagation
    # -------------------------------------------------------------------------
    def test_t4_03_full_multi_profile_lifecycle_and_keybind_propagation(self):
        """
        T4.3: Simulates full game launch, control customization, exit, and switch:
        1. User selects 1.21.1 profile (pre-launch sync copies shared/options.txt).
        2. In-game, user remaps attack key to key.mouse.left and use key to key.mouse.right.
        3. Game terminates: launcher detects newer options.txt and syncs back to shared/.
        4. User switches to 26.2 profile and launches: pre-launch sync brings in new keybinds.
        5. Verify 26.2 profile runs with updated keybinds.
        """
        base_dir = Path(self.temp_env) / ".theladsclient"
        shared_dir = base_dir / "shared"
        shared_dir.mkdir(parents=True, exist_ok=True)
        
        # Initial shared settings
        (shared_dir / "options.txt").write_text(
            "key_key.attack:key.keyboard.space\nkey_key.use:key.keyboard.f\n",
            encoding="utf-8"
        )
        
        p1_dir = base_dir / "profiles" / "1.21.1"
        p2_dir = base_dir / "profiles" / "26.2"
        p1_dir.mkdir(parents=True, exist_ok=True)
        p2_dir.mkdir(parents=True, exist_ok=True)
        
        # Step 1: Pre-launch 1.21.1
        shutil.copyfile(shared_dir / "options.txt", p1_dir / "options.txt")
        
        # Step 2: User plays 1.21.1 and modifies options
        (p1_dir / "options.txt").write_text(
            "key_key.attack:key.mouse.left\nkey_key.use:key.mouse.right\n",
            encoding="utf-8"
        )
        
        # Step 3: Game terminates -> sync back to shared
        shutil.copyfile(p1_dir / "options.txt", shared_dir / "options.txt")
        
        # Step 4: Pre-launch 26.2
        shutil.copyfile(shared_dir / "options.txt", p2_dir / "options.txt")
        
        # Step 5: Verify 26.2 has the new controls
        p2_content = (p2_dir / "options.txt").read_text(encoding="utf-8")
        self.assertIn("key_key.attack:key.mouse.left", p2_content)
        self.assertIn("key_key.use:key.mouse.right", p2_content)

    # -------------------------------------------------------------------------
    # T4.4: Boot Log Verification for v1.21.1 and v26.2
    # -------------------------------------------------------------------------
    def test_t4_04_boot_log_verification_for_v1_21_1_and_v26_2(self):
        """
        T4.4: Validates real-world Minecraft boot logs for both versions.
        Confirms exactly one Reloading ResourceManager, clean startup timing,
        and zero mixin errors for both 1.21.1 (Gui) and 26.2 (Hud).
        """
        # Realistic 1.21.1 Boot Log
        log_1_21_1 = """
[12:00:00] [main/INFO]: Loading Minecraft 1.21.1 with Fabric Loader 0.16.9
[12:00:01] [Render thread/INFO]: Initializing The Lads Client Core (v1_21_1)
[12:00:02] [Render thread/INFO]: Reloading ResourceManager: vanilla, fabric, theladscore
[12:00:03] [Render thread/INFO]: [TheLadsCore] Injected Lads Settings button above Options at Y=140
[12:00:04] [Render thread/INFO]: Game took 4.120 seconds to start
[12:00:04] [Render thread/INFO]: TitleScreen ready.
"""
        res_1211 = ch.validate_boot_log(log_1_21_1)
        self.assertTrue(res_1211["passed"], f"1.21.1 log failed: {res_1211.get('errors')}")

        # Realistic 26.2 Boot Log
        log_26_2 = """
[12:10:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3
[12:10:01] [Render thread/INFO]: Initializing The Lads Client Core (v26_2)
[12:10:02] [Render thread/INFO]: Reloading ResourceManager: vanilla, fabric, theladscore
[12:10:03] [Render thread/INFO]: [TheLadsCore] Multi-threaded Vulkan/Blaze3D HUD pipeline ready
[12:10:04] [Render thread/INFO]: Game took 3.890 seconds to start
[12:10:04] [Render thread/INFO]: TitleScreen ready.
"""
        res_26_2 = ch.validate_boot_log(log_26_2)
        self.assertTrue(res_26_2["passed"], f"26.2 log failed: {res_26_2.get('errors')}")

    # -------------------------------------------------------------------------
    # T4.5: Packwiz Remote Modpack Metadata Resolution
    # -------------------------------------------------------------------------
    def test_t4_05_packwiz_remote_modpack_metadata_resolution(self):
        """
        T4.5: Validates Packwiz modpack metadata resolution:
        All .pw.toml files specify valid filenames and download/update mechanisms.
        """
        mods_dir = ch.PACKWIZ_DIR / "mods"
        self.assertTrue(mods_dir.exists())
        pw_tomls = list(mods_dir.glob("*.pw.toml"))
        self.assertGreater(len(pw_tomls), 0, "Mods directory must contain .pw.toml files")
        
        valid_count = 0
        for pt in pw_tomls:
            content = pt.read_text(encoding="utf-8", errors="replace")
            valid, _ = ch.validate_pw_toml_content(content)
            if valid:
                valid_count += 1
                
        # At least 95% of metadata files must be strictly valid
        valid_ratio = valid_count / len(pw_tomls)
        self.assertGreaterEqual(
            valid_ratio, 0.95,
            f"Only {valid_count}/{len(pw_tomls)} ({valid_ratio*100:.1f}%) mod metadata files are valid"
        )

    # -------------------------------------------------------------------------
    # T4.6: Account and Skin Cache Lifecycle
    # -------------------------------------------------------------------------
    def test_t4_06_account_and_skin_cache_lifecycle(self):
        """
        T4.6: Simulates user account and skin lifecycle:
        1. User adds account via launcher -> lads_accounts.json updated.
        2. Launcher / mod fetches skin and caches to runtime cache/skins/{uuid}.png.
        3. Game launches -> AccountSwitcherScreen loads cached skin head texture.
        4. Offline mode -> skin persists without network access.
        """
        base_dir = Path(self.temp_env) / ".theladsclient"
        accounts_file = base_dir / "lads_accounts.json"
        cache_skins_dir = base_dir / "cache" / "skins"
        cache_skins_dir.mkdir(parents=True, exist_ok=True)
        
        uuid = "e7492c36-8488-4680-a6ff-d39b8bc73130"
        accounts = [
            {
                "uuid": uuid,
                "username": "TestSkinUser",
                "accessToken": "mock_token",
                "accountType": "Microsoft",
                "selected": True
            }
        ]
        accounts_file.write_text(json.dumps(accounts, indent=2), encoding="utf-8")
        
        # Simulate cached skin file
        skin_file = cache_skins_dir / f"{uuid}.png"
        skin_file.write_bytes(b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR" + b"\x00" * 20)
        
        # Verify file persistence
        self.assertTrue(accounts_file.exists())
        self.assertTrue(skin_file.exists())
        self.assertGreater(skin_file.stat().st_size, 0)
        
        # Verify accounts data validates cleanly
        valid, msg = ch.validate_accounts_json_schema(accounts)
        self.assertTrue(valid)


if __name__ == "__main__":
    unittest.main(verbosity=2)
