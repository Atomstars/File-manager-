package io.github.atomstars.findspace.domain;

import java.nio.file.Path;
import java.util.*;

public enum Category {
  DOCUMENTS("Documents", "pdf doc docx txt rtf odt md epub ppt pptx xls xlsx csv tsv"),
  IMAGES("Images", "png jpg jpeg gif webp heic heif svg bmp tiff raw avif"),
  VIDEOS("Videos", "mp4 mov mkv avi webm m4v wmv"),
  AUDIO("Audio", "mp3 wav flac m4a aac ogg"),
  ARCHIVES("Archives", "zip rar 7z tar gz bz2 xz"),
  INSTALLERS("Installers", "exe msi dmg pkg iso apk appimage"),
  CODE("Code", "py js ts tsx jsx java c cpp h html css json yaml yml sql sh bat ps1 ipynb"),
  OTHER("Other", "");

  private final String label;
  private final Set<String> extensions;

  Category(String label, String extensions) {
    this.label = label;
    this.extensions = Set.of(extensions.split(" "));
  }

  @Override
  public String toString() {
    return label;
  }

  public boolean organizable() {
    return this != CODE && this != OTHER;
  }

  public static Category of(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    String extension = name.substring(name.lastIndexOf('.') + 1);
    return Arrays.stream(values())
        .filter(c -> c != OTHER && c.extensions.contains(extension))
        .findFirst()
        .orElse(OTHER);
  }
}
