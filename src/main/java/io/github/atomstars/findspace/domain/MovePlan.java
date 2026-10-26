package io.github.atomstars.findspace.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MovePlan(UUID id, Path root, Instant createdAt, List<Item> items) {
  public MovePlan {
    items = List.copyOf(items);
  }

  public record Item(FileEntry file, Path destination) {}
}
