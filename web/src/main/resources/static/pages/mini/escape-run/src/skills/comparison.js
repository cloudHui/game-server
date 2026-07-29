// Skill 2 — Number Comparison / Magnitude.
// "Tap the BIGGER number." Numerical magnitude comparison is the single
// strongest early predictor of later math achievement. L7 uses place-value
// traps (transposed digits like 34 vs 43) that defeat digit-by-digit guessing.
import { randInt, shuffle, clamp } from "../util.js";

export const meta = {
  id: "comparison",
  name: "Big & Small",
  short: "Comparing",
  icon: "⟩",
  color: "#5aa9ff",
  maxLevel: 7,
  blurb: "Judging which number is larger/smaller — magnitude sense.",
};

function rangeFor(level) {
  const table = [
    [1, 9],
    [1, 20],
    [5, 50],
    [10, 99],
    [20, 200],
    [20, 100], // L6: close two-digit magnitudes
  ];
  return table[clamp(level, 1, 6) - 1];
}

// L7 — a number, its digit-transpose, and a nearby value.
function trapOptions(rng) {
  const t = randInt(rng, 2, 9);
  let o = randInt(rng, 1, 8);
  if (o >= t) o += 1; // distinct digits, so the transpose differs
  const n = t * 10 + o;
  const swap = o * 10 + t;
  let near;
  do {
    near = n + randInt(rng, -9, 9);
  } while (near === n || near === swap || near < 10);
  return [n, swap, near];
}

export function generate(level, rng) {
  const mode = rng() < 0.5 ? "bigger" : "smaller";
  let options;
  if (level >= 7) {
    options = shuffle(rng, trapOptions(rng));
  } else {
    const [lo, hi] = rangeFor(level);
    // Three distinct numbers, one clearly the target.
    const set = new Set();
    while (set.size < 3) set.add(randInt(rng, lo, hi));
    options = shuffle(rng, [...set]);
  }
  const target = mode === "bigger" ? Math.max(...options) : Math.min(...options);
  return {
    skillId: "comparison",
    level,
    prompt: { type: "compare", mode },
    optionKind: "number",
    promptText: mode === "bigger" ? "Tap the BIGGER number" : "Tap the SMALLER number",
    say: mode === "bigger" ? "Which number is bigger?" : "Which number is smaller?",
    options,
    correctIndex: options.indexOf(target),
  };
}
