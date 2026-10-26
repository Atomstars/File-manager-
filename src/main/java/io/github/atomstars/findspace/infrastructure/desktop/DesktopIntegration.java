package io.github.atomstars.findspace.infrastructure.desktop;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.*;

public final class DesktopIntegration {
  private DesktopIntegration() {}

  public static void reveal(Path path) throws IOException {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("This file moved. Refresh the index.");
    if (System.getProperty("os.name").startsWith("Windows"))
      new ProcessBuilder("explorer.exe", "/select,", path.toString()).start();
    else Desktop.getDesktop().open(path.getParent().toFile());
  }

  public static void openFolder(Path path) throws IOException {
    Desktop.getDesktop().open(path.toFile());
  }
}
