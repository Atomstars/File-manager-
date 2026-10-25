# Findspace

**Find your files. Understand your space. Give everything a home.**

A local computer file manager with a native desktop folder ring and a simple
preview → select → apply → undo engine. Desktop mode needs no browser, localhost
server, AI framework, API key or account. The earlier browser interface is retained
as a separate optional launcher.

## Desktop version — start on Windows

1. Install **Python 3.10 or later** from <https://www.python.org/downloads/> if needed.
2. Extract the ZIP into a folder. Do not run from inside the ZIP.
3. Double-click **Launch_Findspace.vbs**, or **Findspace.pyw** if `.pyw` files are
   associated with your installed Python. The native folder ring opens, without a
   terminal window.
4. Optionally run **Create_Desktop_Shortcut.vbs** once to put an icon on your desktop.
   Keep the extracted app folder at its current location; the shortcut points to it.
5. Choose a personal folder. Subfolders appear around the central icon. Open a file
   tool and scan that folder when prompted.

The VBS files are convenience launchers for source mode. Systems where Windows
Script Host/VBScript is disabled can launch `Findspace.pyw` directly, or build and
launch the EXE. They do not change system policy or install a background service.

| Desktop action | Result |
| --- | --- |
| Click the central `f·` orb | Expand or collapse the folder ring. |
| Drag the orb | Move the widget; its position is remembered. |
| Click a folder | Explore its child folders in the ring. |
| Double-click a folder | Open it in Explorer / the system file manager. |
| Right-click a folder | Open, explore, or run a file tool on that folder. |
| Parent / Choose folder | Navigate up or select another starting folder. |
| Arrows below the ring | Page through folders when there are more than eight. |
| Find files / Space / Organize / Copies | Open the native toolbox, including previews and undo. |
| Launch the desktop shortcut again | Expand the existing widget instead of starting a second instance. |
| Right-click the orb | Toggle keep-above-other-windows, open the folder, or quit. |

This is a **floating desktop widget**, not a Windows Explorer shell extension. It
does not replace the Desktop, modify shell settings, or start automatically with
Windows. File operations run only after a scan and an explicit action. Browsing
the folder ring itself does not scan file contents.

## Single-file Windows EXE — no Python needed to run

The source ZIP **does not contain a built Windows executable**. A native Windows
build cannot be produced by PyInstaller on this Linux build machine.

On Windows, with Python installed and internet available for the build, run
**Build_Portable_Exe.bat** once. It creates an isolated build environment, installs
the pinned packaging tool, runs the tests, and produces **dist/Findspace.exe**.
Put that one file on the desktop. End users of the built EXE do not need Python,
an open terminal, a web browser or a server. It remains a running local app until
you choose Quit.

The build is unsigned and must be tested on Windows before distribution. An
optional manual GitHub Actions workflow is included for a Windows build runner;
it has not been run. Packaging reference:
<https://pyinstaller.org/en/stable/>.

On macOS/Linux, run `python3 desktop.py` with Tk and a graphical desktop available.
Windows uses transparent canvas corners; other platforms use an opaque background.
For the optional browser interface, run `python app.py` or `Start_Findspace.bat`.
The desktop source mode requires Tk, included with standard Windows Python.
No installation through pip is needed to run the source app itself.

## What works

| Tool | Behavior |
| --- | --- |
| Find a file | Search indexed names and paths, combine terms such as `invoice pdf`, filter by type, sort by date/name/size, reveal in the system file manager. |
| Space explorer | Category totals, first-level folder totals and the largest files. Totals cover indexed files, not total drive occupancy or physical allocated disk space. |
| Organize | Preview each source/destination; deselect files; move into `Findspace Organized/<type>/`; optionally group by last-modified year/month. |
| Undo | Persistent move journal; restore unchanged files without replacing anything at their old location. |
| Duplicate check | Group nonempty matching-size files by SHA-256 content hash. Hard-linked aliases aren't counted as extra physical copies. No deletion. |

### The organization rules

