import { DurableObject } from "cloudflare:workers";

const json = (value, status = 200, extra = {}) => new Response(JSON.stringify(value), {
  status, headers: { "content-type": "application/json; charset=utf-8", ...extra }
});
const code = () => {
  const alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  const bytes = crypto.getRandomValues(new Uint8Array(6));
  return Array.from(bytes, value => alphabet[value % alphabet.length]).join("");
};
const token = () => crypto.randomUUID() + crypto.randomUUID();

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const cors = corsHeaders(request, env);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (url.pathname === "/api/health") return json({ ok: true }, 200, cors);
    if (url.pathname === "/api/rooms" && request.method === "POST") {
      const roomCode = code();
      const id = env.JAM_ROOMS.idFromName(roomCode);
      const response = await env.JAM_ROOMS.get(id).fetch(new Request("https://room/create", request));
      const body = await response.json();
      return json({ ...body, roomCode }, response.status, cors);
    }
    const match = url.pathname.match(/^\/api\/rooms\/([A-Z0-9]{6})(\/.*)?$/);
    if (!match) return json({ error: "Not found" }, 404, cors);
    const roomCode = match[1];
    const suffix = match[2] || "/";
    const id = env.JAM_ROOMS.idFromName(roomCode);
    const target = new URL(request.url); target.hostname = "room"; target.pathname = suffix;
    const response = await env.JAM_ROOMS.get(id).fetch(new Request(target, request));
    const headers = new Headers(response.headers); Object.entries(cors).forEach(([key, value]) => headers.set(key, value));
    return new Response(response.body, { status: response.status, headers, webSocket: response.webSocket });
  }
};

function corsHeaders(request, env) {
  const origin = request.headers.get("origin") || "";
  const allowed = origin === env.SITE_ORIGIN || origin === "https://abhayshah06.github.io" || /^https:\/\/[a-z0-9-]+\.vercel\.app$/.test(origin) || origin.startsWith("http://localhost:");
  return {
    "access-control-allow-origin": allowed ? origin : env.SITE_ORIGIN,
    "access-control-allow-methods": "GET,POST,OPTIONS",
    "access-control-allow-headers": "content-type,authorization",
    "vary": "Origin"
  };
}

