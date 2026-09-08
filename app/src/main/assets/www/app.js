let cards = [];
let currentId = null;
let photoIndex = 0;
const MAX_PHOTOS = 4;
const PHOTO_BASE = "https://appassets.androidplatform.net/photo/";

const el = (id) => document.getElementById(id);
const native = () => window.KartochkaNative;

function parse(s) {
  const o = typeof s === "string" ? JSON.parse(s) : s;
  if (o && o.error) throw new Error(o.error);
  return o;
}

function setStatus(msg, ok = true) {
  const s = el("status");
  s.textContent = msg || "";
  s.style.color = ok ? "#22c55e" : "#fca5a5";
}

function cardPhotos(c) {
  if (!c) return [];
  return Array.isArray(c.photos) ? c.photos : [];
}

function currentCard() {
  return cards.find((x) => x.id === currentId) || null;
}

function escapeHtml(s) {
  return String(s)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function renderList() {
  const ul = el("cardList");
  ul.innerHTML = "";
  cards.forEach((c) => {
    const n = cardPhotos(c).length;
    const li = document.createElement("li");
    const btn = document.createElement("button");
    btn.type = "button";
    if (c.id === currentId) btn.classList.add("active");
    btn.innerHTML = `<span class="name">${escapeHtml(c.name || "Без названия")}</span><span class="meta">${n ? "📷 " + n + "/4" : "без фото"}</span>`;
    btn.onclick = () => selectCard(c.id);
    li.appendChild(btn);
    ul.appendChild(li);
  });
}

function showEditor(show) {
  el("emptyState").classList.toggle("hidden", show);
  el("editor").classList.toggle("hidden", !show);
}

function renderGallery(photos) {
  const img = el("photoPreview");
  const ph = el("photoPlaceholder");
  const counter = el("photoCounter");
  const prev = el("btnPrev");
  const next = el("btnNext");
  const addBtn = el("btnAddPhoto");
  const removeBtn = el("btnRemovePhoto");

  if (!photos.length) {
    img.removeAttribute("src");
    img.classList.add("hidden");
    ph.classList.remove("hidden");
    counter.textContent = "0 / 4";
    prev.classList.remove("visible");
    next.classList.remove("visible");
    removeBtn.classList.add("hidden");
    addBtn.classList.remove("hidden");
    return;
  }
  if (photoIndex >= photos.length) photoIndex = photos.length - 1;
  img.src = PHOTO_BASE + encodeURIComponent(photos[photoIndex]) + "?t=" + Date.now();
  img.classList.remove("hidden");
  ph.classList.add("hidden");
  counter.textContent = photoIndex + 1 + " / " + photos.length + " · макс. 4";
  const multi = photos.length > 1;
  prev.classList.toggle("visible", multi);
  next.classList.toggle("visible", multi);
  removeBtn.classList.remove("hidden");
  addBtn.classList.toggle("hidden", photos.length >= MAX_PHOTOS);
}

function fillEditor(c) {
  el("fieldName").value = c?.name || "";
  el("fieldDesc").value = c?.description || "";
  renderGallery(cardPhotos(c));
}

function refresh() {
  cards = parse(native().listCards());
  renderList();
  if (currentId) {
    const c = cards.find((x) => x.id === currentId);
    if (c) {
      showEditor(true);
      fillEditor(c);
    } else {
      currentId = null;
      showEditor(false);
    }
  }
}

function selectCard(id) {
  currentId = id;
  photoIndex = 0;
  const c = parse(native().getCard(id));
  const idx = cards.findIndex((x) => x.id === id);
  if (idx >= 0) cards[idx] = c;
  else cards.push(c);
  showEditor(true);
  fillEditor(c);
  renderList();
  setStatus("");
}


function closeCard() {
  currentId = null;
  photoIndex = 0;
  showEditor(false);
  renderList();
  setStatus("");
}

window.__fotoCloseCard = () => {
  if (!currentId) return false;
  closeCard();
  return true;
};

el("btnClose").onclick = () => closeCard();

el("btnPrev").onclick = () => {
  const photos = cardPhotos(currentCard());
  if (photos.length < 2) return;
  photoIndex = (photoIndex - 1 + photos.length) % photos.length;
  renderGallery(photos);
};
el("btnNext").onclick = () => {
  const photos = cardPhotos(currentCard());
  if (photos.length < 2) return;
  photoIndex = (photoIndex + 1) % photos.length;
  renderGallery(photos);
};

(() => {
  const wrap = el("photoWrap");
  let x0 = null;
  wrap.addEventListener("touchstart", (e) => { x0 = e.changedTouches[0].clientX; }, { passive: true });
  wrap.addEventListener("touchend", (e) => {
    if (x0 == null) return;
    const dx = e.changedTouches[0].clientX - x0;
    x0 = null;
    if (Math.abs(dx) < 40) return;
    if (dx < 0) el("btnNext").click();
    else el("btnPrev").click();
  }, { passive: true });
})();

el("btnNew").onclick = () => {
  const c = parse(native().createCard("Новая карта", ""));
  currentId = c.id;
  photoIndex = 0;
  refresh();
  setStatus("Создано");
};

el("btnSave").onclick = () => {
  if (!currentId) return;
  try {
    parse(native().updateCard(currentId, el("fieldName").value, el("fieldDesc").value));
    refresh();
    closeCard();
  } catch (e) {
    setStatus(String(e.message || e), false);
  }
};

el("btnDelete").onclick = () => {
  if (!currentId) return;
  if (!confirm("Удалить эту карту?")) return;
  parse(native().deleteCard(currentId));
  currentId = null;
  showEditor(false);
  refresh();
  setStatus("Удалено");
};

el("btnRemovePhoto").onclick = () => {
  if (!currentId) return;
  try {
    const c = parse(native().removePhoto(currentId, photoIndex));
    const idx = cards.findIndex((x) => x.id === currentId);
    if (idx >= 0) cards[idx] = c;
    if (photoIndex >= cardPhotos(c).length) photoIndex = Math.max(0, cardPhotos(c).length - 1);
    fillEditor(c);
    renderList();
    setStatus("Фото убрано");
  } catch (e) {
    setStatus(String(e.message || e), false);
  }
};

el("btnAddPhoto").onclick = () => {
  if (!currentId) return;
  if (cardPhotos(currentCard()).length >= MAX_PHOTOS) {
    setStatus("Уже 4 фото", false);
    return;
  }
  if (!native() || typeof native().pickPhoto !== "function") {
    setStatus("Нет native pickPhoto", false);
    return;
  }
  native().pickPhoto(currentId);
  setStatus("Выбери источник…");
};

window.onNativePhotoAdded = (card) => {
  try {
    if (typeof card === "string") card = JSON.parse(card);
    if (!card || !card.id) return;
    currentId = card.id;
    const idx = cards.findIndex((x) => x.id === card.id);
    if (idx >= 0) cards[idx] = card;
    else cards.push(card);
    photoIndex = Math.max(0, cardPhotos(card).length - 1);
    fillEditor(card);
    renderList();
    setStatus("Фото " + cardPhotos(card).length + "/4");
  } catch (e) {
    setStatus(String(e.message || e), false);
  }
};

function boot() {
  if (!native()) {
    setStatus("Открой в приложении Фото карта", false);
    return;
  }
  try { refresh(); } catch (e) { setStatus(String(e.message || e), false); }
}
boot();
