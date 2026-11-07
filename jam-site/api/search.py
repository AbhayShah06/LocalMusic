import base64
import difflib
import json
import mimetypes
import re
from http.server import BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from ytmusicapi import YTMusic


class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path in ("/api/mix", "/api/mix.py"):
            return self.serve_mix(parsed)
        if parsed.path != "/api/search":
            return self.serve_static(parsed.path)
        query = parse_qs(parsed.query).get("q", [""])[0].strip()[:120]
        if len(query) < 2:
            return self.reply(400, {"error": "Search is too short"})
        try:
            client = YTMusic()
            # Songs-only search sometimes returns obscure exact-title matches while
            # omitting the known artist entirely. A popular official video gives us
            # an artist hint; search for its audio recording before falling back.
            videos = client.search(query, filter="videos", limit=18)
            anchor = best_anchor(query, videos)
            results = []
            if anchor:
                artists = [a.get("name", "") for a in anchor.get("artists", []) if a.get("name")]
                feature = re.search(r"\b(?:feat|ft)\.?\s+([^)]+)", anchor.get("title", ""), re.I)
                hint = artists[-1] if len(artists) > 1 else feature.group(1) if feature else artists[0] if artists else ""
                if hint:
                    focused = client.search(f"{query} {hint}", filter="songs", limit=25)
                    artist_names = {normalized(name) for name in artists}
                    results.extend(item for item in focused if any(normalized(a.get("name")) in artist_names for a in item.get("artists", [])))
                results.append(anchor)
            results.extend(item for item in client.search(query, limit=25) if item.get("resultType") in ("song", "video"))
            results.extend(client.search(query, filter="songs", limit=35))
            if not results or max((search_score(query, item) for item in results), default=0) < 0.58:
                suggestions = client.get_search_suggestions(query)
                if suggestions:
                    results.extend(client.search(str(suggestions[0]), filter="songs", limit=25))
            unique = {}
            for item in results:
                video_id = item.get("videoId")
                if video_id and video_id not in unique:
                    unique[video_id] = item
            results = sorted(unique.values(), key=lambda item: search_score(query, item), reverse=True)
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

    def serve_mix(self, parsed):
        video_id = parse_qs(parsed.query).get("v", [""])[0]
        if not re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id):
            return self.reply(400, {"error": "Invalid song ID"})
        try:
            radio = YTMusic().get_watch_playlist(videoId=video_id, radio=True, limit=60)
            tracks, seen = [], {video_id}
            for item in radio.get("tracks") or []:
                candidate_id = item.get("videoId")
                title = str(item.get("title") or "").strip()
                length = item.get("length") or ""
                try:
                    duration = 0
                    for part in str(length).split(":"):
                        duration = duration * 60 + int(part)
                except ValueError:
                    duration = 0
                if not candidate_id or candidate_id in seen or not title or "#shorts" in title.lower():
                    continue
                if duration and (duration < 75 or duration > 600):
                    continue
                seen.add(candidate_id)
                artists = ", ".join(a.get("name", "") for a in (item.get("artists") or []) if a.get("name"))
                album = item.get("album") or {}
                thumbnails = item.get("thumbnail") or item.get("thumbnails") or []
                artwork = thumbnails[-1].get("url", "") if thumbnails else ""
                encoded = base64.urlsafe_b64encode(f"https://www.youtube.com/watch?v={candidate_id}".encode()).decode().rstrip("=")
                tracks.append({"id": f"online:0:{encoded}", "title": title,
                               "artist": artists or "Unknown artist", "album": album.get("name", "YouTube Music"),
                               "artwork": artwork})
            return self.reply(200, {"tracks": tracks})
        except Exception:
            return self.reply(502, {"error": "Radio is temporarily unavailable"})

    def do_OPTIONS(self):
        self.send_response(204)
        self.cors()
        self.end_headers()

    def cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "content-type")

    def serve_static(self, path):
        files = {"/": "index.html", "/index.html": "index.html", "/styles.css": "styles.css", "/queue.css": "queue.css", "/app.js": "app.js", "/config.js": "config.js", "/localmusic-logo.png": "localmusic-logo.png"}
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


def normalized(value):
    text = str(value or "").lower()
    text = re.sub(r"\b(?:i['’]?m a|ima)\b", "im a", text)
    text = re.sub(r"\band\b", " ", text)
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def song_title(value, artists=""):
    text = str(value or "")
    parts = re.split(r"\s[-–—]\s", text, maxsplit=1)
    if len(parts) == 2 and normalized(parts[0]) and normalized(parts[0]) in normalized(artists):
        text = parts[1]
    text = re.sub(r"\s*[\[(](?:feat\.?|ft\.?|official|audio|video|lyrics?).*?[\])]", "", text, flags=re.I)
    text = re.sub(r"\s+(?:feat\.?|ft\.?)\s+.*$", "", text, flags=re.I)
    return normalized(text)


def view_count(value):
    match = re.search(r"([\d.]+)\s*([KMB])?", str(value or ""), re.I)
    if not match:
        return 0
    return float(match.group(1)) * {"K": 1e3, "M": 1e6, "B": 1e9}.get((match.group(2) or "").upper(), 1)


def best_anchor(query, videos):
    wanted = normalized(query)
    exact = [item for item in videos if item.get("videoType") == "MUSIC_VIDEO_TYPE_OMV"
             and song_title(item.get("title"), " ".join(a.get("name", "") for a in item.get("artists", []))) == wanted]
    return max(exact, key=lambda item: view_count(item.get("views")), default=None)


def search_score(query, item):
    wanted = normalized(query)
    artists = " ".join(artist.get("name", "") for artist in item.get("artists", []))
    title = song_title(item.get("title"), artists)
    candidate = normalized(f"{title} {artists}")
    if not wanted or not title:
        return 0
    sequence = difflib.SequenceMatcher(None, wanted, title).ratio()
    wanted_tokens, candidate_tokens = set(wanted.split()), set(candidate.split())
    overlap = len(wanted_tokens & candidate_tokens) / max(1, len(wanted_tokens))
    title_match = 1.3 if title == wanted else 0.45 if title.startswith(wanted + " ") else 0
    artist_match = 0.4 if normalized(artists) and normalized(artists) in wanted else 0
    popularity = min(0.18, len(str(int(view_count(item.get("views"))))) * 0.025) if item.get("views") else 0
    top = 0.25 if item.get("category") == "Top result" else 0
    video = -0.18 if item.get("videoType") == "MUSIC_VIDEO_TYPE_OMV" else 0
    return sequence * 0.45 + overlap * 0.55 + title_match + artist_match + popularity + top + video
