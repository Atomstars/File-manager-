package io.github.atomstars.findspace.infrastructure.desktop;

import com.sun.jna.platform.win32.*;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * OS-owned file lock is released on crash; activation uses a local file, never a network listener.
 */
public final class InstanceGuard implements AutoCloseable {
  private final FileChannel channel;
  private final FileLock lock;
  private final Path activation;
  private String lastRequest;
  private WinNT.HANDLE windowsMutex;

  public InstanceGuard(Path state) throws IOException {
    Files.createDirectories(state);
    activation = state.resolve("activate.request");
    channel =
        FileChannel.open(
            state.resolve("desktop.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    FileLock acquired;
    try {
      acquired = channel.tryLock();
    } catch (OverlappingFileLockException alreadyHeld) {
      acquired = null;
    }
    if (acquired != null && System.getProperty("os.name").startsWith("Windows")) {
      try {
        String canonical = state.toRealPath().toString().toLowerCase(Locale.ROOT);
        String digest =
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(StandardCharsets.UTF_8)))
                .substring(0, 20);
        Kernel32.INSTANCE.SetLastError(0);
        windowsMutex =
            Kernel32.INSTANCE.CreateMutex(null, false, "Local\\FindspaceDesktop_" + digest);
        int error = Kernel32.INSTANCE.GetLastError();
        if (windowsMutex == null)
          throw new IOException("Cannot acquire Windows instance mutex: " + error);
        if (error == 183) {
          acquired.release();
          acquired = null;
        }
      } catch (Exception failure) {
        channel.close();
        if (windowsMutex != null) Kernel32.INSTANCE.CloseHandle(windowsMutex);
        throw new IOException("Cannot acquire desktop instance", failure);
      }
    }
    lock = acquired;
    lastRequest = readRequest();
    if (lock == null) Files.writeString(activation, UUID.randomUUID().toString());
  }

  public boolean acquired() {
    return lock != null;
  }

  private String readRequest() throws IOException {
    return Files.exists(activation) ? Files.readString(activation) : "";
  }

  public boolean activationRequested() throws IOException {
    String current = readRequest();
    if (current.equals(lastRequest)) return false;
    lastRequest = current;
    return true;
  }

  @Override
  public synchronized void close() throws IOException {
    try {
      if (lock != null && lock.isValid()) lock.release();
    } finally {
      channel.close();
      if (windowsMutex != null) {
        Kernel32.INSTANCE.CloseHandle(windowsMutex);
        windowsMutex = null;
      }
    }
  }
}
