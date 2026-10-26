package io.github.atomstars.findspace.application;

import io.github.atomstars.findspace.application.port.*;
import io.github.atomstars.findspace.domain.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Journal first, exclusively create a hard link, verify, then unlink. No replacement or copy
 * fallback.
 */
public final class MoveService {
  private final FileAccess files;
  private final WorkspaceStore store;

  public MoveService(FileAccess files, WorkspaceStore store) {
    this.files = files;
    this.store = store;
  }

  private String prepare(Path root, Path source, Path destination, FileEntry.Fingerprint expected)
      throws IOException {
    files.requireContained(source, root);
    files.requireContained(destination, root);
    if (!files.fingerprint(source).equals(expected))
      throw new IOException("File changed since preview.");
    if (files.exists(destination))
      throw new IOException("Destination is occupied; nothing was overwritten.");
    files.prepareDestination(destination, root);
    String sha = files.digest(source);
    if (!files.fingerprint(source).equals(expected))
      throw new IOException("File changed while preparing the move.");
    return sha;
  }

  private void transfer(
      Path root, Path source, Path destination, FileEntry.Fingerprint expected, String sha)
      throws IOException {
    files.requireContained(source, root);
    files.requireContained(destination, root);
    files.link(source, destination);
    // On failed verification keep both paths. Deleting a path after a race can erase someone else's
    // replacement.
    if (!files.fingerprint(source).equals(expected)
        || !files.sameFile(source, destination)
        || !files.digest(destination).equals(sha))
      throw new IOException("File changed during move; both paths retained for review.");
    files.requireContained(source, root);
    files.requireContained(destination, root);
    if (!files.fingerprint(source).equals(expected) || !files.sameFile(source, destination))
      throw new IOException("File identity changed; both paths retained.");
    files.delete(source);
  }

  public OperationReport apply(Path root, List<MovePlan.Item> items) {
    int done = 0;
    List<String> errors = new ArrayList<>();
    String batch = UUID.randomUUID().toString();
    for (var item : items) {
      try {
        var file = item.file();
        String sha = prepare(root, file.path(), item.destination(), file.fingerprint());
        String id = UUID.randomUUID().toString();
        store.insertMove(
            new MoveRecord(
                id, batch, file.path(), item.destination(), sha, "pending", Instant.now()));
        transfer(root, file.path(), item.destination(), file.fingerprint(), sha);
        store.updateStatus(id, "moved");
        done++;
      } catch (IOException failure) {
        errors.add(item.file().name() + ": " + failure.getMessage());
      }
    }
    return new OperationReport(done, errors);
  }

  public OperationReport undo(Path root, String batch) throws IOException {
    int done = 0;
    List<String> errors = new ArrayList<>();
    var records =
        store.moves().stream()
            .filter(
                r ->
                    r.batch().equals(batch)
                        && r.status().equals("moved")
                        && r.source().startsWith(root))
            .toList();
    if (records.isEmpty()) throw new IOException("No moves remain to undo in this folder.");
    for (var record : records) {
      try {
        files.requireContained(record.destination(), root);
        var expected = files.fingerprint(record.destination());
        String sha = prepare(root, record.destination(), record.source(), expected);
        if (!sha.equals(record.sha256())) throw new IOException("Edited file was left in place.");
        store.updateStatus(record.id(), "undo_pending");
        transfer(root, record.destination(), record.source(), expected, sha);
        store.updateStatus(record.id(), "undone");
        done++;
      } catch (IOException failure) {
        errors.add(record.source().getFileName() + ": " + failure.getMessage());
      }
    }
    return new OperationReport(done, errors);
  }

  public OperationReport recover(Path root) throws IOException {
    int done = 0;
    List<String> errors = new ArrayList<>();
    for (var record : store.moves()) {
      if (!record.source().startsWith(root)
          || !(record.status().equals("pending") || record.status().equals("undo_pending")))
        continue;
      try {
        files.requireContained(record.source(), root);
        files.requireContained(record.destination(), root);
        boolean undo = record.status().equals("undo_pending");
        Path from = undo ? record.destination() : record.source(),
            to = undo ? record.source() : record.destination();
        if (!files.exists(from) && files.exists(to) && files.digest(to).equals(record.sha256())) {
          store.updateStatus(record.id(), undo ? "undone" : "moved");
          done++;
        } else errors.add("Unresolved operation (files preserved): " + record.source());
      } catch (IOException failure) {
        errors.add(record.source() + ": " + failure.getMessage());
      }
    }
    return new OperationReport(done, errors);
  }
}
