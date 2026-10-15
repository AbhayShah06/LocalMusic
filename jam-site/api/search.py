import base64
import json
import mimetypes
from http.server import BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from ytmusicapi import YTMusic


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path != "/api/search":
            return self.serve_static(parsed.path)
        query = parse_qs(parsed.query).get("q", [""])[0].strip()[:120]
        if len(query) < 2:
            return self.reply(400, {"error": "Search is too short"})
        try:
            results = YTMusic().search(query, filter="songs", limit=35)
            tracks = []
            for item in results:
                video_id = item.get("videoId")
                if not video_id:
                    continue
                url = f"https://www.youtube.com/watch?v={video_id}"
                encoded = base64.urlsafe_b64encode(url.encode()).decode().rstrip("=")
                artists = ", ".join(x.get("name", "") for x in item.get("artists", []) if x.get("name"))
                album = (item.get("album") or {}).get("name", "YouTube Music")
                thumbnails = item.get("thumbnails") or []
                artwork = thumbnails[-1].get("url", "") if thumbnails else ""
                tracks.append({"id": f"online:0:{encoded}", "title": item.get("title") or "Unknown title", "artist": artists or "Unknown artist", "album": album, "artwork": artwork})
            self.reply(200, {"tracks": tracks})
        except Exception:
            self.reply(502, {"error": "Catalogue search is temporarily unavailable"})

    def do_OPTIONS(self):
        self.send_response(204)
        self.cors()
        self.end_headers()

    def cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "content-type")

    def serve_static(self, path):
        files = {"/": "index.html", "/index.html": "index.html", "/styles.css": "styles.css", "/app.js": "app.js", "/config.js": "config.js", "/localmusic-logo.png": "localmusic-logo.png"}
        name = files.get(path)
        if not name:
            return self.reply(404, {"error": "Not found"})
        target = Path(__file__).resolve().parent.parent / name
        if not target.is_file():
            return self.reply(404, {"error": "Not found"})
        payload = target.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", mimetypes.guess_type(name)[0] or "application/octet-stream")
        self.send_header("Cache-Control", "public, max-age=300" if name == "index.html" else "public, max-age=3600")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def reply(self, status, value):
        payload = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "public, s-maxage=120, stale-while-revalidate=300")
        self.cors()
        self.end_headers()
        self.wfile.write(payload)
