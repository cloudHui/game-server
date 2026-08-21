// Results screen — celebrate the mission, show skills practised, and reward.
import {el, showScreen, starsRow, announce} from "../ui/dom.js";
import {skillMeta} from "../skills/index.js";
import {sfx} from "../engine/audio.js";
import {playVoice} from "../engine/voice.js";

export function showResults(summary, earned, nav, breakDue = false, extras = {}) {
    const {dailyBonus = 0, quests = [], achievements = [], balloonBonus = 0} = extras;
    const praise =
        summary.missionStars === 3
            ? "太棒了！ ⭐⭐⭐"
            : summary.missionStars === 2
                ? "很棒！"
                : summary.missionStars === 1
                    ? "做得好！"
                    : "继续加油！";

    announce(`闯关完成。${praise}答对 ${summary.correct} 题，共 ${summary.total} 题。`);
    if (window.MiniFeedback) window.MiniFeedback.praise("很棒！");
    else playVoice("win");

    const skillRows = Object.entries(summary.skillResults).map(([id, r]) => {
        const m = skillMeta(id);
        return el("div", {class: "stat"}, [
            el("div", {class: "row", style: "gap:8px;align-items:center"}, [
                el("span", {style: `color:${m.color};font-size:20px`, text: m.icon}),
                el("b", {text: m.name}),
            ]),
            el("span", {class: "chip", text: `${r.correct}/${r.seen}`}),
        ]);
    });

    const celebrations = [];
    if (balloonBonus > 0) {
        celebrations.push(el("div", {class: "chip", style: "background:#fce7f3;color:#9d174d"}, [
            el("span", {text: "🎈"}), `气球奖励！+${balloonBonus} ★`,
        ]));
    }
    if (dailyBonus > 0) {
        celebrations.push(el("div", {class: "chip", style: "background:#fef9c3;color:#7c3a00"}, [
            el("span", {text: "🌟"}), `每日挑战完成！+${dailyBonus} ★`,
        ]));
    }
    for (const q of quests) {
        celebrations.push(el("div", {class: "chip", style: "background:#dcfce7;color:#166534"}, [
            el("span", {text: "📜"}), `任务：${q.text}（+${q.reward}★）`,
        ]));
    }
    for (const a of achievements) {
        celebrations.push(el("div", {class: "chip", style: "background:#ede9fe;color:#5b21b6"}, [
            el("span", {text: a.icon}), `获得奖杯：${a.name}！`,
        ]));
    }
    for (const id of summary.events.leveledUp) {
        celebrations.push(el("div", {class: "chip", style: "background:#e0e7ff;color:#3730a3"}, [
            el("span", {text: "⬆"}), `${skillMeta(id).name}升级了！`,
        ]));
    }
    for (const id of summary.events.mastered) {
        celebrations.push(el("div", {class: "chip", style: "background:#fef9c3;color:#854d0e"}, [
            el("span", {text: "🏅"}), `已掌握：${skillMeta(id).name}！`,
        ]));
    }

    const card = el("div", {class: "card sheet-scroll"}, [
        el("h2", {text: "闯关完成！"}),
        breakDue
            ? el("div", {class: "chip", style: "background:#dcfce7;color:#166534"}, [
                el("span", {text: "🌿"}), "今天练得很好，休息一下吧！",
            ])
            : null,
        starsRow(summary.missionStars),
        el("p", {class: "muted", text: praise}),
        el("div", {class: "row"}, [
            el("div", {class: "chip"}, [el("span", {text: "✦"}), `${summary.sparks} 颗星光`]),
            el("div", {class: "chip"}, [el("span", {text: "★"}), `+${earned} 颗星`]),
        ]),
        celebrations.length ? el("div", {class: "chips"}, celebrations) : null,
        el("h3", {text: "本次练习", style: "align-self:flex-start;margin:6px 0 0"}),
        ...skillRows,
        el("button", {
            class: "btn big good", onclick: () => {
                sfx.click();
                nav.again(summary.focal);
            }
        }, "▶ 再玩一次"),
        el("div", {class: "row"}, [
            el("button", {
                class: "btn secondary", onclick: () => {
                    sfx.click();
                    nav.map();
                }
            }, "🗺 地图"),
            el("button", {
                class: "btn secondary", onclick: () => {
                    sfx.click();
                    nav.menu();
                }
            }, "🏠 主菜单"),
        ]),
    ]);

    showScreen(el("div", {class: "screen sheet"}, [card]));
}
