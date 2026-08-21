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
    counting: "数数 1–20 · 一年级", comparison: "数字比较 · 一至二年级",
    arithmetic: "加法与减法 · 一至二年级", patterning: "规律 · 一至三年级",
    bonds: "凑十与数字组成 · 一至二年级", groups: "分组与平均分（乘除法）· 二至三年级",
    shapes: "图形、空间与影子 · 一至三年级", fractions: "平均分、二分之一与四分之一 · 三年级",
};

export function showParentZone(nav) {
    const a = 10 + Math.floor(Math.random() * 30);
    const b = 10 + Math.floor(Math.random() * 30);
    const input = el("input", {class: "gate-input", type: "tel", inputmode: "numeric", "aria-label": "答案"});
    const err = el("p", {class: "muted small hidden", text: "答案不对，请再试一次。"});

    const gate = el("div", {class: "card"}, [
        el("h2", {text: "家长中心"}),
        el("p", {text: `请先完成家长验证：${a} + ${b} 等于多少？`}),
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
                "进入"
            ),
            el("button", {
                class: "btn secondary", onclick: () => {
                    sfx.click();
                    nav.menu();
                }
            }, "返回"),
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
                el("span", {class: "small muted", text: `等级 ${sk.level}/${m.maxLevel} · 正确率 ${acc}%`}),
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
        el("h2", {text: "学习进度"}),
        el("div", {class: "row"}, [
            el("div", {class: "chip"}, [el("span", {text: "🏁"}), `${st.stats.runs} 次闯关`]),
            el("div", {class: "chip"}, [el("span", {text: "✔"}), `答对 ${st.stats.correct} 题`]),
            el("div", {class: "chip"}, [el("span", {text: "🔥"}), `连续 ${st.streak.count} 天`]),
        ]),
        ...skillRows,

        el("h3", {text: "学习原理", style: "align-self:flex-start"}),
        el("ul", {class: "list small"}, [
            el("li", {html: "<b>自适应难度</b>让练习保持在孩子合适的挑战范围。"}),
            el("li", {html: "<b>间隔复习</b>会在合适时间重新安排旧知识。"}),
            el("li", {html: "<b>重视掌握</b>，进度反映理解程度而不是重复次数。"}),
            el("li", {html: "内容覆盖数感、数量比较、运算熟练度、规律和数字组成。"}),
        ]),

        el("h3", {text: "课程对应", style: "align-self:flex-start"}),
        el("p", {
            class: "small muted",
            style: "align-self:flex-start",
            text: "每条道路对应一项小学数学能力："
        }),
        el("ul", {class: "list small"},
            SKILL_ORDER.map((id) => el("li", {html: `<b>${skillMeta(id).name}</b> — ${NCERT[id]}`}))
        ),

        el("h3", {text: "辅助功能", style: "align-self:flex-start"}),
        toggle("sound", "游戏音效"), toggle("music", "背景音乐"), toggle("voice", "目标与题目朗读"),
        toggle("reduceMotion", "减少动画"), toggle("highContrast", "高对比度"),
        toggle("dyslexiaFont", "易读字体"), toggle("colorblind", "色觉友好模式"),

        el("h3", {text: "难度", style: "align-self:flex-start"}),
        speedSelect(),

        el("h3", {text: "单次游戏", style: "align-self:flex-start"}),
        sessionSelect(),

        el("p", {
            class: "small muted", style: "margin-top:8px",
            text: "隐私：所有记录仅保存在本设备中，无广告、无跟踪，不会上传浏览器数据。"
        }),

        el("div", {class: "row"}, [
            el("button", {
                class: "btn secondary", onclick: () => {
                    sfx.click();
                    nav.menu();
                }
            }, "← 返回"),
            el(
                "button",
                {
                    class: "btn ghost",
                    style: "color:#ef4444",
                    onclick: async () => {
                        if (await AppDialog.confirm("确定清除本设备上的全部学习进度吗？此操作无法撤销。")) {
                            reset();
                            nav.menu();
                        }
                    },
                },
                "清除进度"
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
        const o = el("option", {value: v, text: v === 0 ? "不限时" : `${v} 分钟提醒`});
        if (getSetting("sessionMinutes") === v) o.selected = true;
        sel.appendChild(o);
    }
    return el("label", {class: "toggle"}, ["休息提醒", sel]);
}

function speedSelect() {
    const current = getSetting("speedScale") || 1;
    const sel = el("select", {
        class: "gate-input", style: "width:auto;font-size:18px",
        onchange: (e) => setSetting("speedScale", parseFloat(e.target.value))
    });
    for (const [v, label] of [[0.65, "轻松（最慢）"], [0.8, "平稳"], [1, "标准"]]) {
        const o = el("option", {value: v, text: label});
        if (Math.abs(current - v) < 0.001) o.selected = true;
        sel.appendChild(o);
    }
    return el("label", {class: "toggle"}, ["小汽车速度", sel]);
}
