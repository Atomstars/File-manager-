# Findspace continuation

The user chose a Java migration with readable architecture and a clean repository.
Read README.md, docs/ARCHITECTURE.md and docs/CODE_WALKTHROUGH.md.

The Maven project targets Java 21. Swing/FlatLaf provides the desktop UI;
SQLite JDBC owns persistence; JNA supports Windows file IDs and legacy mutex
compatibility. Dependency wiring lives in bootstrap/Findspace.java.
The retired Python baseline is commit a234b45.

Use mvnw.cmd verify and scripts/package-windows.ps1. Use explicit --state and
--root directories for tests. The Windows package includes its runtime.

Preserve preview/select/confirm, exclusive destinations, content/identity checks,
durable journaling, non-destructive recovery and edited-file undo checks.
Keep slow work off the UI thread and SQL/filesystem mutations out of views.

Remaining separate work: signing/installer, custom rules, whole-drive capacity,
content search and cross-device moves. The desktop shell has animated radial
navigation, nested folder rings, pins, preferences and Start Menu app discovery.
Code collection deliberately covers loose files only, preserving project trees.
The orb starts alone; closing the workspace returns to it. Right-click to quit.
The latest shell shows only A when idle, expands on center hover, and anchors the
child ring on the hovered folder. Scroll or right-click to page. Desktop items
and public desktop shortcuts are included alongside pins without moving files.
Interaction preference migration v2 enables desktop-only startup and collapse;
explicit --root isolates test navigation from the user's desktop.
Latest overlap/loading fix: stable icon positions, one active folder ring at a
time, central filename caption and a visible Manage files button below A.
Windows shell icon resolution was removed from navigation's critical path;
the launcher draws folder/type badges immediately. Click A to return from a folder.

Local verification: Maven verify passed with 27 tests, zero failures/errors and
one symlink-permission skip on Windows/JDK 24 (Java 21 bytecode). jpackage produced
the Windows app image with bundled runtime. Main workspace, idle A, hover expansion,
folder-centered child ring and automatic collapse were visually inspected on Windows.
Settings still need full visual acceptance.
CI targets Windows/Linux on JDK 21; that CI has not yet run for this migration.
The current GitHub OAuth token lacks workflow scope, so publishing the changed
workflow may require refreshing authorization. Keep Java sources and CI together.
