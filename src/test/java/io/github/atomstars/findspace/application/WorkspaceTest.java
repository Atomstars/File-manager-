package io.github.atomstars.findspace.application;

import static org.junit.jupiter.api.Assertions.*;

import io.github.atomstars.findspace.domain.*;
import io.github.atomstars.findspace.infrastructure.filesystem.NioFileAccess;
import io.github.atomstars.findspace.infrastructure.persistence.SqliteWorkspaceStore;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceTest {
  @Test
  void collectLooseCodeKeepsProjectSourcesIntact() throws IOException {
    Path loose = put("script.java", "class Demo {}");
    Path project = put("project/Main.java", "class Main {}");
    scan();
    assertTrue(workspace.preview(false, false).items().isEmpty());
    var plan = workspace.preview(true, false, true);
    assertEquals(1, plan.items().size());
    assertEquals(
        root.toRealPath().resolve("Findspace Organized/Code/script.java"),
        plan.items().getFirst().destination());
    assertEquals(1, apply(plan).completed());
    assertFalse(Files.exists(loose));
    assertTrue(Files.exists(project));
  }

  @TempDir Path temporary;
  Path root, state;
  NioFileAccess files;
  SqliteWorkspaceStore store;
  Workspace workspace;

  @BeforeEach
  void setup() throws IOException {
    // Windows runners can supply an 8.3 temp alias. Production scan/journal paths
    // use the canonical root, so recovery fixtures must use that representation too.
    root = Files.createDirectory(temporary.resolve("Downloads")).toRealPath();
    state = Files.createDirectory(temporary.resolve("state")).toRealPath();
    files = new NioFileAccess(state);
    store = new SqliteWorkspaceStore(state);
    workspace = new Workspace(files, store, Clock.systemUTC());
  }

  Path put(String name, String text) throws IOException {
    Path p = root.resolve(name);
    Files.createDirectories(p.getParent());
    return Files.writeString(p, text);
  }

  void scan() throws IOException {
    workspace.scan(root);
  }

  OperationReport apply(MovePlan plan) throws IOException {
    return workspace.apply(
        plan.id(), plan.items().stream().map(i -> i.file().path()).collect(Collectors.toSet()));
  }

  @Test
  void scanSearchAndExclusions() throws IOException {
    put("Invoice October.pdf", "12345");
    put("nested/Resume.pdf", "123");
    put("node_modules/a.txt", "excluded");
    put(".secret.txt", "excluded");
    put("unfinished.crdownload", "excluded");
    scan();
    var snapshot = workspace.snapshot().orElseThrow();
    assertEquals(2, snapshot.files().size());
    assertEquals(8, snapshot.totalBytes());
    assertEquals(
        1,
        new SearchService().search(snapshot, "invoice pdf", null, SearchService.Sort.NAME).size());
    assertTrue(
        new SearchService().search(snapshot, "absent", null, SearchService.Sort.NAME).isEmpty());
    assertEquals(snapshot, new Workspace(files, store, Clock.systemUTC()).snapshot().orElseThrow());
  }

  @Test
  void literalSearchCharacters() throws IOException {
    put("100%_complete.pdf", "a");
    put("100xxcomplete.pdf", "b");
    scan();
    assertEquals(
        1,
        new SearchService()
            .search(workspace.snapshot().orElseThrow(), "%_", null, SearchService.Sort.NAME)
            .size());
  }

  @Test
  void previewIsReadOnlyAndKeepsProjects() throws IOException {
    Path original = put("notes.txt", "notes");
    put("project/spec.pdf", "project");
    put("script.py", "code");
    scan();
    var plan = workspace.preview(false, false);
    assertEquals(1, plan.items().size());
    assertTrue(Files.exists(original));
    assertFalse(Files.exists(root.resolve("Findspace Organized")));
  }

  @Test
  void moveRestartAndUndoPreserveContent() throws IOException {
    Path original = put("resume.pdf", "my resume");
    scan();
    var plan = workspace.preview(false, false);
    assertEquals(1, apply(plan).completed());
    assertFalse(Files.exists(original));
    assertEquals("my resume", Files.readString(plan.items().getFirst().destination()));
    var restarted = new Workspace(files, new SqliteWorkspaceStore(state), Clock.systemUTC());
    assertEquals(1, restarted.undo(restarted.history().getFirst().batch()).completed());
    assertEquals("my resume", Files.readString(original));
    assertTrue(restarted.history().isEmpty());
  }

  @Test
  void changedSourceAndLateDestinationAreSkipped() throws IOException {
    Path original = put("notes.txt", "original");
    scan();
    var plan = workspace.preview(false, false);
    Files.writeString(original, "edited");
    assertEquals(0, apply(plan).completed());
    assertEquals("edited", Files.readString(original));
    plan = workspace.preview(false, false);
    Path destination = plan.items().getFirst().destination();
    Files.createDirectories(destination.getParent());
    Files.writeString(destination, "occupied");
    assertEquals(0, apply(plan).completed());
    assertEquals("occupied", Files.readString(destination));
    assertTrue(Files.exists(original));
  }

  @Test
  void collisionNamesAndSelectedSubset() throws IOException {
    put("a/same.pdf", "one");
    put("b/same.pdf", "two");
    put("Findspace Organized/Documents/same.pdf", "existing");
    scan();
    var plan = workspace.preview(true, true);
    assertEquals(2, plan.items().size());
    assertNotEquals(plan.items().get(0).destination(), plan.items().get(1).destination());
    var selected = plan.items().getFirst();
    assertEquals(1, workspace.apply(plan.id(), Set.of(selected.file().path())).completed());
    assertTrue(Files.exists(plan.items().get(1).file().path()));
    assertEquals(
        "existing", Files.readString(root.resolve("Findspace Organized/Documents/same.pdf")));
  }

  @Test
  void editedUndoAndOccupiedOriginalAreSkipped() throws IOException {
    Path original = put("notes.txt", "original");
    scan();
    var plan = workspace.preview(false, false);
    apply(plan);
    String batch = workspace.history().getFirst().batch();
    Path destination = plan.items().getFirst().destination();
    Files.writeString(destination, "edited");
    assertEquals(0, workspace.undo(batch).completed());
    assertFalse(Files.exists(original));
    Files.writeString(destination, "original");
    Files.writeString(original, "new original");
    assertEquals(0, workspace.undo(batch).completed());
    assertEquals("new original", Files.readString(original));
  }

  @Test
  void unknownEmptyAndConsumedSelectionsAreRejected() throws IOException {
    put("notes.txt", "content");
    scan();
    var plan = workspace.preview(false, false);
    assertThrows(IOException.class, () -> workspace.apply(plan.id(), Set.of()));
    assertThrows(
        IOException.class,
        () -> workspace.apply(plan.id(), Set.of(temporary.resolve("outside.txt"))));
    apply(plan);
    assertThrows(IOException.class, () -> apply(plan));
  }

  @Test
  void expiredAndInvalidatedPlansAreRejected() throws IOException {
    put("notes.txt", "a");
    scan();
    var snapshot = workspace.snapshot().orElseThrow();
    MutableClock clock = new MutableClock();
    var service = new OrganizationService(files, clock);
    var plan = service.preview(snapshot, false, false);
    clock.instant = clock.instant.plusSeconds(3601);
    assertThrows(
        IOException.class,
        () -> service.consume(plan.id(), root, Set.of(root.resolve("notes.txt"))));
    var live = workspace.preview(false, false);
    scan();
    assertThrows(IOException.class, () -> apply(live));
  }

  @Test
  void duplicatesAreReadOnlyAndIgnoreHardlinkAliases() throws IOException {
    Path first = put("one.pdf", "same");
    put("two.txt", "same");
    put("three.pdf", "diff");
    Files.createLink(root.resolve("alias.pdf"), first);
    scan();
    var result = workspace.duplicates();
    assertEquals(1, result.groups().size());
    assertEquals(2, result.groups().getFirst().files().size());
    assertEquals(4, result.groups().getFirst().potentialBytes());
    try (var stream = Files.list(root)) {
      assertEquals(4, stream.count());
    }
  }

  @Test
  void changedDuplicatesAreReported() throws IOException {
    Path first = put("one.pdf", "same");
    put("two.pdf", "same");
    scan();
    Files.writeString(first, "changed");
    var result = workspace.duplicates();
    assertTrue(result.groups().isEmpty());
    assertEquals(1, result.problems().size());
  }

  @Test
  void historyIsScopedAndForgedDestinationsCannotEscape() throws IOException {
    put("notes.txt", "a");
    scan();
    var plan = workspace.preview(false, false);
    var file = plan.items().getFirst().file();
    var report =
        new MoveService(files, store)
            .apply(root, List.of(new MovePlan.Item(file, temporary.resolve("escaped.txt"))));
    assertEquals(0, report.completed());
    assertFalse(Files.exists(temporary.resolve("escaped.txt")));
    apply(plan);
    workspace.scan(Files.createDirectory(temporary.resolve("Other")));
    assertTrue(workspace.history().isEmpty());
  }

  @Test
  void invalidRootDoesNotReplaceIndex() throws IOException {
    put("notes.txt", "a");
    scan();
    Snapshot before = workspace.snapshot().orElseThrow();
    assertThrows(IOException.class, () -> workspace.scan(root.getRoot()));
    assertThrows(IOException.class, () -> workspace.scan(Path.of("relative")));
    assertEquals(before, workspace.snapshot().orElseThrow());
  }

  @Test
  void pendingMoveAndUndoRecoverAcrossRestart() throws IOException {
    put("notes.txt", "recover");
    scan();
    var item = workspace.preview(false, false).items().getFirst();
    Path from = item.file().path(), to = item.destination();
    files.prepareDestination(to, root);
    String sha = files.digest(from);
    store.insertMove(new MoveRecord("id", "batch", from, to, sha, "pending", Instant.now()));
    Files.createLink(to, from);
    Files.delete(from);
    assertEquals(1, workspace.recover().completed());
    assertEquals("moved", store.moves().getFirst().status());
    store.updateStatus("id", "undo_pending");
    Files.createLink(from, to);
    Files.delete(to);
    assertEquals(1, new Workspace(files, store, Clock.systemUTC()).recover().completed());
    assertEquals("undone", store.moves().getFirst().status());
    assertEquals("recover", Files.readString(from));
  }

  @Test
  void ambiguousRecoveryPreservesBothPaths() throws IOException {
    put("notes.txt", "recover");
    scan();
    var item = workspace.preview(false, false).items().getFirst();
    files.prepareDestination(item.destination(), root);
    store.insertMove(
        new MoveRecord(
            "id",
            "batch",
            item.file().path(),
            item.destination(),
            files.digest(item.file().path()),
            "pending",
            Instant.now()));
    Files.createLink(item.destination(), item.file().path());
    var report = workspace.recover();
    assertEquals(0, report.completed());
    assertEquals(1, report.problems().size());
    assertTrue(Files.exists(item.destination()));
    assertTrue(Files.exists(item.file().path()));
    assertEquals("pending", store.moves().getFirst().status());
  }

  @Test
  void symlinksAreExcludedAndCannotBeDestinations() throws IOException {
    Path outside = Files.createDirectory(temporary.resolve("Outside"));
    try {
      Files.createSymbolicLink(root.resolve("linked"), outside);
    } catch (IOException | UnsupportedOperationException e) {
      Assumptions.abort("Symlink privilege unavailable: " + e.getMessage());
    }
    put("notes.txt", "a");
    scan();
    assertEquals(1, workspace.snapshot().orElseThrow().files().size());
    assertThrows(
        IOException.class, () -> files.requireContained(root.resolve("linked/escape.txt"), root));
    assertTrue(files.folders(root).isEmpty());
  }

  @Test
  void stateDirectoryIsNeverIndexed() throws IOException {
    Path nestedState = Files.createDirectory(root.resolve("state"));
    Files.writeString(nestedState.resolve("private.txt"), "state");
    var access = new NioFileAccess(nestedState);
    assertEquals(0, access.scan(root).files().size());
    assertThrows(IOException.class, () -> access.validateRoot(nestedState));
  }

  private static final class MutableClock extends Clock {
    Instant instant = Instant.parse("2026-01-01T00:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return instant;
    }
  }
}
