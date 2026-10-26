package io.github.atomstars.findspace.ui;

import com.formdev.flatlaf.*;
import io.github.atomstars.findspace.infrastructure.desktop.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import javax.swing.*;

/** Explicit, editable preferences. Saving pins changes references, never target files. */
final class SettingsDialog extends JDialog {
  private final UserSettings settings;
  private final DefaultListModel<Path> pins = new DefaultListModel<>();
  private final JList<Path> list = new JList<>(pins);
  private final JTextField folder = new JTextField();
  private final JSlider size = new JSlider(520, 820), hover = new JSlider(150, 1000);
  private final JCheckBox top = new JCheckBox("Keep the orb above other windows"),
      desktop = new JCheckBox("Include all desktop files and shortcuts alongside my pins"),
      collapse = new JCheckBox("Collapse when I move away"),
      startup = new JCheckBox("Start with only the desktop orb");
  private final JComboBox<String> theme = new JComboBox<>(new String[] {"Midnight", "Daylight"});

  SettingsDialog(JFrame owner, UserSettings settings, Runnable saved, Runnable center) {
    super(owner, "Customize your space", true);
    this.settings = settings;
    setSize(760, 660);
    setMinimumSize(new Dimension(650, 580));
    setLocationRelativeTo(owner);
    JPanel content = Ui.padded(new BorderLayout(16, 20));
    setContentPane(content);
    JPanel header = Ui.column();
    header.add(Ui.title("Make room for what matters."));
    header.add(Box.createVerticalStrut(8));
    header.add(new JLabel("Your files, your apps, your way. Changes are applied when you save."));
    content.add(header, BorderLayout.NORTH);
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("My items", items());
    tabs.addTab("Appearance & behavior", appearance(center));
    content.add(tabs);
    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    footer.add(Ui.button("Cancel", this::dispose));
    JButton save = Ui.button("Save my space", () -> save(saved));
    save.putClientProperty("JButton.buttonType", "roundRect");
    footer.add(save);
    content.add(footer, BorderLayout.SOUTH);
    pins.addAll(settings.pins());
    desktop.setSelected(Boolean.parseBoolean(settings.get("orb.includeDesktop", "true")));
    folder.setText(settings.get("orb.root", DesktopCatalog.desktop().toString()));
    size.setValue(Math.max(520, Math.min(820, settings.integer("orb.size", 680))));
    hover.setValue(Math.max(150, Math.min(1000, settings.integer("orb.hover", 350))));
    top.setSelected(Boolean.parseBoolean(settings.get("orb.pinned", "false")));
    collapse.setSelected(Boolean.parseBoolean(settings.get("orb.autocollapse", "true")));
    startup.setSelected(Boolean.parseBoolean(settings.get("orb.startup", "true")));
    theme.setSelectedIndex(settings.get("theme", "dark").equals("light") ? 1 : 0);
  }

  private JPanel items() {
    JPanel panel = Ui.padded(new BorderLayout(10, 12));
    JPanel guidance = Ui.column();
    guidance.add(new JLabel("Pin the files, folders and app shortcuts you want around the orb."));
    guidance.add(
        new JLabel("Desktop items stay in their original locations. Remove only unpins an item."));
    guidance.add(desktop);
    panel.add(guidance, BorderLayout.NORTH);
    list.setFixedCellHeight(44);
    list.setCellRenderer(
        (view, path, index, selected, focus) -> {
          JLabel label = new JLabel(path.getFileName() + "    ·    " + path.getParent());
          label.setOpaque(true);
          label.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
          label.setBackground(selected ? view.getSelectionBackground() : view.getBackground());
          label.setForeground(selected ? view.getSelectionForeground() : view.getForeground());
          return label;
        });
    panel.add(new JScrollPane(list));
    JPanel bottom = Ui.column();
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
    buttons.add(Ui.button("+ Add files or folders", this::add));
    buttons.add(
        Ui.button(
            "Remove pin",
            () -> {
              int i = list.getSelectedIndex();
              if (i >= 0) pins.remove(i);
            }));
    buttons.add(Ui.button("Move up", () -> reorder(-1)));
    buttons.add(Ui.button("Move down", () -> reorder(1)));
    bottom.add(buttons);
    JPanel source = new JPanel(new BorderLayout(10, 8));
    folder.setEditable(false);
    source.add(new JLabel("Default folder"), BorderLayout.NORTH);
    source.add(folder);
    source.add(
        Ui.button(
            "Browse…",
            () -> {
              JFileChooser chooser = new JFileChooser(folder.getText());
              chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
              if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                folder.setText(chooser.getSelectedFile().getAbsolutePath());
            }),
        BorderLayout.EAST);
    bottom.add(source);
    panel.add(bottom, BorderLayout.SOUTH);
    return panel;
  }

  private JPanel appearance(Runnable center) {
    JPanel panel = Ui.padded(new GridLayout(0, 1, 8, 10));
    JPanel themeRow = new JPanel(new BorderLayout());
    themeRow.add(new JLabel("Workspace theme"));
    themeRow.add(theme, BorderLayout.EAST);
    panel.add(themeRow);
    panel.add(slider("Expanded ring size", size, "Compact", "Spacious"));
    panel.add(slider("Folder hover delay", hover, "Quick", "Deliberate"));
    panel.add(top);
    panel.add(collapse);
    panel.add(startup);
    panel.add(Ui.button("Center the orb on my desktop", center));
    return panel;
  }

  private JPanel slider(String label, JSlider slider, String low, String high) {
    JPanel panel = new JPanel(new BorderLayout(8, 4));
    panel.add(new JLabel(label), BorderLayout.NORTH);
    panel.add(slider);
    panel.add(new JLabel(low), BorderLayout.WEST);
    panel.add(new JLabel(high), BorderLayout.EAST);
    return panel;
  }

  private void add() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    chooser.setMultiSelectionEnabled(true);
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
      for (var file : chooser.getSelectedFiles())
        if (!pins.contains(file.toPath()) && pins.size() < 200) pins.addElement(file.toPath());
  }

  private void reorder(int delta) {
    int index = list.getSelectedIndex(), next = index + delta;
    if (index < 0 || next < 0 || next >= pins.size()) return;
    Path value = pins.remove(index);
    pins.add(next, value);
    list.setSelectedIndex(next);
  }

  private void save(Runnable saved) {
    try {
      Path source = Path.of(folder.getText());
      if (!java.nio.file.Files.isDirectory(source))
        throw new IllegalArgumentException("Choose an existing default folder.");
      settings.pins(Collections.list(pins.elements()));
      settings.put("orb.includeDesktop", desktop.isSelected());
      settings.put("orb.root", source);
      settings.put("orb.size", size.getValue());
      settings.put("orb.hover", hover.getValue());
      settings.put("orb.pinned", top.isSelected());
      settings.put("orb.autocollapse", collapse.isSelected());
      settings.put("orb.startup", startup.isSelected());
      settings.put("theme", theme.getSelectedIndex() == 0 ? "dark" : "light");
      settings.save();
      if (theme.getSelectedIndex() == 0) FlatDarkLaf.setup();
      else FlatLightLaf.setup();
      FlatLaf.updateUI();
      saved.run();
      dispose();
    } catch (Exception failure) {
      Ui.message(this, "Could not save settings: " + failure.getMessage());
    }
  }
}
