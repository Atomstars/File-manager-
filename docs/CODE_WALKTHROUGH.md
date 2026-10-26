# Learn the flow

Import pom.xml in your IDE and read these files in order. Use Go to definition
to follow calls.

1. **bootstrap/Findspace.java** parses options, acquires a single-instance lock,
   wires adapters/use cases and opens the desktop window.
2. **domain/FileEntry.java and Snapshot.java** explain the data. Java records
   supply immutable fields, accessors and value equality.
3. **application/Workspace.java** coordinates workflows. scan() asks the adapter
   for metadata, commits a snapshot and invalidates previews. apply() validates
   selections, delegates the move and refreshes the index.
4. **application/OrganizationService.java** plans destinations without moving
   anything. Callers select from its preview instead of supplying destinations.
5. **application/MoveService.java** owns validation → journal → exclusive link →
   verification → unlink → completion. Undo reverses this protocol.
6. **application/port/** defines the filesystem/database behavior the workflows need.
7. **infrastructure/filesystem/ and persistence/** implement these contracts using
   NIO, native Windows file IDs and SQLite. No UI logic belongs here.
8. **ui/MainWindow.java** composes small views. TaskRunner keeps slow operations
   off the UI thread. OrganizationPanel handles selection/confirmation only.
9. **src/test/java/.../application/WorkspaceTest.java** demonstrates real workflows
   in temporary folders. Start with moveRestartAndUndoPreserveContent.

```text
OrganizationPanel.apply()
  → TaskRunner.run()                 leave UI thread
  → Workspace.apply()                coordinate workflow
  → OrganizationService.consume()   validate preview and selection
  → MoveService.apply()              execute journaled protocol
  → FileAccess / WorkspaceStore      filesystem and durable storage
  → Workspace.scan()                 refresh index
  → completion callback              report completed/skipped files
```

Add rules to the planner, database behavior to the store, and presentation to views.
Keep unrelated logic out of Workspace and MainWindow.

For the desktop shell, follow `FolderOrb` (navigation) → `DesktopCatalog`
(read-only discovery) → `OrbCanvas` and `RadialLayout` (presentation). SettingsDialog
edits UserSettings; pins are references. SoftwarePanel uses the same catalog to
discover installed shortcuts. This flow is separate from journaled file moves.
