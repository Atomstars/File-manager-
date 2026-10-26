package io.github.atomstars.findspace.ui;

import io.github.atomstars.findspace.application.SearchService;
import io.github.atomstars.findspace.domain.*;
import io.github.atomstars.findspace.infrastructure.desktop.DesktopIntegration;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.AbstractTableModel;

final class FilesPanel extends JPanel {
  private final JTextField query = new JTextField(25);
  private final JComboBox<Object> category = new JComboBox<>();
  private final JComboBox<SearchService.Sort> sort = new JComboBox<>(SearchService.Sort.values());
  private final FileTable model = new FileTable();
  private final JTable table = new JTable(model);
  private final JLabel count = new JLabel("Scan a folder to start searching.");
  private final TaskRunner tasks;
  private final Timer debounce;
  private Snapshot snapshot;

  FilesPanel(TaskRunner tasks) {
    super(new BorderLayout(12, 16));
    this.tasks = tasks;
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    query.putClientProperty("JTextField.placeholderText", "Search names and paths…");
    category.addItem("All types");
    for (Category c : Category.values()) category.addItem(c);
    controls.add(query);
    controls.add(category);
    controls.add(sort);
    controls.add(Ui.button("Reveal in folder", this::reveal));
    add(controls, BorderLayout.NORTH);
    table.setRowHeight(34);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getColumnModel().getColumn(0).setPreferredWidth(220);
    table.getColumnModel().getColumn(3).setPreferredWidth(430);
    add(new JScrollPane(table), BorderLayout.CENTER);
    add(count, BorderLayout.SOUTH);
    debounce = new Timer(220, e -> refresh());
    debounce.setRepeats(false);
    query
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                debounce.restart();
              }

              public void removeUpdate(DocumentEvent e) {
                debounce.restart();
              }

              public void changedUpdate(DocumentEvent e) {
                debounce.restart();
              }
            });
    category.addActionListener(e -> refresh());
    sort.addActionListener(e -> refresh());
    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) reveal();
          }
        });
  }

  void show(Snapshot snapshot) {
    this.snapshot = snapshot;
    refresh();
  }

  private void refresh() {
    if (snapshot == null) return;
    String text = query.getText();
    Category selected = category.getSelectedItem() instanceof Category c ? c : null;
    var ordering = (SearchService.Sort) sort.getSelectedItem();
    Snapshot current = snapshot;
    tasks.run(
        "Searching",
        () -> new SearchService().search(current, text, selected, ordering),
        matches -> {
          // Bound displayed rows; complete index remains available through filtering.
          model.rows = matches.stream().limit(5000).toList();
          model.fireTableDataChanged();
          count.setText(
              matches.size()
                  + " matching files · showing "
                  + model.rows.size()
                  + " · double-click to reveal");
          if (!text.equals(query.getText())
              || selected != (category.getSelectedItem() instanceof Category c ? c : null)
              || ordering != sort.getSelectedItem()) debounce.restart();
        });
  }

  private void reveal() {
    int row = table.getSelectedRow();
    if (row < 0) return;
    var path = model.rows.get(row).path();
    tasks.run(
        "Opening folder",
        () -> {
          DesktopIntegration.reveal(path);
          return null;
        },
        ignored -> {});
  }

  private static final class FileTable extends AbstractTableModel {
    private List<FileEntry> rows = List.of();

    public int getRowCount() {
      return rows.size();
    }

    public int getColumnCount() {
      return 4;
    }

    public String getColumnName(int column) {
      return new String[] {"Name", "Type", "Size", "Location"}[column];
    }

    public Object getValueAt(int row, int column) {
      var f = rows.get(row);
      return switch (column) {
        case 0 -> f.name();
        case 1 -> f.category();
        case 2 -> Ui.bytes(f.size());
        default -> f.path();
      };
    }
  }
}
