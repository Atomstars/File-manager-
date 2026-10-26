package io.github.atomstars.findspace.ui;

import io.github.atomstars.findspace.domain.DesktopItem;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Paints a radial launcher. It neither reads directories nor launches programs. */
final class OrbCanvas extends JPanel {
  interface Actions {
    void expand();

    void hover(DesktopItem item);

    void open(DesktopItem item);

    void menu(MouseEvent event);

    void drag(MouseEvent event);

    void dragStart(MouseEvent event);

    void dragEnd();

    void collapse();

    void page(int delta);

    void back();
  }

  private final Actions actions;
  private List<DesktopItem> inner = List.of(), outer = List.of();
  private List<Node> nodes = List.of();
  private DesktopItem hovered, selected;
  private Point2D.Double branchCenter;
  private boolean expanded;
  private boolean dragging;
  private String message = "";

  record Node(DesktopItem item, double x, double y, double radius, boolean outer) {}

  OrbCanvas(Actions actions) {
    this.actions = actions;
    setOpaque(false);
    setToolTipText("Hover to open your space · drag to reposition · right-click for options");
    MouseAdapter mouse =
        new MouseAdapter() {
          public void mouseEntered(MouseEvent e) {
            expandAtCenter(e.getPoint());
          }

          public void mouseMoved(MouseEvent e) {
            expandAtCenter(e.getPoint());
            DesktopItem item = hit(e.getPoint());
            if (!Objects.equals(item, hovered)) {
              hovered = item;
              actions.hover(item);
              repaint();
            }
          }

          public void mouseExited(MouseEvent e) {
            actions.hover(null);
          }

          public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
              hovered = hit(e.getPoint());
              actions.menu(e);
              return;
            }
            double center = getWidth() / 2.0;
            dragging = e.getPoint().distance(center, center) < 48;
            if (dragging) actions.dragStart(e);
          }

          public void mouseDragged(MouseEvent e) {
            if (dragging) actions.drag(e);
          }

          public void mouseReleased(MouseEvent e) {
            if (dragging) {
              actions.dragEnd();
              dragging = false;
            }
          }

