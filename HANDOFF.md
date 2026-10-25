# Findspace — continuation handoff

Updated: 25 October 2026.

## Current goal

Build a simple desktop computer file manager. The user struggles with locating
files, manually organizing them and understanding storage. Clicking a desktop icon
should reveal subfolders around it, with access to all file-management tools.
They want to continue this project using Codex from their mobile.

## What exists

The implementation is **Python 3.10+ using the standard library and Tkinter**.
No Python third-party packages are needed to run it. No AI or cloud service.

- Draggable floating desktop orb; expand/collapse into a radial folder navigator.
- Real child folders, up to eight per page. Click to drill down; double-click to
  open in the system file manager; right-click for folder-specific tools.
- Native toolbox: filename/path search, category filters, size/date/name sorting,
  storage by type and first-level folder, largest files, organization preview,
  selected moves, persistent undo history and exact duplicate detection.
- One desktop instance per state directory; launching again requests expansion.
- Preferences remember folder, position and keep-on-top setting.
- Source launchers, desktop-shortcut creator, Windows EXE build script and a
  manual Windows GitHub Actions build workflow.
- An earlier browser interface remains as an optional separate entry point.

## Important files

| Path | Responsibility |
| --- | --- |
| `Findspace.pyw` | Windowed desktop entry point and startup error logging |
| `desktop.py` | Native orb, folder ring and toolbox |
| `desktop_model.py` | Folder pagination, geometry, formatting and preferences |
| `instance.py` | Per-user desktop instance lock |
| `engine.py` | SQLite index; organization plans; verified moves; undo; duplicate hashes |
| `Launch_Findspace.vbs` | Windowless source launcher or EXE launcher if present |
| `Create_Desktop_Shortcut.vbs` | Creates a shortcut pointing at the extracted project |
| `Build_Portable_Exe.bat` | Windows-only build using PyInstaller 6.22.3 |
| `.github/workflows/windows-build.yml` | Manual Windows build workflow; not run yet |
| `app.py`, `web/` | Optional older localhost browser interface |
| `tests/test_engine.py` | File operations, recovery and local API checks |
| `tests/test_desktop.py` | Desktop helpers and controller tests using a canvas test double |
| `tests/ui_smoke.cjs` | Browser controller against live Python API with a minimal DOM |

## Launch

Windows source: `Launch_Findspace.vbs` or `Findspace.pyw` with Python/Tk installed.
Other graphical desktops: `python3 desktop.py`.
Optional browser interface: `python app.py`.
Run only one interface at a time against the same state directory.

Windows state: `%LOCALAPPDATA%\Findspace`.
macOS/Linux state: `~/.local/share/Findspace`.

## Verification already completed

- 30 Python tests passed on Linux.
- `node tests/ui_smoke.cjs` passed against the live local Python server.
- Python syntax compilation passed.
- ZIP archives were checked for integrity.

The native controller tests use a mocked canvas. They are **not visual tests**.
A graphical display and Windows runner were unavailable.

## Unfinished / limits

1. No built or tested Windows `.exe` exists. Build on Windows and verify the result.
2. Native rendering, transparent corners, VBS launchers, Windows shortcut creation,
   Explorer reveal, file-system behavior and desktop placement need Windows tests.
3. Storage totals cover indexed files in one selected folder. Whole-drive capacity
   and free space are not implemented.
4. Search matches names/paths, not document contents or semantic descriptions.
5. Organization rules are basic category + optional modified-month grouping.
   Custom user rules and storage-aware destination choices are not implemented.
6. Moves use hard links on the same filesystem. Unsupported/cross-device locations
   are skipped. No automatic cleanup or deletion, background watcher or autostart.
7. No Java source exists. Java was discussed as an alternative; do not say a port
   has been completed or that the user's laptop has been inspected.

## Environment distinction

Checks in this conversation found Python 3.12.14 and a Java 17 runtime in the
remote Linux development workspace. `javac` was missing. These results say nothing
about the user's Windows laptop; no software was installed on that laptop.

## Recommended continuation

The project repository is https://github.com/Atomstars/File-manager- on branch
`main`. Use this repository as the source of truth for continuation. Read the user's latest instruction on
Python vs Java before a rewrite. Preserve the tested file-handling behavior.
If retaining Python, build/test the existing Windows desktop release before
expanding scope. If moving to Java, port and test the engine and native UI;
the Python implementation remains the behavioral reference.

## Handoff prompt

"Open the Findspace project. Read AGENTS.md, HANDOFF.md and README.md. Preserve
the existing working code. Continue from the current state, identify remaining
Windows packaging/testing work, and follow my latest preference on Python vs Java.
Do not assume this workspace is my laptop."
