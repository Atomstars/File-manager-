package io.github.atomstars.findspace.infrastructure.filesystem;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import io.github.atomstars.findspace.application.port.FileAccess;
import io.github.atomstars.findspace.domain.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Owns path validation, junction exclusion, identity checks and filesystem effects. */
public final class NioFileAccess implements FileAccess {
  private static final Set<String> EXCLUDED =
      Set.of(
          "node_modules",
          "venv",
          "__pycache__",
          "$recycle.bin",
          "system volume information",
          "appdata",
          "windows",
          "program files",
          "program files (x86)",
          "library",
          "target");
  private static final Set<String> TEMP = Set.of("part", "crdownload", "tmp", "download");
  private final Path stateDirectory;

  public NioFileAccess(Path stateDirectory) throws IOException {
    this.stateDirectory = stateDirectory.toRealPath();
  }

  private static BasicFileAttributes attributes(Path path) throws IOException {
    return Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
  }

  private static boolean linkLike(BasicFileAttributes a) {
    return a.isSymbolicLink() || a.isOther();
  }

  private static void checkInterrupted() throws InterruptedIOException {
    if (Thread.currentThread().isInterrupted())
      throw new InterruptedIOException("Operation cancelled");
  }

  @Override
  public Path validateRoot(Path root) throws IOException {
    if (!root.isAbsolute()) throw new IOException("Choose an absolute personal folder path.");
    if (linkLike(attributes(root)))
      throw new IOException("Choose a regular folder, not a link or junction.");
    Path resolved = root.toRealPath();
    if (resolved.getParent() == null
        || EXCLUDED.contains(resolved.getFileName().toString().toLowerCase(Locale.ROOT)))
      throw new IOException("Choose a personal folder, not a whole drive or system folder.");
    if (!Files.isDirectory(resolved, NOFOLLOW_LINKS))
      throw new IOException("Folder is unavailable.");
    if (resolved.startsWith(stateDirectory))
      throw new IOException("Choose a folder outside Findspace's state directory.");
    return resolved;
  }

  @Override
  public void requireContained(Path path, Path root) throws IOException {
    Path normalized = path.toAbsolutePath().normalize();
    if (!normalized.startsWith(root) || normalized.startsWith(stateDirectory))
      throw new IOException("Path is outside the selected workspace.");
    Path current = root;
    if (linkLike(attributes(root)) || !root.toRealPath().equals(root))
      throw new IOException("Selected folder identity changed.");
    for (Path part : root.relativize(normalized)) {
      current = current.resolve(part);
      try {
        if (linkLike(attributes(current)))
          throw new IOException("Links and junctions are excluded.");
      } catch (NoSuchFileException missing) {
        break;
      }
    }
  }

  @Override
  public FileEntry.Fingerprint fingerprint(Path path) throws IOException {
    BasicFileAttributes a = attributes(path);
    if (!a.isRegularFile() || linkLike(a))
      throw new IOException("Only regular files are supported.");
    String identity;
    if (System.getProperty("os.name").startsWith("Windows"))
      identity = WindowsFileIdentity.read(path);
    else if (a.fileKey() != null) identity = a.fileKey().toString();
    else throw new IOException("This filesystem does not expose stable file identity.");
    return new FileEntry.Fingerprint(
        a.size(), a.lastModifiedTime().toInstant().toString(), identity);
  }

  @Override
  public String digest(Path path) throws IOException {
    var before = fingerprint(path);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = Files.newInputStream(path)) {
        byte[] buffer = new byte[64 * 1024];
        for (int count; (count = input.read(buffer)) != -1; ) {
          checkInterrupted();
          digest.update(buffer, 0, count);
        }
      }
      if (!before.equals(fingerprint(path))) throw new IOException("File changed while reading.");
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private boolean excluded(Path path) throws IOException {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.startsWith(".")
        || EXCLUDED.contains(name)
        || Files.isHidden(path)
        || path.startsWith(stateDirectory);
  }

  @Override
  public Snapshot scan(Path selected) throws IOException {
    Path root = validateRoot(selected);
    List<FileEntry> entries = new ArrayList<>();
    int[] skipped = {0};
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a)
              throws IOException {
            checkInterrupted();
            if (!dir.equals(root) && (excluded(dir) || linkLike(a))) {
              skipped[0]++;
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
            checkInterrupted();
            try {
              String name = file.getFileName().toString();
              if (excluded(file)
                  || linkLike(a)
                  || TEMP.contains(
                      name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)))
                skipped[0]++;
              else {
                requireContained(file, root);
                entries.add(new FileEntry(file, Category.of(file), fingerprint(file)));
              }
            } catch (IOException failure) {
              skipped[0]++;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException error) {
            skipped[0]++;
            return FileVisitResult.CONTINUE;
          }
        });
    entries.sort(Comparator.comparing(f -> f.path().toString()));
    return new Snapshot(root, Instant.now(), entries, skipped[0]);
  }

  @Override
  public List<Path> folders(Path root) throws IOException {
    Path safeRoot = validateRoot(root);
    List<Path> result = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(safeRoot)) {
      for (Path child : stream) {
        try {
          var a = attributes(child);
          if (a.isDirectory() && !linkLike(a) && !excluded(child)) result.add(child);
        } catch (IOException ignored) {
          /* An inaccessible child is not a navigable folder. */
        }
      }
    }
    result.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
    return List.copyOf(result);
  }

  @Override
  public boolean exists(Path path) throws IOException {
    try {
      attributes(path);
      return true;
    } catch (NoSuchFileException absent) {
      return false;
    }
  }

  @Override
  public boolean sameFile(Path a, Path b) throws IOException {
    return Files.isSameFile(a, b);
  }

  @Override
  public void prepareDestination(Path destination, Path root) throws IOException {
    requireContained(destination, root);
    Files.createDirectories(destination.getParent());
    requireContained(destination, root);
  }

  @Override
  public void link(Path source, Path destination) throws IOException {
    Files.createLink(destination, source);
  }

  @Override
  public void delete(Path path) throws IOException {
    Files.delete(path);
  }
}
