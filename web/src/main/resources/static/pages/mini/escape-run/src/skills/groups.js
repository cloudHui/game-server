// Skill 6 — Super Groups: multiplication & division.
// Market-stall fantasy — bags of mangoes, plates of laddoos. Builds equal-group
// thinking → repeated addition → the × symbol → tables → sharing (÷) → fact
// families. NCERT: "How Many Times?" (C1), "Grouping and Sharing" (C2),
// tables & sharing (C3).
import {randInt, pick, makeOptions, clamp} from "../util.js";

export const meta = {
    id: "groups",
    name: "Super Groups",
    short: "Times & Share",
    icon: "×",
    color: "#14b8a6",
    maxLevel: 7,
    blurb: "Equal groups, times tables and fair sharing (× and ÷).",
};

// Wrong answers are the real misconceptions: a group too many/few, the
// neighbouring table, and "added instead of multiplied".
function productDistractors(a, b) {
    const p = a * b;
    return [p + a, p - a, p + b, p - b, a + b, (a + 1) * b, (a - 1) * b].filter(
        (x) => x >= 0 && x !== p
    );
}

// L1–3: equal groups shown as bags of dots (with a repeated-add or × caption).
function equalGroups(level, rng) {
    let bags, per, caption;
    if (level === 1) {
        bags = randInt(rng, 2, 3);
        per = randInt(rng, 2, 3);
        caption = null;
    } else if (level === 2) {
        bags = randInt(rng, 2, 3);
        per = randInt(rng, 2, 4);
        caption = Array(bags).fill(per).join(" + ") + " = ?";
    } else {
        bags = randInt(rng, 2, 3);
        per = randInt(rng, 2, 5);
        caption = `${bags} × ${per}`;
    }
    const answer = bags * per;
    const {options, correctIndex} = makeOptions(rng, answer, productDistractors(bags, per));
    return {
        skillId: "groups",
        level,
        prompt: {type: "groups", bags, per, text: caption},
        optionKind: "number",
        promptText: level >= 3 ? `${bags} × ${per} = ?` : `${bags} groups of ${per}`,
        say: `${bags} groups of ${per}. How many in all?`,
        options,
        correctIndex,
        pace: 0.85,
    };
}

// L4: tables 2, 5, 10 — symbolic. L5: tables 3, 4 + missing factor.
function tables(level, rng) {
    if (level === 4) {
        const b = pick(rng, [2, 5, 10]);
        const a = randInt(rng, 2, 10);
        const answer = a * b;
        const {options, correctIndex} = makeOptions(rng, answer, productDistractors(a, b));
        return finishExpr(level, `${a} × ${b} = ?`, answer, options, correctIndex,
            `${a} times ${b}?`);
    }
    // L5 — tables 3,4 OR a missing-factor equation (division readiness).
    const b = pick(rng, [3, 4]);
    const a = randInt(rng, 2, 10);
    const product = a * b;
    if (rng() < 0.5) {
        const {options, correctIndex} = makeOptions(rng, product, productDistractors(a, b));
        return finishExpr(level, `${a} × ${b} = ?`, product, options, correctIndex, `${a} times ${b}?`);
    }
    // missing factor: ? × b = product  → answer a
    const distract = [a + 1, a - 1, a + 2, b, product / b + 1].filter((x) => x >= 1 && x !== a);
    const {options, correctIndex} = makeOptions(rng, a, distract);
    return finishExpr(level, `? × ${b} = ${product}`, a, options, correctIndex,
        `What times ${b} makes ${product}?`);
}

// L6 — sharing (division) shown as a total dealt onto plates.
function sharing(level, rng) {
    const plates = randInt(rng, 2, 4);
    const each = randInt(rng, 2, 6);
    const total = plates * each;
    const distract = [each + 1, each - 1, each + 2, plates, total].filter(
        (x) => x >= 0 && x !== each
    );
    const {options, correctIndex} = makeOptions(rng, each, distract);
    return {
        skillId: "groups",
        level,
        prompt: {type: "share", total, plates, text: `${total} ÷ ${plates}`},
        optionKind: "number",
        promptText: `${total} ÷ ${plates} = ?`,
        say: `${total} shared between ${plates} plates. How many on each?`,
        options,
        correctIndex,
        pace: 0.85,
    };
}

// L7 — fact families: mixed × and ÷ within tables 2–10.
function factFamily(level, rng) {
    const a = randInt(rng, 2, 10);
    const b = randInt(rng, 2, 10);
    const product = a * b;
    if (rng() < 0.5) {
        const {options, correctIndex} = makeOptions(rng, product, productDistractors(a, b));
        return finishExpr(level, `${a} × ${b} = ?`, product, options, correctIndex, `${a} times ${b}?`);
    }
    // division: product ÷ a = b
    const distract = [b + 1, b - 1, b + 2, a, product].filter((x) => x >= 0 && x !== b);
    const {options, correctIndex} = makeOptions(rng, b, distract);
    return finishExpr(level, `${product} ÷ ${a} = ?`, b, options, correctIndex,
        `${product} shared between ${a}?`);
}

function finishExpr(level, text, answer, options, correctIndex, say) {
    return {
        skillId: "groups",
        level,
        prompt: {type: "gtext", text},
        optionKind: "number",
        promptText: text,
        say,
        options,
        correctIndex,
        pace: 0.85,
    };
}

export function generate(level, rng) {
    const lvl = clamp(level, 1, meta.maxLevel);
    if (lvl <= 3) return equalGroups(lvl, rng);
    if (lvl === 4 || lvl === 5) return tables(lvl, rng);
    if (lvl === 6) return sharing(lvl, rng);
    return factFamily(lvl, rng);
}
