"""Per-user desktop instance lock. The OS releases it if the process exits."""
import hashlib
import os
import sys
from pathlib import Path


class InstanceLock:
    def __init__(self, folder):
        self.folder = Path(folder)
        self.handle = None
        self.file = None
        self.acquired = False

    def __enter__(self):
        self.folder.mkdir(parents=True, exist_ok=True)
        if sys.platform == 'win32':
            import ctypes
            from ctypes import wintypes
            self.kernel = ctypes.WinDLL('kernel32', use_last_error=True)
            self.kernel.CreateMutexW.argtypes = (wintypes.LPVOID, wintypes.BOOL, wintypes.LPCWSTR)
            self.kernel.CreateMutexW.restype = wintypes.HANDLE
            self.kernel.CloseHandle.argtypes = (wintypes.HANDLE,)
            name = 'Local\\FindspaceDesktop_' + hashlib.sha256(str(self.folder.resolve()).casefold().encode()).hexdigest()[:20]
            ctypes.set_last_error(0)
            self.handle = self.kernel.CreateMutexW(None, False, name)
            if not self.handle:
                raise OSError(ctypes.get_last_error(), 'Could not create desktop instance lock')
            self.acquired = ctypes.get_last_error() != 183
        else:
            import fcntl
            self.file = (self.folder / 'desktop.lock').open('a')
            try:
                fcntl.flock(self.file, fcntl.LOCK_EX | fcntl.LOCK_NB)
                self.acquired = True
            except BlockingIOError:
                self.acquired = False
        return self

    def __exit__(self, *_):
        if self.handle:
            self.kernel.CloseHandle(self.handle)
        if self.file:
            self.file.close()
