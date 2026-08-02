// Parent Zone — behind a simple grown-up gate. Local-only progress dashboard,
// the learning science behind each skill, accessibility & session settings.
import {el, showScreen} from "../ui/dom.js";
import {get, reset} from "../progress/save.js";
import {SKILL_ORDER, skillMeta} from "../skills/index.js";
import {masteryProgress, accuracy} from "../progress/mastery.js";
import {setSetting, getSetting} from "../progress/settings.js";
import {sfx} from "../engine/audio.js";

// Which NCERT (India) primary-maths strand each skill maps onto. Shown to
// grown-ups so the play maps transparently to the school curriculum.
const NCERT = {
    counting: "Numbers 1–20 · Class 1",
    comparison: "Comparing Numbers · Class 1–2",
    arithmetic: "Addition & Subtraction · Class 1–2",
    patterning: "Patterns · Class 1–3",
    bonds: "Making 10 / Number Bonds · Class 1–2",
    groups: "Grouping & Sharing (× ÷) · Class 2–3",
    shapes: "Shapes & Space / Shadows · Class 1–3",
    fractions: "Fair Share (halves & quarters) · Class 3",
};

export function showParentZone(nav) {
    const a = 10 + Math.floor(Math.random() * 30);
    const b = 10 + Math.floor(Math.random() * 30);
    const input = el("input", {class: "gate-input", type: "tel", inputmode: "numeric", "aria-label": "Answer"});
    const err = el("p", {class: "muted small hidden", text: "Not quite — try again."});

    const gate = el("div", {class: "card"}, [
        el("h2", {text: "For Grown-ups"}),
        el("p", {text: `Please solve to continue: what is ${a} + ${b}?`}),
        input,
        err,
        el("div", {class: "row"}, [
            el(
                "button",
                {
                    class: "btn good",
                    onclick: () => {
                        if (parseInt(input.value, 10) === a + b) {
                            sfx.click();
                            dashboard(nav);
                        } else {
                            err.classList.remove("hidden");
                            input.value = "";
                        }
                    },
                },
                "Enter"
            ),
            el("button", {
                class: "btn secondary", onclick: () => {
                    sfx.click();
                    nav.menu();
                }
            }, "Back"),
        ]),
    ]);
    showScreen(el("div", {class: "screen sheet"}, [gate]));
}

function dashboard(nav) {
    const st = get();
    const skillRows = SKILL_ORDER.map((id) => {
        const m = skillMeta(id);
        const sk = st.skills[id];
        const pct = Math.round(masteryProgress(sk, id) * 100);
        const acc = Math.round(accuracy(sk) * 100);
        return el("div", {style: "width:100%"}, [
            el("div", {class: "row", style: "justify-content:space-between;width:100%"}, [
                el("b", {text: m.name}),
                el("span", {class: "small muted", text: `Lvl ${sk.level}/${m.maxLevel} · ${acc}% acc`}),
            ]),
            el("div", {class: "bar", style: "margin:4px 0 2px"}, [el("i", {style: `width:${pct}%`})]),
            el("p", {class: "small muted", text: m.blurb}),
        ]);
    });

    const toggle = (key, label) =>
        el("label", {class: "toggle"}, [
            label,
            el("input", {
                type: "checkbox",
                class: "sw",
                checked: !!getSetting(key),
                onchange: (e) => setSetting(key, e.target.checked),
            }),
        ]);

    const card = el("div", {class: "card sheet-scroll"}, [
        el("h2", {text: "Progress"}),
        el("div", {class: "row"}, [
            el("div", {class: "chip"}, [el("span", {text: "🏁"}), `${st.stats.runs} missions`]),
            el("div", {class: "chip"}, [el("span", {text: "✔"}), `${st.stats.correct} correct`]),
            el("div", {class: "chip"}, [el("span", {text: "🔥"}), `${st.streak.count}-day streak`]),
        ]),
        ...skillRows,

        el("h3", {text: "How this helps", style: "align-self:flex-start"}),
        el("ul", {class: "list small"}, [
            el("li", {html: "<b>Adaptive difficulty</b> keeps each skill in the child’s ‘just-right’ zone."}),
            el("li", {html: "<b>Spaced review</b> resurfaces skills over time so they stick."}),
            el("li", {html: "<b>Mastery, not grinding</b> — progress reflects understanding."}),
            el("li", {html: "Skills chosen for strong evidence: subitizing, magnitude, fluency, patterning, number bonds."}),
        ]),

        el("h3", {text: "Curriculum (NCERT)", style: "align-self:flex-start"}),
        el("p", {
            class: "small muted",
            style: "align-self:flex-start",
            text: "Each road maps to an India NCERT primary-maths strand:"
        }),
        el("ul", {class: "list small"},
            SKILL_ORDER.map((id) => el("li", {html: `<b>${skillMeta(id).name}</b> — ${NCERT[id]}`}))
        ),

        el("h3", {text: "Accessibility", style: "align-self:flex-start"}),
        toggle("sound", "Sound effects"),
        toggle("music", "Music"),
        toggle("voice", "Narration (voice)"),
        toggle("reduceMotion", "Reduce motion"),
        toggle("highContrast", "High contrast"),
        toggle("dyslexiaFont", "Easy-read text"),
        toggle("colorblind", "Colour-blind friendly"),

        el("h3", {text: "Difficulty", style: "align-self:flex-start"}),
        speedSelect(),

        el("h3", {text: "Session", style: "align-self:flex-start"}),
        sessionSelect(),

        el("p", {
            class: "small muted", style: "margin-top:8px",
            text: "Privacy: everything is stored only on this device. No accounts, no ads, no tracking, no data ever leaves the browser."
        }),

        el("div", {class: "row"}, [
            el("button", {
                class: "btn secondary", onclick: () => {
                    sfx.click();
                    nav.menu();
                }
            }, "← Back"),
            el(
                "button",
                {
                    class: "btn ghost",
                    style: "color:#ef4444",
                    onclick: () => {
                        if (window.confirm("Reset all progress on this device? This can’t be undone.")) {
                            reset();
                            nav.menu();
                        }
                    },
                },
                "Reset progress"
            ),
        ]),
    ]);
    showScreen(el("div", {class: "screen sheet"}, [card]));
}

function sessionSelect() {
    const sel = el("select", {
        class: "gate-input", style: "width:auto;font-size:18px",
        onchange: (e) => setSetting("sessionMinutes", parseInt(e.target.value, 10))
    });
    for (const v of [0, 10, 15, 20, 30]) {
        const o = el("option", {value: v, text: v === 0 ? "No limit" : `${v} min reminder`});
        if (getSetting("sessionMinutes") === v) o.selected = true;
        sel.appendChild(o);
    }
    return el("label", {class: "toggle"}, ["Break reminder", sel]);
}

function speedSelect() {
    const current = getSetting("speedScale") || 1;
    const sel = el("select", {
        class: "gate-input", style: "width:auto;font-size:18px",
        onchange: (e) => setSetting("speedScale", parseFloat(e.target.value))
    });
    for (const [v, label] of [[0.65, "Relaxed (slowest)"], [0.8, "Steady"], [1, "Normal"]]) {
        const o = el("option", {value: v, text: label});
        if (Math.abs(current - v) < 0.001) o.selected = true;
        sel.appendChild(o);
    }
    return el("label", {class: "toggle"}, ["Car speed", sel]);
}
