package io.github.atomstars.findspace.application;

import io.github.atomstars.findspace.application.port.FileAccess;
import io.github.atomstars.findspace.domain.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class DuplicateService {
  public record Group(String sha256, List<FileEntry> files) {
    public Group {
      files = List.copyOf(files);
    }

    public long potentialBytes() {
      return files.getFirst().size() * (files.size() - 1);
    }
  }

  public record Result(List<Group> groups, List<String> problems) {
    public Result {
      groups = List.copyOf(groups);
      problems = List.copyOf(problems);
    }
  }

  private final FileAccess files;

  public DuplicateService(FileAccess files) {
    this.files = files;
  }

  public Result find(Snapshot snapshot) {
    var sizes =
        snapshot.files().stream()
            .filter(f -> f.size() > 0)
            .collect(Collectors.groupingBy(FileEntry::size));
    List<Group> result = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    for (var candidates : sizes.values()) {
      if (candidates.size() < 2) continue;
      Map<String, List<FileEntry>> hashes = new HashMap<>();
      Set<String> identities = new HashSet<>();
      for (var file : candidates) {
        try {
          files.requireContained(file.path(), snapshot.root());
          if (!files.fingerprint(file.path()).equals(file.fingerprint()))
            throw new IOException("Changed since scan");
          if (!identities.add(file.fingerprint().identity())) continue;
          hashes.computeIfAbsent(files.digest(file.path()), ignored -> new ArrayList<>()).add(file);
        } catch (IOException failure) {
          errors.add(file.path() + ": " + failure.getMessage());
        }
      }
      hashes.forEach(
          (hash, matches) -> {
            if (matches.size() > 1) result.add(new Group(hash, matches));
          });
    }
    result.sort(Comparator.comparingLong(Group::potentialBytes).reversed());
    return new Result(result, errors);
  }
}
