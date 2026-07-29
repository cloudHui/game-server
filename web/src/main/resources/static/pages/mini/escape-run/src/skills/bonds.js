// Skill 5 — Number Bonds / Make-10.
// Part-whole composition: "Which number makes 10 with 6?" Composition &
// decomposition of number is core number sense and underpins fluent arithmetic.
// L6 bridges through ten (teen targets); L7 uses place-value parts (30 + ? = 34).
import { randInt, makeOptions, clamp } from "../util.js";

export const meta = {
  id: "bonds",
  name: "Make It Whole",
  short: "Number Bonds",
  icon: "◑",
  color: "#ec4899",
  maxLevel: 7,
  blurb: "Composing and decomposing numbers (e.g. make 10) — deep number sense.",
};

function targetFor(level, rng) {
  const targets = [
    [5], // L1: bonds of 5
    [10], // L2: bonds of 10
    [10], // L3: bonds of 10 (missing-addend framing)
    [10, 20], // L4
    [20], // L5
  ][clamp(level, 1, 5) - 1];
  return targets[Math.floor(rng() * targets.length)];
}

export function generate(level, rng) {
  let target, given;
  if (level >= 7) {
    // Place-value parts: the given is the tens, the answer is the ones.
    target = randInt(rng, 2, 5) * 10 + randInt(rng, 1, 9);
    given = Math.floor(target / 10) * 10;
  } else if (level === 6) {
    // Bridging through ten: 8 + ? = 13.
    target = randInt(rng, 11, 16);
    given = randInt(rng, 5, 9);
  } else {
    target = targetFor(level, rng);
    given = randInt(rng, 1, target - 1);
  }
  const answer = target - given;
  const distractors = [answer + 1, answer - 1, answer + 2, target, given].filter(
    (n) => n >= 0 && n !== answer
  );
  const { options, correctIndex } = makeOptions(rng, answer, distractors);
  return {
    skillId: "bonds",
    level,
    prompt: { type: "bond", target, given },
    optionKind: "number",
    promptText: `${given} + ? = ${target}`,
    say: `What makes ${target} with ${given}?`,
    options,
    correctIndex,
  };
}
