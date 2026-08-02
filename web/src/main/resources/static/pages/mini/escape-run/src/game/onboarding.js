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
        if (voiceId) playVoice(voiceId);
        const card = el("div", {class: "card"}, [
            el("div", {class: "onb-hero"}, [
                pipCanvas(84, mood, "#7c5cff"),
                el("div", {class: "bubble", text}),
            ]),
            body || null,
            footer,
            el("button", {class: "btn ghost", onclick: finish}, "Skip"),
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
                    "aria-label": ["Left lane", "Middle lane", "Right lane"][i],
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
            "Next"
        );
        bubble(
            "point",
            "Hi! I'm Pip. Tap the left, middle and right lanes to steer your car!",
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
            "Grab the stars to score — and steer around the cones!",
            demo,
            el("button", {
                class: "btn good", onclick: () => {
                    sfx.click();
                    step = 2;
                    stepGate();
                }
            }, "Next"),
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
        }, "Next");
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
                            announce("Yes! Three dots. You steer into the ‘3’ lane.");
                        } else {
                            sfx.wrong();
                            b.classList.add("wrong");
                            announce("Not quite — count the dots and try again.");
                        }
                    },
                },
                String(v)
            );
            row.appendChild(b);
        });
        bubble(
            "point",
            "When a question pops up, drive into the lane with the right answer. How many dots?",
            el("div", {class: "col", style: "gap:14px"}, [dots, row]),
            done,
            "onb_gate"
        );
    };

    // Step 4 — ready to play
    const stepReady = () => {
        bubble(
            "cheer",
            "You've got it! Ready to race and learn? Let's go!",
            null,
            el("button", {
                class: "btn big good", onclick: () => {
                    sfx.click();
                    finish();
                }
            }, "▶  Start"),
            "onb_ready"
        );
    };

    stepSteer();
}
