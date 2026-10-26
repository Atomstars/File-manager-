package io.github.atomstars.findspace.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.github.atomstars.findspace.application.*;
import io.github.atomstars.findspace.infrastructure.desktop.*;
import io.github.atomstars.findspace.infrastructure.filesystem.NioFileAccess;
import io.github.atomstars.findspace.infrastructure.persistence.SqliteWorkspaceStore;
import java.nio.file.*;
import java.sql.*;
import java.time.Clock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
  @TempDir Path temporary;

  @Test
  void importsPythonJournalWithBackupAndCanUndo() throws Exception {
    Path root = Files.createDirectory(temporary.resolve("Downloads")),
        state = Files.createDirectory(temporary.resolve("state"));
    Path source = root.resolve("notes.txt"),
        destination = root.resolve("Findspace Organized/Documents/notes.txt");
    Files.createDirectories(destination.getParent());
    Files.writeString(destination, "legacy");
    var files = new NioFileAccess(state);
    try (Connection c =
            DriverManager.getConnection("jdbc:sqlite:" + state.resolve("index.sqlite3"));
        Statement s = c.createStatement()) {
      s.execute(
          "CREATE TABLE moves (id TEXT PRIMARY KEY,batch TEXT,source TEXT,destination TEXT,sha TEXT,status TEXT,created REAL)");
      try (PreparedStatement p =
          c.prepareStatement("INSERT INTO moves VALUES ('id','batch',?,?,?,'moved',1)")) {
        p.setString(1, source.toString());
        p.setString(2, destination.toString());
        p.setString(3, files.digest(destination));
        p.executeUpdate();
      }
    }
    var store = new SqliteWorkspaceStore(state);
    new SqliteWorkspaceStore(state);
    try (var entries = Files.list(state)) {
      assertEquals(
          1, entries.filter(p -> p.getFileName().toString().startsWith("before-java-")).count());
    }
    var workspace = new Workspace(files, store, Clock.systemUTC());
    workspace.scan(root);
    assertEquals(1, workspace.history().size());
    assertEquals(1, workspace.undo("batch").completed());
    assertEquals("legacy", Files.readString(source));
  }

  @Test
  void instanceLockAndActivation() throws Exception {
    try (var first = new InstanceGuard(temporary)) {
      assertTrue(first.acquired());
      assertFalse(first.activationRequested());
      try (var second = new InstanceGuard(temporary)) {
        assertFalse(second.acquired());
        assertTrue(first.activationRequested());
        assertFalse(first.activationRequested());
      }
    }
    try (var restarted = new InstanceGuard(temporary)) {
      assertTrue(restarted.acquired());
    }
  }

  @Test
  void preferencesPersistAndMalformedNumberFallsBack() throws Exception {
    var settings = new UserSettings(temporary);
    settings.put("folder", "C:\\some folder");
    settings.put("orb.x", "invalid");
    settings.save();
    var restored = new UserSettings(temporary);
    assertEquals("C:\\some folder", restored.get("folder", ""));
    assertEquals(90, restored.integer("orb.x", 90));
  }
}
