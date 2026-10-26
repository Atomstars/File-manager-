package io.github.atomstars.findspace.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.atomstars.findspace.infrastructure.desktop.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopShellTest {
  @TempDir Path directory;

  @Test
  void ringsFitAndDoNotOverlap() {
    for (int count = 1; count <= 12; count++) {
      var points = RadialLayout.ring(count, 255, 340);
      for (int i = 0; i < points.size(); i++) {
        assertEquals(255, points.get(i).distance(340, 340), .001);
        assertTrue(points.get(i).x > 30 && points.get(i).x < 650);
        for (int j = i + 1; j < points.size(); j++)
          assertTrue(points.get(i).distance(points.get(j)) > 60);
      }
    }
    assertEquals(List.of(8, 9), RadialLayout.page(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), 1, 8));
    assertTrue(RadialLayout.page(List.of(), 0, 8).isEmpty());
  }

  @Test
  void removingPinsDoesNotDeleteTargets() throws Exception {
    Path target = Files.writeString(directory.resolve("notes.txt"), "keep");
    var settings = new UserSettings(directory);
    settings.pins(List.of(target));
    settings.save();
    assertEquals(List.of(target), new UserSettings(directory).pins());
    settings.pins(List.of());
    settings.save();
    assertTrue(new UserSettings(directory).pins().isEmpty());
    assertEquals("keep", Files.readString(target));
  }

  @Test
  void folderCatalogIncludesRealFilesAndSortsFoldersFirst() throws Exception {
    Files.createDirectory(directory.resolve("Projects"));
    Files.writeString(directory.resolve("notes.txt"), "hello");
    Files.writeString(directory.resolve(".hidden"), "skip");
    var items = DesktopCatalog.children(directory);
    assertEquals(2, items.size());
    assertTrue(items.getFirst().folder());
    assertEquals("notes.txt", items.getLast().label());
    assertThrows(
        java.io.IOException.class, () -> DesktopCatalog.children(directory.resolve("absent")));
  }

  @Test
  void desktopAndPublicShortcutsRemainVisibleWithPinsWithoutMovingFiles() throws Exception {
    Path personal = Files.createDirectory(directory.resolve("desktop"));
    Path common = Files.createDirectory(directory.resolve("public"));
    Path notes = Files.writeString(personal.resolve("notes.txt"), "keep here");
    Path shortcut = Files.writeString(common.resolve("Editor.lnk"), "fixture shortcut");
    Path extra = Files.writeString(directory.resolve("pinned.txt"), "extra");
    var items = DesktopCatalog.launcherItems(List.of(personal, common), List.of(extra, notes));
    assertEquals(List.of(extra, notes, shortcut), items.stream().map(i -> i.path()).toList());
    assertEquals("keep here", Files.readString(notes));
    assertTrue(Files.exists(shortcut));
    Path later = Files.writeString(personal.resolve("new.txt"), "new desktop file");
    assertTrue(
        DesktopCatalog.launcherItems(List.of(personal, common), List.of(extra)).stream()
            .anyMatch(i -> i.path().equals(later)));
  }

  @Test
  void childRingsClearTheCenterControlsAndEachOtherAtEveryFolderPosition() {
    for (double size : new double[] {520, 648, 680, 820}) {
      double center = size / 2, scale = size / 680;
      for (var folder : RadialLayout.ring(8, 190 * scale, center)) {
        var anchor = RadialLayout.branchAnchor(folder, center, scale);
        var points = RadialLayout.ring(8, 72 * scale, anchor.x, anchor.y);
        for (int i = 0; i < points.size(); i++) {
          assertTrue(
              points.get(i).distance(center, center) - 18 * scale > 100 * scale,
              "Child icons must leave room for A, its caption and Manage files");
          assertTrue(points.get(i).distance(center, center) + 18 * scale < size / 2);
          assertTrue(points.get(i).distance(anchor) > (30 + 18) * scale);
          for (int j = i + 1; j < points.size(); j++)
            assertTrue(points.get(i).distance(points.get(j)) > 36 * scale);
        }
      }
    }
  }
}
