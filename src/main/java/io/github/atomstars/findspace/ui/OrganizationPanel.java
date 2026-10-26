package io.github.atomstars.findspace.ui;

import io.github.atomstars.findspace.application.Workspace;
import io.github.atomstars.findspace.domain.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

final class OrganizationPanel extends JPanel {
  private final Workspace workspace;
  private final TaskRunner tasks;
  private final Runnable changed;
  private final JCheckBox recursive = new JCheckBox("Include subfolders"),
      monthly = new JCheckBox("Group by month"),
      collectCode = new JCheckBox("Collect loose code files");
  private final PlanTable model = new PlanTable();
  private final JTable table = new JTable(model);
  private MovePlan plan;

  OrganizationPanel(Workspace workspace, TaskRunner tasks, Runnable changed) {
    super(new BorderLayout(12, 16));
    this.workspace = workspace;
    this.tasks = tasks;
    this.changed = changed;
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(recursive);
    controls.add(monthly);
    controls.add(collectCode);
    controls.add(Ui.button("Preview organization", this::preview));
    controls.add(Ui.button("Move selected", this::apply));
    JPanel introduction = Ui.column();
    introduction.add(Ui.title("A place for every file."));
    introduction.add(Box.createVerticalStrut(10));
    introduction.add(
        new JLabel(
            "1  Choose a folder above     2  Preview your groups     3  Move only what you select"));
    introduction.add(Box.createVerticalStrut(8));
    introduction.add(
        new JLabel(
            "Documents, photos, media and loose code get their own folders inside Findspace Organized."));
    introduction.add(
        new JLabel("Project folders stay intact. Installed applications are managed from Apps."));
    introduction.add(Box.createVerticalStrut(16));
    introduction.add(controls);
    add(introduction, BorderLayout.NORTH);
    table.setRowHeight(34);
    table.getColumnModel().getColumn(0).setMaxWidth(60);
    add(new JScrollPane(table));
    add(
        new JLabel(
            "Preview → select → confirm. Files are never overwritten. Undo is available in History."),
        BorderLayout.SOUTH);
    recursive.addActionListener(e -> invalidatePlan());
    monthly.addActionListener(e -> invalidatePlan());
    collectCode.addActionListener(e -> invalidatePlan());
  }

  void invalidatePlan() {
    plan = null;
    model.items = List.of();
    model.selected.clear();
    model.fireTableDataChanged();
  }

  private void preview() {
    if (recursive.isSelected()
        && JOptionPane.showConfirmDialog(
                this,
                "Moving files inside projects can break links. Include subfolders?",
                "Include subfolders",
                JOptionPane.OK_CANCEL_OPTION)
            != JOptionPane.OK_OPTION) return;
    boolean nested = recursive.isSelected(),
        dated = monthly.isSelected(),
        code = collectCode.isSelected();
    tasks.run(
        "Preparing preview",
        () -> workspace.preview(nested, dated, code),
        result -> {
          plan = result;
          model.items = result.items();
          model.selected.clear();
          result.items().forEach(i -> model.selected.add(i.file().path()));
          model.fireTableDataChanged();
          if (result.items().isEmpty())
            Ui.message(
                this,
                "No eligible files. Code, unknown types and already organized files remain in place.");
        });
  }

  private void apply() {
    if (plan == null) {
      Ui.message(this, "Create a preview first.");
      return;
    }
    if (table.isEditing()) table.getCellEditor().stopCellEditing();
    Set<Path> selected = Set.copyOf(model.selected);
    UUID id = plan.id();
    if (selected.isEmpty()) return;
    if (JOptionPane.showConfirmDialog(
            this,
            "Move " + selected.size() + " selected files to the displayed destinations?",
            "Confirm organization",
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    tasks.run(
        "Organizing files",
        () -> workspace.apply(id, selected),
        report -> {
          invalidatePlan();
          changed.run();
          Ui.message(this, report.summary() + "\n" + String.join("\n", report.problems()));
        });
  }

  private static final class PlanTable extends AbstractTableModel {
    private List<MovePlan.Item> items = List.of();
    private final Set<Path> selected = new HashSet<>();

    public int getRowCount() {
      return items.size();
    }

    public int getColumnCount() {
      return 3;
    }

    public String getColumnName(int c) {
      return new String[] {"Move", "Source", "Destination"}[c];
    }

    public Class<?> getColumnClass(int c) {
      return c == 0 ? Boolean.class : String.class;
    }

    public boolean isCellEditable(int r, int c) {
      return c == 0;
    }

    public Object getValueAt(int r, int c) {
      var i = items.get(r);
      return c == 0
          ? selected.contains(i.file().path())
          : c == 1 ? i.file().path().toString() : i.destination().toString();
    }

    public void setValueAt(Object value, int r, int c) {
      if (Boolean.TRUE.equals(value)) selected.add(items.get(r).file().path());
      else selected.remove(items.get(r).file().path());
      fireTableCellUpdated(r, c);
    }
  }
}
