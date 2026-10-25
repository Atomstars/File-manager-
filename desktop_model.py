"""Pure desktop helpers, kept separate so they can be checked without a display."""
import json
import math
from pathlib import Path

from engine import SKIP


def folder_page(folder, page=0, page_size=8):
    folders = []
    with __import__('os').scandir(folder) as entries:
        for entry in entries:
            if entry.name.startswith('.') or entry.name.lower() in SKIP:
                continue
            try:
                p = Path(entry.path)
                if entry.is_dir(follow_symlinks=False) and not entry.is_symlink() and not (
                    hasattr(p, 'is_junction') and p.is_junction()
                ):
                    folders.append(p)
            except OSError:
                continue
    folders.sort(key=lambda p: p.name.casefold())
    pages = max(1, math.ceil(len(folders) / page_size))
    page = min(max(0, page), pages - 1)
    return folders[page * page_size:(page + 1) * page_size], page, pages, len(folders)


def ring_positions(count, cx=320, cy=310, radius=168):
    if not 0 <= count <= 8:
        raise ValueError('The folder ring displays up to eight folders at a time.')
    return [(round(cx + radius * math.cos(-math.pi / 2 + 2 * math.pi * i / max(count, 1))),
             round(cy + radius * math.sin(-math.pi / 2 + 2 * math.pi * i / max(count, 1))))
            for i in range(count)]


def default_folder():
    home = Path.home()
    for p in (home / 'Downloads', home / 'Desktop', home / 'Documents', home):
        if p.is_dir():
            return p
    return home


def load_preferences(path):
    try:
        data = json.loads(Path(path).read_text(encoding='utf-8'))
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save_preferences(path, values):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix('.tmp')
    temp.write_text(json.dumps(values, indent=2), encoding='utf-8')
    temp.replace(path)


def human_size(n):
    size = float(n or 0)
    for unit in ('B', 'KB', 'MB', 'GB', 'TB'):
        if size < 1024 or unit == 'TB':
            return f'{size:,.0f} B' if unit == 'B' else f'{size:,.1f} {unit}'
        size /= 1024


def clamp_center(cx, cy, screen_w, screen_h, width, height):
    return (max(0, min(int(cx - width / 2), max(0, screen_w - width))),
            max(0, min(int(cy - height / 2), max(0, screen_h - height))))
