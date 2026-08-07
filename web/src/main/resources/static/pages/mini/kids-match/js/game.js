'use strict';

/* ═══════════════════════════════════════════════════════════════════
   KIDS MATCH GAME — Pipe/Flow puzzle  (v3: drag-to-draw)

   Rules:
   • Board has emoji tiles (fixed) + empty cells
   • DRAG from one tile to a matching tile to draw a line
   • Line travels only through EMPTY cells; cannot cross other lines
   • Images + lines stay after connecting; win = all pairs connected
   ═══════════════════════════════════════════════════════════════════ */

// ─── Constants ───────────────────────────────────────────────────────
const ICONS = [
  '🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼',
  '🐨','🐯','🦁','🐸','🐵','🦄','🦋','🐢',
  '🍎','🍊','🍋','🍇','🍓','🫐','🍉','🍌',
  '⭐','🌙','🌈','🎈','🎁','🎀',
];
const PIPE_COLORS = [
  '#ff6b6b','#4ecdc4','#ffe66d','#a55eea',
  '#26de81','#fd9644','#45aaf2','#ff8cc8',
  '#b8e994','#ffeaa7','#74b9ff','#00cec9',
];
const CELL_PX = { 4:80, 5:72, 6:64, 7:56, 8:50 };
const FONT_PX = { 4:38, 5:34, 6:30, 7:26, 8:22 };
const DIFF = {
  easy:   { size: 4, pairs: 3 },
  medium: { size: 6, pairs: 5 },
  hard:   { size: 8, pairs: 8 },
};

// ─── State ───────────────────────────────────────────────────────────
let diff          = 'easy';
let boardSize     = 4;
let tileGrid      = [];    // [r][c] = iconString | null  (fixed tiles)
let occupancy     = [];    // [r][c] = connIdx   | null  (pipe cells)
let connections   = [];    // [{icon, pos1, pos2, color, path:[{r,c}]|null}]

// Drag state
let isDragging    = false;
let dragSource    = null;  // {r,c} — tile where drag started
let dragPath      = [];    // [{r,c}] — cells traced so far
let dragConnIdx   = -1;    // which connection is being drawn

// Game meta
let completedPairs = 0;
let moveCount      = 0;
let levelNum       = 1;
let isComplete     = false;
let hintData       = null; // {path, color} temporary hint

// ─── DOM ────────────────────────────────────────────────────────────
const boardEl    = document.getElementById('board');
const canvasEl   = document.getElementById('line-canvas');
const ctx        = canvasEl.getContext('2d');
const levelEl    = document.getElementById('level-num');
const pairsEl    = document.getElementById('pairs-display');
const coverEl    = document.getElementById('cover-display');
const movesEl    = document.getElementById('moves-count');
const winOverlay = document.getElementById('win-overlay');
const winLevelEl = document.getElementById('win-level');
const winMovesEl = document.getElementById('win-moves');
const winStarsEl = document.getElementById('win-stars');
const toastEl    = document.getElementById('toast');

// ─── Audio ───────────────────────────────────────────────────────────
let audioCtx = null;
function getAC() {
  if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  if (audioCtx.state === 'suspended') audioCtx.resume();
  return audioCtx;
}
function tone(freq, dur, type = 'sine', vol = 0.25, delay = 0) {
  try {
    const ac = getAC(), osc = ac.createOscillator(), g = ac.createGain();
    osc.connect(g); g.connect(ac.destination);
    osc.frequency.value = freq; osc.type = type;
    const t = ac.currentTime + delay;
    g.gain.setValueAtTime(vol, t);
    g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    osc.start(t); osc.stop(t + dur);
  } catch (_) {}
}
const sfxStart   = () => tone(700, 0.07, 'sine', 0.2);
const sfxConnect = () => { tone(523,0.1,'sine',0.28); tone(784,0.15,'sine',0.28,0.12); };
const sfxClear   = () => tone(350, 0.1, 'triangle', 0.18);
const sfxCancel  = () => tone(200, 0.12, 'square', 0.18);
const sfxWin     = () => [523,659,784,1047,1319].forEach((f,i) => tone(f,0.25,'sine',0.22,i*0.13));

// ─── Grid helpers ────────────────────────────────────────────────────
const mkGrid = (size, val = null) =>
  Array.from({ length: size }, () => Array(size).fill(val));

function allPositions(size) {
  const pos = [];
  for (let r = 0; r < size; r++)
    for (let c = 0; c < size; c++) pos.push({ r, c });
  return pos;
}
function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

