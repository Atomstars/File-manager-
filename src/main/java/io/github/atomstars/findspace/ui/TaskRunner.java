package io.github.atomstars.findspace.ui;

import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;

/**
 * All slow work runs off the event thread, one task at a time. UI callbacks return to the event
 * thread.
 */
final class TaskRunner implements AutoCloseable {
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "findspace-workspace"));
  private final Consumer<String> status;
  private final Consumer<Exception> error;
  private boolean busy;

  TaskRunner(Consumer<String> status, Consumer<Exception> error) {
    this.status = status;
    this.error = error;
  }

  boolean busy() {
    return busy;
  }

  <T> void run(String label, Callable<T> task, Consumer<T> completed) {
    if (busy) {
      status.accept("Please wait for the current operation to finish.");
      return;
    }
    busy = true;
    status.accept(label + "…");
    worker.submit(
        () -> {
          try {
            T result = task.call();
            SwingUtilities.invokeLater(
                () -> {
                  busy = false;
                  status.accept("Ready");
                  completed.accept(result);
                });
          } catch (Exception failure) {
            SwingUtilities.invokeLater(
                () -> {
                  busy = false;
                  status.accept("Operation could not complete");
                  error.accept(failure);
                });
          }
        });
  }

  @Override
  public void close() {
    worker.shutdown();
  }
}
