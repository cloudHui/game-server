// Trophy Room — a sticker-album of achievement badges (earned + still-locked).
import {el, showScreen} from "../ui/dom.js";
import {get} from "../progress/save.js";
import {ACHIEVEMENTS} from "../progress/achievements.js";
import {sfx} from "../engine/audio.js";

export function showTrophies(nav) {
    const have = new Set(get().achievements.unlocked);
    const count = have.size;

    const grid = el("div", {class: "trophy-grid"},
        ACHIEVEMENTS.map((a) => {
            const owned = have.has(a.id);
            return el("div", {class: "trophy" + (owned ? "" : " locked"), title: a.desc}, [
                el("div", {class: "trophy-icon", text: owned ? a.icon : "🔒"}),
                el("b", {text: a.name}),
                el("span", {class: "small muted", text: a.desc}),
            ]);
        })
    );

    const card = el("div", {class: "card sheet-scroll"}, [
        el("h2", {text: "🏆 奖杯室"}),
        el("div", {class: "chip"}, [el("span", {text: "🏅"}), `已获得 ${count} / ${ACHIEVEMENTS.length}`]),
        grid,
        el("button", {
            class: "btn ghost", onclick: () => {
                sfx.click();
                nav.menu();
            }
        }, "← 返回"),
    ]);
    showScreen(el("div", {class: "screen sheet"}, [card]));
}
