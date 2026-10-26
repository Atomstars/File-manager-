package io.github.atomstars.findspace.ui;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.IntStream;

/** Pure geometry, independent of rendering and filesystem behavior. */
public final class RadialLayout {
  private RadialLayout() {}

  public static List<Point2D.Double> ring(int count, double radius, double center) {
    return ring(count, radius, center, center);
  }

  public static List<Point2D.Double> ring(
      int count, double radius, double centerX, double centerY) {
    if (count < 0 || count > 12 || radius < 0)
      throw new IllegalArgumentException("Invalid ring geometry");
    return IntStream.range(0, count)
        .mapToObj(
            i -> {
              double angle = -Math.PI / 2 + 2 * Math.PI * i / Math.max(1, count);
              return new Point2D.Double(
                  centerX + radius * Math.cos(angle), centerY + radius * Math.sin(angle));
            })
        .toList();
  }

  public static <T> List<T> page(List<T> items, int page, int size) {
    int start = Math.min(Math.max(0, page) * size, items.size());
    return List.copyOf(items.subList(start, Math.min(start + size, items.size())));
  }

  public static Point2D.Double branchAnchor(Point2D.Double point, double center, double scale) {
    double dx = point.x - center, dy = point.y - center;
    double distance = Math.hypot(dx, dy);
    if (distance < 1) return new Point2D.Double(center, center - 205 * scale);
    return new Point2D.Double(
        center + dx / distance * 205 * scale, center + dy / distance * 205 * scale);
  }
}
