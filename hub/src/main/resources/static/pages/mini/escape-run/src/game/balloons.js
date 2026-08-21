// End-of-mission bonus: pop the balloons for extra stars — against a short
// timer to make it exciting. A joyful reward moment; you can Continue anytime.
// After a Super Groups mission the balloons carry a skip-count (2s/5s/10s) so
// children chant the times-tables as they pop — passive, playful exposure.
import {el, showScreen, announce} from "../ui/dom.js";
import {pipCanvas} from "./mascot.js";
import {sfx} from "../engine/audio.js";

const COLORS = ["#ef4444", "#5aa9ff", "#22c55e", "#f59e0b", "#a855f7", "#ec4899"];
const COUNT = 5;
const TIME_MS = 7000;

export function showBalloonPop({onDone, countBy = null}) {
    let popped = 0;
    let bonus = 0;
    let finished = false;
    let timer = null;
    const poppedNums = [];
    const totalEl = el("span", {text: "0"});
    const field = el("div", {class: "balloon-field"});
    const timeBar = el("i", {style: "width:100%"});
    const chantEl = countBy ? el("div", {class: "chip balloon-chant", text: "…"}) : null;

    const finish = () => {
        if (finished) return;
        finished = true;
        if (timer) clearInterval(timer);
        onDone(bonus);
    };

    for (let i = 0; i < COUNT; i++) {
        const value = 1 + Math.floor(Math.random() * 3); // 1..3
        const face = countBy ? String((i + 1) * countBy) : "★";
        const b = el(
            "button",
            {
                class: "balloon",
                style: `--c:${COLORS[i % COLORS.length]};--i:${i}`,
                "aria-label": countBy ? `Pop ${face}` : "Pop the balloon"
            },
            face
        );
        b.onclick = () => {
            if (finished || b.classList.contains("popped")) return;
            b.classList.add("popped");
            bonus += value;
            popped += 1;
            totalEl.textContent = String(bonus);
            sfx.star();
            if (countBy) {
                // Keep the number visible and chant the sequence in order.
                poppedNums.push((i + 1) * countBy);
                poppedNums.sort((a, c) => a - c);
                chantEl.textContent = poppedNums.join(" … ");
                announce(String((i + 1) * countBy));
            } else {
                b.textContent = `+${value}`;
            }
            if (popped >= COUNT) setTimeout(finish, 500);
        };
        field.appendChild(b);
    }

    const bubbleText = countBy
        ? `Bonus round! Pop them and count by ${countBy}s!`
        : "Bonus round! Pop the balloons before time runs out!";
    const card = el("div", {class: "card"}, [
        el("div", {class: "onb-hero"}, [
            pipCanvas(72, "cheer", "#7c5cff"),
            el("div", {class: "bubble", text: bubbleText}),
        ]),
        el("div", {class: "timebar", "aria-hidden": "true"}, [timeBar]),
        field,
        chantEl,
        el("div", {class: "chip"}, [el("span", {text: "★"}), " 奖励：", totalEl]),
        el("button", {
            class: "btn good", onclick: () => {
                sfx.click();
                finish();
            }
        }, "继续"),
    ]);
    const goal = countBy ? `奖励环节！戳破气球，按 ${countBy} 个一组数数。` : "奖励环节！戳破气球获得更多星星。";
    announce(goal);
    if (window.MiniFeedback) window.MiniFeedback.readGoal(goal);
    showScreen(el("div", {class: "screen sheet"}, [card]));

    // Countdown — auto-continues (keeping whatever was popped) when it runs out.
    const started = Date.now();
    timer = setInterval(() => {
        const frac = Math.max(0, 1 - (Date.now() - started) / TIME_MS);
        timeBar.style.width = frac * 100 + "%";
        if (frac <= 0) finish();
    }, 80);
}
