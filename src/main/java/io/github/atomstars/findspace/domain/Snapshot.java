package io.github.atomstars.findspace.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record Snapshot(Path root, Instant scannedAt, List<FileEntry> files, int skipped) {
  public Snapshot {
    files = List.copyOf(files);
  }

  public long totalBytes() {
    return files.stream().mapToLong(FileEntry::size).sum();
  }
}
