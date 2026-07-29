// Teaching visuals + explanations. Turns a wrong answer into a learning moment
// ("we do / you do") and powers optional hints and level-up micro-lessons.
// Renders small canvases with the same art helpers as the game (no assets).
import { roundRect, star as drawStar, shape as drawShape, solid as drawSolid, partition as drawPartition } from "../engine/renderer.js";

const NAME = { circle: "circle", square: "square", triangle: "triangle", star: "star", rect: "rectangle", oval: "oval" };
const CORNERS = { circle: 0, oval: 0, triangle: 3, square: 4, rect: 4, diamond: 4, star: 5 };

const FONT = '"Segoe UI Rounded","SF Pro Rounded",ui-rounded,"Nunito",system-ui,sans-serif';

function mini(w, h, draw) {
  const c = document.createElement("canvas");
  const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
  c.width = w * dpr;
  c.height = h * dpr;
  c.style.width = w + "px";
  c.style.height = h + "px";
  c.setAttribute("aria-hidden", "true");
  const ctx = c.getContext("2d");
  ctx.scale(dpr, dpr);
  draw(ctx, w, h);
  return c;
}

function dot(ctx, x, y, r, color) {
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
  ctx.fill();
}

// Returns { line, node } explaining the correct answer for a challenge.
export function teachContent(ch) {
  const p = ch.prompt || {};
  const answer = ch.options[ch.correctIndex];
  switch (p.type) {
    case "dots":
      return teachDots(p.count, p.grouped);
    case "expr":
      return teachExpr(p.text, answer);
    case "compare":
      return teachCompare(ch.options, p.mode, ch.correctIndex);
    case "bond":
      return teachBond(p.given, p.target);
    case "double":
      return teachDouble(p, answer);
    case "missing":
      return teachMissing(p);
    case "groups":
      return teachGroups(p, answer);
    case "share":
      return teachShare(p, answer);
    case "gtext":
      return teachGtext(p, answer);
    case "shapename":
      return teachShapeName(p, ch);
    case "showshape":
      return teachCorners(p, answer);
    case "silhouette":
      return teachSilhouette(p, ch);
    case "solidq":
      return teachSolid(ch, answer);
    case "composite":
      return teachComposite(p);
    case "fairshare":
      return teachFair(ch);
    case "onehalf":
    case "onequarter":
      return teachNamedFraction(p, ch);
    case "partof":
      return teachPartOf(p, answer);
    case "comparefrac":
      return teachComparePieces(ch);
    case "partshow":
      return teachPartShow(p);
    case "pattern":
      return teachPattern(p, answer);
    default:
      return { line: "Here's the answer.", node: null };
  }
}

// Optional gentle hint (does NOT give the answer outright).
export function hintLine(ch) {
  const p = ch.prompt || {};
  switch (p.type) {
    case "dots": return p.grouped ? "Each full row is ten — count tens first, then the extras." : "Point and count each dot: 1, 2, 3…";
    case "expr": return p.text.includes("+") ? "Start at the first number and count up." : "Start big and count back.";
    case "compare": return ch.prompt.mode === "bigger" ? "Bigger numbers are further along when you count." : "Smaller numbers come first when you count.";
    case "bond": return `How many more do you add to ${p.given} to reach ${p.target}?`;
    case "double": return p.op === "double" ? "Add the number to itself." : "Split it into two equal parts.";
    case "missing": return "What number makes both sides match?";
    case "groups": return `Count the bags: ${p.bags} groups of ${p.per}. Add them up.`;
    case "share": return `Deal the ${p.total} out onto ${p.plates} plates, one each, until they're gone.`;
    case "gtext": return p.text.includes("÷") ? "How many equal groups can you share it into?" : "Add the same number again and again.";
    case "shapename": return p.name === "equal" ? "Equal parts are exactly the same size." : "Look at the sides and corners.";
    case "showshape": return "Count where two edges meet — those are the corners.";
    case "silhouette": return "Match the outline — count its sides.";
    case "solidq": return "A ball is round all over; a box is all flat; a can is both.";
    case "composite": return "Look at the top piece and the bottom piece.";
    case "fairshare": return "Fair means both parts are exactly the same size.";
    case "onehalf": return "One half is one of two equal parts.";
    case "onequarter": return "One quarter is one of four equal parts.";
    case "partof": return p.denom === 2 ? "Share them into 2 equal groups — count one group." : "Share them into 4 equal groups — count one group.";
    case "comparefrac": return "Fewer cuts make bigger pieces.";
    case "partshow": return "Count the shaded parts out of the total parts.";
    case "pattern": return "Look for the part that repeats.";
    default: return "Take your time.";
  }
}

