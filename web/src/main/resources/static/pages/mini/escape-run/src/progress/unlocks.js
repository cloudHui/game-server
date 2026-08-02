// Skill unlocks — the Adventure Map is a journey, not a wall of buttons. New
// skills appear as "mystery roads" that open when their prerequisites are met,
// so a child meets one new idea at a time (and old saves lose nothing).
import {SKILL_ORDER, skillMeta} from "../skills/index.js";

// Available from the very first launch: the original five plus Shape Shadows,
// which is the gentle on-ramp for the youngest players.
export const BASE_UNLOCKED = [
    "counting",
    "comparison",
    "arithmetic",
    "patterning",
    "bonds",
    "shapes",
];

// Locked skills and how they open. Rules are real pedagogy, not just gating:
// grouping needs addition fluency; fractions build on sharing or equal parts.
export const UNLOCK_RULES = {
    groups: {
        test: (s) => s.skills.arithmetic.level >= 4,
        hint: "Reach Speed Math level 4 to open this road!",
    },
    fractions: {
        test: (s) => s.skills.groups.level >= 2 || s.skills.shapes.level >= 6,
        hint: "Master Super Groups or Shape Shadows to open this road!",
    },
};

export function defaultUnlockedSkills() {
    return [...BASE_UNLOCKED];
}

export function isSkillUnlocked(state, id) {
    const list = (state.unlocks && state.unlocks.skills) || BASE_UNLOCKED;
    return list.includes(id);
}

export function unlockedSkills(state) {
    return SKILL_ORDER.filter((id) => isSkillUnlocked(state, id));
}

export function skillUnlockHint(id) {
    return (UNLOCK_RULES[id] && UNLOCK_RULES[id].hint) || "Keep playing to open this road!";
}

// Apply unlock rules to the (mutable) state. Returns the metas of skills opened
// by THIS call, for the reveal ceremony. Loops to a fixpoint so a cascade (one
// unlock enabling the next) resolves in a single evaluation.
export function evaluateSkillUnlocks(state) {
    if (!state.unlocks.skills) state.unlocks.skills = defaultUnlockedSkills();
    const have = new Set(state.unlocks.skills);
    const fresh = [];
    let changed = true;
    while (changed) {
        changed = false;
        for (const id of Object.keys(UNLOCK_RULES)) {
            if (have.has(id)) continue;
            if (UNLOCK_RULES[id].test(state)) {
                have.add(id);
                state.unlocks.skills.push(id);
                fresh.push(skillMeta(id));
                changed = true;
            }
        }
    }
    return fresh;
}
