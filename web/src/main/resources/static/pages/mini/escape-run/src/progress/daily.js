// Daily Challenge — one special mission per day (date-seeded) with a bonus star
// reward. Feeds the flexible streak. Ethical: never punishes a missed day.
import { SKILL_ORDER } from "../skills/index.js";
import { unlockedSkills } from "./unlocks.js";

export const DAILY_BONUS = 5;

function hash(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

export function dailyKey(d = new Date()) {
  return d.toDateString();
}

// Deterministic focal skill for today. Changes daily and only ever picks a skill
// the child has already unlocked (so the daily is always playable).
export function dailySkill(state, key = dailyKey()) {
  const pool = state ? unlockedSkills(state) : SKILL_ORDER;
  return pool[hash("daily:" + key) % pool.length];
}

export function isDailyDone(state, key = dailyKey()) {
  return state.daily.claimedKey === key;
}

// Mark today's daily complete once; returns bonus stars awarded (0 if already).
export function claimDaily(state, key = dailyKey()) {
  if (isDailyDone(state, key)) return 0;
  state.daily.claimedKey = key;
  return DAILY_BONUS;
}
