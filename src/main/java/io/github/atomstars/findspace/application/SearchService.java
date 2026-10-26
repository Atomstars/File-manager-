package io.github.atomstars.findspace.application;

import io.github.atomstars.findspace.domain.*;
import java.util.*;

public final class SearchService {
  public enum Sort {
    NAME,
    SIZE,
    RECENT
  }

  public List<FileEntry> search(Snapshot snapshot, String query, Category category, Sort sort) {
    String[] terms = query.toLowerCase(Locale.ROOT).strip().split("\\s+");
    Comparator<FileEntry> ordering =
        switch (sort) {
          case NAME -> Comparator.comparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER);
          case SIZE -> Comparator.comparingLong(FileEntry::size).reversed();
          case RECENT -> Comparator.comparing(FileEntry::modified).reversed();
        };
    return snapshot.files().stream()
        .filter(f -> category == null || f.category() == category)
        .filter(
            f ->
                Arrays.stream(terms)
                    .allMatch(t -> f.path().toString().toLowerCase(Locale.ROOT).contains(t)))
        .sorted(ordering)
        .toList();
  }
}
