(() => {
  const $ = id => document.getElementById(id);
  const API = String(window.LOCALMUSIC_JAM_API || "").replace(/\/$/, "");
  const state = { room: null, guest: localStorage.getItem("jamGuest") || "", data: null, socket: null, searching: false, pendingAdds: new Set(), optimisticQueue: null };
  const params = new URLSearchParams(location.search);
  $("room").value = (params.get("room") || "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6);
  $("guest").value = state.guest;

  $("join").addEventListener("click", join);
  $("room").addEventListener("input", event => event.target.value = event.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6));
  $("guest").addEventListener("keydown", event => { if (event.key === "Enter") join(); });
  let searchTimer;
  $("search").addEventListener("input", () => { renderTracks(); clearTimeout(searchTimer); searchTimer = setTimeout(requestSearch, 550); });
  $("share").addEventListener("click", async () => {
    const url = `${location.origin}${location.pathname}?room=${state.room}`;
    if (navigator.share) await navigator.share({ title: "Join my LocalMusic Jam", url }).catch(() => {});
    else { await navigator.clipboard.writeText(url); toast("Join link copied"); }
  });
  for (const action of ["previous", "toggle", "next"]) $(action).addEventListener("click", () => sendControl(action));

  async function join() {
    const room = $("room").value.trim(), guest = $("guest").value.trim();
    $("setupError").textContent = "";
    if (!/^[A-Z0-9]{6}$/.test(room)) return fail("Enter the six-character room code.");
    if (!guest) return fail("Enter your name.");
    if (!API || API.includes("REPLACE_ME")) return fail("Jam service is not connected yet.");
    try {
      const response = await fetch(`${API}/api/rooms/${room}`); const body = await response.json();
      if (!response.ok) throw new Error(body.error || "Jam not found");
      state.room = room; state.guest = guest; state.data = body.room; localStorage.setItem("jamGuest", guest);
      history.replaceState({}, "", `?room=${room}`); $("setup").hidden = true; $("jam").hidden = false; $("roomLabel").textContent = `ROOM ${room}`;
      render(); connect();
    } catch (error) { fail(error.message || "Could not join this Jam."); }
  }

  function connect() {
    if (state.socket) state.socket.close();
    const ws = `${API.replace(/^http/, "ws")}/api/rooms/${state.room}/events`;
    state.socket = new WebSocket(ws);
    state.socket.onmessage = event => { const message = JSON.parse(event.data); if (message.room) { state.data = message.room; render(); } };
    state.socket.onclose = () => { if (state.data?.active) setTimeout(connect, 1800); };
  }

  function render() { renderTracks(); renderQueue(); $("liveDot").style.background = state.data.active ? "#52d68a" : "#777"; if (!state.data.active) toast("This Jam has ended"); }
  function renderTracks() {
    if (!state.data) return;
    const query = $("search").value.trim().toLowerCase();
    const matches = state.data.catalog.filter(t => !query || `${t.title} ${t.artist} ${t.album}`.toLowerCase().includes(query)).slice(0, 80);
    $("resultCount").textContent = matches.length ? matches.length : "";
    const pending = (state.data.searches || []).some(item => item.status === "pending" && item.query.toLowerCase() === query);
    $("searchStatus").textContent = query && (pending || state.searching) ? "Searching the live catalogue…" : query && matches.length ? `${matches.length} results` : "";
    $("results").replaceChildren(...(matches.length ? matches.map(trackNode) : [empty(query ? (pending || state.searching ? "Searching…" : "No results yet") : "Search for a song") ]));
  }
  async function requestSearch() {
    if (!state.data?.active) return; const query = $("search").value.trim(); if (query.length < 2) return;
    const local = state.data.catalog.filter(t => `${t.title} ${t.artist} ${t.album}`.toLowerCase().includes(query.toLowerCase()));
    if (local.length >= 8) return;
    state.searching = true; renderTracks();
    try { const response = await fetch(`${API}/api/rooms/${state.room}/search`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ query }) }); const body = await response.json(); if (!response.ok) throw new Error(body.error || "Search failed"); }
    catch (error) { toast(error.message); } finally { state.searching = false; renderTracks(); }
  }
  function trackNode(track) {
    const row = document.createElement("div"); row.className = "track";
    const art = document.createElement("span"); art.className = "art"; art.textContent = initials(track.artist);
    const meta = document.createElement("div"); meta.className = "meta";
    const title = document.createElement("strong"); title.textContent = track.title;
    const sub = document.createElement("span"); sub.textContent = [track.artist, track.album].filter(Boolean).join(" · "); meta.append(title, sub);
    const queued = (state.data.queue || []).some(item => item.trackId === track.id && item.guest === state.guest), pending = state.pendingAdds.has(track.id);
    const actions = document.createElement("div"); actions.className = "trackActions";
    const next = document.createElement("button"); next.className = "secondary"; next.textContent = "Next"; next.disabled = pending; next.addEventListener("click", () => addTrack(track, next, "next"));
    const add = document.createElement("button"); add.textContent = queued ? "Queued" : pending ? "Adding…" : "Add"; add.disabled = queued || pending; add.addEventListener("click", () => addTrack(track, add, "end"));
    actions.append(next, add); row.append(art, meta, actions); return row;
  }
  async function addTrack(track, button, placement) {
    if (!state.data.active) return toast("This Jam has ended");
    if (state.pendingAdds.has(track.id)) return; state.pendingAdds.add(track.id); button.disabled = true; button.textContent = "Adding…";
    try {
      const requestId = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${track.id}`;
      const response = await fetch(`${API}/api/rooms/${state.room}/queue`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ trackId: track.id, guest: state.guest, requestId, placement }) });
      const body = await response.json(); if (!response.ok) throw new Error(body.error || "Could not add song"); button.textContent = placement === "next" ? "Next" : "Queued"; toast(placement === "next" ? `${track.title} will play next` : `${track.title} is in the queue`);
    } catch (error) { toast(error.message); button.disabled = false; button.textContent = "Add"; } finally { state.pendingAdds.delete(track.id); }
  }
  function renderQueue() {
    const playback = state.data.playback || {}, queue = state.optimisticQueue || playback.upcoming || []; $("queueCount").textContent = queue.length;
    $("nowTitle").textContent = playback.title || "Waiting for music"; $("nowArtist").textContent = playback.artist || "The host controls playback"; $("toggle").textContent = playback.playing ? "Pause" : "Play"; $("nowPulse").classList.toggle("playing", Boolean(playback.playing));
    $("queueList").replaceChildren(...(queue.length ? queue.map((item, index) => queueNode(item, index, queue)) : [empty("Nothing queued yet") ]));
  }
  function queueNode(item, index, queue) {
    const row = document.createElement("div"); row.className = "queueItem"; row.dataset.index = index;
    const handle = document.createElement("button"); handle.className = "dragHandle"; handle.type = "button"; handle.setAttribute("aria-label", `Move ${item.title}`); handle.innerHTML = "<i></i><i></i><i></i>";
    const name = document.createElement("div"); name.className = "queueMeta"; const title = document.createElement("b"); title.textContent = item.title; const artist = document.createElement("span"); artist.textContent = item.artist; name.append(title, artist);
    const play = document.createElement("button"); play.className = "queuePlay"; play.type = "button"; play.textContent = "Play"; play.addEventListener("click", event => { event.stopPropagation(); sendControl("play", index); });
    row.addEventListener("click", () => sendControl("play", index)); bindDrag(handle, row, index, queue);
    row.append(handle, name, play); return row;
  }
  function bindDrag(handle, row, startIndex, queue) {
    let timer, active = false, targetIndex = startIndex;
    const finish = () => { clearTimeout(timer); if (!active) return; active = false; handle.releasePointerCapture?.(pointerId); document.body.classList.remove("sorting"); row.classList.remove("dragging"); if (targetIndex !== startIndex) sendControl("reorder", startIndex, 0, targetIndex); };
    let pointerId;
    handle.addEventListener("pointerdown", event => { event.stopPropagation(); pointerId = event.pointerId; timer = setTimeout(() => { active = true; handle.setPointerCapture?.(pointerId); row.classList.add("dragging"); document.body.classList.add("sorting"); navigator.vibrate?.(18); }, 260); });
    handle.addEventListener("pointermove", event => { if (!active) return; event.preventDefault(); const over = document.elementFromPoint(event.clientX, event.clientY)?.closest?.(".queueItem"); const next = Number(over?.dataset.index); if (!Number.isInteger(next) || next === targetIndex) return; targetIndex = next; const reordered = [...queue]; const [moved] = reordered.splice(startIndex, 1); reordered.splice(targetIndex, 0, moved); state.optimisticQueue = reordered; renderQueue(); });
    handle.addEventListener("pointerup", finish); handle.addEventListener("pointercancel", finish); handle.addEventListener("pointerleave", () => { if (!active) clearTimeout(timer); });
  }
  async function sendControl(action, index, direction, toIndex) {
    if (!state.data?.active) return; const button = $(action); if (button) button.disabled = true;
    try { const requestId = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${action}`; const response = await fetch(`${API}/api/rooms/${state.room}/control`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ action, guest: state.guest, requestId, index, direction, toIndex }) }); const body = await response.json(); if (!response.ok) throw new Error(body.error || "Control failed"); toast(action === "toggle" ? "Playback updated" : action === "next" ? "Skipping forward" : action === "previous" ? "Going back" : action === "play" ? "Playing selection" : "Queue order updated"); }
    catch (error) { state.optimisticQueue = null; renderQueue(); toast(error.message); } finally { if (action === "reorder") setTimeout(() => { state.optimisticQueue = null; renderQueue(); }, 1400); if (button) setTimeout(() => button.disabled = false, 500); }
  }
  function empty(text) { const node = document.createElement("div"); node.className = "empty"; node.textContent = text; return node; }
  function initials(value) { return String(value || "LM").split(/\s+/).slice(0, 2).map(x => x[0] || "").join("").toUpperCase(); }
  function fail(message) { $("setupError").textContent = message; }
  let toastTimer; function toast(message) { $("toast").textContent = message; $("toast").classList.add("show"); clearTimeout(toastTimer); toastTimer = setTimeout(() => $("toast").classList.remove("show"), 2200); }
  if ($("room").value && state.guest) join();
})();