function teachDots(count, grouped) {
  // Match the in-game question layout (runner._drawDots): columns = ceil(sqrt),
  // or rows of ten when the question was shown grouped.
  const cols = grouped ? Math.min(10, count) : Math.min(5, Math.ceil(Math.sqrt(count)));
  const rows = Math.ceil(count / cols);
  const gap = grouped ? 28 : 40;
  const r0 = grouped ? 11 : 13;
  const font = grouped ? 11 : 13;
  const w = cols * gap + 20;
  const h = rows * gap + 20;
  const node = mini(w, h, (ctx) => {
    let n = 0;
    const sx = 10 + gap / 2;
    const sy = 10 + gap / 2;
    for (let r = 0; r < rows; r++)
      for (let c = 0; c < cols && n < count; c++, n++) {
        dot(ctx, sx + c * gap, sy + r * gap, r0, "#f59e0b");
        ctx.fillStyle = "#fff";
        ctx.font = `900 ${font}px ${FONT}`;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(String(n + 1), sx + c * gap, sy + r * gap + 1);
      }
  });
  if (grouped) {
    const tens = Math.floor(count / 10);
    const ones = count % 10;
    const tensWord = tens === 1 ? "1 ten" : `${tens} tens`;
    const line = ones
      ? `${tensWord} and ${ones} more makes ${count}.`
      : `${tensWord} makes ${count}.`;
    return { line, node };
  }
  return { line: `Count them one by one — there are ${count}.`, node };
}

function teachExpr(text, answer) {
  const m = text.match(/(\d+)\s*([+\u2212-])\s*(\d+)/);
  const a = +m[1], op = m[2], b = +m[3];
  const isAdd = op === "+";
  const total = isAdd ? a + b : a;
  const gap = 26;
  const w = Math.min(320, total * gap + 24);
  const node = mini(w, 54, (ctx) => {
    let x = 14;
    for (let i = 0; i < total; i++) {
      const removed = !isAdd && i >= a - b;
      dot(ctx, x, 27, 10, isAdd ? (i < a ? "#5aa9ff" : "#22c55e") : removed ? "#cbd5e1" : "#5aa9ff");
      if (removed) {
        ctx.strokeStyle = "#ef4444";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(x - 8, 19); ctx.lineTo(x + 8, 35);
        ctx.stroke();
      }
      x += gap;
    }
  });
  const line = isAdd
    ? `${a} and ${b} more makes ${answer}.`
    : `${a} take away ${b} leaves ${answer}.`;
  return { line, node };
}

function teachCompare(options, mode, correctIndex) {
  const max = Math.max(...options);
  const bw = 74, gap = 12, h = 90;
  const w = options.length * (bw + gap) + gap;
  const node = mini(w, h, (ctx) => {
    options.forEach((v, i) => {
      const x = gap + i * (bw + gap);
      const bh = 20 + (v / max) * 52;
      ctx.fillStyle = i === correctIndex ? "#22c55e" : "#94a3b8";
      roundRect(ctx, x, h - bh - 18, bw, bh, 8);
      ctx.fill();
      ctx.fillStyle = "#0f172a";
      ctx.font = `900 18px ${FONT}`;
      ctx.textAlign = "center";
      ctx.textBaseline = "alphabetic";
      ctx.fillText(String(v), x + bw / 2, h - 2);
    });
  });
  const target = options[correctIndex];
  return { line: `${target} is the ${mode === "bigger" ? "biggest" : "smallest"}.`, node };
}

