// Adventure Map — pick which skill to practise. Shows level, mastery progress
// and a "review" badge for skills the spaced-repetition scheduler flags as due.
import {el, showScreen} from "../ui/dom.js";
import {get} from "../progress/save.js";
import {SKILL_ORDER, skillMeta} from "../skills/index.js";
import {masteryProgress} from "../progress/mastery.js";
import {dueSkills} from "../progress/scheduler.js";
import {isSkillUnlocked, skillUnlockHint} from "../progress/unlocks.js";
import {sfx} from "../engine/audio.js";

export function showMap(nav) {
    const st = get();
    const due = new Set(dueSkills(st));

    const cards = SKILL_ORDER.map((id) => {
        const m = skillMeta(id);
        if (!isSkillUnlocked(st, id)) return lockedCard(m);
        const sk = st.skills[id];
        const prog = Math.round(masteryProgress(sk, id) * 100);
        return el(
            "button",
            {
                class: "world",
                style: `background:linear-gradient(160deg, ${m.color}, ${shade(m.color)})`,
                onclick: () => {
                    sfx.click();
                    nav.play(id);
                },
            },
            [
                el("div", {class: "row", style: "justify-content:space-between;width:100%"}, [
                    el("span", {style: "font-size:26px", text: m.icon}),
                    due.has(id) ? el("span", {class: "chip due", style: "padding:2px 8px", text: "Review"}) : null,
                ]),
                el("div", {}, [
                    el("div", {text: m.name}),
                    el("small", {text: `Level ${sk.level}${sk.mastered ? " · ★ Mastered" : ""}`}),
                ]),
                el("div", {class: "bar", style: "background:rgba(255,255,255,.3)"}, [
                    el("i", {style: `width:${prog}%;background:#fff`}),
                ]),
            ]
        );
    });

    const card = el("div", {class: "card sheet-scroll"}, [
        el("h2", {text: "Adventure Map"}),
        el("p", {
            class: "muted small",
            text: "Pick a skill to practise. ‘Review’ means it’s a great time to revisit it."
        }),
        el("div", {class: "map-grid"}, cards),
        el("button", {
            class: "btn ghost", onclick: () => {
                sfx.click();
                nav.menu();
            }
        }, "← Back"),
    ]);

    showScreen(el("div", {class: "screen sheet"}, [card]));
}

// A "mystery road": a locked skill shown as a teaser with its unlock hint, so
// the child sees there's more to come without being able to jump ahead.
function lockedCard(m) {
    return el(
        "button",
        {
            class: "world locked",
            disabled: "true",
            "aria-label": `Locked road. ${skillUnlockHint(m.id)}`,
        },
        [
            el("div", {class: "row", style: "justify-content:space-between;width:100%"}, [
                el("span", {style: "font-size:26px", text: "🔒"}),
                el("span", {style: "font-size:26px;opacity:.6", text: "?"}),
            ]),
            el("div", {}, [
                el("div", {text: "Mystery Road"}),
                el("small", {text: skillUnlockHint(m.id)}),
            ]),
        ]
    );
}

function shade(hex) {
    const n = parseInt(hex.slice(1), 16);
    const r = Math.max(0, ((n >> 16) & 255) - 40);
    const g = Math.max(0, ((n >> 8) & 255) - 40);
    const b = Math.max(0, (n & 255) - 40);
    return `rgb(${r},${g},${b})`;
}
