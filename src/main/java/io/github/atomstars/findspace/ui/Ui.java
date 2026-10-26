package io.github.atomstars.findspace.ui;

import java.awt.*;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class Ui {
  static final Color ACCENT = new Color(103, 201, 169);

  private Ui() {}

  static JButton button(String text, Runnable action) {
    JButton button = new JButton(text);
    button.setFocusPainted(false);
    button.addActionListener(e -> action.run());
    return button;
  }

  static JLabel title(String text) {
    JLabel label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(Font.BOLD, 27f));
    return label;
  }

  static JPanel column() {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    return p;
  }

  static JPanel padded(LayoutManager layout) {
    JPanel p = new JPanel(layout);
    p.setBorder(new EmptyBorder(20, 24, 20, 24));
    return p;
  }

  static String bytes(long count) {
    if (count < 1024) return count + " B";
    double value = count;
    String unit = "B";
    for (String candidate : new String[] {"KB", "MB", "GB", "TB", "PB"}) {
      value /= 1024;
      unit = candidate;
      if (value < 1024) break;
    }
    return String.format(Locale.ROOT, "%.1f %s", value, unit);
  }

  static void message(Component owner, String text) {
    JTextArea area = new JTextArea(text, Math.min(18, Math.max(3, text.split("\n").length)), 70);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    JOptionPane.showMessageDialog(
        owner, new JScrollPane(area), "Findspace", JOptionPane.INFORMATION_MESSAGE);
  }
}