function teachBond(given, target) {
  const answer = target - given;
  const cells = target <= 10 ? 10 : target;
  // Big targets (place-value parts) lay out in rows of ten so the tens show.
  const wide = cells > 20;
  const cols = wide ? 10 : Math.min(5, cells);
  const cell = wide ? 24 : 30;
  const pad = 8;
  const rows = Math.ceil(cells / cols);
  const w = cols * cell + pad * 2;
  const h = rows * cell + pad * 2;
  const node = mini(w, h, (ctx) => {
    for (let i = 0; i < cells; i++) {
      const cx = pad + (i % cols) * cell;
      const cy = pad + Math.floor(i / cols) * cell;
      ctx.strokeStyle = "#cbd5e1";
      ctx.lineWidth = 2;
      roundRect(ctx, cx + 2, cy + 2, cell - 4, cell - 4, 6);
      ctx.stroke();
      if (i < target) {
        dot(ctx, cx + cell / 2, cy + cell / 2, wide ? 7 : 9, i < given ? "#5aa9ff" : "#22c55e");
      }
    }
  });
  return { line: `${given} and ${answer} more makes ${target}.`, node };
}

// Doubles/halves: two equal rows make the "same twice" idea visible.
function teachDouble(p, answer) {
  const rowLen = p.op === "double" ? p.n : answer; // always ≤ 10
  const gap = 26;
  const w = rowLen * gap + 24;
  const node = mini(w, 80, (ctx) => {
    for (let i = 0; i < rowLen; i++) {
      dot(ctx, 14 + i * gap, 26, 10, "#5aa9ff");
      dot(ctx, 14 + i * gap, 54, 10, "#22c55e");
    }
  });
  const line =
    p.op === "double"
      ? `${p.n} and ${p.n} makes ${answer}.`
      : `${p.n} splits into ${answer} and ${answer}.`;
  return { line, node };
}

// Missing numbers: show the complete number story (part–part–whole for +,
// crossing out for −), with the hidden part highlighted.
function teachMissing(p) {
  if (p.op === "+") {
    const cells = p.result; // ≤ 12
    const cell = 30, pad = 8;
    const cols = Math.min(6, cells);
    const rows = Math.ceil(cells / cols);
    const node = mini(cols * cell + pad * 2, rows * cell + pad * 2, (ctx) => {
      for (let i = 0; i < cells; i++) {
        const cx = pad + (i % cols) * cell + cell / 2;
        const cy = pad + Math.floor(i / cols) * cell + cell / 2;
        const hidden = p.slot === "a" ? i < p.a : i >= p.a;
        dot(ctx, cx, cy, 10, hidden ? "#22c55e" : "#5aa9ff");
      }
    });
    return { line: `${p.a} and ${p.b} more makes ${p.result}.`, node };
  }
  // subtraction: a dots, the taken-away ones crossed out
  const gap = p.a > 10 ? 22 : 26;
  const w = p.a * gap + 24;
  const node = mini(w, 54, (ctx) => {
    for (let i = 0; i < p.a; i++) {
      const x = 14 + i * gap;
      const removed = i >= p.result;
      dot(ctx, x, 27, 9, removed ? "#cbd5e1" : "#5aa9ff");
      if (removed) {
        ctx.strokeStyle = "#ef4444";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(x - 7, 20);
        ctx.lineTo(x + 7, 34);
        ctx.stroke();
      }
    }
  });
  return { line: `${p.a} take away ${p.b} leaves ${p.result}.`, node };
}

// ---- Super Groups (× ÷) ----
function teachGroups(p, answer) {
  const bags = p.bags, per = p.per;
  const bagW = 44, bagH = 44, gap = 10;
  const w = bags * bagW + (bags - 1) * gap + 16;
  const node = mini(w, bagH + 16, (ctx) => {
    for (let b = 0; b < bags; b++) {
      const bx = 8 + b * (bagW + gap);
      ctx.fillStyle = "rgba(20,184,166,.2)";
      ctx.strokeStyle = "#14b8a6";
      ctx.lineWidth = 2;
      roundRect(ctx, bx, 8, bagW, bagH, 9);
      ctx.fill();
      ctx.stroke();
      const cols = per <= 3 ? 1 : 2;
      const rows = Math.ceil(per / cols);
      let n = 0;
      const dx0 = bx + bagW / 2 - ((cols - 1) * 13) / 2;
      const dy0 = 8 + bagH / 2 - ((rows - 1) * 13) / 2;
      for (let r = 0; r < rows; r++)
        for (let c = 0; c < cols && n < per; c++, n++) dot(ctx, dx0 + c * 13, dy0 + r * 13, 5, "#f59e0b");
    }
  });
  return { line: `${bags} groups of ${per} is ${p.bags} + ${p.per}s… that makes ${answer}.`, node };
}

