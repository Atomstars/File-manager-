package io.github.atomstars.findspace.infrastructure.persistence;

import io.github.atomstars.findspace.application.port.WorkspaceStore;
import io.github.atomstars.findspace.domain.*;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Additive schema: the Python move journal is reused; its old index remains untouched. */
public final class SqliteWorkspaceStore implements WorkspaceStore {
  private final String url;

  public SqliteWorkspaceStore(Path directory) throws IOException {
    Files.createDirectories(directory);
    Path database = directory.resolve("index.sqlite3");
    url = "jdbc:sqlite:" + database.toAbsolutePath();
    // SQLite performs a consistent backup, including committed WAL transactions.
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      boolean migrated;
      try (ResultSet r =
          s.executeQuery(
              "SELECT name FROM sqlite_master WHERE type='table' AND name='java_files'")) {
        migrated = r.next();
      }
      if (!migrated) {
        Path backup = directory.resolve("before-java-" + UUID.randomUUID() + ".sqlite3");
        boolean hasHistory;
        try (ResultSet r =
            s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='moves'")) {
          hasHistory = r.next();
        }
        if (hasHistory) s.execute("VACUUM INTO '" + backup.toString().replace("'", "''") + "'");
      }
      s.execute(
          "CREATE TABLE IF NOT EXISTS java_files (path TEXT PRIMARY KEY,category TEXT,size INTEGER,modified TEXT,identity TEXT)");
      s.execute("CREATE TABLE IF NOT EXISTS java_meta (key TEXT PRIMARY KEY,value TEXT)");
      s.execute(
          "CREATE TABLE IF NOT EXISTS moves (id TEXT PRIMARY KEY,batch TEXT,source TEXT,destination TEXT,sha TEXT,status TEXT,created REAL)");
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  private Connection connect() throws SQLException {
    Connection c = DriverManager.getConnection(url);
    try (Statement s = c.createStatement()) {
      s.execute("PRAGMA busy_timeout=10000");
      s.execute("PRAGMA synchronous=FULL");
    }
    return c;
  }

  private static IOException failure(SQLException e) {
    return new IOException("Cannot access Findspace's local database: " + e.getMessage(), e);
  }

  @Override
  public Optional<Snapshot> loadSnapshot() throws IOException {
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      Map<String, String> meta = new HashMap<>();
      try (ResultSet r = s.executeQuery("SELECT key,value FROM java_meta")) {
        while (r.next()) meta.put(r.getString(1), r.getString(2));
      }
      if (!meta.containsKey("root")) return Optional.empty();
      List<FileEntry> entries = new ArrayList<>();
      try (ResultSet r = s.executeQuery("SELECT * FROM java_files ORDER BY path")) {
        while (r.next())
          entries.add(
              new FileEntry(
                  Path.of(r.getString("path")),
                  Category.valueOf(r.getString("category")),
                  new FileEntry.Fingerprint(
                      r.getLong("size"), r.getString("modified"), r.getString("identity"))));
      }
      return Optional.of(
          new Snapshot(
              Path.of(meta.get("root")),
              Instant.parse(meta.get("scanned")),
              entries,
              Integer.parseInt(meta.getOrDefault("skipped", "0"))));
    } catch (SQLException | IllegalArgumentException e) {
      throw new IOException("Stored index is unreadable; original data has been preserved.", e);
    }
  }

  @Override
  public void saveSnapshot(Snapshot snapshot) throws IOException {
    try (Connection c = connect()) {
      c.setAutoCommit(false);
      try {
        try (Statement s = c.createStatement()) {
          s.executeUpdate("DELETE FROM java_files");
        }
        try (PreparedStatement p =
            c.prepareStatement("INSERT INTO java_files VALUES (?,?,?,?,?)")) {
          for (FileEntry file : snapshot.files()) {
            p.setString(1, file.path().toString());
            p.setString(2, file.category().name());
            p.setLong(3, file.size());
            p.setString(4, file.fingerprint().modified());
            p.setString(5, file.fingerprint().identity());
            p.addBatch();
          }
          p.executeBatch();
        }
        try (PreparedStatement p =
            c.prepareStatement("INSERT OR REPLACE INTO java_meta VALUES (?,?)")) {
          for (var entry :
              Map.of(
                      "root",
                      snapshot.root().toString(),
                      "scanned",
                      snapshot.scannedAt().toString(),
                      "skipped",
                      "" + snapshot.skipped())
                  .entrySet()) {
            p.setString(1, entry.getKey());
            p.setString(2, entry.getValue());
            p.addBatch();
          }
          p.executeBatch();
        }
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  @Override
  public void insertMove(MoveRecord move) throws IOException {
    try (Connection c = connect();
        PreparedStatement p = c.prepareStatement("INSERT INTO moves VALUES (?,?,?,?,?,?,?)")) {
      p.setString(1, move.id());
      p.setString(2, move.batch());
      p.setString(3, move.source().toString());
      p.setString(4, move.destination().toString());
      p.setString(5, move.sha256());
      p.setString(6, move.status());
      p.setDouble(7, move.createdAt().toEpochMilli() / 1000.0);
      p.executeUpdate();
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  @Override
  public List<MoveRecord> moves() throws IOException {
    try (Connection c = connect();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM moves ORDER BY created DESC")) {
      List<MoveRecord> result = new ArrayList<>();
      while (r.next())
        result.add(
            new MoveRecord(
                r.getString("id"),
                r.getString("batch"),
                Path.of(r.getString("source")),
                Path.of(r.getString("destination")),
                r.getString("sha"),
                r.getString("status"),
                Instant.ofEpochMilli((long) (r.getDouble("created") * 1000))));
      return List.copyOf(result);
    } catch (SQLException | IllegalArgumentException e) {
      throw new IOException("Move history could not be read; it has been preserved.", e);
    }
  }

  @Override
  public void updateStatus(String id, String status) throws IOException {
    try (Connection c = connect();
        PreparedStatement p = c.prepareStatement("UPDATE moves SET status=? WHERE id=?")) {
      p.setString(1, status);
      p.setString(2, id);
      p.executeUpdate();
    } catch (SQLException e) {
      throw failure(e);
    }
  }
}
