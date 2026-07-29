// Skill 4 — Patterning.
// "What comes next?" Repeating/growing patterns build algebraic reasoning and
// executive function. Levels 1–2 use shape/colour patterns; 3–5 use number
// sequences (skip counting); 6 adds offsets & descending runs; 7 uses
// two-attribute shape patterns (shape and colour cycle at different lengths).
import { randInt, pick, shuffle, clamp, makeOptions } from "../util.js";

export const meta = {
  id: "patterning",
  name: "What's Next?",
  short: "Patterns",
  icon: "◇",
  color: "#7c5cff",
  maxLevel: 7,
  blurb: "Recognising and extending patterns — early algebra & logic.",
};

const SHAPES = ["circle", "square", "triangle", "star"];
const COLORS = ["#ef4444", "#5aa9ff", "#22c55e", "#f59e0b", "#7c5cff"];

function tokenKey(t) {
  return `${t.shape}|${t.color}`;
}

function shapePattern(level, rng) {
  // Build a repeating unit, then reveal several repetitions + a blank.
  const shapes = shuffle(rng, SHAPES).slice(0, level === 1 ? 2 : 3);
  const colors = shuffle(rng, COLORS).slice(0, level === 1 ? 2 : 3);
  const unitLen = level === 1 ? 2 : pick(rng, [2, 3]);
  const unit = [];
  for (let i = 0; i < unitLen; i++) {
    unit.push({ shape: shapes[i % shapes.length], color: colors[i % colors.length] });
  }
  const revealLen = unitLen * 2; // show two full cycles
  const sequence = [];
  for (let i = 0; i < revealLen; i++) sequence.push(unit[i % unitLen]);
  const answer = unit[revealLen % unitLen];
  sequence.push(null); // the blank

  // options: correct + distractors from the same palette
  const distractPool = [];
  for (const s of shapes)
    for (const c of colors) {
      const t = { shape: s, color: c };
      if (tokenKey(t) !== tokenKey(answer)) distractPool.push(t);
    }
  const opts = [answer, ...shuffle(rng, distractPool).slice(0, 2)];
  const options = shuffle(rng, opts);
  return {
    prompt: { type: "pattern", kind: "shape", sequence },
    optionKind: "shape",
    options,
    correctIndex: options.findIndex((o) => tokenKey(o) === tokenKey(answer)),
  };
}

function numberPattern(level, rng) {
  const step = pick(rng, level >= 4 ? [2, 3, 5, 10] : [1, 2, 5]);
  const start = randInt(rng, 1, 10) * (rng() < 0.5 ? 1 : step);
  const seq = [start, start + step, start + 2 * step, start + 3 * step];
  const answer = start + 4 * step;
  const distractors = new Set([answer + step, answer - step, answer + 1, answer - 1]);
  distractors.delete(answer);
  const options = shuffle(rng, [answer, ...[...distractors].slice(0, 2)]);
  return {
    prompt: { type: "pattern", kind: "number", sequence: [...seq, null] },
    optionKind: "number",
    options,
    correctIndex: options.indexOf(answer),
  };
}

// L6 — skip counting from offsets (13, 23, 33…), ascending or descending.
function offsetPattern(rng) {
  const step = pick(rng, [2, 5, 10]);
  const down = rng() < 0.4;
  const start = down ? 4 * step + randInt(rng, 1, 15) : randInt(rng, 1, 15);
  const dir = down ? -1 : 1;
  const seq = [0, 1, 2, 3].map((i) => start + dir * i * step);
  const answer = start + dir * 4 * step;
  const pool = [answer + step, answer - step, answer + 1, answer - 1].filter(
    (n) => n >= 0 && n !== answer
  );
  const { options, correctIndex } = makeOptions(rng, answer, pool);
  return {
    prompt: { type: "pattern", kind: "number", sequence: [...seq, null] },
    optionKind: "number",
    options,
    correctIndex,
  };
}

// L7 — two attributes cycle at different lengths (shape every 2, colour every 3),
// so extending the pattern means tracking both cycles at once.
function twoAttributePattern(rng) {
  const shapes = shuffle(rng, SHAPES).slice(0, 2);
  const colors = shuffle(rng, COLORS).slice(0, 3);
  const at = (i) => ({ shape: shapes[i % 2], color: colors[i % 3] });
  const reveal = 7; // one full combined cycle (6) + 1
  const sequence = [];
  for (let i = 0; i < reveal; i++) sequence.push(at(i));
  const answer = at(reveal);
  sequence.push(null);
  const distractPool = [];
  for (const s of shapes)
    for (const c of colors) {
      const t = { shape: s, color: c };
      if (tokenKey(t) !== tokenKey(answer)) distractPool.push(t);
    }
  const opts = [answer, ...shuffle(rng, distractPool).slice(0, 2)];
  const options = shuffle(rng, opts);
  return {
    prompt: { type: "pattern", kind: "shape", sequence },
    optionKind: "shape",
    options,
    correctIndex: options.findIndex((o) => tokenKey(o) === tokenKey(answer)),
  };
}

export function generate(level, rng) {
  const lvl = clamp(level, 1, meta.maxLevel);
  const body =
    lvl <= 2 ? shapePattern(lvl, rng)
    : lvl === 6 ? offsetPattern(rng)
    : lvl >= 7 ? twoAttributePattern(rng)
    : numberPattern(lvl, rng);
  return {
    skillId: "patterning",
    level,
    promptText: "What comes next?",
    say: "What comes next in the pattern?",
    ...body,
  };
}
