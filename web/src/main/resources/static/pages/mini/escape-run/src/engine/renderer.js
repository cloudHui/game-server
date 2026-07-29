// Canvas renderer: handles device-pixel-ratio scaling, resize, and provides
// procedural drawing helpers so the whole game ships with zero image assets.

export class Renderer {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.W = 0;
    this.H = 0;
    this.dpr = 1;
    this._resize();
    window.addEventListener("resize", () => this._resize());
    window.addEventListener("orientationchange", () => this._resize());
  }

  _resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
    const w = this.canvas.clientWidth || window.innerWidth;
    const h = this.canvas.clientHeight || window.innerHeight;
    this.dpr = dpr;
    this.W = w;
    this.H = h;
    this.canvas.width = Math.round(w * dpr);
    this.canvas.height = Math.round(h * dpr);
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  clear(color) {
    const { ctx } = this;
    if (color) {
      ctx.fillStyle = color;
      ctx.fillRect(0, 0, this.W, this.H);
    } else {
      ctx.clearRect(0, 0, this.W, this.H);
    }
  }
}

export function roundRect(ctx, x, y, w, h, r) {
  const rr = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + rr, y);
  ctx.arcTo(x + w, y, x + w, y + h, rr);
  ctx.arcTo(x + w, y + h, x, y + h, rr);
  ctx.arcTo(x, y + h, x, y, rr);
  ctx.arcTo(x, y, x + w, y, rr);
  ctx.closePath();
}

export function star(ctx, cx, cy, spikes, outer, inner) {
  let rot = (Math.PI / 2) * 3;
  const step = Math.PI / spikes;
  ctx.beginPath();
  ctx.moveTo(cx, cy - outer);
  for (let i = 0; i < spikes; i++) {
    ctx.lineTo(cx + Math.cos(rot) * outer, cy + Math.sin(rot) * outer);
    rot += step;
    ctx.lineTo(cx + Math.cos(rot) * inner, cy + Math.sin(rot) * inner);
    rot += step;
  }
  ctx.lineTo(cx, cy - outer);
  ctx.closePath();
}

// A collectible spark — a gold five-point star. `outer` is the star radius.
export function spark(ctx, cx, cy, outer = 13) {
  ctx.save();
  ctx.fillStyle = "#ffd23f";
  ctx.strokeStyle = "rgba(0,0,0,.15)";
  ctx.lineWidth = 2;
  star(ctx, cx, cy, 5, outer, outer * (6 / 13));
  ctx.fill();
  ctx.stroke();
  ctx.restore();
}

// A traffic cone (obstacle). `s` scales it; s = 1 matches the in-game size.
export function cone(ctx, cx, cy, s = 1) {
  ctx.save();
  ctx.fillStyle = "#f97316";
  ctx.beginPath();
  ctx.moveTo(cx, cy - 20 * s);
  ctx.lineTo(cx + 16 * s, cy + 18 * s);
  ctx.lineTo(cx - 16 * s, cy + 18 * s);
  ctx.closePath();
  ctx.fill();
  ctx.fillStyle = "#fff";
  ctx.fillRect(cx - 12 * s, cy + 2 * s, 24 * s, 5 * s);
  ctx.restore();
}

