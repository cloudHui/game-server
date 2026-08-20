// Spaced repetition scheduler (Leitner system).
//
// Each skill sits in a box (1..5). Higher boxes are reviewed less often. The
// scheduler surfaces skills that are "due" so practice is distributed over time
// (the spacing effect) — gently, never with "review or lose progress" pressure.
import {SKILL_ORDER} from "../skills/index.js";
import {isSkillUnlocked} from "./unlocks.js";

// Review interval per box, in "learning days".
const INTERVAL = {1: 1, 2: 2, 3: 4, 4: 7, 5: 15};

export function isDue(skillObj, today) {
    if (skillObj.lastPlayedDay == null) return true;
    const interval = INTERVAL[skillObj.box || 1] || 1;
    return today - skillObj.lastPlayedDay >= interval;
}

// Skills that are due for review, most overdue first. Locked skills never
// surface — the child only meets a road once it's open.
export function dueSkills(state) {
    const today = state.day;
    return SKILL_ORDER.filter((id) => isSkillUnlocked(state, id) && isDue(state.skills[id], today)).sort((a, b) => {
        const la = state.skills[a].lastPlayedDay ?? -999;
        const lb = state.skills[b].lastPlayedDay ?? -999;
        return la - lb;
    });
}

export function markPracticed(state, skillId) {
    state.skills[skillId].lastPlayedDay = state.day;
}

// The skill the menu's "Play" button will start next. Rotates so consecutive
// plays don't repeat the same skill, while still preferring skills that are due
// for spaced review.
export function nextSkill(state) {
    const order = SKILL_ORDER.filter((id) => isSkillUnlocked(state, id));
    const last = state.lastPlayedSkill;
    const start = last && order.includes(last) ? (order.indexOf(last) + 1) % order.length : 0;
    const rotated = [...order.slice(start), ...order.slice(0, start)];
    const due = new Set(dueSkills(state));
    return rotated.find((id) => due.has(id)) || rotated[0];
}

// Build the ordered skill sequence for a mission's challenge gates.
//
// By default a mission focuses on a single skill — when a child (or the
// recommender) picks a skill, every gate practises THAT skill, which matches
// the "I picked counting, so give me counting" expectation. Spaced repetition
// still works at the session level: the menu's recommended pick targets the
// skill that's currently due. Pass `interleaveReview: true` to mix in due-skill
// review gates instead.
export function planMission(state, focalSkillId, gateCount = 6, {interleaveReview = false} = {}) {
    if (!interleaveReview) {
        return Array.from({length: gateCount}, () => focalSkillId);
    }
    const due = dueSkills(state).filter((id) => id !== focalSkillId);
    const seq = [];
    for (let i = 0; i < gateCount; i++) {
        if (i % 2 === 0 || !due.length) {
            seq.push(focalSkillId);
        } else {
            seq.push(due[((i / 2) | 0) % due.length]);
        }
    }
    return seq;
}
