// Cosmetic unlocks — earned with stars, never bought with money. Pure delight,
// no pay-to-win, no ads.

export const CARS = {
    classic: {name: "红色赛车", color: "#ef4444", cost: 0},
    aqua: {name: "水蓝飞车", color: "#22d3ee", cost: 30},
    sun: {name: "阳光号", color: "#f59e0b", cost: 70},
    grape: {name: "葡萄闪电", color: "#a855f7", cost: 130},
    lime: {name: "青柠快车", color: "#84cc16", cost: 220},
};

export const WORLDS = {
    meadow: {
        name: "阳光草原",
        sky: ["#8ec5ff", "#c8e8ff"],
        ground: "#7ec96a",
        road: "#4a5578",
        line: "rgba(255,255,255,.8)",
        cost: 0,
    },
    desert: {
        name: "金色沙漠",
        sky: ["#ffd98a", "#ffe9c2"],
        ground: "#e6b366",
        road: "#8a6a3f",
        line: "rgba(255,255,255,.75)",
        cost: 90,
    },
    ocean: {
        name: "深海珊瑚",
        sky: ["#3b82f6", "#93c5fd"],
        ground: "#1e6091",
        road: "#0b3b5c",
        line: "rgba(200,240,255,.8)",
        cost: 200,
    },
    space: {
        name: "星际公路",
        sky: ["#0b1026", "#2a1a5e"],
        ground: "#141a3a",
        road: "#26205a",
        line: "rgba(180,160,255,.8)",
        cost: 350,
    },
};

// A special twilight palette used only during Shape Shadows missions, for
// shadow-theatre ambience. Not purchasable — it's a per-mission mood, not a skin.
export const DUSK_WORLD = {
    name: "影子剧场",
    sky: ["#1e1b4b", "#4c1d95"],
    ground: "#2e1065",
    road: "#312e81",
    line: "rgba(216,200,255,.7)",
    cost: 0,
};

export function worldList() {
    return Object.entries(WORLDS).map(([id, w]) => ({id, ...w}));
}

export function carList() {
    return Object.entries(CARS).map(([id, c]) => ({id, ...c}));
}

// Collectible mascot characters (Pip skins). Earned with stars, never money.
export const CHARACTERS = {
    pip: {name: "皮皮", color: "#7c5cff", cost: 0},
    mint: {name: "薄荷", color: "#10b981", cost: 40},
    tango: {name: "探戈", color: "#f97316", cost: 90},
    berry: {name: "莓莓", color: "#ec4899", cost: 160},
    sky: {name: "小天", color: "#0ea5e9", cost: 260},
};

export function characterList() {
    return Object.entries(CHARACTERS).map(([id, c]) => ({id, ...c}));
}
