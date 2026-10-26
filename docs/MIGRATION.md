# Python to Java

The Python baseline is Git commit a234b45. The active source tree is now Java.
The obsolete browser server/UI, Python tests, PyInstaller build and VBS launchers
are removed after Java integration verification and remain recoverable in Git.
Repository cleanup never deletes personal files or application history.

## Existing users

1. Quit Python before switching. Windows shares the same named instance mutex;
   Java also locks desktop.lock.
2. Launch Java with the same state directory.
3. Existing journals are backed up as before-java-<unique-id>.sqlite3 before
   separate Java index tables are added.
4. Choose and scan the previously organized folder. Its history appears and
   supports undo when contents still match.
5. Python desktop.json preferences remain untouched. Java desktop.properties
   starts with fresh position/theme preferences.

The moves table preserves columns/digests. Java adds undo_pending for interrupted
undo; finish such recovery in Java before using the old app. Java does not reuse
Python index fingerprints. The optional localhost API/browser UI is retired.

The Windows package is a folder with an EXE and bundled runtime, not a single-file
PyInstaller executable. The main window replaces the old toolbox; the desktop orb
starts alone and expands into nested radial folder navigation. Settings own pins,
appearance and behavior. Transparency has an opaque fallback on unsupported desktops.

Run mvnw verify for integration and architecture checks. Tests exercise temporary
files and real SQLite; they do not organize user documents. Packaged startup and
visual acceptance require a graphical Windows environment. CI targets JDK 21;
development can use a newer JDK with Java 21 bytecode output.
