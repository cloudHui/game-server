// Skill 3 — Arithmetic Fluency.
// Quick add/subtract. Fluency frees cognitive load for harder math later.
// L6 adds doubles/halves (derived-fact strategies); L7 adds missing-number
// equations (early algebraic reasoning).
import {randInt, makeOptions, clamp} from "../util.js";

export const meta = {
    id: "arithmetic",
    name: "Speed Math",
    short: "Add & Subtract",
    icon: "+",
    color: "#f59e0b",
    maxLevel: 7,
    blurb: "Fast, accurate addition and subtraction.",
};

// Returns { max, allowSub } for a level.
function paramsFor(level) {
    const table = [
        {max: 5, allowSub: false},
        {max: 10, allowSub: false},
        {max: 10, allowSub: true},
        {max: 20, allowSub: true},
        {max: 20, allowSub: true, teen: true},
    ];
    return table[clamp(level, 1, 5) - 1];
}

// L6 — doubles & halves ("Double 7", "Half of 16"). Halves use even numbers only.
function doubleHalf(level, rng) {
    const isDouble = rng() < 0.5;
    let n, answer, text, say;
    if (isDouble) {
        n = randInt(rng, 2, 10);
        answer = n * 2;
        text = `Double ${n}`;
        say = `What is double ${n}?`;
    } else {
        n = randInt(rng, 2, 10) * 2;
        answer = n / 2;
        text = `Half of ${n}`;
        say = `What is half of ${n}?`;
    }
    // `n` itself is the classic misconception distractor (tapping the number shown)
    const distractors = [answer + 1, answer - 1, answer + 2, n, answer - 2].filter(
        (x) => x >= 0 && x !== answer
    );
    const {options, correctIndex} = makeOptions(rng, answer, distractors);
    return {
        skillId: "arithmetic",
        level,
        prompt: {type: "double", op: isDouble ? "double" : "half", n, text},
        optionKind: "number",
        promptText: `${text} = ?`,
        say,
        options,
        correctIndex,
    };
}

// L7 — missing-number equations ("? + 4 = 9", "9 − ? = 4"). The full fact is
// stored (a op b = result); `slot` says which operand is hidden.
function missingNumber(level, rng) {
    const op = rng() < 0.5 ? "+" : "−";
    const slot = rng() < 0.5 ? "a" : "b";
    let a, b, result;
    if (op === "+") {
        result = randInt(rng, 5, 12);
        a = randInt(rng, 1, result - 1);
        b = result - a;
    } else {
        a = randInt(rng, 3, 12);
        b = randInt(rng, 1, a - 1);
        result = a - b;
    }
    const answer = slot === "a" ? a : b;
    const text = slot === "a" ? `? ${op} ${b} = ${result}` : `${a} ${op} ? = ${result}`;
    const say =
        op === "+"
            ? slot === "a"
                ? `What plus ${b} makes ${result}?`
                : `${a} plus what makes ${result}?`
            : slot === "a"
                ? `What take away ${b} leaves ${result}?`
                : `${a} take away what leaves ${result}?`;
    const distractors = [answer + 1, answer - 1, answer + 2, result, slot === "a" ? b : a].filter(
        (x) => x >= 0 && x !== answer
    );
    const {options, correctIndex} = makeOptions(rng, answer, distractors);
    return {
        skillId: "arithmetic",
        level,
        prompt: {type: "missing", op, slot, a, b, result, text},
        optionKind: "number",
        promptText: text,
        say,
        options,
        correctIndex,
        pace: 0.85, // symbolic equations need a touch more reading time
    };
}

export function generate(level, rng) {
    if (level >= 7) return missingNumber(level, rng);
    if (level === 6) return doubleHalf(level, rng);
    const p = paramsFor(level);
    const sub = p.allowSub && rng() < 0.5;
    let a, b, answer, text;
    if (sub) {
        a = randInt(rng, 2, p.max);
        b = randInt(rng, 1, a);
        answer = a - b;
        text = `${a} − ${b}`;
    } else {
        a = randInt(rng, p.teen ? 5 : 1, p.max - 1);
        b = randInt(rng, 1, p.max - a);
        answer = a + b;
        text = `${a} + ${b}`;
    }
    const distractors = [answer + 1, answer - 1, answer + 2, answer - 2, answer + 3].filter(
        (n) => n >= 0
    );
    const {options, correctIndex} = makeOptions(rng, answer, distractors);
    return {
        skillId: "arithmetic",
        level,
        prompt: {type: "expr", text},
        optionKind: "number",
        promptText: `${text} = ?`,
        say: `What is ${text.replace("−", "minus").replace("+", "plus")}?`,
        options,
        correctIndex,
    };
}
