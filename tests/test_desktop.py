import math
import tempfile
import unittest
from unittest.mock import MagicMock, patch
from pathlib import Path

from desktop_model import clamp_center, folder_page, human_size, load_preferences, ring_positions, save_preferences
from instance import InstanceLock


class DesktopHelpersTests(unittest.TestCase):
    def test_folder_pages_are_sorted_and_do_not_expose_hidden_or_dependencies(self):
        with tempfile.TemporaryDirectory() as name:
            root=Path(name)
            for i in range(19):
                (root/f'Folder {i:02}').mkdir()
            (root/'.private').mkdir()
            (root/'node_modules').mkdir()
            (root/'file.txt').write_text('not a folder')
            paths,page,pages,total=folder_page(root,1)
            self.assertEqual([p.name for p in paths],[f'Folder {i:02}' for i in range(8,16)])
            self.assertEqual((page,pages,total),(1,3,19))
            self.assertEqual(folder_page(root,999)[1],2)
            self.assertEqual(folder_page(root,-1)[1],0)

    def test_empty_folder(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual(folder_page(folder),([],0,1,0))

    def test_symlink_folders_are_skipped(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            (root/'real').mkdir()
            try:
                (root/'alias').symlink_to(root/'real',target_is_directory=True)
            except OSError:
                self.skipTest('Symlinks unavailable')
            self.assertEqual([p.name for p in folder_page(root)[0]],['real'])

    def test_ring_nodes_fit_and_do_not_overlap(self):
        for count in range(9):
            points=ring_positions(count)
            self.assertEqual(len(points),count)
            for x,y in points:
                self.assertTrue(65<x<575)
                self.assertTrue(100<y<500)
            for i,(x,y) in enumerate(points):
                for a,b in points[i+1:]:
                    self.assertGreater(math.hypot(a-x,b-y),125)
        with self.assertRaises(ValueError):
            ring_positions(9)

    def test_widget_is_kept_on_screen(self):
        self.assertEqual(clamp_center(1900,1000,1920,1080,640,640),(1280,440))
        self.assertEqual(clamp_center(-10,-10,1920,1080,640,640),(0,0))

    def test_preferences_round_trip_and_corruption(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'desktop.json'
            self.assertEqual(load_preferences(p),{})
            values={'folder':'C:/Users/You/Downloads','position':[200,300],'pinned':False}
            save_preferences(p,values)
            self.assertEqual(load_preferences(p),values)
            p.write_text('broken')
            self.assertEqual(load_preferences(p),{})

    def test_sizes(self):
        self.assertEqual(human_size(0),'0 B')
        self.assertEqual(human_size(1024),'1.0 KB')

    def test_only_one_desktop_instance_can_hold_lock(self):
        with tempfile.TemporaryDirectory() as folder:
            with InstanceLock(folder) as first:
                self.assertTrue(first.acquired)
                with InstanceLock(folder) as second:
                    self.assertFalse(second.acquired)
            with InstanceLock(folder) as next_instance:
                self.assertTrue(next_instance.acquired)


class NativeControllerTests(unittest.TestCase):
    """Native controller checks using a canvas test double, not a rendering test."""
    def setUp(self):
        import desktop
        self.temp=tempfile.TemporaryDirectory()
        self.base=Path(self.temp.name)
        self.folder=self.base/'Downloads';self.folder.mkdir()
        for i in range(12):
            (self.folder/f'Folder {i:02}').mkdir()
        self.tk=MagicMock()
        self.tk.winfo_screenwidth.return_value=1920
        self.tk.winfo_screenheight.return_value=1080
        self.tk.winfo_x.return_value=100
        self.tk.winfo_y.return_value=100
        self.tk.winfo_width.return_value=88
        self.tk.winfo_height.return_value=88
        self.canvas=MagicMock()
        with patch.object(desktop.tk,'Tk',return_value=self.tk),patch.object(desktop.tk,'Canvas',return_value=self.canvas):
            self.app=desktop.Desktop(self.base/'state')
        self.app.navigate(self.folder)

    def tearDown(self):
        self.temp.cleanup()

    def test_expand_collapse_and_folder_navigation(self):
        self.assertFalse(self.app.expanded)
        self.assertEqual(set(self.app.targets),{'orb'})
        self.app.toggle()
        self.assertTrue(self.app.expanded)
        self.assertEqual(len([t for t in self.app.targets if t.startswith('folder')]),8)
        self.app.targets['folder0']()
        self.assertEqual(self.app.folder,self.folder/'Folder 00')
        self.assertEqual(len([t for t in self.app.targets if t.startswith('folder')]),0)
        self.app.collapse()
        self.assertFalse(self.app.expanded)

    def test_folder_ring_pagination(self):
        self.app.toggle()
        self.app.change_page(1)
        self.assertEqual(self.app.page,1)
        self.assertEqual(len([t for t in self.app.targets if t.startswith('folder')]),4)
        self.app.targets['folder0']()
        self.assertEqual(self.app.folder.name,'Folder 08')

    def test_quit_does_not_interrupt_a_file_operation(self):
        import desktop
        self.app.manager.busy=True
        with patch.object(desktop.messagebox,'showinfo') as alert:
            self.app.close()
        alert.assert_called_once()
        self.tk.destroy.assert_not_called()


if __name__=='__main__':
    unittest.main()