const randInt = n => Math.floor(Math.random() * n);

// ─── Hamiltonian path over the whole grid ────────────────────────────
// Randomized DFS with Warnsdorff heuristic (prefer cells with fewer onward
// moves). Visits every cell exactly once. Falls back to a serpentine path.
function hamiltonianPath(S) {
  const total   = S * S;
  const visited = mkGrid(S, false);
  const path    = [];
  let   budget  = 200000; // safety cap on DFS steps

  const DIRS = [[-1,0],[1,0],[0,-1],[0,1]];

  function onwardDegree(r, c) {
    let d = 0;
    for (const [dr, dc] of DIRS) {
      const nr = r + dr, nc = c + dc;
      if (nr >= 0 && nr < S && nc >= 0 && nc < S && !visited[nr][nc]) d++;
    }
    return d;
  }

  function dfs(r, c) {
    if (--budget <= 0) return false;
    visited[r][c] = true;
    path.push({ r, c });
    if (path.length === total) return true;

    // Collect unvisited neighbours
    let nbrs = [];
    for (const [dr, dc] of DIRS) {
      const nr = r + dr, nc = c + dc;
      if (nr >= 0 && nr < S && nc >= 0 && nc < S && !visited[nr][nc]) {
        nbrs.push({ r: nr, c: nc });
      }
    }
    // Random shuffle, then sort by Warnsdorff degree (fewest options first)
    shuffle(nbrs);
    nbrs.sort((a, b) => onwardDegree(a.r, a.c) - onwardDegree(b.r, b.c));

    for (const n of nbrs) {
      if (dfs(n.r, n.c)) return true;
    }

    visited[r][c] = false;
    path.pop();
    return false;
  }

  if (dfs(randInt(S), randInt(S)) && path.length === total) return path;

  // Fallback: serpentine (boustrophedon) — always full coverage
  const serp = [];
  for (let r = 0; r < S; r++) {
    if (r % 2 === 0) for (let c = 0;     c < S;  c++) serp.push({ r, c });
    else             for (let c = S - 1; c >= 0; c--) serp.push({ r, c });
  }
  return serp;
}

// ─── Level generator (guarantees a full-coverage solution exists) ────
// Build one Hamiltonian path covering every cell, then cut it into
// `numPairs` contiguous segments. Each segment's two ends become a tile
// pair, and the segment itself is the stored solution. Because the
// segments partition the whole board, a fill-everything solution always
// exists.
function generateLevel(size, numPairs) {
  const total = size * size;
  // Each segment needs >= 2 cells (two distinct endpoints).
  const maxPairs = Math.floor(total / 2);
  const K = Math.min(numPairs, maxPairs);

  const ham = hamiltonianPath(size);

  // Assign each segment a base length of 2, distribute the rest randomly.
  const lengths = Array(K).fill(2);
  let remaining = total - 2 * K;
  while (remaining-- > 0) lengths[randInt(K)]++;

  const tiles = mkGrid(size);
  const conns = [];
  let idx = 0;
  for (let i = 0; i < K; i++) {
    const seg  = ham.slice(idx, idx + lengths[i]);
    idx += lengths[i];
    const pos1 = seg[0];
    const pos2 = seg[seg.length - 1];
    tiles[pos1.r][pos1.c] = ICONS[i];
    tiles[pos2.r][pos2.c] = ICONS[i];
    conns.push({
      icon: ICONS[i],
      pos1, pos2,
      color: PIPE_COLORS[i % PIPE_COLORS.length],
      path: null,
      solution: seg,   // the full-coverage answer for this pair (used by hint)
    });
  }

  return { size, tiles, conns };
}

// ─── Coverage helpers ────────────────────────────────────────────────
// A cell counts as covered if it holds a tile OR a pipe passes through it.
function coveredCount() {
  let n = 0;
  for (let r = 0; r < boardSize; r++)
    for (let c = 0; c < boardSize; c++)
      if (tileGrid[r][c] !== null || occupancy[r][c] !== null) n++;
  return n;
}

function isBoardFull() {
  return coveredCount() === boardSize * boardSize;
}

function updateCoverage() {
  coverEl.textContent = `${coveredCount()} / ${boardSize * boardSize}`;
}

// ─── Hit test: which cell is at client coordinates? ──────────────────
function getCellAt(clientX, clientY) {
  const cells = boardEl.querySelectorAll('.cell');
  for (let i = 0; i < cells.length; i++) {
    const rect = cells[i].getBoundingClientRect();
    if (clientX >= rect.left && clientX <= rect.right &&
        clientY >= rect.top  && clientY <= rect.bottom) {
      return { r: Math.floor(i / boardSize), c: i % boardSize };
    }
  }
  return null;
}

