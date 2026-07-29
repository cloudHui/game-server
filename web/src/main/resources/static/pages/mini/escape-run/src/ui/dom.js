// Tiny DOM helpers for the overlay screens (menus, map, results, parent zone).
// No framework — just createElement sugar.

export function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "class") node.className = v;
    else if (k === "html") node.innerHTML = v;
    else if (k === "text") node.textContent = v;
    else if (k.startsWith("on") && typeof v === "function") {
      node.addEventListener(k.slice(2).toLowerCase(), v);
    } else if (v !== false && v != null) {
      node.setAttribute(k, v === true ? "" : v);
    }
  }
  for (const c of [].concat(children)) {
    if (c == null || c === false) continue;
    node.appendChild(typeof c === "string" ? document.createTextNode(c) : c);
  }
  return node;
}

const overlays = () => document.getElementById("overlays");

// Replace the current overlay screen with a new one.
export function showScreen(node) {
  const o = overlays();
  o.innerHTML = "";
  o.appendChild(node);
  return node;
}

export function clearOverlays() {
  overlays().innerHTML = "";
}

export function announce(text) {
  const live = document.getElementById("live");
  if (live) live.textContent = text;
}

export function starsRow(n, total = 3) {
  const wrap = el("div", { class: "stars", "aria-label": `${n} of ${total} stars` });
  for (let i = 0; i < total; i++) {
    wrap.appendChild(el("span", { class: i < n ? "star-on" : "star-off", text: "★" }));
  }
  return wrap;
}
