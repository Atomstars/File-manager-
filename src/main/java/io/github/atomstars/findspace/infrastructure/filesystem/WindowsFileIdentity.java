package io.github.atomstars.findspace.infrastructure.filesystem;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Windows FILE_ID_INFO: volume serial (8 bytes) plus 128-bit file ID. No path-based identity
 * fallback.
 */
final class WindowsFileIdentity {
  private WindowsFileIdentity() {}

  static String read(Path path) throws IOException {
    var kernel = Kernel32.INSTANCE;
    int attributes = kernel.GetFileAttributes(path.toString());
    if (attributes == -1)
      throw new IOException(
          "Cannot inspect file attributes: Windows error " + kernel.GetLastError());
    if ((attributes & 0x400) != 0)
      throw new IOException("Reparse points and cloud placeholders are excluded.");
    // FILE_READ_ATTRIBUTES; share read/write/delete; OPEN_EXISTING; OPEN_REPARSE_POINT.
    WinNT.HANDLE handle = kernel.CreateFile(path.toString(), 0x80, 7, null, 3, 0x00200000, null);
    if (WinBase.INVALID_HANDLE_VALUE.equals(handle))
      throw new IOException("Cannot inspect file identity: Windows error " + kernel.GetLastError());
    try (Memory information = new Memory(24)) {
      if (!kernel.GetFileInformationByHandleEx(handle, 18, information, new WinDef.DWORD(24)))
        throw new IOException(
            "Filesystem does not expose file IDs: Windows error " + kernel.GetLastError());
      return HexFormat.of().formatHex(information.getByteArray(0, 24));
    } finally {
      kernel.CloseHandle(handle);
    }
  }
}
