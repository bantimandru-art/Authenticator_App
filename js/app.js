// UI for the TOTP test bench. Depends on js/totp.js and qrcode.js being loaded first.
{
const PERIOD = 30, DIGITS = 6, STORE = "totp.keys", $ = id => document.getElementById(id);
const els = {
  secret: $("secret"), keyname: $("keyname"), qr: $("qr"), code: $("code"), form: $("form"), toast: $("toast"),
  issuer: $("issuer"), account: $("account"), uri: $("uri"), left: $("left"), prog: $("prog"), peek: $("peek"),
  copy: $("copy"), auto: $("auto"), newkey: $("newkey"), list: $("list"), clear: $("clear"),
};
const circumference = 2 * Math.PI * 11;
els.prog.style.strokeDasharray = circumference;

let keys = [], activeId = null, qr = null, peeking = false, toastTimer = 0;

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORE) || "{}");
    keys = Array.isArray(saved.keys) ? saved.keys : [];
    activeId = saved.activeId ?? null;
    els.auto.checked = saved.auto ?? true;
  } catch (e) { keys = []; }
}

function save() {
  try { localStorage.setItem(STORE, JSON.stringify({ keys, activeId, auto: els.auto.checked })); }
  catch (e) { /* storage unavailable: keys live until the page closes */ }
}

const active = () => keys.find(k => k.id === activeId);
const latest = () => keys[keys.length - 1];
const escapeHtml = t => t.replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const timeText = ms => new Date(ms).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

function createKey() {
  const bytes = new Uint8Array(20);
  crypto.getRandomValues(bytes);
  const prev = latest();
  const key = {
    id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
    n: prev ? prev.n + 1 : 1,
    secret: base32Encode(bytes),
    issuer: prev ? prev.issuer : "Test Bench",
    account: prev ? prev.account : "you@example.com",
    created: Date.now(),
    result: null,
  };
  keys.push(key);
  select(key.id);
}

// Each key gets its number in the account name so the entries are easy to tell apart in the app.
function buildUri(key) {
  const issuer = key.issuer.trim(), account = `${key.account.trim() || "user"} (Key ${key.n})`;
  const label = encodeURIComponent(issuer ? `${issuer}:${account}` : account);
  const params = new URLSearchParams({ secret: key.secret, algorithm: "SHA1", digits: DIGITS, period: PERIOD });
  if (issuer) params.set("issuer", issuer);
  return `otpauth://totp/${label}?${params.toString().replace(/\+/g, "%20")}`;
}

function renderQr(key) {
  const uri = buildUri(key);
  els.uri.textContent = uri;
  if (typeof QRCode === "undefined") {
    els.qr.innerHTML = '<p class="qrerr">The QR library couldn\'t load. Use the secret key above.</p>';
    return;
  }
  if (!qr) qr = new QRCode(els.qr, { text: uri, width: 512, height: 512, correctLevel: QRCode.CorrectLevel.M });
  else { qr.clear(); qr.makeCode(uri); }
}

function renderList() {
  if (!keys.length) { els.list.innerHTML = '<li class="empty">No keys yet.</li>'; return; }
  els.list.innerHTML = keys.slice().reverse().map(k => {
    const status = !k.result ? '<span class="status none">Not tested</span>'
      : `<span class="status ${k.result.ok ? "ok" : "bad"}">${k.result.ok ? "Passed" : "Failed"} ${timeText(k.result.at)}</span>`;
    const isActive = k.id === activeId;
    return `<li class="${isActive ? "active" : ""}">
      <div class="key-info">
        <div class="key-top"><b>Key ${k.n}</b>${status}</div>
        <code>${escapeHtml(k.secret)}</code>
      </div>
      ${isActive ? '<span class="badge">Testing</span>' : `<button class="btn ghost small" data-use="${k.id}" type="button">Test</button>`}
      <button class="remove" data-del="${k.id}" type="button" aria-label="Remove Key ${k.n}">&times;</button>
    </li>`;
  }).join("");
}

function select(id) {
  activeId = id;
  const key = active();
  els.secret.textContent = key.secret;
  els.keyname.textContent = `Key ${key.n}`;
  els.issuer.value = key.issuer;
  els.account.value = key.account;
  els.code.value = "";
  renderQr(key);
  renderList();
  tick();
  save();
}

function tick() {
  const key = active();
  if (!key) return;
  const now = Date.now() / 1000;
  const left = PERIOD - Math.floor(now % PERIOD);
  els.left.textContent = `${left}s left`;
  els.prog.style.strokeDashoffset = circumference * (1 - left / PERIOD);
  els.peek.innerHTML = peeking ? `Expected: <b>${totp(base32Decode(key.secret), now)}</b>` : "Show expected code";
}

function showToast(text, ok) {
  clearTimeout(toastTimer);
  els.toast.textContent = text;
  els.toast.className = `toast show ${ok ? "ok" : "bad"}`;
  toastTimer = setTimeout(() => { els.toast.className = "toast"; }, 2600);
}

els.code.addEventListener("input", () => { els.code.value = els.code.value.replace(/\D/g, "").slice(0, DIGITS); });

els.form.addEventListener("submit", e => {
  e.preventDefault();
  const key = active(), entered = els.code.value.trim();
  if (entered.length !== DIGITS) { showToast("Enter all 6 digits", false); return; }

  const secret = base32Decode(key.secret), now = Date.now() / 1000;
  const ok = entered === totp(secret, now) || entered === totp(secret, now - PERIOD);
  key.result = { ok, at: Date.now() };
  showToast(ok ? `Key ${key.n} validated successfully` : `Key ${key.n} failed: code doesn't match`, ok);

  // Only move on when testing the newest key; re-testing an old key keeps it selected.
  if (els.auto.checked && key === latest()) createKey();
  else select(key.id);
  els.code.focus();
});

els.list.addEventListener("click", e => {
  const use = e.target.closest("[data-use]"), del = e.target.closest("[data-del]");
  if (use) { select(use.dataset.use); els.code.focus(); }
  if (del) {
    keys = keys.filter(k => k.id !== del.dataset.del);
    if (!keys.length) createKey();
    else if (!active()) select(latest().id);
    else { renderList(); save(); }
  }
});

els.newkey.addEventListener("click", () => { createKey(); els.code.focus(); });
els.auto.addEventListener("change", save);
els.clear.addEventListener("click", () => { keys = []; createKey(); });
els.peek.addEventListener("click", () => { peeking = !peeking; tick(); });

els.copy.addEventListener("click", async () => {
  try { await navigator.clipboard.writeText(active().secret); els.copy.textContent = "Copied"; }
  catch (e) { els.copy.textContent = "Select it"; }
  setTimeout(() => { els.copy.textContent = "Copy"; }, 1500);
});

for (const input of [els.issuer, els.account]) {
  input.addEventListener("input", () => {
    const key = active();
    key[input.id] = input.value;
    renderQr(key);
    save();
  });
}

load();
if (active()) select(activeId);
else if (keys.length) select(latest().id);
else createKey();
setInterval(tick, 1000);
}
