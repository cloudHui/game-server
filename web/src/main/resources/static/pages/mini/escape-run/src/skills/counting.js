// Skill 1 — Subitizing / Counting.
// "How many dots?" Builds instant number sense (a unique predictor of later
// math achievement beyond rote counting). L6–7 show the dots in rows of ten
// (structured counting — the on-ramp to place value).
import {randInt, makeOptions, clamp} from "../util.js";

export const meta = {
    id: "counting",
    name: "数一数",
    short: "数数",
    icon: "●",
    color: "#22c55e",
    maxLevel: 7,
    blurb: "快速数清图案数量，培养数感。",
};

// Level controls the range of the quantity to recognize.
function rangeFor(level) {
    const table = [
        [1, 4], // L1
        [3, 6], // L2
        [5, 9], // L3
        [7, 14], // L4
        [10, 20], // L5
        [10, 20], // L6 (same range, grouped by ten — teaches the ten-structure)
        [20, 40], // L7
    ];
    return table[clamp(level, 1, 7) - 1];
}

export function generate(level, rng) {
    const [lo, hi] = rangeFor(level);
    const count = randInt(rng, lo, hi);
    const grouped = level >= 6;
    const distractors = [count - 1, count + 1, count - 2, count + 2, count + 3];
    if (grouped) distractors.push(count - 10, count + 10); // place-value slips
    const {options, correctIndex} = makeOptions(
        rng,
        count,
        distractors.filter((n) => n >= 0)
    );
    return {
        skillId: "counting",
        level,
        prompt: grouped ? {type: "dots", count, grouped: 10} : {type: "dots", count},
        optionKind: "number",
        promptText: "一共有几个圆点？",
        say: "数一数，一共有几个圆点？",
        options,
        correctIndex,
    };
}
