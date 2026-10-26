package io.github.atomstars.findspace.bootstrap;

import com.formdev.flatlaf.*;
import io.github.atomstars.findspace.application.Workspace;
import io.github.atomstars.findspace.infrastructure.desktop.*;
import io.github.atomstars.findspace.infrastructure.filesystem.NioFileAccess;
import io.github.atomstars.findspace.infrastructure.persistence.SqliteWorkspaceStore;
import io.github.atomstars.findspace.ui.MainWindow;
import java.nio.file.*;
import java.time.Clock;
import javax.swing.*;

/** Composition root: the only place that wires infrastructure into application workflows. */
public final class Findspace {
  private Findspace() {}

  public static void main(String[] args) {
    Path state = AppPaths.stateDirectory(), root = null;
    InstanceGuard guard = null;
    try {
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "--state" -> state = Path.of(args[++i]).toAbsolutePath();
          case "--root" -> root = Path.of(args[++i]).toAbsolutePath();
          default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
        }
      }
      guard = new InstanceGuard(state);
      if (!guard.acquired()) {
        guard.close();
        return;
      }
      var settings = new UserSettings(state);
      if (!settings.get("orb.interactionVersion", "").equals("2")) {
        settings.put("orb.interactionVersion", "2");
        settings.put("orb.startup", true);
        settings.put("orb.autocollapse", true);
        settings.put("orb.includeDesktop", true);
        settings.save();
      }
      var workspace =
          new Workspace(
              new NioFileAccess(state), new SqliteWorkspaceStore(state), Clock.systemDefaultZone());
      Path initial =
          root != null
              ? root
              : Path.of(settings.get("folder", AppPaths.defaultFolder().toString()));
      InstanceGuard instance = guard;
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      instance.close();
                    } catch (Exception ignored) {
                    }
                  },
                  "findspace-shutdown"));
      if (settings.get("theme", "dark").equals("light")) FlatLightLaf.setup();
      else FlatDarkLaf.setup();
      UIManager.put("Button.arc", 14);
      UIManager.put("Component.arc", 12);
      UIManager.put("TextComponent.arc", 12);
      boolean scanInitially = root != null;
      if (root != null) {
        settings.put("orb.root", root);
        settings.put("orb.includeDesktop", false);
      }
      SwingUtilities.invokeLater(
          () -> {
            Timer activation = new Timer(500, null);
            MainWindow window =
                new MainWindow(
                    workspace,
                    settings,
                    initial,
                    () -> {
                      activation.stop();
                      try {
                        instance.close();
                      } catch (Exception ignored) {
                      }
                    });
            activation.addActionListener(
                e -> {
                  try {
                    if (instance.activationRequested()) window.activateOrb();
                  } catch (Exception failure) {
                    System.err.println(failure.getMessage());
                  }
                });
            activation.start();
            window.start(scanInitially, initial);
          });
    } catch (Exception failure) {
      if (guard != null)
        try {
          guard.close();
        } catch (Exception ignored) {
        }
      failure.printStackTrace();
      JOptionPane.showMessageDialog(
          null, failure.getMessage(), "Findspace could not start", JOptionPane.ERROR_MESSAGE);
    }
  }
}