// ─── Canvas ──────────────────────────────────────────────────────────
function syncCanvas() {
  const br = boardEl.getBoundingClientRect();
  const cr = boardEl.parentElement.getBoundingClientRect();
  canvasEl.style.left = (br.left - cr.left) + 'px';
  canvasEl.style.top  = (br.top  - cr.top)  + 'px';
  canvasEl.width  = br.width;
  canvasEl.height = br.height;
}

function cellCenter(r, c) {
  const br    = boardEl.getBoundingClientRect();
  const cells = boardEl.querySelectorAll('.cell');
  const rect  = cells[r * boardSize + c].getBoundingClientRect();
  return { x: rect.left - br.left + rect.width / 2,
           y: rect.top  - br.top  + rect.height / 2 };
}

function getCellPx() {
  const c = boardEl.querySelector('.cell');
  return c ? c.getBoundingClientRect().width : 64;
}

// Draw a path array as a rounded pipe stroke
function drawPipeStroke(pts, color, alpha, lineWidthFactor) {
  const px = getCellPx();
  ctx.beginPath();
  ctx.strokeStyle = color;
  ctx.lineWidth   = px * lineWidthFactor;
  ctx.lineCap     = 'round';
  ctx.lineJoin    = 'round';
  ctx.globalAlpha = alpha;
  ctx.moveTo(pts[0].x, pts[0].y);
  pts.slice(1).forEach(p => ctx.lineTo(p.x, p.y));
  ctx.stroke();
  ctx.globalAlpha = 1;
}

function redrawCanvas() {
  ctx.clearRect(0, 0, canvasEl.width, canvasEl.height);
  const px = getCellPx();

  // ── Completed / partial pipes ────────────────────────────────
  // Just a simple connecting line — no endpoint circles, tiles untouched.
  connections.forEach(conn => {
    if (!conn.path || conn.path.length < 2) return;
    const pts = conn.path.map(p => cellCenter(p.r, p.c));
    drawPipeStroke(pts, conn.color, 0.95, 0.16);
  });

  // ── Drag preview (semi-transparent) ─────────────────────────
  if (isDragging && dragPath.length >= 2) {
    const conn  = connections[dragConnIdx];
    const color = conn ? conn.color : '#ffffff';
    const pts   = dragPath.map(p => cellCenter(p.r, p.c));
    drawPipeStroke(pts, color, 0.6, 0.16);
  }

  // ── Hint (dashed dark, visible on light board) ──────────────
  if (hintData) {
    const pts = hintData.path.map(p => cellCenter(p.r, p.c));
    ctx.beginPath();
    ctx.strokeStyle = '#3a5a40';
    ctx.lineWidth   = px * 0.26;
    ctx.lineCap     = 'round';
    ctx.lineJoin    = 'round';
    ctx.globalAlpha = 0.8;
    ctx.setLineDash([px * 0.2, px * 0.12]);
    ctx.moveTo(pts[0].x, pts[0].y);
    pts.slice(1).forEach(p => ctx.lineTo(p.x, p.y));
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;
  }
}

function renderBoard() {
  const S = boardSize;
  boardEl.style.gridTemplateColumns = `repeat(${S}, var(--cell-size))`;
  boardEl.innerHTML = '';

  for (let r = 0; r < S; r++) {
    for (let c = 0; c < S; c++) {
      const cell   = document.createElement('div');
      const icon   = tileGrid[r][c];
      const occIdx = occupancy[r][c];

      cell.className = 'cell';
      cell.dataset.r = r; cell.dataset.c = c;

      if (icon !== null) {
        cell.classList.add('tile');
        cell.textContent = icon;
      } else {
        cell.classList.add('empty');
        if (occIdx !== null) cell.classList.add('on-pipe');
      }

      boardEl.appendChild(cell);
    }
  }

  syncCanvas();
  redrawCanvas();
}

// ─── Drag / draw interaction ─────────────────────────────────────────

function startDrag(pos) {
  if (isComplete) return;
  if (audioCtx) audioCtx.resume();

  const icon = tileGrid[pos.r][pos.c];
  if (icon === null) return; // must start on a tile

  // Find the connection this tile belongs to
  const ci = connections.findIndex(cn =>
    (cn.pos1.r===pos.r&&cn.pos1.c===pos.c) ||
    (cn.pos2.r===pos.r&&cn.pos2.c===pos.c)
  );
  if (ci < 0) return; // safety

  // Clear existing path so these cells become available
  if (connections[ci].path) {
    clearConn(ci);
    renderBoard();
  }

  isDragging   = true;
  dragSource   = pos;
  dragPath     = [{ r: pos.r, c: pos.c }];
  dragConnIdx  = ci;
  sfxStart();
}

