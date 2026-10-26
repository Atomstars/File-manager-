package io.github.atomstars.findspace.domain;

import java.nio.file.Path;
import java.time.Instant;

public record MoveRecord(
    String id,
    String batch,
    Path source,
    Path destination,
    String sha256,
    String status,
    Instant createdAt) {}
