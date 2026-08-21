// In-game HUD as a lightweight DOM overlay on top of the canvas: score, fuel,
// gate-progress dots, a pause button and the challenge prompt banner.
import {el} from "../ui/dom.js";
import {skillMeta} from "../skills/index.js";

// Whether a prompt should suppress the leading skill icon: true for equations,
// prompts starting with a number, or ones naming a fraction (½/¼/¾) — where the
// icon would misread (e.g. "×" before "3 groups of 2" → "×3", or "½" before
// "Tap ¼"). A bare "?" in a plain question ("How many dots?") must NOT count —
// equation prompts always carry an operator or "=".
export function looksMathy(text) {
    return /[+\-×÷=]|[\u2212\u00d7\u00f7\u00bc\u00bd\u00be]/.test(text) || /^\s*\d/.test(text);
}

export class Hud {
    constructor(onPause) {
        this.score = el("span", {text: "0"});
        this.fuelBar = el("i", {class: "fuel-bar"});
        this.dots = el("div", {class: "row", style: "gap:6px"});
        this.banner = el("div", {class: "banner hidden"});
        this.hintBtn = el("button", {class: "hint-btn hidden", "aria-label": "查看提示"}, "💡 提示");
        this.pauseBtn = el(
            "button",
            {class: "pill pause", "aria-label": "Pause", onclick: onPause},
            "⏸"
        );

        this.root = el("div", {class: "hud"}, [
            el("div", {}, [
                el("div", {class: "hud-top"}, [
                    el("div", {class: "pill", "aria-label": "Sparks collected"}, [
                        el("span", {text: "✦"}),
                        this.score,
                    ]),
                    this.pauseBtn,
                ]),
                el("div", {class: "fuel-wrap", "aria-label": "Energy"}, [this.fuelBar]),
            ]),
            el("div", {class: "col", style: "gap:8px"}, [
                this.hintBtn,
                el("div", {class: "row", style: "justify-content:center;margin-bottom:8px"}, [this.dots]),
                this.banner,
            ]),
        ]);
    }

    mount() {
        document.getElementById("overlays").appendChild(this.root);
    }

    destroy() {
        this.root.remove();
    }

    setScore(n) {
        this.score.textContent = String(n);
    }

    setFuel(pct) {
        this.fuelBar.style.width = Math.max(0, Math.min(100, pct)) + "%";
        this.fuelBar.style.background =
            pct < 30 ? "linear-gradient(90deg,#f97316,#ef4444)" : "linear-gradient(90deg,#34d399,#a3e635)";
    }

    setGates(total, done) {
        this.dots.innerHTML = "";
        for (let i = 0; i < total; i++) {
            this.dots.appendChild(
                el("span", {
                    style:
                        "width:22px;height:6px;border-radius:999px;background:" +
                        (i < done ? "#a3e635" : "rgba(255,255,255,.25)"),
                })
            );
        }
    }

    showBanner(text, skillId) {
        const m = skillId ? skillMeta(skillId) : null;
        this.banner.textContent = (m && !looksMathy(text) ? m.icon + "  " : "") + text;
        this.banner.classList.remove("hidden");
    }

    hideBanner() {
        this.banner.classList.add("hidden");
    }

    showHint(onHint) {
        this.hintBtn.onclick = onHint;
        this.hintBtn.classList.remove("hidden");
    }

    hideHint() {
        this.hintBtn.classList.add("hidden");
    }
}
