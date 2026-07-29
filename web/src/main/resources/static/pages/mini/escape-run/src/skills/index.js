// Registry of all skill modules + a convenience generator.
import * as counting from "./counting.js";
import * as comparison from "./comparison.js";
import * as arithmetic from "./arithmetic.js";
import * as patterning from "./patterning.js";
import * as bonds from "./bonds.js";
import * as groups from "./groups.js";
import * as shapes from "./shapes.js";
import * as fractions from "./fractions.js";

export const SKILLS = { counting, comparison, arithmetic, patterning, bonds, groups, shapes, fractions };
export const SKILL_ORDER = ["counting", "comparison", "arithmetic", "patterning", "bonds", "groups", "shapes", "fractions"];

export function skillMeta(id) {
  return SKILLS[id].meta;
}

// Generate a challenge for a skill at a level. `rng` defaults to Math.random.
export function generateChallenge(skillId, level, rng = Math.random) {
  return SKILLS[skillId].generate(level, rng);
}
