// Goals — today's Daily Challenge + three rotating Quests. Bite-sized objectives
// that make each visit purposeful. All earned & local; missing a day is fine.
import {el, showScreen} from "../ui/dom.js";
import {get} from "../progress/save.js";
import {skillMeta} from "../skills/index.js";
import {dailySkill, isDailyDone, DAILY_BONUS} from "../progress/daily.js";
import {ensureQuests} from "../progress/quests.js";
import {sfx} from "../engine/audio.js";

export function showGoals(nav) {
    const st = get();
    const skillId = dailySkill(st);
    const done = isDailyDone(st);
    const m = skillMeta(skillId);

    const daily = el("div", {class: "daily-card", style: `background:linear-gradient(160deg, ${m.color}, #0b1026)`}, [
        el("div", {class: "row", style: "justify-content:space-between;width:100%"}, [
            el("b", {text: "🌟 Daily Challenge"}),
            done
                ? el("span", {class: "chip", style: "background:#dcfce7;color:#166534", text: "Done ✓"})
                : el("span", {class: "chip", style: "background:#fde68a;color:#7c3a00", text: `+${DAILY_BONUS} ★`}),
        ]),
        el("div", {style: "font-size:20px", text: `${m.icon}  ${m.name}`}),
        el(
            "button",
            {
                class: "btn " + (done ? "secondary" : "good"), onclick: () => {
                    sfx.click();
                    nav.daily();
                }
            },
            done ? "Play again" : "▶ Play"
        ),
    ]);

    const quests = ensureQuests(st).map((q) => {
        const pct = Math.min(100, Math.round((q.progress / q.target) * 100));
        return el("div", {class: "stat", style: "flex-direction:column;align-items:stretch;gap:6px"}, [
            el("div", {class: "row", style: "justify-content:space-between"}, [
                el("b", {}, [el("span", {text: q.icon + "  "}), q.text]),
                q.done
                    ? el("span", {
                        class: "chip",
                        style: "background:#dcfce7;color:#166534",
                        text: "✓ +" + q.reward + "★"
                    })
                    : el("span", {class: "small muted", text: `${q.progress}/${q.target}`}),
            ]),
            el("div", {class: "bar"}, [el("i", {style: `width:${pct}%`})]),
        ]);
    });

    const card = el("div", {class: "card sheet-scroll"}, [
        el("h2", {text: "Goals"}),
        daily,
        el("h3", {text: "Today's Quests", style: "align-self:flex-start"}),
        ...quests,
        el("p", {class: "small muted", text: "Quests refresh each day. Rewards are yours to keep."}),
        el("button", {
            class: "btn ghost", onclick: () => {
                sfx.click();
                nav.menu();
            }
        }, "← Back"),
    ]);
    showScreen(el("div", {class: "screen sheet"}, [card]));
}