function moveDrag(clientX, clientY) {
  if (!isDragging) return;

  const pos = getCellAt(clientX, clientY);
  if (!pos) return;

  const last = dragPath[dragPath.length - 1];
  if (pos.r === last.r && pos.c === last.c) return; // same cell

  // ── Backtrack: step back if the cell is already in our path ──
  const prevIdx = dragPath.findIndex(p => p.r === pos.r && p.c === pos.c);
  if (prevIdx >= 0) {
    dragPath = dragPath.slice(0, prevIdx + 1);
    redrawCanvas();
    return;
  }

  // ── Must be strictly adjacent to the last cell ───────────────
  if (Math.abs(pos.r - last.r) + Math.abs(pos.c - last.c) !== 1) return;

  const icon   = tileGrid[pos.r][pos.c];
  const occIdx = occupancy[pos.r][pos.c];

  // ── Destination tile (same icon, different position) ─────────
  const srcIcon = tileGrid[dragSource.r][dragSource.c];
  const isDest  = icon === srcIcon &&
                  !(pos.r === dragSource.r && pos.c === dragSource.c);

  // Block: other tile (not our destination)
  if (icon !== null && !isDest) return;
  // Block: occupied by ANOTHER connection
  if (occIdx !== null && occIdx !== dragConnIdx) return;

  dragPath.push({ r: pos.r, c: pos.c });
  redrawCanvas();
}

function endDrag(clientX, clientY) {
  if (!isDragging) return;
  isDragging = false;

  const last    = dragPath[dragPath.length - 1];
  const srcIcon = tileGrid[dragSource.r][dragSource.c];
  const lastIcon = tileGrid[last.r][last.c];

  // Valid: ended on matching tile (different from source)
  if (lastIcon === srcIcon &&
      !(last.r === dragSource.r && last.c === dragSource.c) &&
      dragPath.length >= 2) {

    const ci = dragConnIdx;
    // Apply path
    connections[ci].path = [...dragPath];
    dragPath.forEach(p => { occupancy[p.r][p.c] = ci; });

    sfxConnect();
    moveCount++;
    movesEl.textContent = moveCount;
    completedPairs = connections.filter(cn => cn.path !== null).length;
    pairsEl.textContent = `${completedPairs} / ${connections.length}`;
    updateCoverage();

    dragSource = null; dragPath = []; dragConnIdx = -1;
    renderBoard();

    // Win only when ALL pairs are connected AND every cell is filled.
    if (completedPairs === connections.length) {
      if (isBoardFull()) {
        setTimeout(handleWin, 450);
      } else {
        showToast('全部连上啦！但还有空格没填满，每个格子都要走到哦 🧩');
      }
    }

  } else {
    // Cancel: discard preview
    sfxCancel();
    dragSource = null; dragPath = []; dragConnIdx = -1;
    redrawCanvas();
  }
}

// ─── Clear helpers ───────────────────────────────────────────────────
function clearConn(ci) {
  const conn = connections[ci];
  if (!conn.path) return;
  conn.path.forEach(p => { if (occupancy[p.r][p.c] === ci) occupancy[p.r][p.c] = null; });
  conn.path = null;
  completedPairs = connections.filter(cn => cn.path !== null).length;
  pairsEl.textContent = `${completedPairs} / ${connections.length}`;
  updateCoverage();
}

function clearAll() {
  connections.forEach((_, i) => clearConn(i));
  isDragging = false; dragSource = null; dragPath = []; dragConnIdx = -1;
  moveCount  = 0; movesEl.textContent = '0';
  hintData   = null;
  renderBoard();
}

// Clear a pipe by clicking/tapping on an intermediate pipe cell
function tapClearPipe(pos) {
  const ci = occupancy[pos.r][pos.c];
  if (ci === null || tileGrid[pos.r][pos.c] !== null) return; // only intermediate cells
  clearConn(ci);
  sfxClear();
  renderBoard();
}

