// Garage — spend earned stars on car skins, world themes, and collectible
// characters. Purely cosmetic; earned with stars, never real money.
import {el, showScreen} from "../ui/dom.js";
import {get, save} from "../progress/save.js";
import {carList, worldList, characterList} from "./cosmetics.js";
import {showReveal} from "./reveal.js";
import {sfx} from "../engine/audio.js";

const LIST_KEY = {car: "cars", world: "worlds", character: "characters"};

export function showGarage(nav) {
    const render = () => {
        const st = get();
        const card = el("div", {class: "card sheet-scroll"}, [
            el("h2", {text: "🚗 Garage"}),
            el("div", {class: "chip"}, [el("span", {text: "★"}), `${st.stars} stars to spend`]),
            el("h3", {text: "Characters", style: "align-self:flex-start;margin:6px 0 0"}),
            ...characterList().map((c) => item("character", c, st, render)),
            el("h3", {text: "Cars", style: "align-self:flex-start;margin:10px 0 0"}),
            ...carList().map((c) => item("car", c, st, render)),
            el("h3", {text: "Worlds", style: "align-self:flex-start;margin:10px 0 0"}),
            ...worldList().map((w) => item("world", w, st, render)),
            el("button", {
                class: "btn ghost", onclick: () => {
                    sfx.click();
                    nav.menu();
                }
            }, "← Back"),
        ]);
        showScreen(el("div", {class: "screen sheet"}, [card]));
    };
    render();
}

function swatchColor(kind, obj) {
    return kind === "world" ? obj.sky[0] : obj.color;
}

function item(kind, obj, st, rerender) {
    const owned = st.unlocks[LIST_KEY[kind]].includes(obj.id);
    const equipped =
        st.equipped[kind] === obj.id || (kind === "world" && !st.equipped.world && obj.id === "meadow");
    const swatch = el("span", {
        style: `width:26px;height:26px;border-radius:8px;display:inline-block;background:${swatchColor(kind, obj)}`,
    });

    let action;
    if (equipped) {
        action = el("span", {class: "chip", style: "background:#dcfce7;color:#166534", text: "Equipped"});
    } else if (owned) {
        action = el("button", {
            class: "btn ghost", onclick: () => {
                equip(kind, obj.id);
                sfx.click();
                rerender();
            }
        }, "Use");
    } else {
        const afford = st.stars >= obj.cost;
        action = el(
            "button",
            {
                class: "btn " + (afford ? "good" : "secondary"),
                disabled: !afford,
                onclick: () => {
                    if (buy(kind, obj)) {
                        showReveal({name: obj.name, swatch: swatchColor(kind, obj), onClose: () => rerender()});
                    }
                },
            },
            `★ ${obj.cost}`
        );
    }

    return el("div", {class: "stat"}, [
        el("div", {class: "row", style: "gap:10px;align-items:center"}, [swatch, el("b", {text: obj.name})]),
        action,
    ]);
}

function equip(kind, id) {
    get().equipped[kind] = id;
    save();
}

function buy(kind, obj) {
    const st = get();
    if (st.stars < obj.cost) return false;
    st.stars -= obj.cost;
    st.unlocks[LIST_KEY[kind]].push(obj.id);
    st.equipped[kind] = obj.id;
    save();
    return true;
}
