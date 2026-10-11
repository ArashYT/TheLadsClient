"""python -m unittest tools/modpack-manager/test_patch_config_overlay.py   (or run this file)

Builds an overlay with the shipped patch 1.1.2 from the dev instance's real DH and ModernFix files (the launcher tests' fixtures)."""
import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("patch_config_overlay", HERE / "patch-config-overlay.py")
pco = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pco)
FIXTURES = HERE.parent.parent / "TheLadsLauncher.Tests" / "Fixtures" / "ZeroSmp" / "owner" / "config"
PATCH = HERE / "patches" / "update-1.1.2.json"


class OverlayTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        config = self.tmp / "inst" / "minecraft" / "config"
        config.mkdir(parents=True)
        for name in ("DistantHorizons.toml", "modernfix-mixins.properties"):
            shutil.copy(FIXTURES / name, config / name)
        (config / "particlerain").mkdir()
        (config / "particlerain" / "config.json").write_text('{"perf": {"maxParticleAmount": 3000, "other": 1}, "x": true}')

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def test_patch_112_sets_keys_in_place_and_leaves_the_instance(self):
        out = self.tmp / "out"
        pco.main([str(self.tmp / "inst"), str(out), str(PATCH)])
        dh = (out / "DistantHorizons.toml").read_text().splitlines()
        self.assertEqual(len(dh), len((FIXTURES / "DistantHorizons.toml").read_text().splitlines()))
        self.assertIn('\t\t\trendererMode = "DEFAULT"', dh)
        self.assertIn("\t\tnumberOfThreads = 1", dh)
        self.assertIn("mixin.perf.dynamic_resources=true", (out / "modernfix-mixins.properties").read_text().splitlines())
        self.assertIn('rendererMode = "DISABLED"', (self.tmp / "inst" / "minecraft" / "config" / "DistantHorizons.toml").read_text())

    def test_version4_file_gets_the_missing_key_inside_its_table(self):
        shutil.copy(HERE / "baseline-1.0.0" / "config" / "DistantHorizons.toml", self.tmp / "inst" / "minecraft" / "config" / "DistantHorizons.toml")
        out = self.tmp / "out"
        pco.main([str(self.tmp / "inst"), str(out), str(PATCH)])
        dh = [l.strip() for l in (out / "DistantHorizons.toml").read_text().splitlines()]
        self.assertEqual(dh[0], "_version = 5")
        plan = dh.index('generatorPlan = "SURFACE_ONLY"')
        table = [l for l in dh[:plan] if l.startswith("[")][-1]
        self.assertEqual(table, "[common.worldGenerator]")

    def test_skip_and_json_paths(self):
        out = self.tmp / "out"
        pco.main([str(self.tmp / "inst"), str(out), str(PATCH), "--skip", "config/modernfix-mixins.properties"])
        self.assertNotIn("mixin.perf.dynamic_resources=true", (out / "modernfix-mixins.properties").read_text().splitlines())
        pco.apply(out, {"configPatches": [{"file": "config/particlerain/config.json", "format": "json", "set": {"perf.maxParticleAmount": 1500}}]})
        self.assertEqual(json.loads((out / "particlerain" / "config.json").read_text()), {"perf": {"maxParticleAmount": 1500, "other": 1}, "x": True})

    def test_refuses_output_inside_the_instance(self):
        with self.assertRaises(SystemExit):
            pco.main([str(self.tmp / "inst"), str(self.tmp / "inst" / "minecraft" / "config" / "x"), str(PATCH)])


if __name__ == "__main__":
    unittest.main()
