package io.github.atomstars.findspace.infrastructure.desktop;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class UserSettings {
  private final Path file;
  private final Properties properties = new Properties();

  public UserSettings(Path state) throws IOException {
    file = state.resolve("desktop.properties");
    if (Files.exists(file))
      try (var input = Files.newInputStream(file)) {
        properties.load(input);
      }
  }

  public String get(String key, String fallback) {
    return properties.getProperty(key, fallback);
  }

  public int integer(String key, int fallback) {
    try {
      return Integer.parseInt(get(key, ""));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  public void put(String key, Object value) {
    properties.setProperty(key, value.toString());
  }

  public List<Path> pins() {
    List<Path> paths = new ArrayList<>();
    for (int i = 0; i < Math.min(200, Math.max(0, integer("pins.count", 0))); i++) {
      String value = get("pins." + i, "");
      try {
        if (!value.isBlank()) paths.add(Path.of(value));
      } catch (InvalidPathException ignored) {
      }
    }
    return List.copyOf(paths);
  }

  public void pins(List<Path> paths) {
    if (paths.size() > 200) throw new IllegalArgumentException("Keep up to 200 pinned items.");
    properties.keySet().removeIf(key -> key.toString().startsWith("pins."));
    put("pins.count", paths.size());
    for (int i = 0; i < paths.size(); i++)
      put("pins." + i, paths.get(i).toAbsolutePath().normalize());
  }

  public void save() throws IOException {
    Path temporary = file.resolveSibling("desktop.properties.tmp");
    try (var output = Files.newOutputStream(temporary)) {
      properties.store(output, "Findspace desktop preferences");
    }
    try {
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
