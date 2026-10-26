package io.github.atomstars.findspace.application;

import io.github.atomstars.findspace.application.port.FileAccess;
import io.github.atomstars.findspace.domain.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Previews are immutable, server-owned capabilities; callers select paths, not destinations. */
public final class OrganizationService {
  private final FileAccess files;
  private final Clock clock;
  private final LinkedHashMap<UUID, MovePlan> plans = new LinkedHashMap<>();

  public OrganizationService(FileAccess files, Clock clock) {
    this.files = files;
    this.clock = clock;
  }

  public void invalidate() {
    plans.clear();
  }

  public MovePlan preview(Snapshot snapshot, boolean recursive, boolean monthly)
      throws IOException {
    return preview(snapshot, recursive, monthly, false);
  }

  public MovePlan preview(
      Snapshot snapshot, boolean recursive, boolean monthly, boolean collectCode)
      throws IOException {
    List<MovePlan.Item> items = new ArrayList<>();
    Set<String> reserved = new HashSet<>();
    for (FileEntry file : snapshot.files()) {
      Path relative = snapshot.root().relativize(file.path());
      boolean looseCode =
          collectCode && file.category() == Category.CODE && relative.getNameCount() == 1;
      if ((!file.category().organizable() && !looseCode)
          || relative.getName(0).toString().equalsIgnoreCase("Findspace Organized")
          || (!recursive && relative.getNameCount() > 1)) continue;
      Path parent =
          snapshot.root().resolve("Findspace Organized").resolve(file.category().toString());
      if (monthly)
        parent =
            parent.resolve(
                DateTimeFormatter.ofPattern("yyyy-MM")
                    .withZone(clock.getZone())
                    .format(file.modified()));
      Path destination = parent.resolve(file.name());
      int suffix = file.name().lastIndexOf('.');
      String stem = suffix > 0 ? file.name().substring(0, suffix) : file.name();
      String extension = suffix > 0 ? file.name().substring(suffix) : "";
      for (int number = 2;
          files.exists(destination)
              || reserved.contains(destination.toString().toLowerCase(Locale.ROOT));
          number++) destination = parent.resolve(stem + " (" + number + ")" + extension);
      reserved.add(destination.toString().toLowerCase(Locale.ROOT));
      items.add(new MovePlan.Item(file, destination));
    }
    MovePlan plan = new MovePlan(UUID.randomUUID(), snapshot.root(), clock.instant(), items);
    plans.put(plan.id(), plan);
    while (plans.size() > 5) plans.remove(plans.keySet().iterator().next());
    return plan;
  }

  public List<MovePlan.Item> consume(UUID id, Path root, Set<Path> selected) throws IOException {
    MovePlan plan = plans.get(id);
    if (plan == null
        || !plan.root().equals(root)
        || Duration.between(plan.createdAt(), clock.instant()).compareTo(Duration.ofHours(1)) > 0)
      throw new IOException("Preview expired. Scan and preview again.");
    List<MovePlan.Item> items =
        plan.items().stream().filter(i -> selected.contains(i.file().path())).toList();
    if (selected.isEmpty() || items.size() != selected.size())
      throw new IOException("Select files from this preview.");
    plans.remove(id);
    return items;
  }
}