Default scope is **loose files at the top of the selected folder**. Existing project
folders remain intact. Including subfolders is an explicit option and can break
links in projects, applications, playlists or documents. Code and unrecognized file
types are left in place. Existing organized files are skipped. Collisions are given
numbered destination names at preview time; if a destination becomes occupied later,
the operation skips that file instead of overwriting it.

Organizing files **does not free storage**. Space explorer and duplicate review help
you decide what to remove separately using your usual file manager.

## File handling

- All data stays on this computer. Desktop mode starts no HTTP server. The optional
  browser launcher binds only to `127.0.0.1` on a free
  port, with a per-launch API token, host/origin checks and no CORS permissions.
- File metadata and move history are saved in `%LOCALAPPDATA%\Findspace` on Windows
  or `~/.local/share/Findspace` on macOS/Linux. Remove that folder to clear the index
  and history (this does not remove your files; undo history will be lost).
- Scans are recursive but skip hidden files/folders, common system directories,
  dependencies, virtual environments, symlinks, junctions and unfinished downloads.
  Unreadable files are counted as skipped. File contents are read only for hashing
  duplicate candidates and verifying moves; search does not index document text.
- Choose personal folders, not Windows, application directories or a whole drive.
- Moves use exclusive hard-link creation followed by unlinking the original. They
  require a filesystem supporting hard links, e.g. NTFS/APFS/ext4. FAT/exFAT,
  cross-device, cloud-placeholder and some network locations may fail and are skipped.
- Content and metadata checks catch changed files. Do not edit or externally move
  files while applying or undoing a plan. This is not an adversarial filesystem sandbox.
- A journal entry is persisted before moving. After an interrupted move, restart can
  recover a completed move into history. If both locations exist after interruption,
  both are preserved for manual review. Undo is best-effort, not an all-or-nothing
  batch transaction. Empty folders are intentionally retained.
- Refresh the index after outside changes. The app currently indexes one folder at a
  time; it has no background watcher. Large scans keep metadata in memory before commit.
- Duplicate hashes identify matching contents, not redundant purpose: a backup or
  project copy may be intentional. Duplicate findings can become stale after edits.

## Development and checks

```sh
python -m unittest discover -s tests -v
node tests/ui_smoke.cjs   # optional: Node 18+; live API + UI controller harness
python app.py --no-browser --port 8765 --root /absolute/path/to/test-folder
```

`engine.py` owns indexing, rules, previews and move history. `desktop.py` is the
native orb, folder ring and toolbox. `desktop_model.py` contains folder pagination
and geometry helpers. `instance.py` prevents duplicate desktop instances.
`Findspace.pyw` is the windowed entry point. `app.py` and `web/` retain the optional
browser interface. Change extension categories in `engine.py` under `TYPES`.

This is a source release, not a signed Windows installer. Windows native rendering,
shortcut/launcher scripts, transparency and Explorer integration must be checked on
a Windows machine. Development verification ran headlessly on Linux. There is no
AI agent, semantic search, billing, telemetry, background autostart or automatic
file deletion. Run one interface at a time against the same local index.

Validation: 30 Python tests cover file handling, recovery, API protection, folder
pagination, ring geometry, preferences, instance locking and native controller
transitions using a canvas test double. The
Node smoke harness exercises overview, search, filtering, storage, duplicates,
preview, confirmation, moving and undo against the live Python server using a
minimal DOM. Native and browser rendering were not visually verified in the build
environment; neither a graphical display nor a Windows runner was available.

## Product direction

The initial promise is simple: **“I can't remember where I saved it, and Downloads is
a mess.”** Prove fast retrieval and trusted organization with a few real users before
adding AI, subscriptions or wider computer-management features.

## Continue with Codex

Repository: <https://github.com/Atomstars/File-manager->, branch `main`.
Read `AGENTS.md` and `HANDOFF.md` first for requirements, verified behavior and
remaining Windows packaging/testing work. The current implementation is Python;
no Java migration or Windows executable has been completed.
