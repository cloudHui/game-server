// Tiny zero-dependency helpers shared across the game.

export function clamp(v, lo, hi) {
  return v < lo ? lo : v > hi ? hi : v;
}

export function lerp(a, b, t) {
  return a + (b - a) * t;
}

// Seedable PRNG (mulberry32) so skill generation is deterministic in tests.
export function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function randInt(rng, lo, hi) {
  return lo + Math.floor(rng() * (hi - lo + 1));
}

export function pick(rng, arr) {
  return arr[Math.floor(rng() * arr.length)];
}

export function shuffle(rng, arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// Build 3 distinct lane options from a correct value and a pool of distractors.
// Returns { options, correctIndex }.
export function makeOptions(rng, correct, distractors, laneCount = 3) {
  const opts = new Set([correct]);
  const pool = shuffle(rng, distractors);
  let i = 0;
  while (opts.size < laneCount && i < pool.length) {
    opts.add(pool[i++]);
  }
  // fallback: fill with nearby numbers if not enough distractors
  let d = 1;
  while (opts.size < laneCount) {
    if (!opts.has(correct + d)) opts.add(correct + d);
    else if (correct - d >= 0 && !opts.has(correct - d)) opts.add(correct - d);
    d++;
  }
  const options = shuffle(rng, [...opts]);
  return { options, correctIndex: options.indexOf(correct) };
}
