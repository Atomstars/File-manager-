# Findspace project instructions

Read `HANDOFF.md` and `README.md` before changing the application.

## User's product requirements

- Make a computer file manager that helps a person who struggles to organize and locate files.
- Desktop-first: a clickable central icon reveals real subfolders around it.
- Folder navigation, quick file retrieval, storage visibility, organization previews and undo.
- Keep launch and setup simple. Avoid requiring an AI service, API key, browser or open terminal in desktop mode.
- User asked about using Java. Current implementation remains Python; no Java migration is implemented.

## Preserve existing behavior

- `Findspace.pyw` → `desktop.py` is the native desktop entry point.
- `engine.py` owns indexing, preview generation, verified moves, undo and exact duplicates.
- Keep index and operation history in the user's state directory, not inside the source tree.
- Never automatically delete duplicates or overwrite existing destination files.
- Preview/select/confirm before moving. Reject stale previews and changed sources.
- Skip symbolic links/junctions and system/dependency directories. Stay within the selected folder.
- Undo must skip edited files and occupied original locations.
- Do not replace real implementation with mock scores, fake file listings or decorative controls.

## Verification

```sh
python -m unittest discover -s tests -v
node tests/ui_smoke.cjs
```

Node is needed only for the optional browser UI smoke test, not to run the desktop app.
Native GUI checks require an actual graphical desktop. Windows packaging requires Windows.
Do not claim a Windows executable or visual verification exists until actually produced/tested.
No subagents or parallel agent work are required by this file.
