package io.github.atomstars.findspace;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void dependenciesPointInward() throws Exception {
    Path base = Path.of("src/main/java/io/github/atomstars/findspace");
    for (String layer : new String[] {"domain", "application", "ui"}) {
      try (var files = Files.walk(base.resolve(layer))) {
        for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
          String source = Files.readString(file);
          if (!layer.equals("ui")) {
            assertFalse(source.contains("javax.swing"), file.toString());
            assertFalse(source.contains("java.sql"), file.toString());
            assertFalse(source.contains("findspace.infrastructure"), file.toString());
          }
          if (layer.equals("domain"))
            assertFalse(source.contains("findspace.application"), file.toString());
          if (layer.equals("ui")) {
            assertFalse(source.contains("java.sql"), file.toString());
            assertFalse(source.contains("Files.delete"), file.toString());
            assertFalse(source.contains("Files.walk"), file.toString());
          }
        }
      }
    }
  }
}
