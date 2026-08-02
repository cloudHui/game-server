// Celebratory unlock reveal — a little "prize" moment when a cosmetic is earned.
// Earned with stars, never money; pure delight, no gacha randomness.
import {el, showScreen, announce} from "../ui/dom.js";
import {pipCanvas} from "./mascot.js";
import {sfx} from "../engine/audio.js";

export function showReveal({title = "New unlock!", name, swatch, icon, subtitle, onClose}) {
    announce(`${title} ${name}`);
    sfx.unlock();
    const confetti = el("div", {class: "confetti", "aria-hidden": "true"},
        ["🎉", "✨", "⭐", "🎊", "✦", "💫"].map((c, i) =>
            el("span", {style: `--i:${i}`, text: c})
        )
    );
    // A skill unlock shows a big glyph badge; a cosmetic shows a colour swatch.
    const badge = icon
        ? el("div", {class: "reveal-swatch reveal-badge", style: `background:${swatch}`}, [
            el("span", {style: "font-size:44px;line-height:1", text: icon}),
        ])
        : el("div", {class: "reveal-swatch", style: `background:${swatch}`});
    const card = el("div", {class: "card reveal-card"}, [
        confetti,
        pipCanvas(92, "cheer", "#7c5cff"),
        el("h2", {text: title}),
        badge,
        el("p", {class: "reveal-name", text: name}),
        subtitle ? el("p", {class: "muted small", text: subtitle}) : null,
        el("button", {
            class: "btn big good", onclick: () => {
                sfx.click();
                onClose && onClose();
            }
        }, "Nice!"),
    ]);
    showScreen(el("div", {class: "screen sheet"}, [card]));
}
