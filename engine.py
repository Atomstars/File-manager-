"""Findspace's local file index and preview/apply/undo harness. Standard library only."""
from __future__ import annotations

import hashlib
import json
import os
import sqlite3
import stat
import threading
import time
import uuid
from collections import Counter, defaultdict
from contextlib import contextmanager
from pathlib import Path

TYPES = {
    'Documents': 'pdf doc docx txt rtf odt md epub ppt pptx xls xlsx csv tsv',
    'Images': 'png jpg jpeg gif webp heic heif svg bmp tiff raw avif',
    'Videos': 'mp4 mov mkv avi webm m4v wmv',
    'Audio': 'mp3 wav flac m4a aac ogg',
    'Archives': 'zip rar 7z tar gz bz2 xz',
    'Installers': 'exe msi dmg pkg iso apk appimage',
    'Code': 'py js ts tsx jsx java c cpp h html css json yaml yml sql sh bat ps1 ipynb',
}
EXTENSIONS = {ext: group for group, extensions in TYPES.items() for ext in extensions.split()}
SKIP = {'.git', 'node_modules', '.venv', 'venv', '__pycache__', '$recycle.bin',
        'system volume information', 'appdata', 'windows', 'program files',
        'program files (x86)', '.findspace', '.next', 'library'}
TEMP_EXT = {'.part', '.crdownload', '.tmp', '.download'}


def category(path):
    return EXTENSIONS.get(Path(path).suffix.lower().lstrip('.'), 'Other')


def fingerprint(path):
    s = Path(path).lstat()
    if not stat.S_ISREG(s.st_mode):
        raise ValueError('Only regular files are supported; links are skipped.')
    return {'size': s.st_size, 'mtime': s.st_mtime_ns, 'inode': s.st_ino, 'device': s.st_dev}


def digest(path):
    before = fingerprint(path)
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    if before != fingerprint(path):
        raise ValueError('File changed while reading. Scan again.')
    return h.hexdigest()


def contained(path, root):
    p, r = Path(path), Path(root).resolve()
    # Refuse symlinks/junctions anywhere below the selected root.
    try:
        relative = p.absolute().relative_to(r)
    except ValueError:
        raise ValueError('Path is outside the selected folder.')
    current = r
    for part in relative.parts:
        current = current / part
        if current.is_symlink() or (hasattr(current, 'is_junction') and current.is_junction()):
            raise ValueError('Symbolic links and junctions are skipped.')
    if not p.resolve().is_relative_to(r):
        raise ValueError('Path is outside the selected folder.')
    return p


