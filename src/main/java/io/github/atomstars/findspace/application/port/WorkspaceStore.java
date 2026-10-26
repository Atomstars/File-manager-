package io.github.atomstars.findspace.application.port;

import io.github.atomstars.findspace.domain.*;
import java.io.IOException;
import java.util.*;

/** Durable state boundary. UI and use cases do not know SQL or database connections. */
public interface WorkspaceStore {
  Optional<Snapshot> loadSnapshot() throws IOException;

  void saveSnapshot(Snapshot snapshot) throws IOException;

  void insertMove(MoveRecord record) throws IOException;

  List<MoveRecord> moves() throws IOException;

  void updateStatus(String id, String status) throws IOException;
}
