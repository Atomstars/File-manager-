package io.github.atomstars.findspace.ui;

import io.github.atomstars.findspace.domain.DesktopItem;
import io.github.atomstars.findspace.infrastructure.desktop.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.Timer;

/** Desktop-only shell: owns hover navigation and delegates painting and settings. */
final class FolderOrb extends JDialog implements OrbCanvas.Actions {
  private final UserSettings settings;
  private final Consumer<Path> manage;
  private final Runnable preferences, software, quit;
  private final OrbCanvas canvas = new OrbCanvas(this);
  private final JPanel surface = new JPanel(null);
  private final JButton manageButton;
  private final Timer hoverTimer, leaveTimer;
  private final Deque<Path> breadcrumbs = new ArrayDeque<>();
  private SwingWorker<Loaded, Void> loader;
  private long generation;
  private List<DesktopItem> items = List.of(), children = List.of();
  private DesktopItem selected, pendingHover;
  private Path root;
  private int page, childPage;
  private boolean expanded, dragged, pinnedMode;
  private Point dragOrigin;
  private Point center;
  private long lastInside = System.currentTimeMillis();

  private record Loaded(List<DesktopItem> items) {}

  FolderOrb(
      JFrame owner,
      UserSettings settings,
      Consumer<Path> manage,
      Runnable preferences,
      Runnable software,
      Runnable quit) {
    super(owner, "Findspace · Desktop", false);
    this.settings = settings;
    this.manage = manage;
    this.preferences = preferences;
    this.software = software;
    this.quit = quit;
    root = Path.of(settings.get("orb.root", DesktopCatalog.desktop().toString()));
    setUndecorated(true);
    boolean translucent =
        getGraphicsConfiguration()
            .getDevice()
            .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
    if (translucent) setBackground(new Color(0, 0, 0, 0));
    else surface.setBackground(new Color(16, 23, 34));
    surface.setOpaque(!translucent);
    setContentPane(surface);
    surface.add(canvas);
    manageButton =
        Ui.button(
            "Manage files",
            () -> {
              Path folder = selected == null ? root : selected.path();
              collapse();
              manage.accept(folder);
            });
    surface.add(manageButton);
    surface.setComponentZOrder(manageButton, 0);
    Rectangle screen = usableScreen();
    center =
        new Point(
            settings.integer("orb.center.x", screen.x + screen.width / 2),
            settings.integer("orb.center.y", screen.y + screen.height / 2));
    hoverTimer =
        new Timer(
            Math.max(150, settings.integer("orb.hover", 350)), e -> loadChildren(pendingHover));
    hoverTimer.setRepeats(false);
    leaveTimer =
        new Timer(
            200,
            e -> {
              if (!expanded
                  || !Boolean.parseBoolean(settings.get("orb.autocollapse", "true"))
                  || MenuSelectionManager.defaultManager().getSelectedPath().length > 0) return;
              PointerInfo pointer = MouseInfo.getPointerInfo();
              Point local = pointer == null ? null : pointer.getLocation();
              if (local != null) SwingUtilities.convertPointFromScreen(local, canvas);
              if (local != null
                  && (canvas.containsNavigation(local) || manageButton.getBounds().contains(local)))
                lastInside = System.currentTimeMillis();
              else if (System.currentTimeMillis() - lastInside > 550) collapse();
            });
    applySettings();
    leaveTimer.start();
  }

  void applySettings() {
    setAlwaysOnTop(Boolean.parseBoolean(settings.get("orb.pinned", "false")));
    root = Path.of(settings.get("orb.root", DesktopCatalog.desktop().toString()));
    hoverTimer.setInitialDelay(Math.max(150, settings.integer("orb.hover", 350)));
    page = 0;
    childPage = 0;
    selected = null;
    breadcrumbs.clear();
    children = List.of();
    if (expanded) loadRoot();
    position();
  }

  void display() {
    collapse();
    setVisible(true);
    toFront();
  }

  void centerOnScreen() {
    Rectangle screen = usableScreen();
    center = new Point(screen.x + screen.width / 2, screen.y + screen.height / 2);
    position();
    persistPosition();
  }

  @Override
  public void expand() {
    if (expanded) return;
    expanded = true;
    canvas.expanded(true);
    position();
    lastInside = System.currentTimeMillis();
    render();
    loadRoot();
  }

