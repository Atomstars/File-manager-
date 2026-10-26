package io.github.atomstars.findspace.ui;

import io.github.atomstars.findspace.application.*;
import io.github.atomstars.findspace.infrastructure.desktop.DesktopIntegration;
import java.awt.*;
import java.nio.file.Path;
import javax.swing.*;
import javax.swing.tree.*;

final class DuplicatesPanel extends JPanel {
  private final JTree tree =
      new JTree(new DefaultMutableTreeNode("Run a duplicate check after scanning."));

  DuplicatesPanel(Workspace workspace, TaskRunner tasks) {
    super(new BorderLayout(12, 16));
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(
        Ui.button(
            "Find exact duplicates",
            () -> tasks.run("Comparing file contents", workspace::duplicates, this::show)));
    controls.add(
        Ui.button(
            "Reveal selected file",
            () -> {
              if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node
                  && node.getUserObject() instanceof Path path)
                tasks.run(
                    "Opening folder",
                    () -> {
                      DesktopIntegration.reveal(path);
                      return null;
                    },
                    ignored -> {});
            }));
    add(controls, BorderLayout.NORTH);
    add(new JScrollPane(tree));
    add(
        new JLabel(
            "Content matches only. Backups may be intentional. Nothing is deleted automatically."),
        BorderLayout.SOUTH);
  }

  private void show(DuplicateService.Result result) {
    DefaultMutableTreeNode root =
        new DefaultMutableTreeNode(
            result.groups().size()
                + " duplicate groups · "
                + result.problems().size()
                + " skipped");
    for (var group : result.groups()) {
      var node =
          new DefaultMutableTreeNode(
              group.files().size()
                  + " matches · "
                  + Ui.bytes(group.potentialBytes())
                  + " potentially redundant");
      group.files().forEach(f -> node.add(new DefaultMutableTreeNode(f.path())));
      root.add(node);
    }
    tree.setModel(new DefaultTreeModel(root));
    for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    if (!result.problems().isEmpty()) Ui.message(this, String.join("\n", result.problems()));
  }

  void clear() {
    tree.setModel(
        new DefaultTreeModel(new DefaultMutableTreeNode("Run a duplicate check after scanning.")));
  }
}
