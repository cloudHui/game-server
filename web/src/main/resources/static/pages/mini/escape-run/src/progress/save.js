// Local-only persistence. No accounts, no network, no tracking.
// All progress lives in the child's browser via localStorage.

const KEY = "escape-run:v1";
const SCHEMA_VERSION = 1;
import { defaultUnlockedSkills, evaluateSkillUnlocks } from "./unlocks.js";

export const SKILL_IDS = [
  "counting",
  "comparison",
  "arithmetic",
  "patterning",
  "bonds",
  "groups",
  "shapes",
  "fractions",
];

function defaultSkill() {
  return {
    level: 1, // adaptive difficulty level (1..N)
    box: 1, // Leitner spaced-repetition box (1..5)
    seen: 0,
    correct: 0,
    streak: 0, // consecutive correct at current level
    miss: 0, // consecutive misses (for gentle level-down)
    lastPlayedDay: null, // day index for spaced repetition
    mastered: false,
  };
}

function defaultState() {
  const skills = {};
  for (const id of SKILL_IDS) skills[id] = defaultSkill();
  return {
    version: SCHEMA_VERSION,
    stars: 0,
    skills,
    unlocks: { cars: ["classic"], worlds: ["meadow"], characters: ["pip"], skills: defaultUnlockedSkills() },
    equipped: { car: "classic", character: "pip" },
    streak: { count: 0, lastDay: null, weekDays: [] },
    stats: { runs: 0, correct: 0, seen: 0, playMs: 0, hints: 0, recovered: 0, threeStars: 0, questsDone: 0, starsEarned: 0, playedSkills: [] },
    onboarded: false,
    settings: {
      sound: true,
      music: true,
      voice: true,
      reduceMotion: false,
      highContrast: false,
      dyslexiaFont: false,
      colorblind: false,
      sessionMinutes: 0, // 0 = no limit
      speedScale: 0.65, // parent-adjustable car speed; relaxed (slowest) by default for young kids
    },
    day: 0, // internal "learning day" counter, advanced once per real day
    lastRealDay: null,
    lastPlayedSkill: null,
    daily: { claimedKey: null },
    quests: { key: null, list: [] },
    achievements: { unlocked: [] },
  };
}

let state = null;

function migrate(loaded) {
  const base = defaultState();
  if (!loaded || typeof loaded !== "object") return base;
  // shallow-merge with defaults so new fields appear for old saves
  const merged = { ...base, ...loaded };
  merged.settings = { ...base.settings, ...(loaded.settings || {}) };
  merged.unlocks = { ...base.unlocks, ...(loaded.unlocks || {}) };
  merged.equipped = { ...base.equipped, ...(loaded.equipped || {}) };
  merged.streak = { ...base.streak, ...(loaded.streak || {}) };
  merged.stats = { ...base.stats, ...(loaded.stats || {}) };
  merged.daily = { ...base.daily, ...(loaded.daily || {}) };
  merged.quests = { ...base.quests, ...(loaded.quests || {}) };
  merged.achievements = { ...base.achievements, ...(loaded.achievements || {}) };
  merged.skills = {};
  for (const id of SKILL_IDS) {
    merged.skills[id] = { ...defaultSkill(), ...((loaded.skills || {})[id] || {}) };
  }
  merged.version = SCHEMA_VERSION;
  return merged;
}

export function load() {
  if (state) return state;
  try {
    const raw = localStorage.getItem(KEY);
    state = migrate(raw ? JSON.parse(raw) : null);
  } catch (e) {
    state = defaultState();
  }
  advanceDayIfNeeded();
  // Silently open any roads the player has already earned (e.g. an existing
  // save whose Speed Math is already past level 4). No ceremony on load.
  evaluateSkillUnlocks(state);
  return state;
}

export function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch (e) {
    /* storage full or blocked — game still playable this session */
  }
}

export function get() {
  return state || load();
}

export function reset() {
  state = defaultState();
  save();
  return state;
}

// Advance the internal "learning day" once per real calendar day.
// Used by the spaced-repetition scheduler. Keeps things timezone-local.
function todayStr() {
  return new Date().toDateString();
}

export function advanceDayIfNeeded() {
  const s = state;
  const today = todayStr();
  if (s.lastRealDay !== today) {
    if (s.lastRealDay !== null) s.day += 1;
    s.lastRealDay = today;
    save();
  }
}

// Flexible, non-punitive streak: increments on any play day; never resets to
// zero as a punishment — if a day is missed the count simply pauses.
export function recordPlayDay() {
  const s = state;
  const today = todayStr();
  if (s.streak.lastDay === today) return s.streak.count;
  const yesterday = new Date(Date.now() - 864e5).toDateString();
  if (s.streak.lastDay === yesterday || s.streak.lastDay === null) {
    s.streak.count += 1;
  } else {
    // Missed one+ days: gently restart at 1 (a fresh, encouraging start).
    s.streak.count = 1;
  }
  s.streak.lastDay = today;
  save();
  return s.streak.count;
}
