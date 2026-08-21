// Title menu + pause overlay.
import {el, showScreen} from "../ui/dom.js";
import {get} from "../progress/save.js";
import {isDailyDone} from "../progress/daily.js";
import {ensureQuests} from "../progress/quests.js";
import {nextSkill} from "../progress/scheduler.js";
import {skillMeta} from "../skills/index.js";
import {sfx, unlock as unlockAudio, startMusic} from "../engine/audio.js";

export function showMenu(nav) {
    const st = get();
    const streak = st.streak.count || 0;
    const dailyOpen = !isDailyDone(st);
    const questsLeft = ensureQuests(st).filter((q) => !q.done).length;
    const next = nextSkill(st);
    const nm = skillMeta(next);

    const card = el("div", {class: "card sheet-scroll"}, [
        el("h1", {class: "logo", text: "小汽车\n比大小"}),
        el("p", {class: "tagline", text: "适合 6–8 岁儿童的数学闯关"}),
        el("div", {class: "spacer"}),
        el(
            "button",
            {
                class: "btn big good",
                style: "flex-direction:column;gap:2px;line-height:1.1",
                onclick: () => {
                    unlockAudio();
                    sfx.click();
                    nav.play(next);
                },
            },
            [
                el("span", {text: "▶ 开始闯关"}),
                el("span", {style: "font-size:14px;font-weight:800;opacity:.9", text: `下一项：${nm.icon} ${nm.name}`}),
            ]
        ),
        el("div", {class: "row"}, [
            el("button", {
                class: "btn secondary", onclick: () => {
                    sfx.click();
                    nav.map();
                }
            }, "🗺 闯关地图"),
            el("button", {
                class: "btn secondary", onclick: () => {
                    sfx.click();
                    nav.garage();
                }
            }, "🚗 小车库"),
        ]),
        el("div", {class: "row"}, [
            menuButton("🌟 今日目标", dailyOpen || questsLeft ? "•" : "", () => {
                sfx.click();
                nav.goals();
            }),
            el("button", {
                class: "btn secondary", onclick: () => {
                    sfx.click();
                    nav.trophies();
                }
            }, "🏆 奖杯"),
        ]),
        el("div", {class: "row", style: "margin-top:4px"}, [
            el("div", {class: "chip"}, [el("span", {text: "🔥"}), `连续 ${streak} 天`]),
            el("div", {class: "chip"}, [el("span", {text: "★"}), `${st.stars} 颗星`]),
        ]),
        el(
            "button",
            {
                class: "btn ghost", style: "margin-top:6px", onclick: () => {
                    sfx.click();
                    nav.parent();
                }
            },
            "👪 家长中心"
        ),
        el(
            "button",
            {
                class: "btn ghost", onclick: () => {
                    sfx.click();
                    nav.onboarding();
                }
            },
            "❔ 游戏方法"
        ),
        el("p", {class: "muted small", text: "无广告 · 可离线使用 · 游戏记录只保存在本设备"}),
    ]);

    showScreen(el("div", {class: "screen sheet"}, [card]));
    startMusic("menu_calm");
}

// A secondary button with an optional attention dot badge.
function menuButton(label, badge, onclick) {
    const btn = el("button", {class: "btn secondary", style: "position:relative", onclick}, label);
    if (badge) {
        btn.appendChild(
            el("span", {
                style:
                    "position:absolute;top:-4px;right:-4px;width:16px;height:16px;border-radius:50%;background:#ef4444;border:2px solid #fff",
            })
        );
    }
    return btn;
}

export function showPause(runner) {
    const wrap = el("div", {id: "pause-screen", class: "screen sheet"}, [
        el("div", {class: "card"}, [
            el("h2", {text: "游戏暂停"}),
            el("button", {class: "btn big good", onclick: () => runner.togglePause()}, "▶ 继续"),
            el(
                "button",
                {
                    class: "btn secondary", onclick: () => {
                        runner.destroy();
                        runner.onQuit();
                    }
                },
                "返回主菜单"
            ),
        ]),
    ]);
    document.getElementById("overlays").appendChild(wrap);
}
