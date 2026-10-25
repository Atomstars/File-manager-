#!/usr/bin/env python3
"""Run `python app.py`. No third-party packages or cloud service required."""
import argparse
import json
import os
import secrets
import subprocess
import sys
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from engine import Manager

HERE = Path(__file__).resolve().parent


def make_server(manager, port=0, allow_open=True):
    token = secrets.token_urlsafe(32)

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # File paths and searches stay out of HTTP logs.

        def respond(self, value, code=200, mime='application/json'):
            body = json.dumps(value).encode() if mime == 'application/json' else value.encode()
            self.send_response(code)
            self.send_header('Content-Type', mime + '; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('X-Frame-Options', 'DENY')
            self.send_header('Referrer-Policy', 'no-referrer')
            self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
            self.end_headers()
            self.wfile.write(body)

        def allowed(self, auth=True):
            host = f'127.0.0.1:{self.server.server_port}'
            if self.headers.get('Host') != host:
                self.respond({'error': 'Invalid host.'}, 403)
                return False
            if self.headers.get('Origin') not in (None, f'http://{host}'):
                self.respond({'error': 'Cross-origin request denied.'}, 403)
                return False
            if self.headers.get('Sec-Fetch-Site') == 'cross-site':
                self.respond({'error': 'Cross-site request denied.'}, 403)
                return False
            if auth and not secrets.compare_digest(self.headers.get('X-Findspace-Token', ''), token):
                self.respond({'error': 'Session expired. Reload Findspace.'}, 403)
                return False
            return True

        def do_GET(self):
            path = urlparse(self.path).path
            if not self.allowed(auth=path.startswith('/api/')):
                return
            try:
                if path == '/':
                    self.respond((HERE / 'web/index.html').read_text(encoding='utf-8').replace('__TOKEN__', token), mime='text/html')
                elif path in ('/style.css', '/ui.js'):
                    self.respond((HERE / 'web' / path[1:]).read_text(encoding='utf-8'), mime='text/css' if path.endswith('css') else 'text/javascript')
                elif path == '/api/status':
                    self.respond(manager.summary())
                elif path == '/api/files':
                    q = {k: v[0] for k, v in parse_qs(urlparse(self.path).query).items()}
                    self.respond(manager.files(query=q.get('q', ''), kind=q.get('kind', ''), sort=q.get('sort', 'recent'), offset=q.get('offset', 0)))
                else:
                    self.respond({'error': 'Not found'}, 404)
            except (ValueError, OSError) as exc:
                self.respond({'error': str(exc)}, 400)

        def do_POST(self):
            if not self.allowed():
                return
            try:
                length = int(self.headers.get('Content-Length', 0))
                if not 0 < length <= 2000000:
                    raise ValueError('Invalid request size.')
                data = json.loads(self.rfile.read(length))
                path = urlparse(self.path).path
                if path == '/api/scan':
                    root = str(manager.validate_root(data.get('path', '')))
                    manager.start(lambda: manager.scan(root))
                    self.respond({'started': True})
                elif path == '/api/plan':
                    if data.get('scope', 'loose') not in ('loose', 'all'):
                        raise ValueError('Unknown scope')
                    self.respond(manager.plan(data.get('scope', 'loose'), bool(data.get('dated'))))
                elif path == '/api/apply':
                    manager.start(lambda: manager.apply(data.get('plan'), data.get('selected')))
                    self.respond({'started': True})
                elif path == '/api/undo':
                    manager.start(lambda: manager.undo(data.get('batch')))
                    self.respond({'started': True})
                elif path == '/api/duplicates':
                    manager.start(manager.find_duplicates)
                    self.respond({'started': True})
                elif path == '/api/pick':
                    picker = 'import tkinter as t; from tkinter import filedialog; r=t.Tk(); r.withdraw(); r.attributes("-topmost", True); print(filedialog.askdirectory(title="Choose a folder for Findspace")); r.destroy()'
                    result = subprocess.run([sys.executable, '-c', picker], capture_output=True, text=True, timeout=180)
                    if result.returncode:
                        raise ValueError('Folder picker unavailable. Paste the folder path instead.')
                    self.respond({'path': result.stdout.strip()})
                elif path == '/api/reveal':
                    row = manager.file(data.get('id'))
                    p = Path(row['path'])
                    if not p.is_file():
                        raise ValueError('File moved or removed. Scan again.')
                    if not allow_open:
                        raise ValueError('Opening folders is disabled in this test session.')
                    if sys.platform == 'win32':
                        subprocess.Popen(['explorer', '/select,', str(p)])
                    elif sys.platform == 'darwin':
                        subprocess.Popen(['open', '-R', str(p)])
                    else:
                        subprocess.Popen(['xdg-open', str(p.parent)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    self.respond({'ok': True})
                else:
                    self.respond({'error': 'Not found'}, 404)
            except (ValueError, OSError, TypeError, subprocess.TimeoutExpired) as exc:
                self.respond({'error': str(exc)}, 400)

    return ThreadingHTTPServer(('127.0.0.1', port), Handler), token


def main():
    parser = argparse.ArgumentParser(description='Findspace — find your files, make room, organize with a preview.')
    parser.add_argument('--port', type=int, default=0, help='Local port; default chooses a free port')
    parser.add_argument('--no-browser', action='store_true')
    parser.add_argument('--state-dir', type=Path)
    parser.add_argument('--root', type=Path, help='Scan this personal folder at startup')
    args = parser.parse_args()
    state = args.state_dir or Path(os.environ.get('LOCALAPPDATA', Path.home() / '.local/share')) / 'Findspace'
    manager = Manager(state)
    manager.recover_pending()
    if args.root:
        root = str(manager.validate_root(str(args.root)))
        manager.start(lambda: manager.scan(root))
    server, _ = make_server(manager, args.port)
    url = f'http://127.0.0.1:{server.server_port}'
    print(f'Findspace is running at {url}\nKeep this window open. Press Ctrl+C to quit.', flush=True)
    if not args.no_browser:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\nClosing Findspace. Wait for any file operation to finish…')
        import time
        while manager.busy:
            time.sleep(.1)
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