class Manager:
    def __init__(self, state_dir):
        self.state_dir = Path(state_dir).resolve()
        self.state_dir.mkdir(parents=True, exist_ok=True)
        self.db = self.state_dir / 'index.sqlite3'
        self.lock = threading.RLock()
        self.busy = False
        self.progress = {'stage': 'idle', 'count': 0, 'errors': 0, 'message': ''}
        self.plans = {}
        self.duplicates = []
        self.root = ''
        self.last_scan = None
        with self.connect() as c:
            c.execute('CREATE TABLE IF NOT EXISTS files (id TEXT PRIMARY KEY,path TEXT UNIQUE,name TEXT,category TEXT,size INTEGER,modified REAL,fp TEXT)')
            c.execute('CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY,value TEXT)')
            c.execute('CREATE TABLE IF NOT EXISTS moves (id TEXT PRIMARY KEY,batch TEXT,source TEXT,destination TEXT,sha TEXT,status TEXT,created REAL)')
            saved = dict(c.execute('SELECT key,value FROM meta'))
            self.root = saved.get('root', '')
            self.last_scan = float(saved['last_scan']) if 'last_scan' in saved else None

    @contextmanager
    def connect(self):
        c = sqlite3.connect(self.db, timeout=30)
        c.row_factory = sqlite3.Row
        try:
            with c:
                yield c
        finally:
            c.close()

    def start(self, operation):
        with self.lock:
            if self.busy:
                raise ValueError('Another operation is running. Wait for it to finish.')
            self.busy = True
            self.progress = {'stage': 'starting', 'count': 0, 'errors': 0, 'message': ''}
        def run():
            try:
                operation()
                self.progress['stage'] = 'done'
            except Exception as exc:
                self.progress.update(stage='error', message=str(exc))
            finally:
                with self.lock:
                    self.busy = False
        threading.Thread(target=run, daemon=True).start()

    def validate_root(self, value):
        if not value or not Path(value).expanduser().is_absolute():
            raise ValueError('Enter an absolute folder path, for example C:\\Users\\You\\Downloads.')
        original = Path(value).expanduser()
        if original.is_symlink() or (hasattr(original, 'is_junction') and original.is_junction()):
            raise ValueError('Choose a regular folder, not a link or junction.')
        p = original.resolve()
        if not p.is_dir():
            raise ValueError('That folder does not exist or cannot be read.')
        if p == Path(p.anchor) or p.name.lower() in SKIP:
            raise ValueError('Choose a personal folder such as Downloads or Documents, rather than a system folder or whole drive.')
        return p

    def scan(self, value):
        root = self.validate_root(value)
        rows, errors = [], 0
        self.progress.update(stage='scanning', count=0, errors=0)
        def onerror(exc):
            nonlocal errors
            errors += 1
        for base, dirs, files in os.walk(root, onerror=onerror, followlinks=False):
            dirs[:] = [d for d in dirs if d.lower() not in SKIP and not d.startswith('.')
                       and not (Path(base) / d).is_symlink()
                       and not (hasattr(Path(base) / d, 'is_junction') and (Path(base) / d).is_junction())
                       and (Path(base) / d).resolve() != self.state_dir]
            for name in files:
                p = Path(base) / name
                if name.startswith('.') or p.suffix.lower() in TEMP_EXT or p.resolve().is_relative_to(self.state_dir):
                    continue
                try:
                    contained(p, root)
                    fp = fingerprint(p)
                    rows.append((uuid.uuid5(uuid.NAMESPACE_URL, str(p)).hex, str(p), name,
                                 category(p), fp['size'], fp['mtime'] / 1e9, json.dumps(fp)))
                except (OSError, ValueError):
                    errors += 1
                self.progress.update(count=len(rows), errors=errors)
        now = time.time()
        with self.lock, self.connect() as c:
            c.execute('DELETE FROM files')
            c.executemany('INSERT INTO files VALUES (?,?,?,?,?,?,?)', rows)
            c.execute('INSERT OR REPLACE INTO meta VALUES (?,?)', ('root', str(root)))
            c.execute('INSERT OR REPLACE INTO meta VALUES (?,?)', ('last_scan', str(now)))
            self.root, self.last_scan = str(root), now
        self.plans.clear()
        self.duplicates = []
        self.progress.update(count=len(rows), errors=errors, message='Index refreshed. Hidden, system, dependency and temporary files are excluded.')

    def files(self, query='', kind='', sort='recent', limit=500, offset=0):
        clauses, args = [], []
        for term in query.lower().split():
            clauses.append('lower(path) LIKE ? ESCAPE char(92)')
            args.append('%' + term.replace('\\', '\\\\').replace('%', '\\%').replace('_', '\\_') + '%')
        if kind:
            clauses.append('category = ?')
            args.append(kind)
        where = ' WHERE ' + ' AND '.join(clauses) if clauses else ''
        ordering = {'recent': 'modified DESC', 'size': 'size DESC', 'name': 'name COLLATE NOCASE'}.get(sort, 'modified DESC')
        with self.connect() as c:
            total = c.execute('SELECT count(*) FROM files' + where, args).fetchone()[0]
            rows = c.execute('SELECT * FROM files' + where + ' ORDER BY ' + ordering + ' LIMIT ? OFFSET ?', args + [min(500, max(1, int(limit))), max(0, int(offset))]).fetchall()
        return {'total': total, 'items': [{k: r[k] for k in r.keys() if k != 'fp'} for r in rows]}

    def summary(self):
        with self.lock, self.connect() as c:
            groups = [dict(r) for r in c.execute('SELECT category,count(*) count,sum(size) size FROM files GROUP BY category ORDER BY size DESC')]
            folders = Counter()
            for row in c.execute('SELECT path,size FROM files'):
                rel = Path(row['path']).relative_to(self.root)
                folders[rel.parts[0] if len(rel.parts) > 1 else 'Files in root'] += row['size']
            current_moves = [dict(r) for r in c.execute("SELECT batch,source,created FROM moves WHERE status='moved' ORDER BY created DESC")
                             if self.root and Path(r['source']).is_relative_to(self.root)]
            batch_map = {}
            for move in current_moves:
                batch = batch_map.setdefault(move['batch'], {'batch': move['batch'], 'count': 0, 'created': move['created']})
                batch['count'] += 1
            batches = list(batch_map.values())[:10]
        return {'root': self.root, 'last_scan': self.last_scan, 'groups': groups,
                'count': sum(g['count'] for g in groups), 'size': sum(g['size'] for g in groups),
                'folders': [{'name': n, 'size': s} for n, s in folders.most_common(8)],
                'history': batches, 'busy': self.busy, 'progress': dict(self.progress),
                'duplicates': self.duplicates}

    def file(self, ident):
        with self.connect() as c:
            row = c.execute('SELECT * FROM files WHERE id=?', (ident,)).fetchone()
        if row is None:
            raise ValueError('File not found. Refresh the index.')
        contained(row['path'], self.root)
        return dict(row)

    def plan(self, scope='loose', dated=False):
        with self.lock:
            if self.busy:
                raise ValueError('Wait for the current operation to finish.')
            if not self.root:
                raise ValueError('Scan a folder first.')
            plan_id = uuid.uuid4().hex
            items, skipped, used = [], 0, set()
            with self.connect() as c:
                rows = c.execute('SELECT * FROM files ORDER BY path').fetchall()
            for row in rows:
                src = Path(row['path'])
                rel = src.relative_to(self.root)
                # Leave projects and existing folder structures intact by default.
                if rel.parts[0] == 'Findspace Organized' or (scope == 'loose' and len(rel.parts) > 1) or row['category'] in {'Code', 'Other'}:
                    skipped += 1
                    continue
                dest = Path(self.root) / 'Findspace Organized' / row['category']
                if dated:
                    dest /= time.strftime('%Y-%m', time.localtime(row['modified']))
                dest /= src.name
                base = dest
                i = 2
                while dest.exists() or str(dest).casefold() in used:
                    dest = base.with_name(f'{base.stem} ({i}){base.suffix}')
                    i += 1
                used.add(str(dest).casefold())
                items.append({'id': row['id'], 'source': str(src), 'destination': str(dest), 'size': row['size'], 'fp': json.loads(row['fp'])})
            self.plans[plan_id] = {'root': self.root, 'items': items, 'created': time.time()}
            # Bound in-memory previews.
            for key in list(self.plans)[:-5]:
                self.plans.pop(key)
            return {'id': plan_id, 'items': [{k: v for k, v in i.items() if k != 'fp'} for i in items], 'skipped': skipped}

    def safe_move(self, src, dest, expected):
        contained(src, self.root)
        contained(dest, self.root)
        if fingerprint(src) != expected:
            raise ValueError('File changed since the preview. Scan and preview again.')
        if dest.exists():
            raise ValueError('Destination already exists. No file was overwritten.')
        dest.parent.mkdir(parents=True, exist_ok=True)
        contained(dest, self.root)
        # Both paths are on the selected filesystem. Hard-link creation is atomic
        # and fails on collisions. Unsupported filesystems fail without moving.
        if src.stat().st_dev != dest.parent.stat().st_dev:
            raise ValueError('Cross-device moves are skipped.')
        sha = digest(src)
        if fingerprint(src) != expected:
            raise ValueError('File changed while preparing the move.')
        move_id = uuid.uuid4().hex
        return sha, move_id

    def apply(self, plan_id, selected):
        plan = self.plans.get(plan_id)
        if not plan or plan['root'] != self.root or time.time() - plan['created'] > 3600:
            raise ValueError('Preview expired. Create a fresh preview.')
        if not isinstance(selected, list) or not selected:
            raise ValueError('Select at least one file.')
        wanted = set(selected)
        items = [i for i in plan['items'] if i['id'] in wanted]
        if len(items) != len(wanted):
            raise ValueError('Selection is not part of this preview.')
        batch = uuid.uuid4().hex
        done, failures = 0, []
        self.progress.update(stage='organizing', count=0, errors=0)
        for item in items:
            src, dest = Path(item['source']), Path(item['destination'])
            try:
                sha, move_id = self.safe_move(src, dest, item['fp'])
                with self.connect() as c:
                    c.execute('INSERT INTO moves VALUES (?,?,?,?,?,?,?)', (move_id, batch, str(src), str(dest), sha, 'pending', time.time()))
                os.link(src, dest)  # exclusive: never overwrite
                if fingerprint(src) != item['fp'] or digest(dest) != sha:
                    dest.unlink()
                    raise ValueError('File changed during move; original retained.')
                src.unlink()
                with self.connect() as c:
                    c.execute("UPDATE moves SET status='moved' WHERE id=?", (move_id,))
                done += 1
            except (OSError, ValueError) as exc:
                failures.append(f'{src.name}: {exc}')
            self.progress.update(count=done, errors=len(failures))
        self.scan(self.root)
        self.progress.update(count=done, errors=len(failures), message=f'Moved {done} file(s). ' + ' | '.join(failures[:5]))

    def undo(self, batch):
        with self.connect() as c:
            rows = c.execute("SELECT * FROM moves WHERE batch=? AND status='moved' ORDER BY created DESC", (batch,)).fetchall()
        if not rows:
            raise ValueError('No remaining moves to undo for this batch.')
        done, errors = 0, []
        for row in rows:
            source, dest = Path(row['source']), Path(row['destination'])
            try:
                contained(source, self.root)
                contained(dest, self.root)
                if source.exists():
                    raise ValueError('Original location is occupied; skipped.')
                expected = fingerprint(dest)
                if digest(dest) != row['sha']:
                    raise ValueError('File was edited after the move; skipped.')
                self.safe_move(dest, source, expected)
                os.link(dest, source)
                if fingerprint(dest) != expected or digest(source) != row['sha']:
                    source.unlink()
                    raise ValueError('File changed during undo; current file retained.')
                dest.unlink()
                with self.connect() as c:
                    c.execute("UPDATE moves SET status='undone' WHERE id=?", (row['id'],))
                done += 1
            except (OSError, ValueError) as exc:
                errors.append(f'{source.name}: {exc}')
        self.scan(self.root)
        self.progress.update(count=done, errors=len(errors), message=f'Restored {done} file(s). ' + ' | '.join(errors[:5]))

    def find_duplicates(self):
        self.progress.update(stage='checking duplicates', count=0, errors=0)
        candidates = defaultdict(list)
        with self.connect() as c:
            for r in c.execute('SELECT * FROM files WHERE size > 0 ORDER BY path'):
                candidates[r['size']].append(dict(r))
        groups, seen, errors = [], 0, 0
        for size, rows in candidates.items():
            if len(rows) < 2:
                continue
            hashes = defaultdict(list)
            for row in rows:
                try:
                    contained(row['path'], self.root)
                    if fingerprint(row['path']) != json.loads(row['fp']):
                        raise ValueError('Changed file')
                    hashes[digest(row['path'])].append(row)
                except (OSError, ValueError):
                    errors += 1
                seen += 1
                self.progress.update(count=seen, errors=errors)
            for sha, matches in hashes.items():
                # Hard-linked paths share data already, so do not count them twice.
                unique = {}
                for r in matches:
                    fp = json.loads(r['fp'])
                    unique[(fp['device'], fp['inode'])] = r
                if len(unique) > 1:
                    groups.append({'sha': sha, 'size': size, 'potential': size * (len(unique) - 1),
                                   'files': [{k: v for k, v in r.items() if k != 'fp'} for r in unique.values()]})
        self.duplicates = sorted(groups, key=lambda g: g['potential'], reverse=True)
        self.progress['message'] = f'{len(groups)} exact duplicate group(s). Nothing was deleted.'

    def recover_pending(self):
        # A crash between creating a hard link and recording success is recoverable.
        with self.connect() as c:
            for row in c.execute("SELECT * FROM moves WHERE status='pending'").fetchall():
                src, dst = Path(row['source']), Path(row['destination'])
                if not src.exists() and dst.is_file() and not dst.is_symlink():
                    if digest(dst) == row['sha']:
                        c.execute("UPDATE moves SET status='moved' WHERE id=?", (row['id'],))
                # If both paths exist, preserve both: no automatic deletion on restart.
