package io.github.atomstars.findspace.ui;

import io.github.atomstars.findspace.domain.DesktopItem;
import io.github.atomstars.findspace.infrastructure.desktop.*;
import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;

/** Installed-app shortcuts only; installation/uninstallation stays with the operating system. */
final class SoftwarePanel extends JPanel {
  private final DefaultListModel<DesktopItem> model = new DefaultListModel<>();
  private final JList<DesktopItem> list = new JList<>(model);

  SoftwarePanel(TaskRunner tasks, UserSettings settings, Runnable changed) {
    super(new BorderLayout(12, 16));
    JPanel header = Ui.column();
    header.add(Ui.title("Your apps, within reach."));
    header.add(Box.createVerticalStrut(8));
    header.add(
        new JLabel(
            "Discover Start-menu shortcuts, launch an app, or pin it to your desktop ring."));
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
    buttons.add(
        Ui.button(
            "Discover apps",
            () ->
                tasks.run(
                    "Finding app shortcuts",
                    DesktopCatalog::applications,
                    items -> {
                      model.clear();
                      model.addAll(items);
                    })));
    buttons.add(
        Ui.button(
            "Launch selected",
            () -> {
              DesktopItem item = list.getSelectedValue();
              if (item != null)
                tasks.run(
                    "Opening " + item.label(),
                    () -> {
                      DesktopCatalog.open(item.path());
                      return null;
                    },
                    ignored -> {});
            }));
    buttons.add(
        Ui.button(
            "Pin to my ring",
            () -> {
              DesktopItem item = list.getSelectedValue();
              if (item == null) return;
              try {
                var pins = new ArrayList<>(settings.pins());
                if (!pins.contains(item.path())) pins.add(item.path());
                settings.pins(pins);
                settings.save();
                changed.run();
                Ui.message(this, item.label() + " is pinned to your ring.");
              } catch (Exception failure) {
                Ui.message(this, failure.getMessage());
              }
            }));
    buttons.add(
        Ui.button(
            "Windows app settings",
            () ->
                tasks.run(
                    "Opening Windows app settings",
                    () -> {
                      DesktopCatalog.manageApplications();
                      return null;
                    },
                    ignored -> {})));
    header.add(Box.createVerticalStrut(16));
    header.add(buttons);
    add(header, BorderLayout.NORTH);
    list.setFixedCellHeight(44);
    list.setCellRenderer(
        (view, item, index, selected, focus) -> {
          JLabel label = new JLabel("   " + item.label());
          label.setOpaque(true);
          label.setBackground(selected ? view.getSelectionBackground() : view.getBackground());
          label.setForeground(selected ? view.getSelectionForeground() : view.getForeground());
          label.setToolTipText(item.path().toString());
          return label;
        });
    add(new JScrollPane(list));
    add(
        new JLabel(
            "App shortcuts can be pinned safely. Use Windows settings to uninstall; installed program folders are never moved."),
        BorderLayout.SOUTH);
  }
}
