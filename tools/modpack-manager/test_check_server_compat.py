"""python -m unittest tools/modpack-manager/test_check_server_compat.py   (or run this file)

Uses testdata/zero-smp-status.json, a real status ping of the Zero SMP server (favicon, MOTD and players stripped)."""
import importlib.util
import io
import json
import tempfile
import unittest
import unittest.mock
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("check_server_compat", HERE / "check-server-compat.py")
csc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(csc)
STATUS = json.loads((HERE / "testdata" / "zero-smp-status.json").read_text(encoding="utf-8"))


def make_jar(path, files):
    with zipfile.ZipFile(path, "w") as z:
        for name, data in files.items():
            z.writestr(name, data)


def mods_toml(mod_id, extra=""):
    return f'modLoader="javafml"\n[[mods]]\nmodId="{mod_id}"\n{extra}\n[[dependencies.{mod_id}]]\nmodId="forge"\nmandatory=true\n'


class DecodeTest(unittest.TestCase):
    def setUp(self):
        self.server = csc.server_info(STATUS)

    def test_recorded_status_decodes(self):
        s = self.server
        self.assertFalse(s["truncated"])
        self.assertEqual(len(s["mods"]), 154)
        self.assertEqual(len(s["channels"]), 83)
        self.assertEqual(sum(1 for c in s["channels"] if c[2]), 37)
        self.assertEqual(s["mods"]["corgilib"], "4.0.3.5")
        self.assertIsNone(s["mods"]["jade"])  # ignoreServerOnly
        self.assertEqual(s["owner"]["jei:channel"], "jei")
        self.assertIn(("jei:channel", "1.0.0", True), s["channels"])
        self.assertIn(("dataanchor:level_tracked_data", "1", False), s["channels"])

    def test_non_forge_server(self):
        self.assertIsNone(csc.server_info({"version": {"name": "1.20.1"}}))

    def test_required_mods_skip_builtin_and_unattributable_channels(self):
        required = csc.required_mods(self.server)
        self.assertIn("jei", required)
        self.assertIn("balm", required)
        self.assertNotIn("puzzleslib_1", required)  # free-standing "puzzleslib-1:play": no mod has that id
        self.assertTrue(required.isdisjoint({"minecraft", "forge", "fml"}))


class PackTest(unittest.TestCase):
    def setUp(self):
        self.server = csc.server_info(STATUS)
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def jar(self, name, files):
        make_jar(Path(self.dir.name) / name, files)

    def test_mod_ids_ignore_dependency_tables_and_normalise_fabric_ids(self):
        self.jar("a.jar", {"META-INF/mods.toml": mods_toml("alpha")})
        self.jar("b.jar", {"fabric.mod.json": json.dumps({"id": "fabric-api-base", "provides": ["x-y"]})})
        jars = csc.scan_mods_dir(self.dir.name)
        self.assertEqual(jars["a.jar"]["ids"], {"alpha"})
        self.assertEqual(jars["b.jar"]["ids"], {"fabric_api_base", "x_y"})

    def test_missing_required_mod_is_an_error(self):
        self.jar("jei.jar", {"META-INF/mods.toml": mods_toml("jei")})
        text = [t for level, t in csc.analyze(self.server, csc.scan_mods_dir(self.dir.name)) if level == "ERROR"]
        self.assertTrue(any("'balm'" in t for t in text))
        self.assertFalse(any("'jei'" in t for t in text))

    def test_strict_channel_of_a_mod_the_server_lacks_is_an_error(self):
        strict = b"newSimpleChannel lambda$init$0 equals"
        self.jar("crawl.jar", {"META-INF/mods.toml": mods_toml("crawlondemand"), "a/Net.class": strict})
        self.jar("apple.jar", {"META-INF/mods.toml": mods_toml("appleskin"), "a/Net.class": b"ChannelBuilder lambda$static$0"})
        self.jar("optional.jar", {"META-INF/mods.toml": mods_toml("optmod"), "a/Net.class": strict + b" acceptMissingOr"})
        self.jar("ignore.jar", {"META-INF/mods.toml": mods_toml("ignmod", 'displayTest="IGNORE_ALL_VERSION"'), "a/Net.class": strict})
        text = "\n".join(t for level, t in csc.analyze(self.server, csc.scan_mods_dir(self.dir.name)) if level == "ERROR")
        self.assertIn("crawl.jar", text)
        for name in ("apple.jar", "optional.jar", "ignore.jar"):
            self.assertNotIn(name, text)

    def test_removal_guard(self):
        def levels(name, jars=None):
            return sorted(level for level, _ in csc.check_removals(self.server, [name], jars or {}))

        self.assertEqual(levels("Corgilib-Forge-1.20.1-4.0.3.5.jar"), ["ERROR"])
        self.assertEqual(levels("Data_Anchor-forge-1.20.1-1.0.0.22.jar"), ["ERROR"])
        self.assertEqual(levels("jei-1.20.1-forge-15.49.0.191.jar"), ["ERROR"])  # owns the required jei:channel
        self.assertEqual(levels("Clumps-forge-1.20.1-12.0.0.4.jar"), ["WARN"])  # listed with a version, no channel
        self.assertEqual(levels("midnightlib-forge-1.9.2+1.20.1.jar"), [])  # ignoreServerOnly
        self.assertEqual(levels("player-animation-lib-forge-1.0.2-rc1+1.20.jar"), ["WARN"])  # id playeranimator, listed with a version
        self.assertEqual(levels("MouseTweaks-forge-mc1.20.1-2.25.1.jar"), [])  # not on the server
        # a jar that is in the pack folder is judged by the ids inside it, not by its file name
        self.jar("renamed.jar", {"META-INF/mods.toml": mods_toml("corgilib")})
        jars = csc.scan_mods_dir(self.dir.name)
        self.assertEqual(levels("renamed.jar", jars), ["ERROR"])

    def test_cli_exit_codes(self):
        status = str(HERE / "testdata" / "zero-smp-status.json")
        out = io.StringIO()
        with unittest.mock.patch("sys.stdout", out):
            self.assertEqual(csc.main(["--status", status, "--remove", "Corgilib-Forge-1.20.1-4.0.3.5.jar"]), 1)
            self.assertEqual(csc.main(["--status", status, "--remove", "MouseTweaks-forge-mc1.20.1-2.25.1.jar"]), 0)
        self.assertEqual(csc.main(["--status", str(Path(self.dir.name) / "missing.json")]), 2)


if __name__ == "__main__":
    unittest.main()
