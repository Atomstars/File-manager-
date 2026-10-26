package io.github.atomstars.findspace.application.port;

import io.github.atomstars.findspace.domain.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface FileAccess {
  Path validateRoot(Path root) throws IOException;

  void requireContained(Path path, Path root) throws IOException;

  FileEntry.Fingerprint fingerprint(Path path) throws IOException;

  String digest(Path path) throws IOException;

  Snapshot scan(Path root) throws IOException;

  List<Path> folders(Path root) throws IOException;

  boolean exists(Path path) throws IOException;

  boolean sameFile(Path first, Path second) throws IOException;

  void prepareDestination(Path destination, Path root) throws IOException;

  void link(Path source, Path destination) throws IOException;

  void delete(Path path) throws IOException;
}
