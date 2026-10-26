package io.github.atomstars.findspace.ui;

import io.github.atomstars.findspace.application.Workspace;
import io.github.atomstars.findspace.domain.MoveRecord;
import java.awt.*;
import java.util.List;
import javax.swing.*;

final class HistoryPanel extends JPanel {
  private final DefaultListModel<MoveRecord> model = new DefaultListModel<>();
  private final JList<MoveRecord> history = new JList<>(model);

  HistoryPanel(Workspace workspace, TaskRunner tasks, Runnable changed) {
    super(new BorderLayout(12, 16));
    history.setCellRenderer(
        (list, record, index, selected, focus) -> {
          JLabel label =
              new JLabel(
                  record.status()
                      + "  ·  "
                      + record.source().getFileName()
                      + "  →  "
                      + record.destination());
          label.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
          label.setOpaque(true);
          label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
          return label;
        });
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(
        Ui.button(
            "Refresh history", () -> tasks.run("Reading history", workspace::history, this::show)));
    controls.add(
        Ui.button(
            "Undo selected batch",
            () -> {
              MoveRecord selected = history.getSelectedValue();
              if (selected == null) return;
              if (JOptionPane.showConfirmDialog(
                      this,
                      "Restore unchanged files in this batch? Edited files and occupied locations will be skipped.",
                      "Confirm undo",
                      JOptionPane.OK_CANCEL_OPTION)
                  != JOptionPane.OK_OPTION) return;
              tasks.run(
                  "Restoring files",
                  () -> workspace.undo(selected.batch()),
                  report -> {
                    changed.run();
                    Ui.message(
                        this, report.summary() + "\n" + String.join("\n", report.problems()));
                  });
            }));
    controls.add(
        Ui.button(
            "Recover interrupted operations",
            () ->
                tasks.run(
                    "Checking journal",
                    workspace::recover,
                    report -> {
                      Ui.message(
                          this, report.summary() + "\n" + String.join("\n", report.problems()));
                      changed.run();
                    })));
    add(controls, BorderLayout.NORTH);
    add(new JScrollPane(history));
    add(
        new JLabel(
            "History is scoped to the scanned folder. Ambiguous operations preserve both paths for manual review."),
        BorderLayout.SOUTH);
  }

  void show(List<MoveRecord> records) {
    model.clear();
    model.addAll(records);
  }
}
