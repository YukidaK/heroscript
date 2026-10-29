"""Servidor estático local com suporte a Range requests (o CheerpJ baixa os .jar em pedaços).

Uso: python3 scripts/serve.py [pasta] [porta]   (padrão: site 8000)
"""
import http.server
import os
import re
import sys


class RangeHandler(http.server.SimpleHTTPRequestHandler):
    def send_head(self):
        match = re.fullmatch(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
        path = self.translate_path(self.path)
        if not match or not os.path.isfile(path):
            return super().send_head()
        size = os.path.getsize(path)
        start_s, end_s = match.groups()
        if start_s:
            start, end = int(start_s), min(int(end_s or size - 1), size - 1)
        else:
            start, end = max(0, size - int(end_s or 0)), size - 1
        if start >= size or start > end:
            self.send_error(416, "Range Not Satisfiable")
            return None
        f = open(path, "rb")
        f.seek(start)
        self.send_response(206)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(end - start + 1))
        self.send_header("Accept-Ranges", "bytes")
        self.end_headers()
        self._remaining = end - start + 1
        return f

    def copyfile(self, source, outputfile):
        remaining = getattr(self, "_remaining", None)
        if remaining is None:
            return super().copyfile(source, outputfile)
        while remaining > 0:
            chunk = source.read(min(64 * 1024, remaining))
            if not chunk:
                break
            outputfile.write(chunk)
            remaining -= len(chunk)

    def end_headers(self):
        if self.command == "GET" and "Range" not in self.headers:
            self.send_header("Accept-Ranges", "bytes")
        super().end_headers()


if __name__ == "__main__":
    directory = sys.argv[1] if len(sys.argv) > 1 else "site"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 8000
    handler = lambda *a, **k: RangeHandler(*a, directory=directory, **k)
    print(f"Servindo {directory} em http://localhost:{port}/")
    http.server.ThreadingHTTPServer(("", port), handler).serve_forever()
