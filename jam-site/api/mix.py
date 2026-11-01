import base64
import json
import re
from http.server import BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse

from ytmusicapi import YTMusic


def song_length(value):
    try:
        parts = [int(part) for part in str(value).split(":")]
        seconds = 0
        for part in parts:
            seconds = seconds * 60 + part
        return seconds
    except (TypeError, ValueError):
        return 0


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        video_id = parse_qs(parsed.query).get("v", [""])[0]
        if not re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id):
            return self.reply(400, {"error": "Invalid song ID"})
        try:
            radio = YTMusic().get_watch_playlist(videoId=video_id, radio=True, limit=60)
            tracks = []
            seen = {video_id}
            for item in radio.get("tracks") or []:
                candidate_id = item.get("videoId")
                title = str(item.get("title") or "").strip()
                length = song_length(item.get("length"))
                if not candidate_id or candidate_id in seen or not title or "#shorts" in title.lower():
                    continue
                if length and (length < 75 or length > 600):
                    continue
                seen.add(candidate_id)
                artists = ", ".join(a.get("name", "") for a in (item.get("artists") or []) if a.get("name"))
                album = item.get("album") or {}
                thumbnails = item.get("thumbnail") or item.get("thumbnails") or []
                artwork = thumbnails[-1].get("url", "") if thumbnails else ""
                url = f"https://www.youtube.com/watch?v={candidate_id}"
                encoded = base64.urlsafe_b64encode(url.encode()).decode().rstrip("=")
                tracks.append({"id": f"online:0:{encoded}", "title": title,
                               "artist": artists or "Unknown artist", "album": album.get("name", "YouTube Music"),
                               "artwork": artwork})
            self.reply(200, {"tracks": tracks})
        except Exception:
            self.reply(502, {"error": "Radio is temporarily unavailable"})

    def reply(self, status, value):
        payload = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "private, no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)