function teachShare(p, answer) {
  const plates = p.plates;
  const pw = 52, gap = 12;
  const w = plates * pw + (plates - 1) * gap + 16;
  const node = mini(w, 64, (ctx) => {
    for (let i = 0; i < plates; i++) {
      const px = 8 + i * (pw + gap) + pw / 2;
      ctx.fillStyle = "#e2e8f0";
      ctx.strokeStyle = "#94a3b8";
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.ellipse(px, 42, pw / 2, pw / 4, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      // the equal share dealt onto each plate
      const cols = Math.min(3, answer);
      let n = 0;
      const dx0 = px - ((cols - 1) * 11) / 2;
      for (let k = 0; k < answer; k++, n++) {
        dot(ctx, dx0 + (k % cols) * 11, 20 + Math.floor(k / cols) * 11, 4.5, "#f59e0b");
      }
    }
  });
  return { line: `${p.total} shared onto ${plates} plates is ${answer} each.`, node };
}

function teachGtext(p, answer) {
  const m = p.text.match(/(\d+)\s*×\s*(\d+)/);
  if (m) {
    const a = +m[1], b = +m[2];
    return teachGroups({ bags: Math.min(a, 4), per: Math.min(b, 5), text: p.text }, a * b);
  }
  const d = p.text.match(/(\d+)\s*÷\s*(\d+)/);
  if (d) return teachShare({ total: +d[1], plates: +d[2] }, answer);
  return { line: `The answer is ${answer}.`, node: null };
}

// ---- Shape Shadows ----
function teachShapeName(p, ch) {
  if (p.name === "equal") {
    const t = ch.options[ch.correctIndex];
    const node = mini(90, 90, (ctx) =>
      drawPartition(ctx, t.style, 45, 45, 34, 2, [], { cuts: [0.5, 0.5], color: "#5aa9ff" })
    );
    return { line: "Both parts are exactly the same size — that's equal.", node };
  }
  const node = mini(90, 90, (ctx) => drawShape(ctx, p.name, 45, 45, 34, "#f43f5e"));
  return { line: `This is a ${NAME[p.name] || p.name}.`, node };
}

function teachCorners(p, answer) {
  const node = mini(110, 100, (ctx) => {
    drawShape(ctx, p.shape, 55, 50, 36, "#f43f5e");
    // mark the corners with little dots
    const pts = cornerPoints(p.shape, 55, 50, 36);
    ctx.fillStyle = "#0b1026";
    for (const [x, y] of pts) dot(ctx, x, y, 5, "#0b1026");
  });
  return { line: `A ${NAME[p.shape] || p.shape} has ${answer} corner${answer === 1 ? "" : "s"}.`, node };
}

function cornerPoints(kind, cx, cy, r) {
  if (kind === "triangle") return [[cx, cy - r], [cx + r, cy + r], [cx - r, cy + r]];
  if (kind === "square") return [[cx - r, cy - r], [cx + r, cy - r], [cx + r, cy + r], [cx - r, cy + r]];
  if (kind === "diamond") return [[cx, cy - r], [cx + r, cy], [cx, cy + r], [cx - r, cy]];
  if (kind === "rect") return [[cx - r * 1.35, cy - r * 0.75], [cx + r * 1.35, cy - r * 0.75], [cx + r * 1.35, cy + r * 0.75], [cx - r * 1.35, cy + r * 0.75]];
  return [];
}

function teachSilhouette(p, ch) {
  const node = mini(160, 90, (ctx) => {
    drawShape(ctx, p.shape, 45, 45, 32, "#0b1026", p.rot); // the shadow
    drawShape(ctx, p.shape, 115, 45, 32, "#f43f5e"); // the real shape
  });
  return { line: `The shadow is a ${NAME[p.shape] || p.shape}.`, node };
}

function teachSolid(ch, answer) {
  const node = mini(90, 90, (ctx) => drawSolid(ctx, answer.solid, 45, 45, 30, "#5aa9ff"));
  const why = answer.solid === "ball"
    ? "A ball is round all over, so it rolls every way."
    : answer.solid === "box"
    ? "A box has only flat sides, so it stacks and never rolls."
    : "A can has flat ends and a round side, so it can roll and stack.";
  return { line: why, node };
}

function teachComposite(p) {
  const node = mini(120, 120, (ctx) => {
    drawShape(ctx, p.parts[1], 60, 34, 24, "#f59e0b");
    drawShape(ctx, p.parts[0], 60, 80, 28, "#5aa9ff");
  });
  return { line: `A ${NAME[p.parts[0]] || p.parts[0]} and a ${NAME[p.parts[1]] || p.parts[1]} make it.`, node };
}

function teachFair(ch) {
  const t = ch.options[ch.correctIndex];
  const node = mini(100, 100, (ctx) =>
    drawPartition(ctx, t.style, 50, 50, 36, 2, [], { cuts: [0.5, 0.5], color: "#f97316" })
  );
  return { line: "A fair share cuts it into two equal parts.", node };
}

function teachNamedFraction(p, ch) {
  const t = ch.options[ch.correctIndex];
  const denom = t.cuts.length;
  const word = denom === 2 ? "half" : "quarter";
  const node = mini(100, 100, (ctx) =>
    drawPartition(ctx, t.style, 50, 50, 36, denom, t.shaded, { cuts: t.cuts, color: "#f97316" })
  );
  return { line: `The shaded piece is one ${word} — 1 of ${denom} equal parts.`, node };
}

function teachPartOf(p, answer) {
  const per = answer;
  const node = mini(160, 90, (ctx) => {
    const perCols = Math.min(per, 4);
    for (let s = 0; s < p.denom; s++) {
      const bx = 10 + s * (150 / p.denom);
      ctx.strokeStyle = "#94a3b8";
      ctx.setLineDash([4, 4]);
      roundRect(ctx, bx, 20, 150 / p.denom - 8, 50, 8);
      ctx.stroke();
      ctx.setLineDash([]);
      for (let i = 0; i < per; i++) {
        const c = i % perCols, r = Math.floor(i / perCols);
        dot(ctx, bx + 10 + c * 14, 34 + r * 16, 5, "#f59e0b");
      }
    }
  });
  const word = p.denom === 2 ? "half" : "a quarter";
  return { line: `Share ${p.total} into ${p.denom} equal groups — ${word} is ${per}.`, node };
}

function teachComparePieces(ch) {
  const t = ch.options[ch.correctIndex];
  const node = mini(180, 90, (ctx) => {
    drawPartition(ctx, "pie", 40, 45, 30, 2, [true, false], { cuts: [0.5, 0.5], color: "#f97316" });
    drawPartition(ctx, "pie", 140, 45, 30, 4, [true, false, false, false], { cuts: [0.25, 0.25, 0.25, 0.25], color: "#f97316" });
  });
  return { line: "Cut into fewer pieces, each piece is bigger. One half beats one quarter.", node };
}

function teachPartShow(p) {
  const cuts = Array.from({ length: p.denom }, () => 1 / p.denom);
  const shaded = Array.from({ length: p.denom }, (_, i) => i < p.on);
  const label = p.denom === 2 ? "½" : p.on === 1 ? "¼" : "¾";
  const node = mini(100, 100, (ctx) =>
    drawPartition(ctx, p.style, 50, 50, 36, p.denom, shaded, { cuts, color: "#f97316" })
  );
  return { line: `${p.on} of ${p.denom} parts are shaded — that's ${label}.`, node };
}

function teachPattern(p, answer) {
  const seq = p.sequence;
  const gap = seq.length > 7 ? 34 : 44; // long two-attribute rows still fit the card
  const w = seq.length * gap + 16;
  const node = mini(w, 52, (ctx) => {
    const sx = 8 + gap / 2;
    seq.forEach((item, i) => {
      const x = sx + i * gap;
      if (item == null) {
        if (p.kind === "shape") drawShape(ctx, answer.shape, x, 26, 15, answer.color);
        else text(ctx, String(answer), x, 26, "#22c55e");
      } else if (p.kind === "shape") {
        drawShape(ctx, item.shape, x, 26, 15, item.color);
      } else {
        text(ctx, String(item), x, 26, "#0f172a");
      }
    });
  });
  let line = "The pattern repeats — see what comes next.";
  if (p.kind === "number") {
    const step = seq[1] - seq[0];
    line =
      step >= 0
        ? `Each step adds ${step}. Next is ${answer}.`
        : `Each step takes away ${-step}. Next is ${answer}.`;
  }
  return { line, node };
}

function text(ctx, t, x, y, color) {
  ctx.fillStyle = color;
  ctx.font = `900 22px ${FONT}`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(t, x, y);
}
