package io.github.atomstars.findspace.infrastructure.desktop;

import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;
import io.github.atomstars.findspace.domain.DesktopItem;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Read-only launcher catalog; installed applications are represented by shortcuts, never relocated.
 */
public final class DesktopCatalog {
  private DesktopCatalog() {}

  public static Path desktop() {
    if (System.getProperty("os.name").startsWith("Windows")) {
      try {
        return Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Desktop));
      } catch (RuntimeException unavailable) {
        /* Fallback for nonstandard profiles. */
      }
    }
    return Path.of(System.getProperty("user.home"), "Desktop");
  }

  public static DesktopItem item(Path path) throws IOException {
    var attributes =
        Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (attributes.isSymbolicLink() || attributes.isOther())
      throw new IOException("Links and junctions cannot be browsed: " + path);
    if (!attributes.isDirectory() && !attributes.isRegularFile())
      throw new IOException("Unsupported item: " + path);
    String label = path.getFileName() == null ? path.toString() : path.getFileName().toString();
    if (label.toLowerCase(Locale.ROOT).endsWith(".lnk"))
      label = label.substring(0, label.length() - 4);
    return new DesktopItem(path.toAbsolutePath().normalize(), label, attributes.isDirectory());
  }

  public static List<Path> desktopFolders() {
    List<Path> folders = new ArrayList<>();
    folders.add(desktop());
    if (System.getProperty("os.name").startsWith("Windows")) {
      try {
        Path common = Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_PublicDesktop));
        if (Files.isDirectory(common) && !folders.contains(common)) folders.add(common);
      } catch (RuntimeException unavailable) {
        // Some profiles do not expose a public desktop.
      }
    }
    return List.copyOf(folders);
  }

  /** Pins first, then current desktop contents. References only; never moves or copies files. */
  public static List<DesktopItem> launcherItems(List<Path> folders, List<Path> pins)
      throws IOException {
    Map<Path, DesktopItem> entries = new LinkedHashMap<>();
    for (Path pin : pins) {
      try {
        DesktopItem entry = item(pin);
        entries.put(entry.path(), entry);
      } catch (IOException unavailable) {
        // Keep unavailable pins in preferences so removable drives can return.
      }
    }
    for (Path folder : folders) {
      if (!Files.exists(folder)) continue;
      for (DesktopItem entry : children(folder)) entries.putIfAbsent(entry.path(), entry);
    }
    return List.copyOf(entries.values());
  }

  public static List<DesktopItem> children(Path folder) throws IOException {
    if (!item(folder).folder()) throw new IOException("Choose a regular folder.");
    List<DesktopItem> result = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
      for (Path path : stream) {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Navigation cancelled");
        if (result.size() >= 2000)
          throw new IOException(
              "This folder has more than 2,000 visible items. Open it in Explorer or pin a smaller folder.");
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith(".")
            || Set.of(
                    "node_modules",
                    "__pycache__",
                    "desktop.ini",
                    "$recycle.bin",
                    "system volume information")
                .contains(name)) continue;
        try {
          if (!Files.isHidden(path)) result.add(item(path));
        } catch (IOException inaccessible) {
          /* Unavailable items are not offered for launch. */
        }
      }
    }
    result.sort(
        Comparator.comparing(DesktopItem::folder)
            .reversed()
            .thenComparing(DesktopItem::label, String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(result);
  }

  public static List<DesktopItem> applications() throws IOException {
    List<DesktopItem> result = new ArrayList<>();
    for (String variable : List.of("APPDATA", "PROGRAMDATA")) {
      String base = System.getenv(variable);
      if (base == null) continue;
      Path start = Path.of(base, "Microsoft", "Windows", "Start Menu", "Programs");
      if (!Files.isDirectory(start)) continue;
      try (var paths = Files.walk(start, 6)) {
        for (Path path :
            paths
                .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".lnk"))
                .limit(2000)
                .toList()) {
          try {
            result.add(item(path));
          } catch (IOException inaccessible) {
          }
        }
      }
    }
    result.sort(Comparator.comparing(DesktopItem::label, String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(result);
  }

  public static void open(Path path) throws IOException {
    item(path); // Revalidate existence and link policy on click, never launch on hover.
    java.awt.Desktop.getDesktop().open(path.toFile());
  }

  public static void manageApplications() throws IOException {
    if (!System.getProperty("os.name").startsWith("Windows"))
      throw new IOException("Use your operating system's application manager.");
    new ProcessBuilder("explorer.exe", "ms-settings:appsfeatures").start();
  }
}
