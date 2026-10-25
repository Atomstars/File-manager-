"""Native Findspace desktop orb and toolbox; no browser or HTTP service needed."""
from __future__ import annotations

import os
import subprocess
import sys
import time
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from desktop_model import (clamp_center, default_folder, folder_page, human_size,
                           load_preferences, ring_positions, save_preferences)
from engine import Manager
from instance import InstanceLock

BG = '#f7f7f2'
INK = '#293632'
MUTED = '#7c8980'
ACCENT = '#e8ac7e'
GREEN = '#73886c'
KEY = '#ff00fe'
HERE = Path(__file__).resolve().parent


def open_folder(folder):
    p = Path(folder)
    if not p.is_dir():
        raise ValueError('This folder is no longer available.')
    if sys.platform == 'win32':
        os.startfile(str(p))
    elif sys.platform == 'darwin':
        subprocess.Popen(['open', str(p)])
    else:
        subprocess.Popen(['xdg-open', str(p)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def reveal_file(path):
    p = Path(path)
    if not p.is_file():
        raise ValueError('That file moved or was removed. Refresh the index.')
    if sys.platform == 'win32':
        subprocess.Popen(['explorer', '/select,', str(p)])
    elif sys.platform == 'darwin':
        subprocess.Popen(['open', '-R', str(p)])
    else:
        open_folder(p.parent)


class Desktop:
    def __init__(self, state_dir=None):
        self.root = tk.Tk()
        self.root.title('Findspace Desktop')
        self.root.overrideredirect(True)
        self.state_dir = Path(state_dir or Path(os.environ.get('LOCALAPPDATA', Path.home() / '.local/share')) / 'Findspace')
        self.manager = Manager(self.state_dir)
        self.manager.recover_pending()
        self.preferences = load_preferences(self.state_dir / 'desktop.json')
        saved_folder = self.preferences.get('folder')
        p = Path(saved_folder if isinstance(saved_folder,str) else default_folder())
        self.folder = p if p.is_dir() else default_folder()
        self.pinned = bool(self.preferences.get('pinned', True))
        self.root.attributes('-topmost', self.pinned)
        self.transparent = sys.platform == 'win32'
        if self.transparent:
            self.root.configure(bg=KEY)
            self.root.wm_attributes('-transparentcolor', KEY)
        self.expanded = False
        self.page = 0
        self.toolbox = None
        self.drag_origin = None
        self.dragged = False
        self.double_click_job = None
        self.ui_jobs = []
        self.center = (self.root.winfo_screenwidth() - 125, self.root.winfo_screenheight() - 165)
        saved_position = self.preferences.get('position')
        if isinstance(saved_position, list) and len(saved_position) == 2 and all(isinstance(v, (float, int)) for v in saved_position):
            self.center = tuple(saved_position)
        self.canvas = tk.Canvas(self.root, highlightthickness=0, bg=KEY if self.transparent else BG)
        self.canvas.pack(fill='both', expand=True)
        self.canvas.bind('<ButtonPress-1>', self.press)
        self.canvas.bind('<B1-Motion>', self.drag)
        self.canvas.bind('<ButtonRelease-1>', self.release)
        self.canvas.bind('<Button-3>', self.context_menu)
        self.root.bind('<Escape>', lambda _: self.collapse())
        self.root.bind('<FocusIn>', lambda _: None)
        self.draw()
        self.activation_path = self.state_dir / 'activate.request'
        self.activation_stamp = self.activation_path.stat().st_mtime_ns if self.activation_path.exists() else 0
        self.root.after(500,self.check_activation)

    def check_activation(self):
        try:
            stamp=self.activation_path.stat().st_mtime_ns if self.activation_path.exists() else 0
            if stamp!=self.activation_stamp:
                self.activation_stamp=stamp
                self.expanded=True
                self.draw()
                self.root.lift()
                self.root.focus_force()
        except OSError:
            pass
        self.root.after(500,self.check_activation)

    def save(self):
        self.preferences.update(folder=str(self.folder), pinned=self.pinned, position=list(self.center))
        save_preferences(self.state_dir / 'desktop.json', self.preferences)

    def geometry(self, width, height):
        x, y = clamp_center(*self.center, self.root.winfo_screenwidth(), self.root.winfo_screenheight(), width, height)
        self.root.geometry(f'{width}x{height}+{x}+{y}')
        self.center = (x + width / 2, y + height / 2)

    def draw(self):
        self.canvas.delete('all')
        self.targets = {}
        if not self.expanded:
            self.geometry(88, 88)
            self.orb(44, 44, 34)
            return
        self.geometry(640, 640)
        c = self.canvas
        c.create_polygon(38,20,602,20,622,20,622,40,622,615,622,635,602,635,38,635,18,635,18,615,18,40,18,20,
                         smooth=True,fill=BG,outline='#e3e6db',width=2)
        c.create_text(320, 38, text='findspace', fill=INK, font=('Segoe UI', 17, 'bold'))
        short_path = str(self.folder)
        if len(short_path) > 65:
            short_path = '…' + short_path[-64:]
        c.create_text(320, 63, text=short_path, fill=MUTED, font=('Segoe UI', 9))
        self.pill(230, 85, '↑ Parent', self.parent, width=95)
        self.pill(340, 85, 'Choose folder', self.choose_folder, width=115)
        try:
            folders, self.page, pages, total = folder_page(self.folder, self.page)
        except OSError as exc:
            folders, pages, total = [], 1, 0
            c.create_text(320, 445, text=f'Folder unavailable: {exc.strerror}', fill=MUTED, font=('Segoe UI', 10))
        c.create_oval(152, 142, 488, 478, outline='#e4e7dc', dash=(3, 6))
        for index, (folder, (x, y)) in enumerate(zip(folders, ring_positions(len(folders)))):
            tag = f'folder{index}'
            c.create_line(320, 310, x, y, fill='#e3e7dd', width=1)
            c.create_oval(x-36, y-36, x+36, y+36, fill='#f4e9d6', outline='#e5d9bd', tags=(tag,))
            self.folder_glyph(x, y-2, tag)
            label = folder.name if len(folder.name) <= 17 else folder.name[:15] + '…'
            c.create_rectangle(x-64, y+39, x+64, y+60, fill=BG, outline=BG, tags=(tag,))
            c.create_text(x, y+49, text=label, fill=INK, font=('Segoe UI', 9), tags=(tag,))
            self.targets[tag] = lambda p=folder: self.navigate(p)
            c.tag_bind(tag, '<Double-Button-1>', lambda _, p=folder: self.folder_double_click(p))
            c.tag_bind(tag, '<Button-3>', lambda event, p=folder: self.folder_menu(event, p))
        self.orb(320, 310, 48)
        label = self.folder.name or str(self.folder)
        c.create_text(320, 375, text=label[:25], fill=INK, font=('Segoe UI', 10, 'bold'))
        if not folders:
            c.create_text(320, 419, text='No visible subfolders here.\nUse the tools below to find and organize files.', justify='center', fill=MUTED, font=('Segoe UI', 10))
        if pages > 1:
            self.pill(202, 552, '‹', lambda: self.change_page(-1), width=42)
            c.create_text(320, 552, text=f'{self.page+1} / {pages} · {total} folders', fill=MUTED, font=('Segoe UI', 9))
            self.pill(438, 552, '›', lambda: self.change_page(1), width=42)
        else:
            c.create_text(320, 552, text='Click to explore · Double-click to open · Drag the center to move', fill=MUTED, font=('Segoe UI', 8))
        for x, label, tool in ((155, '⌕  Find files', 'files'), (265, '▥  Space', 'space'), (375, '▣  Organize', 'organize'), (485, '▢  Copies', 'duplicates')):
            self.pill(x, 586, label, lambda tab=tool: self.open_tools(tab), width=103, dark=True)
        self.pill(270, 620, 'Collapse', self.collapse, width=88)
        self.pill(370, 620, 'Quit', self.close, width=70)

    def orb(self, x, y, radius):
        c = self.canvas
        c.create_oval(x-radius, y-radius, x+radius, y+radius, fill=INK, outline='#465c4d', width=2, tags=('orb',))
        c.create_text(x-3, y-2, text='f', fill='#fbf7ed', font=('Segoe UI', int(radius*.95), 'bold'), tags=('orb',))
        c.create_oval(x+10, y+11, x+17, y+18, fill=ACCENT, outline=ACCENT, tags=('orb',))
        self.targets['orb'] = self.toggle

    def folder_glyph(self, x, y, tag):
        self.canvas.create_polygon(x-17, y-10, x-4, y-10, x+1, y-5, x+17, y-5, x+17, y+13, x-17, y+13,
                                   fill='#d9b77d', outline='#c4a269', tags=(tag,))
        self.canvas.create_line(x-15, y-2, x+15, y-2, fill='#f3ddb4', width=2, tags=(tag,))

    def pill(self, x, y, label, command, width=100, dark=False):
        tag = f'action{len(self.targets)}'
        self.canvas.create_rectangle(x-width/2, y-14, x+width/2, y+14,
                                     fill=INK if dark else '#e9ede2', outline=INK if dark else '#e9ede2', tags=(tag,))
        self.canvas.create_text(x, y, text=label, fill='#fafaf3' if dark else '#687b61', font=('Segoe UI', 9), tags=(tag,))
        self.targets[tag] = command

    def press(self, event):
        tags = self.canvas.gettags('current')
        self.pressed_tag = next((tag for tag in tags if tag in self.targets), None)
        self.dragged = False
        self.drag_origin = (event.x_root, event.y_root, self.root.winfo_x(), self.root.winfo_y()) if 'orb' in tags else None

    def drag(self, event):
        if not self.drag_origin:
            return
        sx, sy, x, y = self.drag_origin
        dx, dy = event.x_root - sx, event.y_root - sy
        if abs(dx) + abs(dy) > 5:
            self.dragged = True
            self.root.geometry(f'+{max(0,x+dx)}+{max(0,y+dy)}')

    def release(self, event):
        if self.dragged:
            self.root.update_idletasks()
            self.center = (self.root.winfo_x()+self.root.winfo_width()/2, self.root.winfo_y()+self.root.winfo_height()/2)
            self.save()
        elif self.pressed_tag:
            action = self.targets.get(self.pressed_tag)
            if self.pressed_tag.startswith('folder'):
                if self.double_click_job:
                    self.root.after_cancel(self.double_click_job)
                # A short delay allows a double-click to open the folder without navigating.
                self.double_click_job = self.root.after(300, lambda: self.run_folder_action(action))
            elif action:
                action()
        self.drag_origin = None

    def run_folder_action(self, action):
        self.double_click_job = None
        if action:
            action()

    def folder_double_click(self, folder):
        if self.double_click_job:
            self.root.after_cancel(self.double_click_job)
            self.double_click_job = None
        self.pressed_tag = None
        self.safe_open(folder)

    def safe_open(self, folder):
        try:
            open_folder(folder)
        except (ValueError, OSError) as exc:
            messagebox.showerror('Findspace', str(exc), parent=self.root)

    def navigate(self, folder):
        if not Path(folder).is_dir():
            messagebox.showerror('Findspace', 'That folder is no longer available.', parent=self.root)
            return
        self.folder, self.page = Path(folder), 0
        self.save()
        self.draw()

    def parent(self):
        self.navigate(self.folder.parent)

    def choose_folder(self):
        path = filedialog.askdirectory(parent=self.root, title='Choose a folder for Findspace', initialdir=str(self.folder))
        if path:
            self.navigate(Path(path))

    def change_page(self, delta):
        self.page += delta
        self.draw()

    def toggle(self):
        self.expanded = not self.expanded
        self.draw()

    def collapse(self):
        if self.expanded:
            self.expanded = False
            self.draw()

    def context_menu(self, event):
        tags = self.canvas.gettags('current')
        if any(tag.startswith('folder') for tag in tags):
            return
        menu = tk.Menu(self.root, tearoff=False)
        menu.add_command(label='Expand / collapse', command=self.toggle)
        menu.add_command(label='Choose folder…', command=self.choose_folder)
        menu.add_command(label='Open current folder', command=lambda: self.safe_open(self.folder))
        menu.add_command(label='File tools', command=lambda: self.open_tools('files'))
        menu.add_separator()
        menu.add_checkbutton(label='Keep above other windows', onvalue=True, offvalue=False,
                             variable=tk.BooleanVar(value=self.pinned), command=self.toggle_pin)
        menu.add_command(label='Quit Findspace', command=self.close)
        menu.tk_popup(event.x_root, event.y_root)

    def folder_menu(self, event, folder):
        menu = tk.Menu(self.root, tearoff=False)
        menu.add_command(label='Explore subfolders', command=lambda: self.navigate(folder))
        menu.add_command(label='Open in file manager', command=lambda: self.safe_open(folder))
        for name, tool in [('Find files here','files'),('Explore space here','space'),('Organize here','organize'),('Find duplicates here','duplicates')]:
            menu.add_command(label=name, command=lambda p=folder, tab=tool: self.open_folder_tool(p, tab))
        menu.tk_popup(event.x_root, event.y_root)
        return 'break'

    def open_folder_tool(self, folder, tool):
        self.navigate(folder)
        self.open_tools(tool)

    def toggle_pin(self):
        self.pinned = not self.pinned
        self.root.attributes('-topmost', self.pinned)
        self.save()

    def open_tools(self, tab):
        if self.toolbox is None or not self.toolbox.window.winfo_exists():
            self.toolbox = Toolbox(self)
        self.toolbox.show(tab)

    def close(self):
        if self.manager.busy:
            messagebox.showinfo('Findspace is working', 'Wait for the current file operation to finish, then quit.', parent=self.root)
            return
        self.save()
        self.root.destroy()

    def run(self):
        self.root.mainloop()


class Toolbox:
    def __init__(self, desktop):
        self.desktop = desktop
        self.manager = desktop.manager
        self.window = tk.Toplevel(desktop.root)
        self.window.title('Findspace — File tools')
        self.window.configure(bg=BG)
        self.window.geometry('1060x720')
        self.window.minsize(820, 570)
        self.window.protocol('WM_DELETE_WINDOW', self.hide)
        self.after_id = None
        self.last_scan = None
        self.last_busy = False
        self.plan = None
        self.selected = set()
        self.offset = 0
        self.total = 0
        self.search_job = None
        self.duplicate_rows = {}
        self.file_rows = {}
        self._style()
        heading = tk.Frame(self.window, bg=BG)
        heading.pack(fill='x', padx=25, pady=(23,12))
        tk.Label(heading, text='findspace', font=('Segoe UI',21,'bold'), fg=INK, bg=BG).pack(side='left')
        tk.Label(heading, text='YOUR FILES. YOUR DECISIONS.', font=('Segoe UI',8), fg=MUTED, bg=BG).pack(side='left', padx=20, pady=(7,0))
        top = tk.Frame(self.window, bg=BG)
        top.pack(fill='x', padx=25)
        self.path_var = tk.StringVar(value=str(desktop.folder))
        ttk.Entry(top, textvariable=self.path_var).pack(side='left',fill='x',expand=True,ipady=6)
        ttk.Button(top,text='Browse…',command=self.browse).pack(side='left',padx=8)
        self.scan_button = ttk.Button(top,text='Scan folder',command=self.scan)
        self.scan_button.pack(side='left')
        self.status = tk.StringVar(value='Choose a personal folder to get started.')
        tk.Label(self.window,textvariable=self.status,bg=BG,fg=MUTED,font=('Segoe UI',9),anchor='w',wraplength=950).pack(fill='x',padx=25,pady=13)
        self.notebook = ttk.Notebook(self.window)
        self.notebook.pack(fill='both',expand=True,padx=25,pady=(0,15))
        self.tabs = {}
        for key,label in [('files','Find files'),('space','Space explorer'),('organize','Organize & undo'),('duplicates','Exact duplicates')]:
            frame = ttk.Frame(self.notebook,padding=18)
            self.notebook.add(frame,text=label)
            self.tabs[key]=frame
        self.build_files()
        self.build_space()
        self.build_organize()
        self.build_duplicates()
        tk.Label(self.window,text='Local only  ·  No uploads  ·  Index refreshed when you scan  ·  Duplicate files are never automatically deleted',bg=BG,fg=MUTED,font=('Segoe UI',8)).pack(pady=(0,17))
        self.notebook.bind('<<NotebookTabChanged>>',lambda _: self.refresh_tab())
        self.poll()

    def _style(self):
        style = ttk.Style(self.window)
        style.theme_use('clam')
        style.configure('TFrame',background=BG)
        style.configure('TLabel',background=BG,foreground=INK,font=('Segoe UI',10))
        style.configure('TButton',font=('Segoe UI',9),padding=(11,7),background='#e7ebdf',foreground=INK)
        style.configure('TCheckbutton',background=BG,foreground=INK,font=('Segoe UI',9))
        style.configure('Treeview',font=('Segoe UI',9),rowheight=30,background='white',fieldbackground='white',foreground=INK,borderwidth=0)
        style.configure('Treeview.Heading',font=('Segoe UI',9,'bold'),background='#e9ece3',foreground='#6a786e',padding=8)
        style.map('Treeview',background=[('selected','#dce6d3')],foreground=[('selected',INK)])
        style.configure('TNotebook',background=BG,borderwidth=0)
        style.configure('TNotebook.Tab',font=('Segoe UI',10),padding=(16,11))

    def tree(self,parent,columns,labels,widths):
        holder = ttk.Frame(parent)
        holder.pack(fill='both',expand=True,pady=10)
        tree = ttk.Treeview(holder,columns=columns,show='headings',selectmode='browse')
        for col,label,width in zip(columns,labels,widths):
            tree.heading(col,text=label)
            tree.column(col,width=width,minwidth=45,stretch=col in ('name','path','source','destination'))
        scroll = ttk.Scrollbar(holder,orient='vertical',command=tree.yview)
        horizontal = ttk.Scrollbar(holder,orient='horizontal',command=tree.xview)
        tree.configure(yscrollcommand=scroll.set,xscrollcommand=horizontal.set)
        tree.grid(row=0,column=0,sticky='nsew')
        scroll.grid(row=0,column=1,sticky='ns')
        horizontal.grid(row=1,column=0,sticky='ew')
        holder.rowconfigure(0,weight=1)
        holder.columnconfigure(0,weight=1)
        return tree

    def build_files(self):
        frame = self.tabs['files']
        row = ttk.Frame(frame);row.pack(fill='x')
        self.query = tk.StringVar()
        self.kind = tk.StringVar(value='All types')
        self.sort = tk.StringVar(value='Recent')
        self.search_entry = ttk.Entry(row,textvariable=self.query)
        self.search_entry.pack(side='left',fill='x',expand=True,ipady=6)
        self.search_entry.bind('<KeyRelease>',self.schedule_search)
        self.kind_box = ttk.Combobox(row,textvariable=self.kind,values=['All types'],state='readonly',width=15)
        self.kind_box.pack(side='left',padx=8)
        self.kind_box.bind('<<ComboboxSelected>>',lambda _: self.search(reset=True))
        sort = ttk.Combobox(row,textvariable=self.sort,values=['Recent','Largest','Name'],state='readonly',width=12)
        sort.pack(side='left')
        sort.bind('<<ComboboxSelected>>',lambda _: self.search(reset=True))
        ttk.Label(frame,text='Search names, paths or extensions. Combine words, e.g. invoice pdf. Double-click to reveal a file.').pack(anchor='w',pady=(10,0))
        self.files_tree = self.tree(frame,('name','size','modified','path'),('File name','Size','Modified','Location'),(240,85,105,420))
        self.files_tree.bind('<Double-1>',lambda _: self.reveal_selected(self.files_tree,self.file_rows))
        row = ttk.Frame(frame);row.pack(fill='x')
        ttk.Button(row,text='Show in folder',command=lambda:self.reveal_selected(self.files_tree,self.file_rows)).pack(side='left')
        self.file_count = tk.StringVar()
        ttk.Label(row,textvariable=self.file_count).pack(side='left',padx=15)
        self.prev_button = ttk.Button(row,text='Previous',command=lambda:self.change_page(-1));self.prev_button.pack(side='right',padx=5)
        self.next_button = ttk.Button(row,text='Next',command=lambda:self.change_page(1));self.next_button.pack(side='right')

    def build_space(self):
        frame=self.tabs['space']
        self.space_summary=tk.StringVar()
        ttk.Label(frame,textvariable=self.space_summary,font=('Segoe UI',19,'bold')).pack(anchor='w')
        ttk.Label(frame,text='Indexed file sizes, not whole-drive usage. Organizing moves files; it does not free space.').pack(anchor='w',pady=(8,10))
        self.space_tree=self.tree(frame,('name','size','count'),('Category / first-level folder','Size','File count'),(480,140,100))
        ttk.Button(frame,text='Find the largest files',command=self.largest_files).pack(anchor='e')

    def build_organize(self):
        frame=self.tabs['organize']
        ttk.Label(frame,text='Preview → choose files → move → undo. Default: loose files only; existing folders stay intact.').pack(anchor='w')
        row=ttk.Frame(frame);row.pack(fill='x',pady=12)
        self.scope=tk.StringVar(value='Loose files only')
        ttk.Combobox(row,textvariable=self.scope,values=['Loose files only','Include subfolders (links may break)'],state='readonly',width=34).pack(side='left')
        self.dated=tk.BooleanVar(value=False)
        ttk.Checkbutton(row,text='Year-month folders',variable=self.dated).pack(side='left',padx=12)
        self.preview_button=ttk.Button(row,text='Preview plan',command=self.preview);self.preview_button.pack(side='right')
        self.plan_tree=self.tree(frame,('selected','source','destination','size'),('Move?','Current location','Proposed location','Size'),(65,300,380,85))
        self.plan_tree.bind('<ButtonRelease-1>',self.select_plan_row)
        self.plan_tree.bind('<space>',self.select_plan_row)
        row=ttk.Frame(frame);row.pack(fill='x')
        self.plan_summary=tk.StringVar(value='Create a preview. Nothing moves until you confirm.')
        ttk.Label(row,textvariable=self.plan_summary).pack(side='left')
        ttk.Button(row,text='Select / clear all',command=self.toggle_selection).pack(side='right',padx=8)
        self.move_button=ttk.Button(row,text='Move selected',command=self.apply);self.move_button.pack(side='right')
        row=ttk.Frame(frame);row.pack(fill='x',pady=(15,0))
        self.history=tk.StringVar()
        self.history_box=ttk.Combobox(row,textvariable=self.history,state='readonly',width=66)
        self.history_box.pack(side='left',fill='x',expand=True)
        self.undo_button=ttk.Button(row,text='Undo batch',command=self.undo);self.undo_button.pack(side='right',padx=(12,0))
        self.batches={}

    def build_duplicates(self):
        frame=self.tabs['duplicates']
        row=ttk.Frame(frame);row.pack(fill='x')
        ttk.Label(row,text='Find exact content copies. Review their locations before removing anything yourself.').pack(side='left')
        self.duplicate_button=ttk.Button(row,text='Check duplicates',command=self.check_duplicates)
        self.duplicate_button.pack(side='right')
        self.duplicate_summary=tk.StringVar(value='Run a check. Large folders may take a while.')
        ttk.Label(frame,textvariable=self.duplicate_summary).pack(anchor='w',pady=(12,0))
        self.duplicate_tree=self.tree(frame,('group','name','size','path'),('Group','File name','Size','Location'),(65,240,85,460))
        self.duplicate_tree.bind('<Double-1>',lambda _: self.reveal_selected(self.duplicate_tree,self.duplicate_rows))
        ttk.Button(frame,text='Show selected in folder',command=lambda:self.reveal_selected(self.duplicate_tree,self.duplicate_rows)).pack(anchor='e')

    def show(self,tab):
        self.window.deiconify()
        self.window.lift()
        self.notebook.select(self.tabs[tab])
        if not self.manager.busy and str(self.desktop.folder) != self.manager.root:
            self.path_var.set(str(self.desktop.folder))
            self.scan()
        self.refresh_tab()
        if tab=='files':
            self.search_entry.focus_set()

    def hide(self):
        self.window.withdraw()

    def browse(self):
        value=filedialog.askdirectory(parent=self.window,initialdir=str(self.desktop.folder),title='Choose a personal folder')
        if value:
            self.path_var.set(value)
            self.scan()

    def scan(self):
        try:
            folder=self.manager.validate_root(self.path_var.get())
            self.manager.start(lambda:self.manager.scan(str(folder)))
            self.desktop.navigate(folder)
            self.mark_started()
        except (ValueError,OSError) as exc:
            self.error(exc)

    def mark_started(self):
        # Fast jobs may finish before the next Tk tick; retain completion handling.
        self.last_busy=True
        self.poll_status()

    def error(self,exc):
        messagebox.showerror('Findspace',str(exc),parent=self.window)

    def schedule_search(self,_=None):
        if self.search_job:
            self.window.after_cancel(self.search_job)
        self.search_job=self.window.after(160,lambda:self.search(reset=True))

    def search(self,reset=False):
        self.search_job=None
        if reset:
            self.offset=0
        try:
            result=self.manager.files(query=self.query.get(),kind='' if self.kind.get()=='All types' else self.kind.get(),sort={'Recent':'recent','Largest':'size','Name':'name'}[self.sort.get()],offset=self.offset)
            self.files_tree.delete(*self.files_tree.get_children())
            self.file_rows={}
            for row in result['items']:
                self.files_tree.insert('', 'end', iid=row['id'], values=(row['name'],human_size(row['size']),time.strftime('%d %b %Y',time.localtime(row['modified'])),row['path']))
                self.file_rows[row['id']]=row['path']
            self.total=result['total']
            self.file_count.set(f'{self.total:,} matches · {self.offset+1 if self.total else 0}–{min(self.offset+500,self.total)}')
            self.prev_button.state(['!disabled'] if self.offset else ['disabled'])
            self.next_button.state(['!disabled'] if self.offset+500<self.total else ['disabled'])
        except (ValueError,OSError) as exc:
            self.error(exc)

    def change_page(self,delta):
        self.offset=max(0,self.offset+delta*500)
        self.search()

    def reveal_selected(self,tree,rows):
        sel=tree.selection()
        if not sel:
            return
        try:
            reveal_file(rows[sel[0]])
        except (ValueError,OSError) as exc:
            self.error(exc)

    def largest_files(self):
        self.query.set('');self.kind.set('All types');self.sort.set('Largest')
        self.notebook.select(self.tabs['files'])
        self.search(reset=True)

    def refresh_tab(self):
        active=self.notebook.select()
        if active==str(self.tabs['files']):
            self.search()
        elif active==str(self.tabs['space']):
            info=self.manager.summary()
            self.space_summary.set(f"{human_size(info['size'])}  ·  {info['count']:,} indexed files")
            self.space_tree.delete(*self.space_tree.get_children())
            for group in info['groups']:
                self.space_tree.insert('','end',values=(group['category'],human_size(group['size']),group['count']))
            if info['folders']:
                self.space_tree.insert('','end',values=('— Top folders —','',''))
                for folder in info['folders']:
                    self.space_tree.insert('','end',values=(folder['name'],human_size(folder['size']),''))
        elif active==str(self.tabs['organize']):
            self.refresh_history()
        elif active==str(self.tabs['duplicates']):
            self.render_duplicates()

    def preview(self):
        try:
            self.plan=self.manager.plan(scope='loose' if self.scope.get()=='Loose files only' else 'all',dated=self.dated.get())
            self.selected={item['id'] for item in self.plan['items']}
            self.render_plan()
        except (ValueError,OSError) as exc:
            self.error(exc)

    def render_plan(self):
        self.plan_tree.delete(*self.plan_tree.get_children())
        if not self.plan:
            self.selected.clear()
            self.plan_summary.set('Create a fresh preview for this index.')
            return
        for item in self.plan['items']:
            self.plan_tree.insert('','end',iid=item['id'],values=('✓' if item['id'] in self.selected else '—',str(Path(item['source']).relative_to(self.manager.root)),str(Path(item['destination']).relative_to(self.manager.root)),human_size(item['size'])))
        self.plan_summary.set(f"{len(self.selected)} selected · {self.plan['skipped']} left in place")

    def select_plan_row(self,event):
        if not self.plan or self.manager.busy:
            return
        row=self.plan_tree.identify_row(event.y) if getattr(event,'keysym',None)!='space' else self.plan_tree.focus()
        if row:
            if row in self.selected:
                self.selected.remove(row)
            else:
                self.selected.add(row)
            self.plan_tree.set(row,'selected','✓' if row in self.selected else '—')
            self.plan_summary.set(f"{len(self.selected)} selected · {self.plan['skipped']} left in place")

    def toggle_selection(self):
        if self.plan and not self.manager.busy:
            self.selected=set() if self.selected else {i['id'] for i in self.plan['items']}
            self.render_plan()

    def apply(self):
        if not self.plan or not self.selected:
            messagebox.showinfo('Preview first','Create a preview and select files before moving.',parent=self.window)
            return
        if not messagebox.askyesno('Confirm file moves',f'Move {len(self.selected)} selected file(s) to the destinations shown?\n\nExisting files are never overwritten. Unchanged files can be restored with Undo batch.',parent=self.window):
            return
        plan_id,selected=self.plan['id'],list(self.selected)
        try:
            self.manager.start(lambda:self.manager.apply(plan_id,selected))
            self.mark_started()
        except ValueError as exc:
            self.error(exc)

    def refresh_history(self):
        rows=self.manager.summary()['history']
        self.batches={f"{time.strftime('%d %b %H:%M:%S',time.localtime(r['created']))} · {r['count']} file(s) · {r['batch'][:6]}":r['batch'] for r in rows}
        self.history_box.configure(values=list(self.batches))
        if self.history.get() not in self.batches:
            self.history.set(next(iter(self.batches),'No moves to undo in this folder'))

    def undo(self):
        batch=self.batches.get(self.history.get())
        if not batch:
            return
        if not messagebox.askyesno('Restore original locations?','Unchanged files will return to their original locations. Edited files and occupied destinations are skipped.',parent=self.window):
            return
        try:
            self.manager.start(lambda:self.manager.undo(batch))
            self.mark_started()
        except ValueError as exc:
            self.error(exc)

    def check_duplicates(self):
        if not self.manager.root:
            messagebox.showinfo('Scan first','Scan a folder before checking duplicates.',parent=self.window)
            return
        try:
            self.manager.start(self.manager.find_duplicates)
            self.mark_started()
        except ValueError as exc:
            self.error(exc)

    def render_duplicates(self):
        groups=self.manager.duplicates
        self.duplicate_tree.delete(*self.duplicate_tree.get_children())
        self.duplicate_rows={}
        for index,group in enumerate(groups,1):
            for row in group['files']:
                self.duplicate_tree.insert('','end',iid=row['id'],values=(index,row['name'],human_size(row['size']),row['path']))
                self.duplicate_rows[row['id']]=row['path']
        self.duplicate_summary.set(f"{len(groups)} exact duplicate groups · {human_size(sum(g['potential'] for g in groups))} in extra copies. Nothing deleted." if groups else 'No results yet. Run a check to find exact content copies.')

    def poll_status(self):
        info=self.manager.summary()
        busy=info['busy']
        for button in (self.scan_button,self.preview_button,self.move_button,self.undo_button,self.duplicate_button):
            button.state(['disabled'] if busy else ['!disabled'])
        if busy:
            self.status.set(f"{info['progress']['stage'].capitalize()} · {info['progress']['count']:,} files processed · {info['progress']['errors']} skipped/failed")
        elif self.last_busy:
            self.status.set(info['progress']['message'] or 'Ready.')
            if info['progress']['stage']=='error':
                self.error(info['progress']['message'])
            self.plan=None
            self.render_plan()
            self.refresh_history()
            self.refresh_tab()
        elif not self.status.get():
            self.status.set('Ready. Choose a folder and scan it.')
        if info['last_scan']!=self.last_scan:
            self.last_scan=info['last_scan']
            self.kind_box.configure(values=['All types']+[g['category'] for g in info['groups']])
            self.offset=0
            self.refresh_tab()
        self.last_busy=busy

    def poll(self):
        if not self.window.winfo_exists():
            return
        self.poll_status()
        self.after_id=self.window.after(500,self.poll)


def main():
    state=Path(os.environ.get('LOCALAPPDATA', Path.home() / '.local/share')) / 'Findspace'
    with InstanceLock(state) as lock:
        if not lock.acquired:
            (state/'activate.request').write_text(str(time.time_ns()),encoding='utf-8')
            return
        app=Desktop(state)
        # Native icon is optional when running an unpacked source tree.
        if sys.platform=='win32' and (HERE/'findspace.ico').is_file():
            app.root.iconbitmap(str(HERE/'findspace.ico'))
        app.expanded=True
        app.draw()
        app.run()


if __name__=='__main__':
    main()
