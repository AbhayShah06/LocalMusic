const AUDIO = /\.(mp3|m4a|aac|flac|ogg|opus|wav)$/i;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!authorized(request, env)) return json({ error: "Unauthorized" }, 401);
    if (url.pathname === "/v1/health") return json({ ok: true });
    if (url.pathname === "/v1/catalog") return catalog(url, env);
    if (url.pathname.startsWith("/v1/stream/")) return stream(request, decodeURIComponent(url.pathname.slice(11)), env);
    return json({ error: "Not found" }, 404);
  }
};

function authorized(request, env) {
  const header = request.headers.get("Authorization") || "";
  return env.READ_TOKEN && constantTime(header, `Bearer ${env.READ_TOKEN}`);
}

function constantTime(a, b) {
  if (a.length !== b.length) return false;
  let result = 0; for (let i = 0; i < a.length; i++) result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return result === 0;
}

async function catalog(url, env) {
  const query = (url.searchParams.get("q") || "").toLowerCase();
  const offset = Math.max(0, Number(url.searchParams.get("offset")) || 0);
  const limit = Math.min(200, Math.max(1, Number(url.searchParams.get("limit")) || 100));
  const objects = []; let cursor;
  do {
    const page = await env.MUSIC.list({ cursor, limit: 1000, include: ["customMetadata"] });
    objects.push(...page.objects); cursor = page.truncated ? page.cursor : undefined;
  } while (cursor && objects.length < 5000);
  const tracks = objects.filter(o => AUDIO.test(o.key)).map(toTrack).filter(t => !query || `${t.title} ${t.artist} ${t.album}`.toLowerCase().includes(query));
  tracks.sort((a, b) => a.title.localeCompare(b.title));
  return json({ tracks: tracks.slice(offset, offset + limit), total: tracks.length });
}

function toTrack(object) {
  const parts = object.key.split("/"); const file = parts.pop() || object.key;
  const title = (object.customMetadata?.title || file.replace(/\.[^.]+$/, "")).replace(/^\d+\s*[-.]\s*/, "");
  return { key: object.key, title, artist: object.customMetadata?.artist || parts[0] || "Unknown artist", album: object.customMetadata?.album || parts[1] || "Unknown album", size: object.size };
}

async function stream(request, key, env) {
  const range = request.headers.get("Range");
  const object = await env.MUSIC.get(key, range ? { range: request.headers } : undefined);
  if (!object) return json({ error: "Not found" }, 404);
  const headers = new Headers(); object.writeHttpMetadata(headers); headers.set("etag", object.httpEtag); headers.set("accept-ranges", "bytes"); headers.set("cache-control", "private, max-age=3600");
  if (object.range) { const start = object.range.offset || 0; const length = object.range.length || object.size; headers.set("content-range", `bytes ${start}-${start + length - 1}/${object.size}`); headers.set("content-length", String(length)); return new Response(object.body, { status: 206, headers }); }
  headers.set("content-length", String(object.size)); return new Response(object.body, { headers });
}

function json(value, status = 200) { return new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } }); }
