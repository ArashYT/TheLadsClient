"""Offline checks that REMOVED and NATIVE mods cannot re-enter generated manifests or coverage, and that every hash Lads
shipped stays recorded ("published" earlier pins, "retired" mods).

Run from the repository root: python tools/test_sync_removed.py
Uses the shipped manifests as read-only fixtures; everything is written to a temp directory.
"""
import json
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

import sync_instance_mods as sync

REPO = pathlib.Path(__file__).resolve().parents[1]
GAME = '26.3'


def version(mod, requires=()):
    return {'id': mod['versionId'], 'project_id': mod['projectId'], 'game_versions': sync.GAMES, 'loaders': ['fabric'],
            'version_type': 'release', 'dependencies': [{'dependency_type': 'required', 'project_id': p} for p in requires]}


class RemovedModsTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = pathlib.Path(temp.name)
        self.target = self.root / 'TheLadsLauncher/game-mods' / GAME / 'client-mods.json'
        self.target.parent.mkdir(parents=True)
        self.shipped = (REPO / 'TheLadsLauncher/game-mods' / GAME / 'client-mods.json').read_text(encoding='utf8')
        self.current = json.loads(self.shipped)
        retired = self.goodmc_retired = next(r for r in self.current['retired'] if r['modId'] == 'goodmc')
        # The 1.2.2 manifest entry.
        self.goodmc = {'projectId': 'hwir46QE', 'projectSlug': 'goodmc-old-combat', 'name': retired['name'], 'modId': 'goodmc',
                       'versionId': 'id4n4Y0j', 'version': '6.0.1f', 'fileName': 'GoodMC-Fabric-26.3-6.0.1f.jar',
                       'url': 'https://cdn.modrinth.com/data/hwir46QE/versions/id4n4Y0j/GoodMC-Fabric-26.3-6.0.1f.jar',
                       'sha512': retired['sha512'][0], 'size': 267889, 'license': 'LicenseRef-All-Rights-Reserved',
                       'sourceUrl': None, 'projectUrl': 'https://modrinth.com/mod/goodmc-old-combat'}

    def serve(self, mods):
        """Answer lock()'s Modrinth calls offline: every mod is its own version, and entry() returns it unchanged."""
        by_version = {m['versionId']: m for m in mods}

        def api(path, body=None):
            if path.startswith('version/'):
                return version(by_version[path.removeprefix('version/')])
            return [version(m) for m in mods if m['projectId'] == path.split('/')[1]]  # project/<id>/version?...
        patcher = mock.patch.multiple(sync, ROOT=self.root, api=api, entry=lambda v, game: by_version[v['id']])
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_regenerating_every_shipped_manifest_is_identical(self):
        # Identical output also proves no shipped manifest still lists a removed mod.
        for game in sync.GAMES:
            with self.subTest(game=game):
                shipped = (REPO / 'TheLadsLauncher/game-mods' / game / 'client-mods.json').read_text(encoding='utf8')
                target = self.root / 'TheLadsLauncher/game-mods' / game / 'client-mods.json'
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(shipped, encoding='utf8')
                self.serve(json.loads(shipped)['mods'])
                sync.lock(game, [])
                self.assertEqual(target.read_text(encoding='utf8'), shipped)
                # Both 26.x packs shipped GoodMC, so both must carry the same retirement hashes for the installer.
                goodmc = [r for r in json.loads(shipped).get('retired', []) if r['modId'] == 'goodmc']
                self.assertEqual(goodmc, [self.goodmc_retired] if game.startswith('26.') else [])

    def test_goodmc_in_the_old_manifest_is_dropped_and_retired_with_its_hash(self):
        old = {'minecraftVersion': GAME, 'resolveThroughApi': True, 'mods': self.current['mods'] + [self.goodmc]}
        self.target.write_text(json.dumps(old), encoding='utf8')
        self.serve(old['mods'])
        sync.lock(GAME, [])
        result = json.loads(self.target.read_text(encoding='utf8'))
        self.assertEqual(result['mods'], self.current['mods'])
        self.assertEqual(result['retired'], [{'modId': 'goodmc', 'projectId': 'hwir46QE', 'name': self.goodmc['name'],
                                              'sha512': [self.goodmc['sha512']], 'reason': 'Removed from The Lads Client pack.'}])

    def test_existing_retired_entry_is_kept_without_duplicate_hashes(self):
        old = self.current | {'mods': self.current['mods'] + [self.goodmc]}
        self.target.write_text(json.dumps(old), encoding='utf8')
        self.serve(old['mods'])
        sync.lock(GAME, [])
        self.assertEqual(self.target.read_text(encoding='utf8'), self.shipped)

    def test_dependency_on_the_removed_project_fails_before_writing(self):
        self.target.write_text(self.shipped, encoding='utf8')
        fork = self.goodmc | {'modId': 'oldcombat', 'versionId': 'Fork0001', 'sha512': 'f' * 128}
        self.serve(self.current['mods'] + [fork])
        requester = self.current['mods'][0]
        rows = [{'game': GAME, 'status': 'modrinth', 'projectId': requester['projectId'],
                 'selected': version(requester, requires=['hwir46QE'])}]
        with self.assertRaisesRegex(ValueError, 'oldcombat'):
            sync.lock(GAME, rows)
        self.assertEqual(self.target.read_text(encoding='utf8'), self.shipped)

    def test_a_changed_pin_keeps_every_earlier_hash_in_published(self):
        first = self.current['mods'][0]
        old = self.current | {'mods': [first | {'sha512': 'a' * 128}] + self.current['mods'][1:],
                              'published': {first['modId']: ['b' * 128, first['sha512']]}}
        self.target.write_text(json.dumps(old), encoding='utf8')
        self.serve(self.current['mods'])
        sync.lock(GAME, [])
        result = json.loads(self.target.read_text(encoding='utf8'))
        self.assertEqual(result['mods'], self.current['mods'])
        # Back on an earlier pin: it is the current one again, so it leaves the history list.
        self.assertEqual(result['published'], {first['modId']: ['b' * 128, 'a' * 128]})

    def test_a_mod_that_becomes_native_is_retired_with_all_its_hashes(self):
        self.assertIn('clumps', sync.NATIVE[GAME])
        clumps = self.goodmc | {'projectId': 'Wnxd13zP', 'name': 'Clumps', 'modId': 'clumps', 'versionId': 'Clumps01',
                                'sha512': 'c' * 128}
        old = self.current | {'mods': self.current['mods'] + [clumps], 'published': {'clumps': ['d' * 128]}}
        self.target.write_text(json.dumps(old), encoding='utf8')
        self.serve(old['mods'])
        sync.lock(GAME, [])
        result = json.loads(self.target.read_text(encoding='utf8'))
        self.assertEqual(result['mods'], self.current['mods'])
        self.assertNotIn('published', result)
        self.assertIn({'modId': 'clumps', 'projectId': 'Wnxd13zP', 'name': 'Clumps', 'sha512': ['d' * 128, 'c' * 128],
                       'reason': 'Replaced by native Lads Core functionality.'}, result['retired'])

    def test_dependency_on_a_native_mod_fails_before_writing(self):
        self.target.write_text(self.shipped, encoding='utf8')
        clumps = self.goodmc | {'projectId': 'Wnxd13zP', 'modId': 'clumps', 'versionId': 'Clumps01', 'sha512': 'c' * 128}
        self.serve(self.current['mods'] + [clumps])
        requester = self.current['mods'][0]
        rows = [{'game': GAME, 'status': 'modrinth', 'projectId': requester['projectId'],
                 'selected': version(requester, requires=['Wnxd13zP'])}]
        with self.assertRaisesRegex(ValueError, 'clumps'):
            sync.lock(GAME, rows)
        self.assertEqual(self.target.read_text(encoding='utf8'), self.shipped)

    def test_coverage_skips_removed_mods_by_id_or_project(self):
        instance = [{'id': 'goodmc', 'name': 'GoodMC', 'project': None, 'modrinth': None},
                    {'id': 'oldcombat', 'name': 'Old Combat', 'project': 'hwir46QE', 'modrinth': None},
                    {'id': 'sodium', 'name': 'Sodium', 'project': 'AANobbMI', 'modrinth': None}]

        def survey(item):
            row, game = item
            return {'game': game, 'modId': row['id'], 'name': row['name'], 'projectId': row['project'],
                    'status': 'unavailable', 'reason': 'offline'}
        (self.root / 'docs').mkdir()
        with mock.patch.multiple(sync, ROOT=self.root, AUDIT=self.root / 'audit', inventory=lambda _: instance, survey=survey), \
                mock.patch.object(sys, 'argv', ['sync_instance_mods.py', str(self.root / 'instance')]):
            sync.main()
        coverage = json.loads((self.root / 'docs/mod-coverage.json').read_text(encoding='utf8'))
        self.assertEqual([r['modId'] for r in coverage['coverage']], ['sodium'] * len(sync.GAMES))
        self.assertEqual(coverage['uniqueModCount'], 3)  # the header still describes the source instance


if __name__ == '__main__':
    unittest.main()
