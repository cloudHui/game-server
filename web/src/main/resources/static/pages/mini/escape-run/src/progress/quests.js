// Quests — three small, rotating daily goals that reward stars. Bite-sized
// objectives that make each session feel purposeful. Reset daily; earned, local,
// no pressure (missing a day just gives you fresh quests tomorrow).
import {mulberry32} from "../util.js";
import {skillMeta} from "../skills/index.js";
import {dailyKey} from "./daily.js";
import {unlockedSkills} from "./unlocks.js";

const TEMPLATES = [
    {
        type: "correct", make: (r) => ({target: 6 + Math.floor(r() * 6), reward: 4}),
        label: (q) => `Answer ${q.target} questions correctly`, icon: "✔"
    },
    {
        type: "sparks", make: (r) => ({target: 20 + Math.floor(r() * 20), reward: 4}),
        label: (q) => `Collect ${q.target} sparks`, icon: "✦"
    },
    {
        type: "missions", make: (r) => ({target: 2 + Math.floor(r() * 2), reward: 3}),
        label: (q) => `Finish ${q.target} missions`, icon: "🏁"
    },
    {
        type: "threeStar", make: () => ({target: 1, reward: 5}),
        label: () => `Earn a 3-star mission`, icon: "⭐"
    },
    {
        type: "skill", make: (r, skills) => ({target: 1, reward: 4, param: skills[Math.floor(r() * skills.length)]}),
        label: (q) => `Practise ${skillMeta(q.param).name}`, icon: "🎯"
    },
];

function seedFrom(key) {
    let h = 2166136261;
    for (let i = 0; i < key.length; i++) {
        h ^= key.charCodeAt(i);
        h = Math.imul(h, 16777619);
    }
    return h >>> 0;
}

function generate(key, skills) {
    const rng = mulberry32(seedFrom("quests:" + key));
    const order = TEMPLATES.map((t, i) => [i, rng()]).sort((a, b) => a[1] - b[1]).map((x) => x[0]);
    const list = [];
    for (let i = 0; i < 3; i++) {
        const t = TEMPLATES[order[i]];
        const base = t.make(rng, skills);
        const q = {id: `${t.type}-${i}`, type: t.type, icon: t.icon, progress: 0, done: false, ...base};
        q.text = t.label(q);
        list.push(q);
    }
    return list;
}

// Regenerate the day's quests if the date changed.
export function ensureQuests(state, key = dailyKey()) {
    if (state.quests.key !== key || !state.quests.list.length) {
        state.quests.key = key;
        state.quests.list = generate(key, unlockedSkills(state));
    }
    return state.quests.list;
}

// Apply a finished run to quest progress. Awards stars for newly-completed
// quests and returns them (for celebratory toasts).
export function applyRunToQuests(state, summary) {
    ensureQuests(state);
    const completed = [];
    for (const q of state.quests.list) {
        if (q.done) continue;
        switch (q.type) {
            case "correct":
                q.progress += summary.correct;
                break;
            case "sparks":
                q.progress += summary.sparks;
                break;
            case "missions":
                q.progress += 1;
                break;
            case "threeStar":
                if (summary.missionStars >= 3) q.progress = q.target;
                break;
            case "skill":
                if (summary.focal === q.param) q.progress = q.target;
                break;
        }
        if (q.progress >= q.target) {
            q.progress = q.target;
            q.done = true;
            state.stars += q.reward;
            completed.push(q);
        }
    }
    return completed;
}
