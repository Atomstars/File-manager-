package io.github.atomstars.findspace.infrastructure.desktop;

import java.nio.file.*;

public final class AppPaths {
  private AppPaths() {}

  public static Path stateDirectory() {
    String local = System.getenv("LOCALAPPDATA");
    return local != null
        ? Path.of(local, "Findspace")
        : Path.of(System.getProperty("user.home"), ".local", "share", "Findspace");
  }

  public static Path defaultFolder() {
    Path home = Path.of(System.getProperty("user.home"));
    for (String name : new String[] {"Downloads", "Documents", "Desktop"})
      if (Files.isDirectory(home.resolve(name))) return home.resolve(name);
    return home;
  }
}
