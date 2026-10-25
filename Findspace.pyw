"""Windowed Windows entry point, also used by the portable executable build."""
import os
import sys
import traceback
from pathlib import Path

if __name__ == '__main__':
    try:
        from desktop import main
        main()
    except Exception:
        folder = Path(os.environ.get('LOCALAPPDATA', Path.home() / '.local/share')) / 'Findspace'
        folder.mkdir(parents=True, exist_ok=True)
        log = folder / 'launcher-error.log'
        log.write_text(traceback.format_exc(), encoding='utf-8')
        try:
            import tkinter as tk
            from tkinter import messagebox
            root = tk.Tk();root.withdraw()
            messagebox.showerror('Findspace could not start', f'Please check Python 3.10+ and Tk are installed.\n\nDetails were saved to:\n{log}')
            root.destroy()
        except Exception:
            pass
        sys.exit(1)