// ─── Win ─────────────────────────────────────────────────────────────
function handleWin() {
  isComplete = true;
  sfxWin();
  const maxMoves = connections.length * 2;
  const stars = moveCount <= maxMoves      ? '⭐⭐⭐'
               : moveCount <= maxMoves * 2 ? '⭐⭐' : '⭐';
  winLevelEl.textContent = `关卡 ${levelNum}`;
  winMovesEl.textContent = `用了 ${moveCount} 步`;
  winStarsEl.textContent = stars;
  winOverlay.classList.remove('hidden');
  levelNum++;
}

// ─── Hint ─────────────────────────────────────────────────────────────
function showHint() {
  if (isComplete) return;
  // Prefer an unfinished pair; otherwise hint a wrongly-routed one.
  let target = connections.find(cn => cn.path === null);
  if (!target) {
    // All connected but board not full → hint a pair whose drawn path
    // differs from its full-coverage solution.
    target = connections.find(cn =>
      !cn.path || cn.path.length !== cn.solution.length);
  }
  if (!target || !target.solution) return;

  // Show the real full-coverage solution segment for this pair.
  hintData = { path: target.solution, color: target.color };
  redrawCanvas();
  setTimeout(() => { if (hintData) { hintData = null; redrawCanvas(); } }, 2800);
}

// ─── New level ────────────────────────────────────────────────────────
function newLevel() {
  winOverlay.classList.add('hidden');
  isComplete = false; isDragging = false;
  dragSource = null; dragPath = []; dragConnIdx = -1;
  hintData = null; completedPairs = 0;
  moveCount = 0; movesEl.textContent = '0';

  const cfg  = DIFF[diff];
  const data = generateLevel(cfg.size, cfg.pairs);
  if (!data) { showToast('生成关卡失败，请重试'); return; }

  boardSize   = data.size;
  tileGrid    = data.tiles;
  occupancy   = mkGrid(boardSize, null);
  connections = data.conns;

  document.documentElement.style.setProperty('--cell-size', CELL_PX[boardSize] + 'px');
  document.documentElement.style.setProperty('--cell-font', FONT_PX[boardSize] + 'px');

  levelEl.textContent = `${levelNum}`;
  pairsEl.textContent = `0 / ${connections.length}`;
  updateCoverage();
  renderBoard();
}

// ─── Toast ───────────────────────────────────────────────────────────
let toastTimer = null;
function showToast(msg) {
  toastEl.textContent = msg;
  toastEl.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.add('hidden'), 2500);
}

// ─── Event wiring ────────────────────────────────────────────────────

// ── Mouse ──────────────────────────────────────────────────────
boardEl.addEventListener('mousedown', e => {
  if (e.button !== 0) return;
  e.preventDefault();
  const pos = getCellAt(e.clientX, e.clientY);
  if (!pos) return;

  // Short-tap on a pipe intermediate cell → clear that line
  if (tileGrid[pos.r][pos.c] === null && occupancy[pos.r][pos.c] !== null) {
    tapClearPipe(pos);
    return;
  }
  startDrag(pos);
});

window.addEventListener('mousemove', e => {
  moveDrag(e.clientX, e.clientY);
});

window.addEventListener('mouseup', e => {
  endDrag(e.clientX, e.clientY);
});

// ── Touch ──────────────────────────────────────────────────────
boardEl.addEventListener('touchstart', e => {
  e.preventDefault(); // prevent scroll + ghost-click
  const t = e.touches[0];
  const pos = getCellAt(t.clientX, t.clientY);
  if (!pos) return;
  if (tileGrid[pos.r][pos.c] === null && occupancy[pos.r][pos.c] !== null) {
    tapClearPipe(pos);
    return;
  }
  startDrag(pos);
}, { passive: false });

window.addEventListener('touchmove', e => {
  if (!isDragging) return;
  e.preventDefault();
  const t = e.touches[0];
  moveDrag(t.clientX, t.clientY);
}, { passive: false });

window.addEventListener('touchend', e => {
  if (!isDragging) return;
  const t = e.changedTouches[0];
  endDrag(t.clientX, t.clientY);
});

// ── Buttons ────────────────────────────────────────────────────
document.getElementById('btn-new').addEventListener('click',   newLevel);
document.getElementById('btn-hint').addEventListener('click',  showHint);
document.getElementById('btn-clear').addEventListener('click', clearAll);
document.getElementById('btn-next').addEventListener('click',  newLevel);

document.querySelectorAll('.diff-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.diff-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    diff     = btn.dataset.diff;
    levelNum = 1;
    newLevel();
  });
});

window.addEventListener('resize', () => { syncCanvas(); redrawCanvas(); });

// ─── Init ─────────────────────────────────────────────────────
newLevel();
