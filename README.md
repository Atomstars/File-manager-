# Findspace

**Find your files. Understand your space. Give everything a home.**

A local Java desktop workspace with search, storage summaries, a floating folder
navigator, organization previews, exact duplicates and persistent undo.
No account, cloud service, browser or API key is needed.

## Run

Development requires JDK 21 or later. Maven 3.9.12 is pinned by the wrapper;
the first run downloads Maven and declared dependencies.

```powershell
.\mvnw.cmd verify
java --enable-native-access=ALL-UNNAMED -jar target/findspace.jar
```

On Linux/macOS use `./mvnw verify`. Import `pom.xml` into your Java IDE.
A graphical desktop is required. For isolated testing, pass
`--state C:\Temp\findspace-state --root C:\Temp\sample-files`.

Findspace starts with only an **A** badge. Hover A to expand your ring; hover a folder
to reveal a small ring centered on that folder. The other desktop icons temporarily
hide to keep the rings clear; click A to return. Double-click
an item to open it. Drag the center to reposition. Right-click for all actions.
Move away to collapse back to A. Scroll over the ring (or use the right-click
Next/Previous page commands) to browse additional items.

Use **Settings → My items** to add and reorder files, folders or app shortcuts.
Desktop files and shortcuts, including the public Windows desktop, appear alongside
pins by default. They remain at their original paths; nothing is moved or copied.
Each expansion refreshes the list. Settings can disable desktop inclusion. Appearance
settings control size, hover delay, theme, always-on-top and automatic collapse.
Both rings page eight items. Recycle Bin and other virtual Windows shell icons are
not filesystem items and are not included. Hidden/unavailable items are excluded.

**Manage files** opens the workspace: choose a folder, preview destinations,
select files, then confirm. Its button appears directly below A when expanded.
The launcher uses instant folder/type badges to avoid waiting for Windows shell
icon resolution. Optional **Collect loose code** gathers standalone
code files into `Findspace Organized/Code`; code inside project folders stays in place.
Search, storage, duplicates and persistent undo are available from the sidebar.
**Apps** discovers Windows Start Menu shortcuts for opening and pinning, with
access to Windows app settings. Installed application directories are never moved.
Closing the workspace returns to the orb; right-click the orb to quit.

## Windows distribution

```powershell
.\scripts\package-windows.ps1
.\scripts\install-desktop.ps1
```

This verifies the project and creates `target/desktop/Findspace/Findspace.exe`
with its Java runtime. Distribute the **entire Findspace folder**, not the EXE
alone. End users need neither Java nor Python installed. The desktop installation
script copies the package to a new version folder under `%LOCALAPPDATA%\Programs\Findspace`
and creates **Findspace.lnk** on the desktop. Previous installations and preferences
are retained; an existing shortcut is backed up before replacement.
The package is unsigned; signing/installer work is
separate. Existing outputs are preserved; use `-Destination` for another build.

## Structure

```text
src/main/java/io/github/atomstars/findspace/
  bootstrap/                  Startup and dependency wiring
  domain/                     Immutable records and classification
  application/                Search, planning, moves, recovery, duplicates
    port/                     Filesystem and persistence interfaces
  infrastructure/
    filesystem/               NIO and Windows file identity
    persistence/              SQLite and legacy history compatibility
    desktop/                  Preferences, instance locking, OS integration
  ui/                         Focused views and background task coordination
src/test/java/                Integration and architecture checks
docs/                        System design and learning walkthrough
scripts/                     Development and Windows packaging
.github/workflows/           CI verification and Windows artifact
```

Start with [the code walkthrough](docs/CODE_WALKTHROUGH.md) and
[system design](docs/ARCHITECTURE.md).

## File safety

- Preview, select and confirm before moves. Previews expire after an hour and
  are invalidated by scans. Changed files and occupied destinations are skipped.
- Loose files only by default. Including subfolders can break project references
  and requires explicit selection. Code stays in place unless loose-code collection
  is selected; code inside subfolders and unknown types remain in place.
- Moves exclusively create a hard link, verify identity/content, then unlink the
  source. Unsupported/cross-device operations are skipped; no copy/delete fallback.
- Journal intent before mutation. Undo survives restart and skips edited files
  and occupied originals. Interrupted moves and undo can be recovered.
- Ambiguous recovery preserves both paths for manual review. Duplicate checks
  never delete files. Hard-linked aliases are not counted as duplicate copies.
- Links, junctions, reparse points, hidden/system/dependency entries and unfinished
  downloads are excluded. Unreadable entries count as skipped.

Avoid external edits/moves while organizing. This is a personal file tool, not
an adversarial filesystem sandbox.

## State and migration

State lives in `%LOCALAPPDATA%\Findspace` on Windows and
`~/.local/share/Findspace` elsewhere. An existing Python move journal is backed up
before the first additive Java schema update and remains usable for undo.
Java uses separate index tables and requires a fresh scan. Python preferences
remain untouched; Java uses `desktop.properties`.
See [migration notes](docs/MIGRATION.md).

The superseded Python/browser implementation, tests and launchers are removed
from the active tree but recoverable from Git commit `a234b45`.

## Development

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spotless:apply
```

Verification compiles, tests real files and SQLite, packages, and checks Java
formatting. Symlink tests explicitly skip when OS privileges prevent creation.
Do not commit generated `target/`, database files, runtimes or credentials.

Scope: one selected folder, explicit refresh, name/path search and category/month
organization. Metadata remains in memory and SQLite; the table displays up to
5,000 matches. Storage totals represent indexed file lengths, not physical
allocation/whole-drive capacity. No watcher, automatic cleanup, autostart or
semantic search. The orb uses transparency where the desktop supports it, with
an opaque fallback. It is a floating application, not a Windows shell replacement.
