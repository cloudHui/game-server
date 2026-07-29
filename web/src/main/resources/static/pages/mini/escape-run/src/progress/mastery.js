// Mastery model + adaptive difficulty (Zone of Proximal Development).
//
// Difficulty is tuned per skill so the child stays in flow — promoted after a
// short run of successes, and gently eased back after repeated misses (never
// punished). Mastery is demonstrated, not bought with time.
import { skillMeta } from "../skills/index.js";
import { clamp } from "../util.js";

const PROMOTE_STREAK = 3; // correct-in-a-row to level up
const DEMOTE_MISS = 2; // misses-in-a-row to ease down a level

// Record one answer for a skill object (mutates it). Returns event flags.
export function recordResult(skillObj, skillId, correct) {
  const max = skillMeta(skillId).maxLevel;
  skillObj.seen += 1;
  const evt = { leveledUp: false, leveledDown: false, mastered: false };

  if (correct) {
    skillObj.correct += 1;
    skillObj.streak += 1;
    skillObj.miss = 0;
    skillObj.box = clamp((skillObj.box || 1) + 1, 1, 5); // Leitner: move up a box
    if (skillObj.streak >= PROMOTE_STREAK && skillObj.level < max) {
      skillObj.level += 1;
      skillObj.streak = 0;
      evt.leveledUp = true;
    }
  } else {
    skillObj.streak = 0;
    skillObj.miss += 1;
    skillObj.box = 1; // Leitner: reset to first box, review sooner
    if (skillObj.miss >= DEMOTE_MISS && skillObj.level > 1) {
      skillObj.level -= 1;
      skillObj.miss = 0;
      evt.leveledDown = true;
    }
  }

  // Mastered = reached top level and holding it well. Sticky: once earned it is
  // never taken away, so raising the level ceiling can't strip badges.
  const wasMastered = skillObj.mastered;
  if (skillObj.level >= max && (skillObj.box || 1) >= 4) skillObj.mastered = true;
  if (skillObj.mastered && !wasMastered) evt.mastered = true;

  return evt;
}

export function accuracy(skillObj) {
  return skillObj.seen ? skillObj.correct / skillObj.seen : 0;
}

// Progress toward mastery, 0..1, for parent dashboard & map.
export function masteryProgress(skillObj, skillId) {
  const max = skillMeta(skillId).maxLevel;
  const levelPart = (skillObj.level - 1) / (max - 1); // 0..1 across levels
  const boxPart = ((skillObj.box || 1) - 1) / 4; // 0..1 within top level
  return clamp(levelPart * 0.8 + boxPart * 0.2, 0, 1);
}
