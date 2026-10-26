package io.github.atomstars.findspace.ui;

import io.github.atomstars.findspace.application.Workspace;
import io.github.atomstars.findspace.domain.Snapshot;
import io.github.atomstars.findspace.infrastructure.desktop.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.*;

/** Composition of small views. No SQL, hashing, traversal or file mutation lives here. */
public final class MainWindow extends JFrame {
  private final Workspace workspace;
  private final UserSettings settings;
  private final TaskRunner tasks;
  private final JLabel status = new JLabel("Choose a folder to begin."),
      location = new JLabel("No folder indexed");
  private final JProgressBar progress = new JProgressBar();
  private final CardLayout layout = new CardLayout();
  private final JPanel pages = new JPanel(layout);
  private final FilesPanel files;
  private final StoragePanel storage = new StoragePanel();
  private final OrganizationPanel organization;
  private final HistoryPanel history;
  private final DuplicatesPanel duplicates;
  private final FolderOrb orb;
  private final Runnable shutdown;
  private String active = "Files";
  private Snapshot displayed;

  public MainWindow(
      Workspace workspace, UserSettings settings, Path startingFolder, Runnable shutdown) {
    super("Findspace — Your files, in focus");
    this.workspace = workspace;
    this.settings = settings;
    this.shutdown = shutdown;
    tasks =
        new TaskRunner(
            message -> {
              status.setText(message);
              progress.setIndeterminate(message.endsWith("…"));
            },
            failure -> Ui.message(this, failure.getMessage()));
    files = new FilesPanel(tasks);
    organization = new OrganizationPanel(workspace, tasks, this::refresh);
    history = new HistoryPanel(workspace, tasks, this::refresh);
    duplicates = new DuplicatesPanel(workspace, tasks);
    orb =
        new FolderOrb(
            this, settings, this::manageFolder, this::settings, this::software, this::quit);
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(1000, 650));
    setSize(1240, 800);
    setLocationRelativeTo(null);
    JPanel root = new JPanel(new BorderLayout());
    setContentPane(root);
    JPanel sidebar = Ui.padded(new BorderLayout(0, 28));
    sidebar.setPreferredSize(new Dimension(220, 0));
    JPanel brand = Ui.column();
    brand.add(Ui.title("findspace"));
    brand.add(new JLabel("A little order. More clarity."));
    sidebar.add(brand, BorderLayout.NORTH);
    JPanel navigation = new JPanel(new GridLayout(9, 1, 0, 8));
    for (String name :
        new String[] {"Files", "Storage", "Manage files", "Duplicates", "History", "Apps"})
      navigation.add(Ui.button(name, () -> showPage(name)));
    navigation.add(Ui.button("Folder orb", orb::display));
    navigation.add(Ui.button("Settings", this::settings));
    navigation.add(
        Ui.button(
            "Back to desktop",
            () -> {
              setVisible(false);
              orb.display();
            }));
    sidebar.add(navigation, BorderLayout.CENTER);
    sidebar.add(new JLabel("LOCAL · PRIVATE · YOURS"), BorderLayout.SOUTH);
    root.add(sidebar, BorderLayout.WEST);
    JPanel workspacePanel = Ui.padded(new BorderLayout(12, 22));
    JPanel header = new JPanel(new BorderLayout(12, 8));
    JPanel headings = Ui.column();
    headings.add(Ui.title("Your workspace"));
    headings.add(Box.createVerticalStrut(8));
    headings.add(location);
    header.add(headings);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    actions.add(Ui.button("Choose folder", this::chooseFolder));
    actions.add(
        Ui.button(
            "Refresh scan", () -> scan(displayed == null ? startingFolder : displayed.root())));
    header.add(actions, BorderLayout.EAST);
    workspacePanel.add(header, BorderLayout.NORTH);
    pages.add(files, "Files");
    pages.add(storage, "Storage");
    pages.add(organization, "Manage files");
    pages.add(duplicates, "Duplicates");
    pages.add(history, "History");
    pages.add(new SoftwarePanel(tasks, settings, orb::applySettings), "Apps");
    workspacePanel.add(pages);
    JPanel footer = new JPanel(new BorderLayout(16, 0));
    progress.setPreferredSize(new Dimension(120, 6));
    footer.add(status);
    footer.add(progress, BorderLayout.EAST);
    workspacePanel.add(footer, BorderLayout.SOUTH);
    root.add(workspacePanel);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            setVisible(false);
            orb.display();
          }
        });
    getRootPane()
        .registerKeyboardAction(
            e -> chooseFolder(),
            KeyStroke.getKeyStroke("control O"),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    getRootPane()
        .registerKeyboardAction(
            e -> scan(displayed == null ? startingFolder : displayed.root()),
            KeyStroke.getKeyStroke("F5"),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    displayed = workspace.snapshot().orElse(null);
    if (displayed != null) {
      location.setText(displayed.root() + " · saved index; refresh to check outside changes");
    }
  }

  public void activate() {
    setVisible(true);
    setState(Frame.NORMAL);
    toFront();
    requestFocus();
  }

  public void start(boolean scanInitially, Path initial) {
    if (Boolean.parseBoolean(settings.get("orb.startup", "true"))) orb.display();
    else activate();
    if (scanInitially) scan(initial);
    else if (displayed != null) files.show(displayed);
  }

  public void activateOrb() {
    orb.display();
  }

  private void manageFolder(Path root) {
    activate();
    showPage("Manage files");
    scan(root);
  }

  private void settings() {
    new SettingsDialog(this, settings, orb::applySettings, orb::centerOnScreen).setVisible(true);
  }

  private void software() {
    activate();
    showPage("Apps");
  }

  public void scan(Path root) {
    tasks.run(
        "Scanning " + root.getFileName(),
        () -> {
          Snapshot snapshot = workspace.scan(root);
          var report = workspace.recover();
          return new ScanResult(snapshot, report.problems());
        },
        result -> {
          displayed = result.snapshot();
          settings.put("folder", displayed.root());
          organization.invalidatePlan();
          duplicates.clear();
          location.setText(
              displayed.root()
                  + " · "
                  + displayed.files().size()
                  + " files · "
                  + Ui.bytes(displayed.totalBytes()));
          showPage(active);
          if (!result.problems().isEmpty()) Ui.message(this, String.join("\n", result.problems()));
        });
  }

  private record ScanResult(Snapshot snapshot, java.util.List<String> problems) {}

  private void chooseFolder() {
    if (tasks.busy()) return;
    JFileChooser chooser =
        new JFileChooser(
            displayed == null ? AppPaths.defaultFolder().toFile() : displayed.root().toFile());
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
      scan(chooser.getSelectedFile().toPath());
  }

  private void refresh() {
    displayed = workspace.snapshot().orElse(null);
    organization.invalidatePlan();
    duplicates.clear();
    if (displayed != null)
      location.setText(
          displayed.root()
              + " · "
              + displayed.files().size()
              + " files · "
              + Ui.bytes(displayed.totalBytes()));
    showPage(active);
  }

  private void showPage(String name) {
    if (tasks.busy()) return;
    active = name;
    layout.show(pages, name);
    if (displayed == null) return;
    switch (name) {
      case "Files" -> files.show(displayed);
      case "Storage" -> storage.show(displayed);
      case "History" -> tasks.run("Reading history", workspace::history, history::show);
      default -> {}
    }
  }

  private void quit() {
    if (tasks.busy()) {
      Ui.message(this, "Wait for the current file operation to finish before quitting.");
      return;
    }
    try {
      settings.save();
    } catch (IOException e) {
      Ui.message(this, "Could not save preferences: " + e.getMessage());
    }
    orb.dispose();
    tasks.close();
    dispose();
    shutdown.run();
  }
}
