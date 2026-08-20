// First-run onboarding: a short, hands-on, mascot-guided tutorial that teaches
// the three core mechanics by doing (steer, collect/dodge, answer the gate).
// "Show, then guided practice" — minimal reading, big tap targets, skippable.
import {el, showScreen, announce} from "../ui/dom.js";
import {pipCanvas} from "./mascot.js";
import {spark, cone} from "../engine/renderer.js";
import {sfx, unlock as unlockAudio} from "../engine/audio.js";
import {playVoice} from "../engine/voice.js";
import {get, save} from "../progress/save.js";

// A small canvas showing an in-game item (spark or cone), drawn with the same
// helpers the runner uses so the tutorial always matches gameplay.
function itemCanvas(kind, size = 52) {
    const c = document.createElement("canvas");
    const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
    c.width = size * dpr;
    c.height = size * dpr;
    c.style.width = c.style.height = size + "px";
    c.setAttribute("aria-hidden", "true");
    const ctx = c.getContext("2d");
    ctx.scale(dpr, dpr);
    if (kind === "spark") spark(ctx, size / 2, size / 2, 16);
    else cone(ctx, size / 2, size / 2 + 2, 1.15);
    return c;
}

export function showOnboarding(onDone) {
    let step = 0;
    const finish = () => {
        get().onboarded = true;
        save();
        onDone();
    };

    const bubble = (mood, text, body, footer, voiceId) => {
        announce(text);
        if (window.MiniFeedback) window.MiniFeedback.readGoal(text);
        else if (voiceId) playVoice(voiceId);
        const card = el("div", {class: "card"}, [
            el("div", {class: "onb-hero"}, [
                pipCanvas(84, mood, "#7c5cff"),
                el("div", {class: "bubble", text}),
            ]),
            body || null,
            footer,
            el("button", {class: "btn ghost", onclick: finish}, "跳过"),
        ]);
        showScreen(el("div", {class: "screen sheet"}, [card]));
    };

    // Step 1 — steering (tap each lane once to continue)
    const stepSteer = () => {
        const tapped = new Set();
        const car = el("div", {class: "onb-car"});
        const lanes = [0, 1, 2].map((i) =>
            el(
                "button",
                {
                    class: "onb-lane",
                    "aria-label": ["左车道", "中间车道", "右车道"][i],
                    onclick: () => {
                        unlockAudio();
                        sfx.move();
                        car.style.left = `calc(${i * 33.33}% + 8px)`;
                        tapped.add(i);
                        if (tapped.size === 3) {
                            next.disabled = false;
                            next.classList.remove("secondary");
                            next.classList.add("good");
                        }
                    },
                },
                ["◀", "▲", "▶"][i]
            )
        );
        const track = el("div", {class: "onb-track"}, [...lanes, car]);
        const next = el(
            "button",
            {
                class: "btn secondary", disabled: true, onclick: () => {
                    sfx.click();
                    step = 1;
                    stepCollect();
                }
            },
            "下一步"
        );
        bubble(
            "point",
            "你好！我是皮皮。依次点击左、中、右车道，试着控制小汽车！",
            track,
            next,
            "onb_steer"
        );
    };

    // Step 2 — collect sparks, dodge cones. Draw the *actual* in-game visuals so
    // the tutorial matches gameplay exactly.
    const stepCollect = () => {
        const demo = el("div", {class: "onb-demo"}, [
            el("div", {class: "onb-item"}, [itemCanvas("spark")]),
            el("div", {class: "onb-item"}, [itemCanvas("cone")]),
            el("div", {class: "onb-item"}, [itemCanvas("spark")]),
        ]);
        bubble(
            "cheer",
            "收集星光可以得分，记得绕开路障！",
            demo,
            el("button", {
                class: "btn good", onclick: () => {
                    sfx.click();
                    step = 2;
                    stepGate();
                }
            }, "下一步"),
            "onb_collect"
        );
    };

    // Step 3 — the challenge gate (tap the correct answer)
    const stepGate = () => {
        const dots = el("div", {class: "onb-dots"}, [0, 1, 2].map(() => el("span")));
        const options = [2, 3, 4];
        const row = el("div", {class: "onb-options"});
        const done = el("button", {
            class: "btn good hidden",
            onclick: () => {
                sfx.click();
                step = 3;
                stepReady();
            },
        }, "下一步");
        options.forEach((v) => {
            const b = el(
                "button",
                {
                    class: "onb-opt",
                    onclick: () => {
                        if (v === 3) {
                            sfx.correct();
                            b.classList.add("correct");
                            [...row.children].forEach((c) => (c.disabled = true));
                            done.classList.remove("hidden");
                            announce("答对了！有三个圆点，请驶入数字 3 的车道。");
                            if (window.MiniFeedback) window.MiniFeedback.praise("很棒！");
                        } else {
                            sfx.wrong();
                            b.classList.add("wrong");
                            announce("还差一点，再数一数圆点并重试。");
                        }
                    },
                },
                String(v)
            );
            row.appendChild(b);
        });
        bubble(
            "point",
            "看到题目时，把小汽车开进正确答案的车道。这儿有几个圆点？",
            el("div", {class: "col", style: "gap:14px"}, [dots, row]),
            done,
            "onb_gate"
        );
    };

    // Step 4 — ready to play
    const stepReady = () => {
        bubble(
            "cheer",
            "你已经学会了！准备好一边开车一边学数学了吗？出发吧！",
            null,
            el("button", {
                class: "btn big good", onclick: () => {
                    sfx.click();
                    finish();
                }
            }, "▶ 开始"),
            "onb_ready"
        );
    };

    stepSteer();
}
