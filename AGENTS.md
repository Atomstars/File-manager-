# Findspace development instructions

Read README.md, HANDOFF.md and docs/ARCHITECTURE.md before changes.

Findspace is a local Java desktop application. Preserve real folder navigation,
search, storage, preview/select/confirm organization, duplicates and persistent undo.

## Architecture

- domain: immutable records and classification.
- application: named use cases and ports; no Swing, JDBC or OS APIs.
- infrastructure: filesystem, SQLite and desktop adapters.
- ui: focused views; slow work runs through the background task runner.
- bootstrap: dependency wiring.

Keep code with its owner. Avoid a monolithic engine/controller. Use the pinned
Maven wrapper and Java formatter.

## File safety

Keep indexes/history in the user's state directory. Never overwrite destinations
or automatically delete duplicates. Reject expired previews and changed files.
Skip links/junctions/reparse points and stay inside the selected folder.
Journal before mutation. Preserve ambiguous paths. Undo skips edited files and
occupied originals. Back up existing databases before migration and preserve
Python move-history compatibility.

## Verification

Run mvnw.cmd verify (Windows) or ./mvnw verify. Format with mvnw.cmd spotless:apply.
Build the Windows package with scripts/package-windows.ps1. Test with temporary
--state and --root directories. Do not claim visual verification or packaging
unless performed. No subagents are required. Never commit target/, databases,
runtimes or credentials.
