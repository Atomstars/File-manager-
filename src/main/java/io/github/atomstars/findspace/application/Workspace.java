package io.github.atomstars.findspace.application;

import io.github.atomstars.findspace.application.port.*;
import io.github.atomstars.findspace.domain.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;

/**
 * Coordinates use cases. A single UI worker serializes operations; synchronized methods also
 * protect other callers.
 */
public final class Workspace {
  private final FileAccess files;
  private final WorkspaceStore store;
  private final OrganizationService organization;
  private final MoveService moves;
  private final DuplicateService duplicates;
  private Snapshot snapshot;

  public Workspace(FileAccess files, WorkspaceStore store, Clock clock) throws IOException {
    this.files = files;
    this.store = store;
    organization = new OrganizationService(files, clock);
    moves = new MoveService(files, store);
    duplicates = new DuplicateService(files);
    snapshot = store.loadSnapshot().orElse(null);
  }

  public synchronized Optional<Snapshot> snapshot() {
    return Optional.ofNullable(snapshot);
  }

  public synchronized Snapshot scan(Path root) throws IOException {
    Snapshot scanned = files.scan(root);
    store.saveSnapshot(scanned);
    snapshot = scanned;
    organization.invalidate();
    return scanned;
  }

  public List<Path> folders(Path path) throws IOException {
    return files.folders(path);
  }

  private Snapshot requireSnapshot() throws IOException {
    if (snapshot == null) throw new IOException("Choose a folder and scan it first.");
    return snapshot;
  }

  public synchronized MovePlan preview(boolean recursive, boolean monthly) throws IOException {
    return organization.preview(requireSnapshot(), recursive, monthly);
  }

  public synchronized MovePlan preview(boolean recursive, boolean monthly, boolean collectCode)
      throws IOException {
    return organization.preview(requireSnapshot(), recursive, monthly, collectCode);
  }

  public synchronized OperationReport apply(UUID id, Set<Path> selected) throws IOException {
    Path root = requireSnapshot().root();
    OperationReport report = moves.apply(root, organization.consume(id, root, selected));
    scan(root);
    return report;
  }

  public synchronized OperationReport undo(String batch) throws IOException {
    Path root = requireSnapshot().root();
    OperationReport report = moves.undo(root, batch);
    scan(root);
    return report;
  }

  public synchronized OperationReport recover() throws IOException {
    return moves.recover(requireSnapshot().root());
  }

  public synchronized List<MoveRecord> history() throws IOException {
    Path root = requireSnapshot().root();
    return store.moves().stream()
        .filter(r -> r.source().startsWith(root) && !r.status().equals("undone"))
        .toList();
  }

  public synchronized DuplicateService.Result duplicates() throws IOException {
    return duplicates.find(requireSnapshot());
  }
}
