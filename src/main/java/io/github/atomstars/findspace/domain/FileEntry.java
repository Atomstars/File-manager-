package io.github.atomstars.findspace.domain;

import java.nio.file.Path;
import java.time.Instant;

public record FileEntry(Path path, Category category, Fingerprint fingerprint) {
  public String name() {
    return path.getFileName().toString();
  }

  public long size() {
    return fingerprint.size();
  }

  public Instant modified() {
    return Instant.parse(fingerprint.modified());
  }

  public record Fingerprint(long size, String modified, String identity) {}
}
