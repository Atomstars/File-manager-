package io.github.atomstars.findspace.ui;

import io.github.atomstars.findspace.domain.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;

final class StoragePanel extends JPanel {
  StoragePanel() {
    super(new BorderLayout());
  }

  void show(Snapshot snapshot) {
    removeAll();
    JPanel content = Ui.column();
    content.add(Ui.title(Ui.bytes(snapshot.totalBytes()) + " indexed"));
    content.add(Box.createVerticalStrut(12));
    content.add(
        new JLabel(
            snapshot.files().size()
                + " files · "
                + snapshot.skipped()
                + " excluded or unreadable entries"));
    content.add(
        new JLabel(
            "Folder totals count file lengths, not whole-drive usage. Hard links may share storage."));
    content.add(Box.createVerticalStrut(26));
    Map<String, Long> categories = new LinkedHashMap<>(), folders = new HashMap<>();
    for (var f : snapshot.files()) {
      categories.merge(f.category().toString(), f.size(), Long::sum);
      var relative = snapshot.root().relativize(f.path());
      folders.merge(
          relative.getNameCount() == 1 ? "Files in root" : relative.getName(0).toString(),
          f.size(),
          Long::sum);
    }
    bars(content, categories, snapshot.totalBytes());
    content.add(Box.createVerticalStrut(24));
    content.add(new JLabel("TOP FOLDERS"));
    content.add(Box.createVerticalStrut(12));
    bars(content, folders, snapshot.totalBytes());
    add(new JScrollPane(content));
    revalidate();
    repaint();
  }

  private void bars(JPanel content, Map<String, Long> values, long total) {
    values.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(8)
        .forEach(
            entry -> {
              JPanel row = new JPanel(new BorderLayout(16, 6));
              row.setMaximumSize(new Dimension(10000, 54));
              row.add(new JLabel(entry.getKey()), BorderLayout.WEST);
              row.add(new JLabel(Ui.bytes(entry.getValue())), BorderLayout.EAST);
              JProgressBar bar = new JProgressBar(0, 1000);
              bar.setValue(total == 0 ? 0 : (int) (1000.0 * entry.getValue() / total));
              bar.setForeground(Ui.ACCENT);
              row.add(bar, BorderLayout.SOUTH);
              content.add(row);
              content.add(Box.createVerticalStrut(12));
            });
  }
}
