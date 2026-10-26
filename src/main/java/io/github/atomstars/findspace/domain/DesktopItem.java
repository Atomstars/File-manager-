package io.github.atomstars.findspace.domain;

import java.nio.file.Path;

/** A launcher reference. Removing a pin never removes its target. */
public record DesktopItem(Path path, String label, boolean folder) {}