  @Override
  public void collapse() {
    expanded = false;
    generation++;
    if (loader != null) loader.cancel(true);
    hoverTimer.stop();
    pendingHover = null;
    selected = null;
    children = List.of();
    canvas.expanded(false);
    position();
  }

  private void position() {
    int size = expanded ? Math.max(520, Math.min(820, settings.integer("orb.size", 680))) : 96;
    Rectangle screen = usableScreen();
    size = Math.min(size, Math.min(screen.width, screen.height) - 24);
    center.x =
        Math.max(screen.x + size / 2, Math.min(center.x, screen.x + screen.width - size / 2));
    center.y =
        Math.max(screen.y + size / 2, Math.min(center.y, screen.y + screen.height - size / 2));
    setBounds(center.x - size / 2, center.y - size / 2, size, size);
    canvas.setBounds(0, 0, size, size);
    double scale = size / 680.0;
    manageButton.setBounds(
        size / 2 - (int) (56 * scale),
        size / 2 + (int) (54 * scale),
        (int) (112 * scale),
        (int) (28 * scale));
    manageButton.setVisible(expanded);
    if (getGraphicsConfiguration()
        .getDevice()
        .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))
      setShape(new Ellipse2D.Double(0, 0, size, size));
    surface.revalidate();
    surface.repaint();
  }

  private Rectangle usableScreen() {
    Rectangle bounds = new Rectangle(getGraphicsConfiguration().getBounds());
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
    bounds.x += insets.left;
    bounds.y += insets.top;
    bounds.width -= insets.left + insets.right;
    bounds.height -= insets.top + insets.bottom;
    return bounds;
  }

  private void loadRoot() {
    List<Path> pins = settings.pins();
    pinnedMode = breadcrumbs.isEmpty() && !pins.isEmpty();
    boolean usePins = pinnedMode;
    Path current = root;
    load(
        () -> {
          if (Boolean.parseBoolean(settings.get("orb.includeDesktop", "true"))) {
            List<Path> folders = new ArrayList<>(DesktopCatalog.desktopFolders());
            if (!folders.contains(current)) folders.add(current);
            return DesktopCatalog.launcherItems(folders, pins);
          }
          if (!usePins) return DesktopCatalog.children(current);
          List<DesktopItem> result = new ArrayList<>();
          for (Path pin : pins) {
            try {
              result.add(DesktopCatalog.item(pin));
            } catch (Exception unavailable) {
            }
          }
          return List.copyOf(result);
        },
        loaded -> {
          items = loaded.items();
          selected = null;
          children = List.of();
          render();
          canvas.message(
              items.isEmpty()
                  ? "No items here · use Settings to add files, folders or apps"
                  : "Hover a folder to peek · scroll for more · double-click to open");
        });
  }

  private interface Listing {
    List<DesktopItem> read() throws Exception;
  }

  private void load(Listing listing, Consumer<Loaded> finished) {
    long request = ++generation;
    if (loader != null) loader.cancel(true);
    canvas.message("Opening your space…");
    loader =
        new SwingWorker<>() {
          protected Loaded doInBackground() throws Exception {
            return new Loaded(listing.read());
          }

          protected void done() {
            if (isCancelled() || request != generation || !expanded) return;
            try {
              finished.accept(get());
            } catch (Exception error) {
              canvas.message(
                  "Cannot browse this folder. Right-click to open it or choose another.");
            }
          }
        };
    loader.execute();
  }

  private void render() {
    canvas.content(
        RadialLayout.page(items, page, 8), RadialLayout.page(children, childPage, 8), selected);
  }

  @Override
  public void hover(DesktopItem item) {
    hoverTimer.stop();
    pendingHover = item;
    if (item == null) {
      return;
    }
    if (item.equals(selected)) return;
    if (item.folder()) {
      hoverTimer.restart();
    } else if (!children.contains(item) && selected != null) {
      generation++;
      if (loader != null) loader.cancel(true);
      selected = null;
      children = List.of();
      breadcrumbs.clear();
      render();
    }
  }

  private void loadChildren(DesktopItem item) {
    if (item == null || !expanded || item.equals(selected)) return;
    boolean deeper = children.contains(item);
    Path parent = deeper && selected != null ? selected.path() : null;
    load(
        () -> DesktopCatalog.children(item.path()),
        loaded -> {
          if (!item.equals(pendingHover)) return;
          if (parent != null) breadcrumbs.push(parent);
          else breadcrumbs.clear();
          selected = item;
          children = loaded.items();
          childPage = 0;
          render();
          canvas.message(
              children.isEmpty()
                  ? "This folder is empty"
                  : children.size()
                      + " items · hover a subfolder to go deeper · double-click a file to open");
        });
  }

  @Override
  public void back() {
    if (selected == null) return;
    generation++;
    if (loader != null) loader.cancel(true);
    hoverTimer.stop();
    pendingHover = null;
    selected = null;
    children = List.of();
    breadcrumbs.clear();
    render();
  }

  @Override
  public void page(int delta) {
    if (selected != null)
      childPage = Math.floorMod(childPage + delta, Math.max(1, (children.size() + 7) / 8));
    else page = Math.floorMod(page + delta, Math.max(1, (items.size() + 7) / 8));
    render();
  }

  @Override
  public void open(DesktopItem item) {
    if (item == null) return;
    new SwingWorker<Void, Void>() {
      protected Void doInBackground() throws Exception {
        DesktopCatalog.open(item.path());
        return null;
      }

      protected void done() {
        try {
          get();
        } catch (Exception error) {
          Ui.message(
              FolderOrb.this,
              "Could not open "
                  + item.label()
                  + ". Check that it still exists and has an associated application.");
        }
      }
    }.execute();
  }

  @Override
  public void menu(MouseEvent event) {
    JPopupMenu menu = new JPopupMenu();
    DesktopItem item = canvas.hovered();
    if (item != null) {
      add(menu, "Open " + item.label(), () -> open(item));
      if (item.folder()) add(menu, "Manage files in this folder", () -> manage.accept(item.path()));
      add(
          menu,
          "Pin this item",
          () -> {
            List<Path> pins = new ArrayList<>(settings.pins());
            if (!pins.contains(item.path())) pins.add(item.path());
            try {
              settings.pins(pins);
              settings.save();
              loadRoot();
            } catch (Exception failure) {
              Ui.message(this, failure.getMessage());
            }
          });
      menu.addSeparator();
    }
    add(menu, "Manage files", () -> manage.accept(root));
    add(menu, "My apps", software);
    add(menu, "Customize my space", preferences);
    add(menu, "Previous page", () -> page(-1));
    add(menu, "Next page", () -> page(1));
    add(
        menu,
        "Back to desktop items",
        () -> {
          breadcrumbs.clear();
          root = Path.of(settings.get("orb.root", DesktopCatalog.desktop().toString()));
          page = 0;
          loadRoot();
        });
    if (!breadcrumbs.isEmpty())
      add(
          menu,
          "Back one folder",
          () -> {
            Path parent = breadcrumbs.pop();
            load(
                () -> DesktopCatalog.children(parent),
                loaded -> {
                  selected = new DesktopItem(parent, parent.getFileName().toString(), true);
                  children = loaded.items();
                  childPage = 0;
                  render();
                });
          });
    add(menu, "Center on desktop", this::centerOnScreen);
    add(menu, "Collapse", this::collapse);
    menu.addSeparator();
    add(menu, "Quit Findspace", quit);
    menu.show(canvas, event.getX(), event.getY());
  }

  private static void add(JPopupMenu menu, String label, Runnable action) {
    JMenuItem item = new JMenuItem(label);
    item.addActionListener(e -> action.run());
    menu.add(item);
  }

  @Override
  public void dragStart(MouseEvent event) {
    dragOrigin = event.getLocationOnScreen();
    dragged = false;
    hoverTimer.stop();
  }

  @Override
  public void drag(MouseEvent event) {
    Point now = event.getLocationOnScreen();
    int dx = now.x - dragOrigin.x, dy = now.y - dragOrigin.y;
    if (Math.abs(dx) + Math.abs(dy) > 2) dragged = true;
    center.translate(dx, dy);
    dragOrigin = now;
    position();
  }

  @Override
  public void dragEnd() {
    if (dragged) persistPosition();
  }

  private void persistPosition() {
    settings.put("orb.center.x", center.x);
    settings.put("orb.center.y", center.y);
    try {
      settings.save();
    } catch (Exception failure) {
      canvas.message("Position could not be saved.");
    }
  }

  @Override
  public void dispose() {
    hoverTimer.stop();
    leaveTimer.stop();
    if (loader != null) loader.cancel(true);
    super.dispose();
  }
}
