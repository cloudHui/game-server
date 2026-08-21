// Skill 7 — Shape Shadows: 2D/3D shape sense.
// Togalu shadow-puppet fantasy. The gentle on-ramp for the youngest players
// (Class 1 starts with shapes) rising to properties, silhouettes, solids, equal
// parts (the bridge to Fair Share) and composing shapes.
// NCERT: "What is Long? What is Round?" (C1), "Shapes Around Us", "Shadow Story".
import {randInt, pick, shuffle, clamp} from "../util.js";

export const meta = {
    id: "shapes",
    name: "认识图形",
    short: "图形",
    icon: "▲",
    color: "#f43f5e",
    maxLevel: 7,
    blurb: "认识图形、角、影子、立体形状和平均分。",
};

const NAMEABLE = ["circle", "square", "triangle", "star", "rect", "oval"];
const COLORS = ["#ef4444", "#5aa9ff", "#22c55e", "#f59e0b", "#a855f7", "#ec4899"];
const CORNERS = {circle: 0, oval: 0, triangle: 3, square: 4, rect: 4, diamond: 4, star: 5};
const NAME = {circle: "圆形", square: "正方形", triangle: "三角形", star: "星形", rect: "长方形", oval: "椭圆形"};

// Build 3 distinct object options + correctIndex (makeOptions is number-only).
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

// L1–2 — "Tap the circle": name a shape (tiles are shapes).
function nameShape(level, rng) {
    const palette = level === 1 ? ["circle", "square", "triangle"] : NAMEABLE;
    const target = pick(rng, palette);
    const color = pick(rng, COLORS);
    const rotOf = () => (level >= 2 ? (rng() - 0.5) * 0.9 : 0);
    const make = (shape) => ({shape, color: pick(rng, COLORS), rot: rotOf()});
    const correct = {shape: target, color, rot: rotOf()};
    const {options, correctIndex} = objectOptions(rng, correct, palette.map(make), (o) => o.shape);
    return {
        skillId: "shapes",
        level,
        prompt: {type: "shapename", name: target},
        optionKind: "shape",
        promptText: `选择${NAME[target]}`,
        say: `请选择${NAME[target]}。`,
        options,
        correctIndex,
    };
}

// L3 — "How many corners?": show one shape, pick the number.
function corners(level, rng) {
    const shape = pick(rng, ["triangle", "square", "circle", "diamond", "rect"]);
    const answer = CORNERS[shape];
    const pool = [0, 3, 4, 5].filter((n) => n !== answer);
    const opts = shuffle(rng, [answer, ...shuffle(rng, pool).slice(0, 2)]);
    return {
        skillId: "shapes",
        level,
        prompt: {type: "showshape", shape},
        optionKind: "number",
        promptText: "这个图形有几个角？",
        say: "这个图形有几个角？",
        options: opts,
        correctIndex: opts.indexOf(answer),
        pace: 0.85,
    };
}

// L4 — shadow theatre: a black silhouette; pick the matching shape.
function silhouette(level, rng) {
    const target = pick(rng, NAMEABLE);
    const rot = (rng() - 0.5) * 1.0;
    const make = (shape) => ({shape, color: pick(rng, COLORS), rot: 0});
    const correct = {shape: target, color: pick(rng, COLORS), rot: 0};
    const {options, correctIndex} = objectOptions(rng, correct, NAMEABLE.map(make), (o) => o.shape);
    return {
        skillId: "shapes",
        level,
        prompt: {type: "silhouette", shape: target, rot},
        optionKind: "shape",
        promptText: "哪个图形能形成这个影子？",
        say: "请选择能形成这个影子的图形。",
        options,
        correctIndex,
        pace: 0.85,
    };
}

// L5 — 3D intuition: which solid matches a property? Wording is deliberately
// unambiguous so exactly one of ball/box/can is ever correct (a can rolls AND
// stacks, so "which rolls?"/"which stacks?" would be wrong for it).
function solids(level, rng) {
    const q = pick(rng, [
        {text: "哪一个像球一样到处都是圆的？", say: "哪一个像球一样到处都是圆的？", answer: "ball"},
        {text: "哪一个只有平面？", say: "哪一个立体图形只有平面？", answer: "box"},
        {text: "哪一个既能滚动又能叠放？", say: "哪一个既能滚动又能叠放？", answer: "can"},
    ]);
    const all = ["ball", "box", "can"];
    const options = shuffle(rng, all.map((solid) => ({solid})));
    return {
        skillId: "shapes",
        level,
        prompt: {type: "solidq", q: q.text},
        optionKind: "solid",
        promptText: q.text,
        say: q.say,
        options,
        correctIndex: options.findIndex((o) => o.solid === q.answer),
        pace: 0.85,
    };
}

// L6 — equal parts (the bridge into Fair Share): which is cut into 2 equal parts?
function equalParts(level, rng) {
    const style = pick(rng, ["pie", "bar"]);
    const color = "#5aa9ff";
    const equalTile = {style, cuts: [0.5, 0.5], shaded: [], color};
    // Two visibly-unequal tiles with clearly off-centre, distinct splits.
    const pool = shuffle(rng, [0.25, 0.33, 0.67, 0.75]);
    const unequals = [pool[0], pool[1]].map((a) => ({style, cuts: [a, 1 - a], shaded: [], color}));
    const options = shuffle(rng, [equalTile, ...unequals]);
    return {
        skillId: "shapes",
        level,
        prompt: {type: "shapename", name: "equal"},
        optionKind: "part",
        promptText: "哪一个被平均分成两份？",
        say: "哪一个被平均分成相等的两份？",
        options,
        correctIndex: options.findIndex((o) => Math.abs(o.cuts[0] - 0.5) < 0.001),
        pace: 0.85,
    };
}

// L7 — compose: which two shapes make this silhouette?
const COMPOSITES = [
    {name: "house", parts: ["square", "triangle"]},
    {name: "ice cream", parts: ["triangle", "circle"]},
    {name: "rocket", parts: ["rect", "triangle"]},
];

function compose(level, rng) {
    const target = pick(rng, COMPOSITES);
    const key = (o) => [...o.pair].sort().join("+");
    const correct = {pair: target.parts};
    const distractPool = [
        {pair: ["square", "circle"]},
        {pair: ["triangle", "triangle"]},
        {pair: ["circle", "circle"]},
        {pair: ["rect", "circle"]},
        {pair: ["square", "star"]},
    ].filter((o) => key(o) !== key(correct));
    const {options, correctIndex} = objectOptions(rng, correct, distractPool, key);
    return {
        skillId: "shapes",
        level,
        prompt: {type: "composite", name: target.name, parts: target.parts},
        optionKind: "pair",
        promptText: "哪两个图形能拼成它？",
        say: "请选择能拼成目标图案的两个图形。",
        options,
        correctIndex,
        pace: 0.85,
    };
}

export function generate(level, rng) {
    const lvl = clamp(level, 1, meta.maxLevel);
    if (lvl <= 2) return nameShape(lvl, rng);
    if (lvl === 3) return corners(lvl, rng);
    if (lvl === 4) return silhouette(lvl, rng);
    if (lvl === 5) return solids(lvl, rng);
    if (lvl === 6) return equalParts(lvl, rng);
    return compose(lvl, rng);
}
