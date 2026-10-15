(() => {
  const $ = id => document.getElementById(id);
  const API = String(window.LOCALMUSIC_JAM_API || "").replace(/\/$/, "");
  const state = { room: null, guest: localStorage.getItem("jamGuest") || "", data: null, socket: null };
  const params = new URLSearchParams(location.search);
  $("room").value = (params.get("room") || "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6);
  $("guest").value = state.guest;

  $("join").addEventListener("click", join);
  $("room").addEventListener("input", event => event.target.value = event.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6));
  $("guest").addEventListener("keydown", event => { if (event.key === "Enter") join(); });
  $("search").addEventListener("input", renderTracks);
  $("share").addEventListener("click", async () => {
    const url = `${location.origin}${location.pathname}?room=${state.room}`;
    if (navigator.share) await navigator.share({ title: "Join my LocalMusic Jam", url }).catch(() => {});
    else { await navigator.clipboard.writeText(url); toast("Join link copied"); }
  });

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
    $("resultCount").textContent = matches.length;
    $("results").replaceChildren(...(matches.length ? matches.map(trackNode) : [empty("No songs found") ]));
  }
  function trackNode(track) {
    const row = document.createElement("div"); row.className = "track";
    const art = document.createElement("span"); art.className = "art"; art.textContent = initials(track.artist);
    const meta = document.createElement("div"); meta.className = "meta";
    const title = document.createElement("strong"); title.textContent = track.title;
    const sub = document.createElement("span"); sub.textContent = [track.artist, track.album].filter(Boolean).join(" · "); meta.append(title, sub);
    const add = document.createElement("button"); add.textContent = "Add"; add.addEventListener("click", () => addTrack(track, add));
    row.append(art, meta, add); return row;
  }
  async function addTrack(track, button) {
    if (!state.data.active) return toast("This Jam has ended");
    button.disabled = true; button.textContent = "Added";
    try {
      const response = await fetch(`${API}/api/rooms/${state.room}/queue`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ trackId: track.id, guest: state.guest }) });
      const body = await response.json(); if (!response.ok) throw new Error(body.error || "Could not add song"); toast(`${track.title} added`);
    } catch (error) { toast(error.message); button.textContent = "Add"; } finally { setTimeout(() => { button.disabled = false; button.textContent = "Add"; }, 900); }
  }
  function renderQueue() {
    const queue = state.data.queue || []; $("queueCount").textContent = queue.length;
    $("queueList").replaceChildren(...(queue.length ? queue.slice(-12).map(item => { const row = document.createElement("div"); row.className = "queueItem"; const name = document.createElement("b"); name.textContent = `${item.title} · ${item.artist}`; const guest = document.createElement("small"); guest.textContent = item.guest; row.append(name, guest); return row; }) : [empty("The queue is open") ]));
  }
  function empty(text) { const node = document.createElement("div"); node.className = "empty"; node.textContent = text; return node; }
  function initials(value) { return String(value || "LM").split(/\s+/).slice(0, 2).map(x => x[0] || "").join("").toUpperCase(); }
  function fail(message) { $("setupError").textContent = message; }
  let toastTimer; function toast(message) { $("toast").textContent = message; $("toast").classList.add("show"); clearTimeout(toastTimer); toastTimer = setTimeout(() => $("toast").classList.remove("show"), 2200); }
  if ($("room").value && state.guest) join();
})();
