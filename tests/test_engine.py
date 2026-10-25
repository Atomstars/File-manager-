import json
import os
import tempfile
import threading
import unittest
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from engine import Manager, digest
from app import make_server


class EngineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.root = self.base / 'Downloads'
        self.root.mkdir()
        self.manager = Manager(self.base / 'state')

    def tearDown(self):
        self.temp.cleanup()

    def put(self, name, content=b'example'):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(content)
        return p

    def scan(self):
        self.manager.scan(str(self.root))

    def apply(self, plan):
        self.manager.apply(plan['id'], [i['id'] for i in plan['items']])

    def test_index_search_storage_and_exclusions(self):
        self.put('Invoice October.pdf', b'12345')
        self.put('nested/Resume.pdf', b'123')
        self.put('node_modules/a.txt')
        self.put('.private.txt')
        self.put('unfinished.crdownload')
        self.scan()
        self.assertEqual(self.manager.summary()['count'], 2)
        self.assertEqual(self.manager.summary()['size'], 8)
        self.assertEqual(self.manager.files(query='invoice pdf')['total'], 1)
        self.assertEqual(self.manager.files(query='not present')['total'], 0)
        self.assertEqual(self.manager.files(kind='Images')['total'], 0)

    def test_literal_percent_underscore_search(self):
        self.put('100%_complete.pdf')
        self.put('100xxcomplete.pdf')
        self.scan()
        self.assertEqual(self.manager.files(query='%_')['total'], 1)

    def test_preview_does_not_mutate_and_default_keeps_projects(self):
        p = self.put('notes.txt')
        self.put('project/spec.pdf')
        self.put('script.py')
        self.scan()
        plan = self.manager.plan()
        self.assertEqual(len(plan['items']), 1)
        self.assertTrue(p.exists())
        self.assertFalse((self.root / 'Findspace Organized').exists())

    def test_move_and_persistent_undo_preserve_contents(self):
        p = self.put('resume.pdf', b'my resume')
        self.scan()
        plan = self.manager.plan()
        target = Path(plan['items'][0]['destination'])
        self.apply(plan)
        self.assertFalse(p.exists())
        self.assertEqual(target.read_bytes(), b'my resume')
        manager = Manager(self.base / 'state')
        batch = manager.summary()['history'][0]['batch']
        manager.undo(batch)
        self.assertEqual(p.read_bytes(), b'my resume')
        self.assertFalse(target.exists())
        self.assertFalse(manager.summary()['history'])

    def test_plan_handles_name_collisions(self):
        self.put('a/same.pdf', b'one')
        self.put('b/same.pdf', b'two')
        self.put('Findspace Organized/Documents/same.pdf', b'existing')
        self.scan()
        plan = self.manager.plan(scope='all')
        self.assertEqual(len(plan['items']), 2)
        self.assertEqual(len({x['destination'] for x in plan['items']}), 2)
        self.apply(plan)
        self.assertEqual((self.root / 'Findspace Organized/Documents/same.pdf').read_bytes(), b'existing')

    def test_late_collision_never_overwrites(self):
        p = self.put('notes.txt', b'original')
        self.scan()
        plan = self.manager.plan()
        target = Path(plan['items'][0]['destination'])
        target.parent.mkdir(parents=True)
        target.write_bytes(b'other')
        self.apply(plan)
        self.assertEqual(p.read_bytes(), b'original')
        self.assertEqual(target.read_bytes(), b'other')
        self.assertEqual(self.manager.progress['errors'], 1)

    def test_changed_source_is_skipped(self):
        p = self.put('notes.txt')
        self.scan()
        plan = self.manager.plan()
        p.write_bytes(b'updated content')
        self.apply(plan)
        self.assertTrue(p.exists())
        self.assertEqual(self.manager.progress['errors'], 1)

    def test_undo_edited_file_is_skipped(self):
        self.put('notes.txt')
        self.scan()
        plan = self.manager.plan()
        self.apply(plan)
        Path(plan['items'][0]['destination']).write_bytes(b'changed')
        self.manager.undo(self.manager.summary()['history'][0]['batch'])
        self.assertFalse((self.root / 'notes.txt').exists())
        self.assertEqual(self.manager.progress['errors'], 1)

    def test_undo_occupied_original_is_skipped(self):
        p = self.put('notes.txt')
        self.scan()
        plan = self.manager.plan()
        self.apply(plan)
        p.write_bytes(b'new original')
        self.manager.undo(self.manager.summary()['history'][0]['batch'])
        self.assertEqual(p.read_bytes(), b'new original')
        self.assertTrue(Path(plan['items'][0]['destination']).exists())

    def test_duplicates_are_content_based_and_read_only(self):
        self.put('one.pdf', b'identical')
        self.put('two.txt', b'identical')
        self.put('three.pdf', b'different')
        self.scan()
        self.manager.find_duplicates()
        groups = self.manager.duplicates
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0]['potential'], len(b'identical'))
        self.assertEqual(len(list(self.root.iterdir())), 3)

    def test_symlink_outside_root_is_not_indexed(self):
        outside = self.base / 'outside.pdf'
        outside.write_bytes(b'outside')
        try:
            (self.root / 'link.pdf').symlink_to(outside)
        except OSError:
            self.skipTest('Symlink unavailable')
        self.scan()
        self.assertEqual(self.manager.summary()['count'], 0)

    def test_pending_move_recovery(self):
        self.put('notes.txt', b'recover')
        self.scan()
        plan = self.manager.plan()
        item = self.manager.plans[plan['id']]['items'][0]
        source, dest = Path(item['source']), Path(item['destination'])
        sha, move_id = self.manager.safe_move(source, dest, item['fp'])
        with self.manager.connect() as c:
            c.execute('INSERT INTO moves VALUES (?,?,?,?,?,?,?)',
                      (move_id, 'batch', str(source), str(dest), sha, 'pending', 1))
        os.link(source,dest)
        source.unlink()
        self.manager.recover_pending()
        with self.manager.connect() as c:
            self.assertEqual(c.execute('SELECT status FROM moves WHERE id=?', (move_id,)).fetchone()['status'], 'moved')
        self.assertEqual(self.manager.summary()['history'][0]['count'], 1)
        self.manager.undo('batch')
        self.assertEqual(source.read_bytes(),b'recover')

    def test_pending_move_recovery_preserves_both_paths(self):
        self.put('notes.txt', b'recover')
        self.scan()
        plan = self.manager.plan()
        item = self.manager.plans[plan['id']]['items'][0]
        source, dest = Path(item['source']), Path(item['destination'])
        sha, move_id = self.manager.safe_move(source, dest, item['fp'])
        with self.manager.connect() as c:
            c.execute('INSERT INTO moves VALUES (?,?,?,?,?,?,?)',
                      (move_id, 'batch', str(source), str(dest), sha, 'pending', 1))
        os.link(source, dest)
        self.manager.recover_pending()
        with self.manager.connect() as c:
            self.assertEqual(c.execute('SELECT status FROM moves WHERE id=?', (move_id,)).fetchone()['status'], 'pending')
        self.assertEqual(source.read_bytes(), b'recover')
        self.assertEqual(dest.read_bytes(), b'recover')

    def test_history_is_scoped_to_current_folder(self):
        self.put('notes.txt')
        self.scan()
        self.apply(self.manager.plan())
        self.assertEqual(len(self.manager.summary()['history']), 1)
        other = self.base / 'Documents'
        other.mkdir()
        self.manager.scan(str(other))
        self.assertEqual(self.manager.summary()['history'], [])

    def test_invalid_selection_and_expired_preview_do_not_move_files(self):
        p = self.put('notes.txt')
        self.scan()
        plan = self.manager.plan()
        with self.assertRaises(ValueError):
            self.manager.apply(plan['id'], ['not-in-preview'])
        self.manager.plans[plan['id']]['created'] = 0
        with self.assertRaises(ValueError):
            self.apply(plan)
        self.assertTrue(p.exists())

    def test_forged_plan_cannot_escape_selected_folder(self):
        p = self.put('notes.txt')
        self.scan()
        plan = self.manager.plan()
        self.manager.plans[plan['id']]['items'][0]['destination'] = str(self.base / 'escaped.txt')
        self.apply(plan)
        self.assertTrue(p.exists())
        self.assertFalse((self.base / 'escaped.txt').exists())
        self.assertEqual(self.manager.progress['errors'], 1)

    def test_job_failure_releases_busy_state(self):
        self.manager.start(lambda: (_ for _ in ()).throw(ValueError('Test failure')))
        import time
        deadline = time.time() + 2
        while self.manager.busy and time.time() < deadline:
            time.sleep(.01)
        self.assertFalse(self.manager.busy)
        self.assertEqual(self.manager.progress['stage'], 'error')


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.manager = Manager(Path(self.temp.name) / 'state')
        self.server, self.token = make_server(self.manager, allow_open=False)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.base = f'http://127.0.0.1:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.temp.cleanup()

    def test_shell_and_authenticated_api(self):
        with urlopen(self.base) as r:
            html = r.read().decode()
        self.assertIn(self.token, html)
        self.assertNotIn('__TOKEN__', html)
        with urlopen(Request(self.base + '/api/status', headers={'X-Findspace-Token':self.token})) as r:
            self.assertEqual(json.load(r)['count'], 0)

    def test_api_rejects_missing_token_and_foreign_origin(self):
        for headers in ({}, {'X-Findspace-Token':self.token,'Origin':'https://evil.example'}):
            with self.assertRaises(HTTPError) as e:
                urlopen(Request(self.base + '/api/status', headers=headers))
            self.assertEqual(e.exception.code, 403)

    def test_bad_root_cannot_start_scan(self):
        req = Request(self.base+'/api/scan',data=json.dumps({'path':'/'}).encode(),headers={'X-Findspace-Token':self.token,'Content-Type':'application/json'})
        with self.assertRaises(HTTPError) as e:
            urlopen(req)
        self.assertEqual(e.exception.code,400)
        self.assertFalse(self.manager.busy)


if __name__ == '__main__':
    unittest.main()