          public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)
                && e.getClickCount() == 1
                && e.getPoint().distance(getWidth() / 2.0, getHeight() / 2.0) < 43) {
              actions.back();
            }
            if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
              DesktopItem item = hit(e.getPoint());
              if (item != null) actions.open(item);
            }
          }
        };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
    addMouseWheelListener(
        e -> {
          if (expanded) actions.page(Integer.signum(e.getWheelRotation()));
        });
    getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "collapse");
    getActionMap()
        .put(
            "collapse",
            new AbstractAction() {
              public void actionPerformed(ActionEvent e) {
                actions.collapse();
              }
            });
  }

  void content(List<DesktopItem> inner, List<DesktopItem> outer, DesktopItem selected) {
    this.inner = inner;
    this.outer = outer;
    if (!Objects.equals(this.selected, selected)) {
      branchCenter =
          nodes.stream()
              .filter(n -> n.item.equals(selected))
              .map(n -> new Point2D.Double(n.x, n.y))
              .findFirst()
              .orElse(null);
    }
    this.selected = selected;
    hovered = null;
    repaint();
  }

  void expanded(boolean expanded) {
    this.expanded = expanded;
    if (!expanded) {
      selected = null;
      hovered = null;
      inner = List.of();
      outer = List.of();
      nodes = List.of();
      branchCenter = null;
    }
    repaint();
  }

  void message(String message) {
    this.message = message;
    repaint();
  }

  DesktopItem hovered() {
    return hovered;
  }

  private DesktopItem hit(Point point) {
    return nodes.stream()
        .sorted(Comparator.comparing(Node::outer).reversed())
        .filter(n -> point.distance(n.x, n.y) < n.radius + 10)
        .map(Node::item)
        .findFirst()
        .orElse(null);
  }

  private void expandAtCenter(Point point) {
    double center = getWidth() / 2.0;
    if (!expanded && point.distance(center, center) <= 40) actions.expand();
  }

  boolean containsNavigation(Point point) {
    double center = getWidth() / 2.0, scale = getWidth() / 680.0;
    if (!expanded) return point.distance(center, center) <= 44;
    return point.distance(center, center) <= 220 * scale
        || (selected != null
            && branchCenter != null
            && point.distance(branchCenter) <= 130 * scale);
  }

  @Override
  public String getToolTipText(MouseEvent e) {
    DesktopItem item = hit(e.getPoint());
    return item == null ? null : item.path().toString();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    double center = getWidth() / 2.0, scale = getWidth() / 680.0;
    if (!expanded) scale = 1;
    if (expanded) {
      boolean nested = selected != null;
      double innerRadius = 190 * scale;
      double outerRadius = 72 * scale;
      g.setColor(new Color(123, 198, 201, 35));
      g.setStroke(new BasicStroke(1));
      if (!nested)
        g.draw(
            new Ellipse2D.Double(
                center - innerRadius, center - innerRadius, innerRadius * 2, innerRadius * 2));
      List<Node> next = new ArrayList<>();
      var positions = RadialLayout.ring(inner.size(), innerRadius, center);
      for (int i = 0; i < inner.size(); i++) {
        boolean chosen = inner.get(i).equals(selected);
        if (chosen) branchCenter = RadialLayout.branchAnchor(positions.get(i), center, scale);
        if (!nested)
          next.add(
              new Node(
                  inner.get(i),
                  positions.get(i).x,
                  positions.get(i).y,
                  (nested && !chosen ? 19 : 30) * scale,
                  false));
      }
      if (nested) {
        if (branchCenter == null) branchCenter = new Point2D.Double(center, center - innerRadius);
        branchCenter = RadialLayout.branchAnchor(branchCenter, center, scale);
        next.add(new Node(selected, branchCenter.x, branchCenter.y, 30 * scale, false));
        g.setColor(new Color(23, 34, 49, 220));
        g.fill(
            new Ellipse2D.Double(
                branchCenter.x - outerRadius - 32 * scale,
                branchCenter.y - outerRadius - 32 * scale,
                2 * outerRadius + 64 * scale,
                2 * outerRadius + 64 * scale));
        g.setColor(new Color(130, 219, 205, 100));
        g.draw(
            new Ellipse2D.Double(
                branchCenter.x - outerRadius,
                branchCenter.y - outerRadius,
                outerRadius * 2,
                outerRadius * 2));
        positions = RadialLayout.ring(outer.size(), outerRadius, branchCenter.x, branchCenter.y);
        for (int i = 0; i < outer.size(); i++)
          next.add(
              new Node(outer.get(i), positions.get(i).x, positions.get(i).y, 18 * scale, true));
      }
      nodes = next;
      for (Node node : nodes) paintNode(g, node, scale, nested);
    } else nodes = List.of();
    double r = expanded ? 43 * scale : 39;
    g.setPaint(
        new GradientPaint(
            (float) (center - r),
            (float) (center - r),
            new Color(133, 225, 204),
            (float) (center + r),
            (float) (center + r),
            new Color(75, 134, 221)));
    g.fill(new Ellipse2D.Double(center - r, center - r, 2 * r, 2 * r));
    g.setColor(new Color(255, 255, 255, 150));
    g.draw(new Ellipse2D.Double(center - r + 1, center - r + 1, 2 * r - 2, 2 * r - 2));
    g.setColor(new Color(15, 32, 45));
    g.setFont(new Font("Segoe UI", Font.BOLD, (int) (expanded ? 29 * scale : 30)));
    centered(g, "A", center, center + 10 * scale);
    if (expanded && selected != null) {
      g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(10, (int) (12 * scale))));
      g.setColor(new Color(230, 241, 250));
      centered(
          g,
          truncate(hovered == null ? selected.label() : hovered.label(), 18),
          center,
          center - 64 * scale);
    }
    if (expanded && inner.isEmpty()) {
      g.setColor(new Color(225, 236, 246));
      g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
      centered(g, truncate(message, 36), center, center - 65 * scale);
    }
    g.dispose();
  }

  private void paintNode(Graphics2D g, Node node, double scale, boolean nested) {
    boolean active = node.item.equals(hovered) || node.item.equals(selected);
    double r = node.radius;
    g.setColor(active ? new Color(103, 201, 169, 210) : new Color(43, 57, 75, 235));
    g.fill(new Ellipse2D.Double(node.x - r, node.y - r, 2 * r, 2 * r));
    g.setColor(active ? new Color(188, 255, 232) : new Color(125, 157, 184, 100));
    g.draw(new Ellipse2D.Double(node.x - r, node.y - r, 2 * r, 2 * r));
    if (node.item.folder()) {
      g.setColor(new Color(236, 211, 143));
      g.fillRoundRect(
          (int) (node.x - 10 * scale),
          (int) (node.y - 7 * scale),
          (int) (20 * scale),
          (int) (14 * scale),
          4,
          4);
    } else {
      String name = node.item.path().getFileName().toString();
      int dot = name.lastIndexOf('.');
      String type = dot >= 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "FILE";
      if (type.equals("LNK") || type.equals("URL")) type = "APP";
      g.setColor(new Color(222, 237, 250));
      g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(8, (int) (9 * scale))));
      centered(g, truncate(type, 4), node.x, node.y + 3 * scale);
    }
    if (!nested) {
      g.setFont(new Font("Segoe UI", active ? Font.BOLD : Font.PLAIN, (int) (12 * scale)));
      g.setColor(new Color(227, 235, 244));
      centered(g, truncate(node.item.label(), 16), node.x, node.y + r + 17 * scale);
    }
  }

  private static String truncate(String text, int max) {
    return text.length() > max ? text.substring(0, max - 1) + "…" : text;
  }

  private static void centered(Graphics2D g, String text, double x, double y) {
    g.drawString(text, (float) (x - g.getFontMetrics().stringWidth(text) / 2.0), (float) y);
  }
}
