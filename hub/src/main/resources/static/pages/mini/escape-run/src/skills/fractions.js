// Skill 8 — Fair Share: basic fractions (halves & quarters).
// A picnic with Pip's friends — cutting rotis, splitting mangoes fairly.
// NCERT: "Fair Share" (Class 3), halves and quarters. We stop at ½/¼/¾ — no
// remainders, no equivalent fractions, no thirds-as-symbols.
import {randInt, pick, shuffle, clamp, makeOptions} from "../util.js";

export const meta = {
    id: "fractions",
    name: "认识分数",
    short: "分数",
    icon: "½",
    color: "#f97316",
    maxLevel: 7,
    blurb: "认识平均分、一半、四分之一和简单分数。",
};

const COLOR = "#f97316";

// Build a `part` tile: a pie/bar cut into `denom` equal wedges with `on` shaded.
function fracTile(style, denom, on) {
    const cuts = Array.from({length: denom}, () => 1 / denom);
    const shaded = Array.from({length: denom}, (_, i) => i < on);
    return {style, cuts, shaded, color: COLOR};
}

// Distinct object options + correctIndex (makeOptions is number-only).
function objectOptions(rng, correct, pool, keyOf) {
    const opts = [correct];
    const seen = new Set([keyOf(correct)]);
    for (const cand of shuffle(rng, pool)) {
        if (opts.length >= 3) break;
        if (!seen.has(keyOf(cand))) {
            seen.add(keyOf(cand));
            opts.push(cand);
        }
    }
    const options = shuffle(rng, opts);
    return {options, correctIndex: options.findIndex((o) => keyOf(o) === keyOf(correct))};
}

// L1 — "Tap the fair share": one equal cut vs unequal cuts.
function fairShare(level, rng) {
    const style = pick(rng, ["pie", "bar"]);
    const fair = {style, cuts: [0.5, 0.5], shaded: [], color: COLOR};
    const pool = shuffle(rng, [0.25, 0.33, 0.67, 0.75]);
    const unfair = [pool[0], pool[1]].map((a) => ({style, cuts: [a, 1 - a], shaded: [], color: COLOR}));
    const options = shuffle(rng, [fair, ...unfair]);
    return {
        skillId: "fractions",
        level,
        prompt: {type: "fairshare"},
        optionKind: "part",
        promptText: "选择平均分成两份的图形",
        say: "哪一个图形被平均分成了相等的两份？",
        options,
        correctIndex: options.findIndex((o) => Math.abs(o.cuts[0] - 0.5) < 0.001),
        pace: 0.85,
    };
}

// L2 / L3 — "Which shows one half / one quarter?" (unit fraction shaded).
function namedFraction(level, rng, denom, word) {
    const style = pick(rng, ["pie", "bar", "roti"]);
    const key = (o) => o.cuts.length;
    const correct = fracTile(style, denom, 1);
    const others = [2, 3, 4].filter((d) => d !== denom).map((d) => fracTile(style, d, 1));
    const {options, correctIndex} = objectOptions(rng, correct, others, key);
    return {
        skillId: "fractions",
        level,
        prompt: {type: level === 2 ? "onehalf" : "onequarter"},
        optionKind: "part",
        promptText: `哪个表示${word === "half" ? "二分之一" : "四分之一"}？`,
        say: `哪个图形表示${word === "half" ? "二分之一" : "四分之一"}？`,
        options,
        correctIndex,
        pace: 0.85,
    };
}

// L4 / L5 — a fraction of a group: "Half of 8?" / "A quarter of 8?".
function partOfGroup(level, rng, denom, word) {
    // total must divide evenly and stay ≤ 20 (no remainders — NCERT cap).
    const maxUnits = Math.floor(20 / denom);
    const per = randInt(rng, 2, maxUnits); // dots in each equal share
    const total = per * denom;
    const distractors = [total, per + denom, per + 1, Math.max(1, per - 1)].filter((n) => n !== per);
    const {options, correctIndex} = makeOptions(rng, per, distractors);
    return {
        skillId: "fractions",
        level,
        prompt: {type: "partof", total, denom},
        optionKind: "number",
        promptText: `${total} 的${word === "half" ? "一半" : "四分之一"}是多少？`,
        say: `${total} 的${word === "half" ? "一半" : "四分之一"}是多少？`,
        options,
        correctIndex,
        pace: 0.85,
    };
}

// L6 — compare unit fractions: "Which piece is biggest?" (½ vs ⅓ vs ¼).
function comparePieces(level, rng) {
    const style = pick(rng, ["pie", "roti"]);
    const key = (o) => o.cuts.length;
    const options = shuffle(rng, [2, 3, 4].map((d) => fracTile(style, d, 1)));
    return {
        skillId: "fractions",
        level,
        prompt: {type: "comparefrac"},
        optionKind: "part",
        promptText: "哪一份最大？",
        say: "分成的份数越多，每一份越小。请选择最大的一份。",
        options,
        // biggest single piece = fewest cuts = denominator 2 (one half).
        correctIndex: options.findIndex((o) => o.cuts.length === 2),
        pace: 0.85,
    };
}

// L7 — symbols: a shaded shape → tap the matching fraction (½, ¼, ¾).
const FRACS = [
    {label: "½", denom: 2, on: 1, v: 0.5},
    {label: "¼", denom: 4, on: 1, v: 0.25},
    {label: "¾", denom: 4, on: 3, v: 0.75},
];

function nameSymbol(level, rng) {
    const style = pick(rng, ["pie", "bar", "roti"]);
    const target = pick(rng, FRACS);
    // Shuffle the lanes so the symbol's position isn't a giveaway (the child must
    // read ½ / ¼ / ¾ rather than learn "½ is always the left lane").
    const options = shuffle(rng, FRACS.map((f) => ({label: f.label, v: f.v})));
    return {
        skillId: "fractions",
        level,
        prompt: {type: "partshow", style, denom: target.denom, on: target.on},
        optionKind: "frac",
        promptText: `选择 ${target.label}`,
        say: "涂色部分表示哪个分数？",
        options,
        correctIndex: options.findIndex((o) => o.v === target.v),
        pace: 0.85,
    };
}

export function generate(level, rng) {
    const lvl = clamp(level, 1, meta.maxLevel);
    if (lvl === 1) return fairShare(lvl, rng);
    if (lvl === 2) return namedFraction(lvl, rng, 2, "half");
    if (lvl === 3) return namedFraction(lvl, rng, 4, "quarter");
    if (lvl === 4) return partOfGroup(lvl, rng, 2, "Half");
    if (lvl === 5) return partOfGroup(lvl, rng, 4, "A quarter");
    if (lvl === 6) return comparePieces(lvl, rng);
    return nameSymbol(lvl, rng);
}