export class JamRoom extends DurableObject {
  constructor(ctx, env) { super(ctx, env); this.ctx = ctx; this.env = env; }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/create" && request.method === "POST") return this.create(request);
    if (url.pathname === "/" && request.method === "GET") return this.snapshotResponse(request);
    if (url.pathname === "/queue" && request.method === "POST") return this.add(request);
    if (url.pathname === "/control" && request.method === "POST") return this.control(request);
    if (url.pathname === "/playback" && request.method === "POST") return this.playback(request);
    if (url.pathname === "/search" && request.method === "POST") return this.search(request);
    const results = url.pathname.match(/^\/search\/([a-f0-9-]{36})\/results$/);
    if (results && request.method === "POST") return this.searchResults(request, results[1]);
    if (url.pathname === "/close" && request.method === "POST") return this.close(request);
    if (url.pathname === "/events" && request.headers.get("upgrade") === "websocket") return this.connect();
    return json({ error: "Not found" }, 404);
  }

  async create(request) {
    if (await this.ctx.storage.get("room")) return json({ error: "Room exists" }, 409);
    const input = await request.json().catch(() => ({}));
    const catalog = sanitizeCatalog(input.catalog);
    const hostToken = token();
    const room = { active: true, createdAt: Date.now(), updatedAt: Date.now(), hostToken, catalog, queue: [], searches: [], controls: [], playback: { title: "", artist: "", playing: false, upcoming: [] }, revision: 1 };
    await this.ctx.storage.put("room", room);
    await this.ctx.storage.setAlarm(Date.now() + 12 * 60 * 60 * 1000);
    return json({ hostToken, room: publicRoom(room) }, 201);
  }

  async snapshotResponse(request) {
    const room = await this.ctx.storage.get("room");
    return room ? json({ room: publicRoom(room, authorized(request, room.hostToken)) }) : json({ error: "Jam not found" }, 404);
  }

  async add(request) {
    const room = await this.ctx.storage.get("room");
    if (!room) return json({ error: "Jam not found" }, 404);
    if (!room.active) return json({ error: "This Jam is closed" }, 410);
    if (room.queue.length >= 200) return json({ error: "The queue is full" }, 409);
    const input = await request.json().catch(() => ({}));
    const track = room.catalog.find(item => item.id === String(input.trackId || ""));
    if (!track) return json({ error: "Song not found" }, 404);
    const guest = String(input.guest || "Guest").trim().slice(0, 28) || "Guest";
    const requestId = String(input.requestId || "").slice(0, 80); const now = Date.now();
    const duplicate = room.queue.find(item => (requestId && item.requestId === requestId) || (item.trackId === track.id && item.guest === guest && now - item.addedAt < 12_000));
    if (duplicate) return json({ ok: true, duplicate: true, entry: duplicate, revision: room.revision }, 200);
    const entry = { id: crypto.randomUUID(), requestId, trackId: track.id, title: track.title, artist: track.artist, guest, addedAt: now };
    room.queue.push(entry); room.updatedAt = Date.now(); room.revision += 1;
    await this.ctx.storage.put("room", room); this.broadcast(room);
    return json({ ok: true, entry, revision: room.revision }, 201);
  }

  async control(request) {
    const room = await this.ctx.storage.get("room"); if (!room) return json({ error: "Jam not found" }, 404); if (!room.active) return json({ error: "This Jam is closed" }, 410);
    const input = await request.json().catch(() => ({})); const action = String(input.action || "");
    if (!new Set(["previous", "toggle", "next", "move"]).has(action)) return json({ error: "Unknown control" }, 400);
    room.controls ||= []; const requestId = String(input.requestId || "").slice(0, 80); const duplicate = room.controls.find(item => requestId && item.requestId === requestId); if (duplicate) return json({ ok: true, duplicate: true, control: duplicate }, 200);
    const control = { id: crypto.randomUUID(), requestId, action, guest: String(input.guest || "Guest").trim().slice(0, 28) || "Guest", index: Math.max(-1, Math.min(19, Number(input.index) || 0)), direction: Math.sign(Number(input.direction) || 0), createdAt: Date.now() };
    room.controls.push(control); room.controls = room.controls.slice(-80); room.updatedAt = Date.now(); room.revision += 1; await this.ctx.storage.put("room", room); this.broadcast(room); return json({ ok: true, control }, 201);
  }

  async playback(request) {
    const room = await this.ctx.storage.get("room"); if (!room) return json({ error: "Jam not found" }, 404); if (!authorized(request, room.hostToken)) return json({ error: "Host access required" }, 401);
    const input = await request.json().catch(() => ({})); const upcoming = Array.isArray(input.upcoming) ? input.upcoming.slice(0, 20).map(item => ({ title: String(item?.title || "Unknown title").slice(0, 160), artist: String(item?.artist || "Unknown artist").slice(0, 160) })) : [];
    room.playback = { title: String(input.title || "").slice(0, 160), artist: String(input.artist || "").slice(0, 160), playing: Boolean(input.playing), upcoming }; room.updatedAt = Date.now(); room.revision += 1; await this.ctx.storage.put("room", room); this.broadcast(room); return json({ ok: true, revision: room.revision });
  }

  async search(request) {
    const room = await this.ctx.storage.get("room");
    if (!room) return json({ error: "Jam not found" }, 404);
    if (!room.active) return json({ error: "This Jam is closed" }, 410);
    const input = await request.json().catch(() => ({}));
    const query = String(input.query || "").trim().replace(/\s+/g, " ").slice(0, 120);
    if (query.length < 2) return json({ error: "Search is too short" }, 400);
    room.searches ||= [];
    const recent = room.searches.find(item => item.query.toLowerCase() === query.toLowerCase() && Date.now() - item.createdAt < 10 * 60 * 1000);
    if (recent) return json({ ok: true, search: recent }, 200);
    const search = { id: crypto.randomUUID(), query, status: "pending", createdAt: Date.now() };
    room.searches.push(search); room.searches = room.searches.slice(-30); room.updatedAt = Date.now(); room.revision += 1;
    await this.ctx.storage.put("room", room); this.broadcast(room);
    const endpoint = this.env.SEARCH_API;
    if (endpoint) {
      try {
        const response = await fetch(`${endpoint}?q=${encodeURIComponent(query)}`);
        if (response.ok) {
          const body = await response.json(); const additions = sanitizeCatalog(body.tracks);
          const known = new Set(room.catalog.map(item => item.id)); for (const item of additions) if (!known.has(item.id)) { room.catalog.push(item); known.add(item.id); }
          room.catalog = room.catalog.slice(-1200); search.status = "ready"; search.resultCount = additions.length; room.updatedAt = Date.now(); room.revision += 1;
          await this.ctx.storage.put("room", room); this.broadcast(room);
          return json({ ok: true, search, count: additions.length }, 200);
        }
      } catch { }
    }
    return json({ ok: true, search }, 202);
  }

  async searchResults(request, searchId) {
    const room = await this.ctx.storage.get("room");
    if (!room) return json({ error: "Jam not found" }, 404);
    if (!authorized(request, room.hostToken)) return json({ error: "Host access required" }, 401);
    const search = (room.searches || []).find(item => item.id === searchId);
    if (!search) return json({ error: "Search not found" }, 404);
    const input = await request.json().catch(() => ({})); const additions = sanitizeCatalog(input.catalog);
    const known = new Set(room.catalog.map(item => item.id)); for (const item of additions) if (!known.has(item.id)) { room.catalog.push(item); known.add(item.id); }
    room.catalog = room.catalog.slice(-1200); search.status = "ready"; search.resultCount = additions.length; room.updatedAt = Date.now(); room.revision += 1;
    await this.ctx.storage.put("room", room); this.broadcast(room);
    return json({ ok: true, count: additions.length, revision: room.revision });
  }

  async close(request) {
    const room = await this.ctx.storage.get("room");
    if (!room) return json({ error: "Jam not found" }, 404);
    if (!authorized(request, room.hostToken)) return json({ error: "Host access required" }, 401);
    room.active = false; room.updatedAt = Date.now(); room.revision += 1;
    await this.ctx.storage.put("room", room); this.broadcast(room);
    return json({ ok: true });
  }

  connect() {
    const pair = new WebSocketPair();
    this.ctx.acceptWebSocket(pair[1]);
    this.ctx.storage.get("room").then(room => { if (room) pair[1].send(JSON.stringify({ type: "state", room: publicRoom(room, false) })); });
    return new Response(null, { status: 101, webSocket: pair[0] });
  }

  async webSocketMessage() { }
  async webSocketClose(ws) { ws.close(1000, "Jam closed"); }
  async alarm() { const room = await this.ctx.storage.get("room"); if (room) { room.active = false; await this.ctx.storage.put("room", room); this.broadcast(room); } }
  broadcast(room) { const message = JSON.stringify({ type: "state", room: publicRoom(room, false) }); for (const ws of this.ctx.getWebSockets()) try { ws.send(message); } catch { } }
}

function sanitizeCatalog(value) {
  if (!Array.isArray(value)) return [];
  const seen = new Set(); const catalog = [];
  for (const raw of value.slice(0, 1200)) {
    const id = String(raw?.id || "").slice(0, 700);
    if (!id || seen.has(id)) continue; seen.add(id);
    catalog.push({ id, title: String(raw?.title || "Unknown title").slice(0, 160), artist: String(raw?.artist || "Unknown artist").slice(0, 160), album: String(raw?.album || "").slice(0, 160) });
  }
  return catalog;
}
function publicRoom(room, host) { const value = { active: room.active, createdAt: room.createdAt, updatedAt: room.updatedAt, catalog: room.catalog, queue: room.queue, searches: room.searches || [], playback: room.playback || { title: "", artist: "", playing: false, upcoming: [] }, revision: room.revision }; if (host) value.controls = room.controls || []; return value; }
function authorized(request, hostToken) { return request.headers.get("authorization") === `Bearer ${hostToken}`; }
