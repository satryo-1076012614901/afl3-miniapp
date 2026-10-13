"use strict";
const $ = (id) => document.getElementById(id);
const state = { step: 1, tv: false, tvError: "", lastSync: null, competitions: [], selectedId: null, participantsLoaded: false, participants: [], matches: [], busy: false, competitionEdit: null, participantEdit: null, logs: [] };
const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const selected = () => state.competitions.find((c) => c.id === state.selectedId);
const participantName = (id) => id == null ? "Menunggu peserta" : state.participants.find((p) => p.id === id)?.name ?? `Peserta #${id}`;
const badge = (status) => `<span class="badge ${escapeHtml(status)}">${escapeHtml(status)}</span>`;
const disabled = (condition) => condition || state.busy ? "disabled" : "";
const competitionPath = (id = state.selectedId) => `api/competitions/${id}`;
function feedback(message, error = false) {
  $("feedback").hidden = !message;
  $("feedback").textContent = message;
  $("feedback").className = error ? "error" : "";
}
const logTime = new Intl.DateTimeFormat("id-ID", { timeZone: "Asia/Jakarta", day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23" });
function renderLogs() {
  $("requests").innerHTML = state.logs.map((log, index) => `<details class="request ${log.error ? "error" : ""}" ${index === 0 ? "open" : ""}><summary><time datetime="${log.timestamp}">${escapeHtml(logTime.format(new Date(log.timestamp)))} WIB</time> · ${escapeHtml(log.method)} /${escapeHtml(log.path)} → ${escapeHtml(log.status)} · ${log.duration} ms</summary><pre>${escapeHtml(JSON.stringify({ timestamp: log.timestamp, request: log.body ?? null, response: log.response }, null, 2))}</pre></details>`).join("") || '<p class="empty">Belum ada request.</p>';
}
async function api(path, method = "GET", body) {
  const started = performance.now();
  const log = { timestamp: new Date().toISOString(), path, method, body, status: "NETWORK ERROR", response: null, error: true };
  try {
    // Relative URLs preserve /develop/ and /production/ when served behind Traefik.
    const response = await fetch(new URL(path, document.baseURI), {
      method, headers: body === undefined ? {} : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000), cache: "no-store",
    });
    log.status = response.status;
    const text = await response.text();
    try { log.response = text ? JSON.parse(text) : null; } catch { log.response = text; }
    log.error = !response.ok;
    if (!response.ok) throw new Error(`${response.status} · ${log.response?.code ?? "API_ERROR"}: ${log.response?.message ?? response.statusText}`);
    return log.response;
  } catch (error) {
    if (log.status === "NETWORK ERROR") {
      log.response = error.name === "TimeoutError" ? "Request melewati batas waktu 15 detik." : "Tidak dapat menghubungi API. Periksa backend dan koneksi.";
      throw new Error(log.response);
    }
    throw error;
  } finally {
    log.duration = Math.round(performance.now() - started);
    state.logs.unshift(log);
    state.logs = state.logs.slice(0, 30);
    renderLogs();
  }
}
function clearCompetitionData() {
  state.participants = []; state.matches = []; state.participantsLoaded = false; state.lastSync = null;
}
async function load(step = state.step) {
  // Fetch only the active stage; publish related bracket data as one snapshot.
  if (step === 1) {
    const competitions = await api("api/competitions");
    const id = competitions.some((c) => c.id === state.selectedId) ? state.selectedId : competitions[0]?.id ?? null;
    if (id !== state.selectedId) clearCompetitionData();
    Object.assign(state, { competitions, selectedId: id });
    return;
  }
  const id = state.selectedId;
  if (id == null) return;
  const competition = step === 3 ? await api(competitionPath(id)) : selected();
  const participants = step === 2 || !state.participantsLoaded ? await api(`${competitionPath(id)}/participants`) : state.participants;
  const matches = step === 3 ? await api(`${competitionPath(id)}/matches`) : state.matches;
  state.competitions = state.competitions.map((c) => c.id === id ? competition : c);
  Object.assign(state, { participants, participantsLoaded: true, matches, lastSync: new Date().toISOString() });
}
async function run(action, success = "") {
  if (state.busy) return;
  state.busy = true;
  feedback("");
  render();
  try { await action(); feedback(success); }
  catch (error) { feedback(error.message, true); }
  finally { state.busy = false; render(); focusNameField(); }
}
async function mutate(path, method, body) {
  await api(path, method, body);
  clearCompetitionEdit();
  clearParticipantEdit();
  try { await load(); }
  catch (error) {
    state.competitions = []; clearCompetitionData(); state.selectedId = null;
    throw new Error(`Perubahan berhasil, tetapi refresh gagal. Klik Refresh untuk memuat ulang. ${error.message}`);
  }
}
function clearCompetitionEdit() {
  state.competitionEdit = null; $("competition-form").reset();
}
function clearParticipantEdit() {
  state.participantEdit = null; $("participant-form").reset();
}
function canVisitStep(step) {
  if (step === 1) return true;
  if (!selected()) return false;
  return step === 2 || (step === 3 && (selected().status !== "OPEN" || !state.participantsLoaded || state.matches.length > 0 || (state.participants.length >= 2 && state.participants.length <= 16)));
}
function goToStep(step) {
  if (state.busy || !canVisitStep(step)) return;
  run(async () => {
    if (step !== state.step) {
      await load(step);
      if (!canVisitStep(step)) throw new Error("Tambahkan 2–16 peserta sebelum membuka bracket.");
    }
    state.step = step;
  });
}
function focusNameField() {
  const form = state.step === 1 ? $("competition-form") : state.step === 2 ? $("participant-form") : null;
  if (form && !form.querySelector("fieldset").disabled) form.elements.name.focus();
}
function renderWizard() {
  if (!canVisitStep(state.step)) state.step = selected() ? 2 : 1;
  for (const button of document.querySelectorAll("[data-step]")) {
    const step = Number(button.dataset.step);
    button.disabled = state.busy || !canVisitStep(step);
    if (step === state.step) button.setAttribute("aria-current", "step");
    else button.removeAttribute("aria-current");
    $("step-" + step).hidden = step !== state.step;
  }
  $("competition-detail").hidden = !selected();
  $("previous-step").hidden = state.step === 1;
  $("previous-step").disabled = state.busy;
  $("next-step").hidden = state.step === 3;
  $("next-step").disabled = state.busy || !canVisitStep(state.step + 1);
  $("next-step").textContent = state.step === 1 ? "Lanjut ke peserta →" : "Lanjut ke bracket →";
  $("step-progress").textContent = `Langkah 0${state.step} dari 03`;
}
function render() {
  renderWizard();
  const c = selected(), open = c?.status === "OPEN";
  $("refresh").disabled = state.busy;
  $("competition-count").textContent = state.competitions.length;
  $("competitions").innerHTML = state.competitions.map((item) => `<button class="competition-card ${item.id === state.selectedId ? "active" : ""}" data-select="${item.id}" aria-pressed="${item.id === state.selectedId}" ${disabled(false)}><strong>${escapeHtml(item.name)}</strong>${badge(item.status)}<small>#${item.id} · ${item.participantType === "TEAM" ? "Tim" : "Individual"}</small></button>`).join("") || '<p class="empty">Belum ada kompetisi. Buat yang pertama di bawah.</p>';
  $("competition-form").querySelector("fieldset").disabled = state.busy;
  $("competition-form-title").textContent = state.competitionEdit ? "Edit kompetisi" : "Buat kompetisi";
  $("competition-save").textContent = state.competitionEdit ? "Simpan perubahan" : "Buat kompetisi";
  $("competition-cancel").hidden = !state.competitionEdit;
  $("competition-form").elements.participantType.disabled = state.busy || (!!state.competitionEdit && (!state.participantsLoaded || state.participants.length > 0));
  $("selected-content").hidden = !c;
  if (!c) { $("competition-detail").innerHTML = '<p class="empty">Pilih atau buat kompetisi untuk mulai.</p>'; return; }
  const final = state.matches.find((m) => m.nextMatchId == null && m.status === "COMPLETED");
  $("competition-detail").innerHTML = `<div class="detail-title"><div><p class="eyebrow">COMPETITION #${c.id}</p><h2>${escapeHtml(c.name)}</h2>${competitionStateMachine(c.status)}</div><div class="actions"><button class="secondary" data-action="edit-competition" ${disabled(!open)}>Edit</button><button class="danger" data-action="delete-competition" ${disabled(!open)}>Hapus</button></div></div>${final ? `<p class="champion">★ Juara: ${escapeHtml(participantName(final.winnerId))}</p>` : ""}`;
  $("participant-count").textContent = `${state.participants.length} / 16`;
  $("participant-hint").textContent = open ? "Tambahkan 2–16 peserta sebelum generate bracket. Urutan pendaftaran menentukan seeding." : "Peserta terkunci selama bracket berlangsung. Reset bracket untuk mengedit kembali.";
  $("participants").innerHTML = state.participants.map((p, i) => `<div class="participant-row"><div><strong>${i + 1}. ${escapeHtml(p.name)}</strong><p>#${p.id}${p.members?.length ? ` · ${escapeHtml(p.members.map((m) => m.name).join(", "))}` : ""}</p></div><div class="actions"><button class="secondary" data-edit-participant="${p.id}" ${disabled(!open)}>Edit</button><button class="danger" data-delete-participant="${p.id}" ${disabled(!open)}>Hapus</button></div></div>`).join("") || '<p class="empty">Belum ada peserta.</p>';
  $("participant-form").querySelector("fieldset").disabled = state.busy || !open;
  $("participant-form-title").textContent = state.participantEdit ? "Edit peserta" : "Tambah peserta";
  $("participant-save").textContent = state.participantEdit ? "Simpan perubahan" : "Tambah peserta";
  $("participant-cancel").hidden = !state.participantEdit;
  $("members-label").hidden = c.participantType !== "TEAM";
  $("generate").disabled = state.busy || !open || state.participants.length < 2 || state.participants.length > 16;
  $("view-bracket").disabled = state.busy || !state.matches.length || !document.fullscreenEnabled;
  $("reset").disabled = state.busy || c.status !== "IN_MATCH";
  $("bracket-hint").textContent = !state.matches.length ? "Bracket belum dibuat. Peserta dengan bye otomatis lolos." : "Pilih pemenang secara eksplisit; skor opsional. Pemenang otomatis masuk ke ronde berikutnya.";
  const rounds = [...new Set(state.matches.map((m) => m.round))].sort((a, b) => a - b);
  if (!state.tv) {
    $("bracket").innerHTML = rounds.map((round) => `<section class="simple-round"><h3>${round === rounds.at(-1) ? "FINAL" : `RONDE ${round}`}</h3><div class="simple-matches">${state.matches.filter((m) => m.round === round).map(renderMatch).join("")}</div></section>`).join("");
    return;
  }
  // Lay out complete branches from the final backwards. Averaging positions in
  // registration order can place a one-source BYE on top of a two-source match.
  const positions = new Map();
  const sourcesByMatch = new Map(state.matches.map((m) => [m.id, []]));
  for (const m of state.matches) sourcesByMatch.get(m.nextMatchId)?.push(m);
  const cardHeight = 160;
  const laneHeight = 200;
  let leafIndex = 0;
  function placeBranch(match) {
    const sources = sourcesByMatch.get(match.id).toSorted((a, b) => a.matchNumber - b.matchNumber);
    const centers = sources.map(placeBranch);
    const y = centers.length ? (centers[0] + centers.at(-1)) / 2 : leafIndex++ * laneHeight;
    positions.set(match.id, y);
    return y;
  }
  const roots = state.matches.filter((m) => m.nextMatchId == null).toSorted((a, b) => a.matchNumber - b.matchNumber);
  roots.forEach(placeBranch);
  const height = Math.max(cardHeight, ...[...positions.values()].map((y) => y + cardHeight));
  const columnWidth = 300;
  const width = rounds.length * columnWidth - 40;
  const lines = state.matches.filter((m) => m.nextMatchId != null).map((m) => {
    const next = state.matches.find((n) => n.id === m.nextMatchId);
    if (!next) return "";
    const x1 = rounds.indexOf(m.round) * columnWidth + 260;
    const x2 = rounds.indexOf(next.round) * columnWidth;
    const y1 = positions.get(m.id) + cardHeight / 2, y2 = positions.get(next.id) + cardHeight / 2;
    return `<path d="M ${x1} ${y1} H ${x1 + 20} V ${y2} H ${x2}" />`;
  }).join("");
  $("bracket").innerHTML = rounds.length ? `<div class="bracket-diagram" style="width:${width}px;height:${height + 32}px" data-width="${width}" data-height="${height + 32}"><svg class="bracket-lines" width="${width}" height="${height}" aria-hidden="true">${lines}</svg>${rounds.map((round, index) => `<section class="round" style="left:${index * columnWidth}px;height:${height}px"><h3>${round === rounds.at(-1) ? "FINAL" : `RONDE ${round}`}</h3>${state.matches.filter((m) => m.round === round).map((m) => `<div class="match-position" style="top:${positions.get(m.id) + 32}px">${renderMatch(m)}</div>`).join("")}</section>`).join("")}</div>` : "";
  if (state.tv) $("bracket").insertAdjacentHTML("beforeend", `<p class="tv-sync" role="status">${escapeHtml(state.tvError || `Diperbarui ${state.lastSync ? logTime.format(new Date(state.lastSync)) : "—"} WIB · Auto-refresh 5 detik · Esc untuk kembali`)}</p>`);
  fitFullscreenBracket();
}
function competitionStateMachine(status) {
  const statuses = ["OPEN", "IN_MATCH", "COMPLETED"];
  const current = statuses.indexOf(status);
  return `<ol class="competition-states" aria-label="Status kompetisi">${statuses.map((value, index) => `<li class="${index === current ? "current" : index < current ? "passed" : "upcoming"}" ${index === current ? 'aria-current="step"' : ""}><span class="state-dot" aria-hidden="true">${index < current ? "✓" : index + 1}</span><span>${value.replace("_", " ")}${index === current ? '<small>Status saat ini</small>' : ""}</span></li>`).join("")}</ol>`;
}
function renderMatch(m) {
  const next = state.matches.find((item) => item.id === m.nextMatchId);
  const sources = state.matches.filter((source) => source.nextMatchId === m.id);
  const isBye = (m.round === 1 && [m.participant1Id, m.participant2Id].filter((id) => id != null).length === 1) || (m.round > 1 && sources.length === 1);
  const slots = isBye ? [m.participant2Id != null && m.participant1Id == null ? 2 : 1] : [1, 2];
  const canUndo = !isBye && m.status === "COMPLETED" && m.participant1Id != null && m.participant2Id != null && next?.status !== "COMPLETED";
  return `<article class="match ${isBye ? "bye-match" : ""}" data-match="${m.id}"><div class="match-header"><span>Match ${m.matchNumber} · #${m.id}</span>${badge(m.status)}</div>${slots.map((slot) => `<div class="match-player ${m.winnerId != null && m.winnerId === m[`participant${slot}Id`] ? "winner" : ""}"><span>${escapeHtml(participantName(m[`participant${slot}Id`]))}</span><span>${m[`score${slot}`] ?? "—"}</span></div>`).join("")}${isBye ? `<p class="bye-note">BYE · ${m.status === "COMPLETED" ? "Lolos otomatis" : "Menunggu pemenang ronde sebelumnya"}</p>` : ""}${m.status === "READY" && !state.tv ? `<details class="result-entry"><summary>Isi hasil</summary><form data-result="${m.id}"><fieldset ${disabled(false)}><label>Pemenang<select name="winnerId" required><option value="">Pilih pemenang</option>${[m.participant1Id, m.participant2Id].filter((id) => id != null).map((id) => `<option value="${id}">${escapeHtml(participantName(id))}</option>`).join("")}</select></label><div class="scores"><label>Skor 1<input name="score1" type="number" min="0" max="2147483647" step="1" placeholder="Opsional"></label><label>Skor 2<input name="score2" type="number" min="0" max="2147483647" step="1" placeholder="Opsional"></label></div><button type="submit">Simpan hasil</button></fieldset></form></details>` : ""}${canUndo && !state.tv ? `<button class="secondary" data-undo="${m.id}" ${disabled(false)}>Undo hasil</button>` : ""}</article>`;
}
$("competition-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const body = { name: form.elements.name.value.trim(), participantType: form.elements.participantType.value };
  if (!body.name) return feedback("Nama kompetisi wajib diisi.", true);
  const id = state.competitionEdit;
  run(async () => {
    if (id) await mutate(competitionPath(id), "PUT", body);
    else {
      const created = await api("api/competitions", "POST", body);
      clearCompetitionData();
      state.selectedId = created.id;
      clearCompetitionEdit(); clearParticipantEdit();
      try { await load(); } catch (error) { state.competitions = []; state.selectedId = null; throw new Error(`Kompetisi berhasil dibuat, tetapi refresh gagal. ${error.message}`); }
    }
  }, "Kompetisi disimpan.");
});
$("participant-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const body = { name: form.elements.name.value.trim() };
  if (!body.name) return feedback("Nama peserta wajib diisi.", true);
  if (selected()?.participantType === "TEAM") body.members = form.elements.members.value.split(/\r?\n/).map((name) => name.trim()).filter(Boolean).map((name) => ({ name }));
  const id = state.participantEdit;
  run(() => mutate(`${competitionPath()}/participants${id ? `/${id}` : ""}`, id ? "PUT" : "POST", body), "Peserta disimpan.");
});
document.addEventListener("submit", (event) => {
  const id = event.target.dataset.result;
  if (!id) return;
  event.preventDefault();
  const data = new FormData(event.target);
  const body = { winnerId: Number(data.get("winnerId")) };
  for (const key of ["score1", "score2"]) if (data.get(key) !== "") body[key] = Number(data.get(key));
  run(() => mutate(`${competitionPath()}/matches/${id}/result`, "POST", body), "Hasil disimpan. Bracket diperbarui.");
});
document.addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (!button || button.disabled || state.busy) return;
  const d = button.dataset;
  if (d.step) goToStep(Number(d.step));
  if (d.select) {
    clearCompetitionEdit(); clearParticipantEdit();
    const id = Number(d.select);
    if (id !== state.selectedId) clearCompetitionData();
    state.selectedId = id;
    render(); focusNameField();
  }
  if (d.action === "edit-competition") run(async () => {
    // The type selector needs participant existence only when editing the competition.
    if (!state.participantsLoaded) await load(2);
    const c = selected(); state.competitionEdit = c.id; state.step = 1;
    $("competition-form").elements.name.value = c.name;
    $("competition-form").elements.participantType.value = c.participantType;
  });
  if (d.action === "delete-competition" && confirm(`Hapus kompetisi "${selected().name}" dan seluruh pesertanya?`)) run(() => mutate(competitionPath(), "DELETE"), "Kompetisi dihapus.");
  if (d.editParticipant) {
    const p = state.participants.find((item) => item.id === Number(d.editParticipant)); state.participantEdit = p.id;
    $("participant-form").elements.name.value = p.name;
    $("participant-form").elements.members.value = p.members?.map((m) => m.name).join("\n") ?? "";
    render(); $("participant-form").elements.name.focus();
  }
  if (d.deleteParticipant && confirm("Hapus peserta ini dari kompetisi?")) run(() => mutate(`${competitionPath()}/participants/${d.deleteParticipant}`, "DELETE"), "Peserta dihapus.");
  if (d.undo && confirm("Batalkan hasil pertandingan ini? Slot pemenang di ronde berikutnya akan dikosongkan.")) run(() => mutate(`${competitionPath()}/matches/${d.undo}/undo`, "POST"), "Hasil dibatalkan.");
});
$("competition-cancel").onclick = () => { clearCompetitionEdit(); render(); };
$("participant-cancel").onclick = () => { clearParticipantEdit(); render(); };
$("generate").onclick = () => run(() => mutate(`${competitionPath()}/matches`, "POST"), "Bracket dibuat. Kompetisi dimulai.");
$("reset").onclick = () => {
  if (confirm("Hapus seluruh bracket dan hasil pertandingan? Peserta tetap tersimpan; kompetisi kembali OPEN.")) run(() => mutate(`${competitionPath()}/matches`, "DELETE"), "Bracket direset.");
};
$("previous-step").onclick = () => goToStep(state.step - 1);
$("next-step").onclick = () => goToStep(state.step + 1);
function fitFullscreenBracket() {
  const diagram = $("bracket").querySelector(".bracket-diagram");
  if (!state.tv || !diagram) return;
  const scale = Math.min(($("bracket").clientWidth - 64) / Number(diagram.dataset.width), ($("bracket").clientHeight - 64) / Number(diagram.dataset.height));
  diagram.style.transform = `translate(-50%, -50%) scale(${scale})`;
}
$("view-bracket").onclick = async () => {
  try { await $("bracket").requestFullscreen(); }
  catch { feedback("Browser tidak dapat membuka fullscreen. Coba buka halaman di Chrome atau browser yang mendukung fullscreen.", true); }
};
let tvRefreshTimer;
document.addEventListener("fullscreenchange", () => {
  state.tv = document.fullscreenElement === $("bracket");
  clearInterval(tvRefreshTimer);
  state.tvError = "";
  if (state.tv) tvRefreshTimer = setInterval(() => {
    if (state.busy || document.hidden) return;
    run(async () => {
      try { await load(3); state.tvError = ""; }
      catch (error) { state.tvError = `${error.message} · Menampilkan data terakhir; mencoba lagi otomatis.`; throw error; }
    });
  }, 5000);
  render();
});
window.addEventListener("resize", fitFullscreenBracket);
$("clear-log").onclick = () => { state.logs = []; renderLogs(); };
async function refresh() {
  clearCompetitionEdit(); clearParticipantEdit();
  try {
    const health = await api("system/status");
    $("health").textContent = `API ${health.status} · ${health.environment}`;
  } catch (error) {
    $("health").textContent = "API tidak sehat / tidak terhubung";
    feedback(error.message, true);
  }
  try { await load(); }
  catch (error) { state.competitions = []; state.selectedId = null; clearCompetitionData(); throw error; }
}
$("refresh").onclick = () => run(refresh);
run(refresh);
