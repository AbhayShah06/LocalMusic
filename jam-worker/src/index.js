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
  const allowed = origin === env.SITE_ORIGIN || origin === "https://abhayshah06.github.io" || origin.startsWith("http://localhost:");
  return {
    "access-control-allow-origin": allowed ? origin : env.SITE_ORIGIN,
    "access-control-allow-methods": "GET,POST,OPTIONS",
    "access-control-allow-headers": "content-type,authorization",
    "vary": "Origin"
  };
}

export class JamRoom extends DurableObject {
  constructor(ctx, env) { super(ctx, env); this.ctx = ctx; }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/create" && request.method === "POST") return this.create(request);
    if (url.pathname === "/" && request.method === "GET") return this.snapshotResponse();
    if (url.pathname === "/queue" && request.method === "POST") return this.add(request);
    if (url.pathname === "/close" && request.method === "POST") return this.close(request);
    if (url.pathname === "/events" && request.headers.get("upgrade") === "websocket") return this.connect();
    return json({ error: "Not found" }, 404);
  }

  async create(request) {
    if (await this.ctx.storage.get("room")) return json({ error: "Room exists" }, 409);
    const input = await request.json().catch(() => ({}));
    const catalog = sanitizeCatalog(input.catalog);
    const hostToken = token();
    const room = { active: true, createdAt: Date.now(), updatedAt: Date.now(), hostToken, catalog, queue: [], revision: 1 };
    await this.ctx.storage.put("room", room);
    await this.ctx.storage.setAlarm(Date.now() + 12 * 60 * 60 * 1000);
    return json({ hostToken, room: publicRoom(room) }, 201);
  }

  async snapshotResponse() {
    const room = await this.ctx.storage.get("room");
    return room ? json({ room: publicRoom(room) }) : json({ error: "Jam not found" }, 404);
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
    const entry = { id: crypto.randomUUID(), trackId: track.id, title: track.title, artist: track.artist, guest, addedAt: Date.now() };
    room.queue.push(entry); room.updatedAt = Date.now(); room.revision += 1;
    await this.ctx.storage.put("room", room); this.broadcast(room);
    return json({ ok: true, entry, revision: room.revision }, 201);
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
    this.ctx.storage.get("room").then(room => { if (room) pair[1].send(JSON.stringify({ type: "state", room: publicRoom(room) })); });
    return new Response(null, { status: 101, webSocket: pair[0] });
  }

  async webSocketMessage() { }
  async webSocketClose(ws) { ws.close(1000, "Jam closed"); }
  async alarm() { const room = await this.ctx.storage.get("room"); if (room) { room.active = false; await this.ctx.storage.put("room", room); this.broadcast(room); } }
  broadcast(room) { const message = JSON.stringify({ type: "state", room: publicRoom(room) }); for (const ws of this.ctx.getWebSockets()) try { ws.send(message); } catch { } }
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
function publicRoom(room) { return { active: room.active, createdAt: room.createdAt, updatedAt: room.updatedAt, catalog: room.catalog, queue: room.queue, revision: room.revision }; }
function authorized(request, hostToken) { return request.headers.get("authorization") === `Bearer ${hostToken}`; }