// Draw one of the pattern-token shapes centred at (cx,cy) with given radius.
// `rot` (radians) optionally rotates non-round shapes.
export function shape(ctx, kind, cx, cy, r, color, rot = 0) {
  ctx.save();
  ctx.translate(cx, cy);
  if (rot) ctx.rotate(rot);
  ctx.fillStyle = color;
  ctx.strokeStyle = "rgba(0,0,0,.18)";
  ctx.lineWidth = 2;
  switch (kind) {
    case "circle":
      ctx.beginPath();
      ctx.arc(0, 0, r, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      break;
    case "square":
      roundRect(ctx, -r, -r, r * 2, r * 2, r * 0.3);
      ctx.fill();
      ctx.stroke();
      break;
    case "rect":
      roundRect(ctx, -r * 1.35, -r * 0.75, r * 2.7, r * 1.5, r * 0.25);
      ctx.fill();
      ctx.stroke();
      break;
    case "oval":
      ctx.beginPath();
      ctx.ellipse(0, 0, r * 1.3, r * 0.85, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      break;
    case "triangle":
      ctx.beginPath();
      ctx.moveTo(0, -r);
      ctx.lineTo(r, r);
      ctx.lineTo(-r, r);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
      break;
    case "star":
      star(ctx, 0, 0, 5, r, r * 0.45);
      ctx.fill();
      ctx.stroke();
      break;
    case "diamond":
      ctx.beginPath();
      ctx.moveTo(0, -r);
      ctx.lineTo(r, 0);
      ctx.lineTo(0, r);
      ctx.lineTo(-r, 0);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
      break;
  }
  ctx.restore();
}

// A simple 3D solid glyph (for shape-sense levels). kind: "ball" | "box" | "can".
export function solid(ctx, kind, cx, cy, r, color = "#5aa9ff") {
  ctx.save();
  ctx.translate(cx, cy);
  ctx.strokeStyle = "rgba(0,0,0,.22)";
  ctx.lineWidth = 2;
  const dark = shade(color, 45);
  if (kind === "ball") {
    const g = ctx.createRadialGradient(-r * 0.3, -r * 0.3, r * 0.2, 0, 0, r);
    g.addColorStop(0, tint(color, 60));
    g.addColorStop(1, color);
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(0, 0, r, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  } else if (kind === "box") {
    const s = r * 1.3, d = r * 0.5;
    // front face
    ctx.fillStyle = color;
    ctx.fillRect(-s / 2, -s / 2 + d / 2, s, s);
    ctx.strokeRect(-s / 2, -s / 2 + d / 2, s, s);
    // top face
    ctx.fillStyle = tint(color, 40);
    ctx.beginPath();
    ctx.moveTo(-s / 2, -s / 2 + d / 2);
    ctx.lineTo(-s / 2 + d, -s / 2 - d / 2);
    ctx.lineTo(s / 2 + d, -s / 2 - d / 2);
    ctx.lineTo(s / 2, -s / 2 + d / 2);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    // side face
    ctx.fillStyle = dark;
    ctx.beginPath();
    ctx.moveTo(s / 2, -s / 2 + d / 2);
    ctx.lineTo(s / 2 + d, -s / 2 - d / 2);
    ctx.lineTo(s / 2 + d, s / 2 - d / 2);
    ctx.lineTo(s / 2, s / 2 + d / 2);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
  } else if (kind === "can") {
    const w = r * 1.1, h = r * 1.6, ry = r * 0.32;
    // body
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.moveTo(-w, -h / 2);
    ctx.lineTo(-w, h / 2);
    ctx.ellipse(0, h / 2, w, ry, 0, Math.PI, 0, true);
    ctx.lineTo(w, -h / 2);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    // top ellipse
    ctx.fillStyle = tint(color, 45);
    ctx.beginPath();
    ctx.ellipse(0, -h / 2, w, ry, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  }
  ctx.restore();
}

// A partitioned whole (pie/bar/roti) for fractions & equal-parts questions.
// style: "pie" | "bar" | "roti". `parts` = number of equal slices; `shaded` =
// array of booleans marking filled slices. `equal` false draws an unequal cut.
export function partition(ctx, style, cx, cy, r, parts, shaded = [], opts = {}) {
  ctx.save();
  ctx.translate(cx, cy);
  const fill = opts.color || "#f59e0b";
  const empty = "#fef3c7";
  ctx.lineWidth = opts.line || 3;
  ctx.strokeStyle = "rgba(120,53,15,.55)";
  const cuts = opts.cuts || equalCuts(parts);
  if (style === "bar") {
    const w = r * 2.4, h = r * 1.2;
    let x = -w / 2;
    for (let i = 0; i < cuts.length; i++) {
      const seg = w * cuts[i];
      ctx.fillStyle = shaded[i] ? fill : empty;
      ctx.fillRect(x, -h / 2, seg, h);
      ctx.strokeRect(x, -h / 2, seg, h);
      x += seg;
    }
  } else {
    // pie / roti (roti = pie with a warmer rim)
    let a0 = -Math.PI / 2;
    for (let i = 0; i < cuts.length; i++) {
      const a1 = a0 + cuts[i] * Math.PI * 2;
      ctx.fillStyle = shaded[i] ? fill : empty;
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.arc(0, 0, r, a0, a1);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
      a0 = a1;
    }
    ctx.strokeStyle = style === "roti" ? "#d97706" : "rgba(120,53,15,.55)";
    ctx.lineWidth = style === "roti" ? 4 : ctx.lineWidth;
    ctx.beginPath();
    ctx.arc(0, 0, r, 0, Math.PI * 2);
    ctx.stroke();
  }
  ctx.restore();
}

function equalCuts(parts) {
  return Array.from({ length: parts }, () => 1 / parts);
}

function tint(hex, amt) {
  return shade(hex, -amt);
}
function shade(hex, amt) {
  const n = parseInt(hex.slice(1), 16);
  const r = Math.max(0, Math.min(255, ((n >> 16) & 255) - amt));
  const g = Math.max(0, Math.min(255, ((n >> 8) & 255) - amt));
  const b = Math.max(0, Math.min(255, (n & 255) - amt));
  return `rgb(${r},${g},${b})`;
}

// A friendly little car, drawn top-down-ish, centred at (cx, cy).
export function car(ctx, cx, cy, w, h, body) {
  ctx.save();
  ctx.translate(cx, cy);
  // shadow
  ctx.fillStyle = "rgba(0,0,0,.25)";
  ctx.beginPath();
  ctx.ellipse(0, h * 0.5, w * 0.55, h * 0.22, 0, 0, Math.PI * 2);
  ctx.fill();
  // body
  ctx.fillStyle = body;
  roundRect(ctx, -w / 2, -h / 2, w, h, w * 0.28);
  ctx.fill();
  // windshield
  ctx.fillStyle = "rgba(255,255,255,.85)";
  roundRect(ctx, -w * 0.32, -h * 0.34, w * 0.64, h * 0.26, w * 0.16);
  ctx.fill();
  // headlights
  ctx.fillStyle = "#fff59d";
  ctx.beginPath();
  ctx.arc(-w * 0.28, -h * 0.44, w * 0.08, 0, Math.PI * 2);
  ctx.arc(w * 0.28, -h * 0.44, w * 0.08, 0, Math.PI * 2);
  ctx.fill();
  // wheels
  ctx.fillStyle = "#1f2937";
  roundRect(ctx, -w * 0.56, -h * 0.28, w * 0.14, h * 0.28, 4);
  roundRect(ctx, w * 0.42, -h * 0.28, w * 0.14, h * 0.28, 4);
  roundRect(ctx, -w * 0.56, h * 0.06, w * 0.14, h * 0.28, 4);
  roundRect(ctx, w * 0.42, h * 0.06, w * 0.14, h * 0.28, 4);
  ctx.fill();
  ctx.restore();
}
