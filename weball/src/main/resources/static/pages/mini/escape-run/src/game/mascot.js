// Pip — the friendly companion who guides onboarding and reacts during play.
// Drawn procedurally (no image assets) so it stays tiny and recolourable for
// collectible character skins later.
import {roundRect, star} from "../engine/renderer.js";

export const MOODS = ["idle", "cheer", "oops", "point"];

// Draw Pip centred at (cx, cy) filling roughly `size` pixels.
export function drawPip(ctx, cx, cy, size, mood = "idle", color = "#7c5cff") {
    const s = size;
    ctx.save();
    ctx.translate(cx, cy);
    const bob = mood === "cheer" ? -s * 0.06 : 0;

    // shadow
    ctx.fillStyle = "rgba(0,0,0,.18)";
    ctx.beginPath();
    ctx.ellipse(0, s * 0.5, s * 0.42, s * 0.12, 0, 0, Math.PI * 2);
    ctx.fill();

    // antenna + star
    ctx.strokeStyle = color;
    ctx.lineWidth = Math.max(2, s * 0.05);
    ctx.beginPath();
    ctx.moveTo(0, -s * 0.34 + bob);
    ctx.lineTo(0, -s * 0.52 + bob);
    ctx.stroke();
    ctx.fillStyle = "#ffd23f";
    star(ctx, 0, -s * 0.58 + bob, 5, s * 0.1, s * 0.045);
    ctx.fill();

    // body
    ctx.fillStyle = color;
    roundRect(ctx, -s * 0.36, -s * 0.34 + bob, s * 0.72, s * 0.72, s * 0.26);
    ctx.fill();
    // belly highlight
    ctx.fillStyle = "rgba(255,255,255,.22)";
    roundRect(ctx, -s * 0.26, -s * 0.24 + bob, s * 0.52, s * 0.3, s * 0.16);
    ctx.fill();

    // face plate
    ctx.fillStyle = "#eef2ff";
    roundRect(ctx, -s * 0.26, -s * 0.18 + bob, s * 0.52, s * 0.34, s * 0.14);
    ctx.fill();

    // eyes
    const eyeY = -s * 0.03 + bob;
    ctx.fillStyle = "#1f2937";
    if (mood === "cheer") {
        // happy arcs
        ctx.lineWidth = Math.max(2, s * 0.04);
        ctx.strokeStyle = "#1f2937";
        for (const ex of [-s * 0.12, s * 0.12]) {
            ctx.beginPath();
            ctx.arc(ex, eyeY, s * 0.06, Math.PI, 0);
            ctx.stroke();
        }
    } else {
        for (const ex of [-s * 0.12, s * 0.12]) {
            ctx.beginPath();
            ctx.arc(ex, eyeY, s * 0.055, 0, Math.PI * 2);
            ctx.fill();
        }
    }

    // mouth
    ctx.strokeStyle = "#1f2937";
    ctx.lineWidth = Math.max(2, s * 0.035);
    ctx.beginPath();
    if (mood === "oops") {
        ctx.arc(0, s * 0.14 + bob, s * 0.06, Math.PI, 0); // small frown
    } else {
        ctx.arc(0, s * 0.08 + bob, s * 0.08, 0.15 * Math.PI, 0.85 * Math.PI); // smile
    }
    ctx.stroke();

    // pointing arm (for "point")
    if (mood === "point") {
        ctx.fillStyle = color;
        roundRect(ctx, s * 0.32, -s * 0.02 + bob, s * 0.24, s * 0.12, s * 0.06);
        ctx.fill();
    }
    ctx.restore();
}

// A standalone <canvas> with Pip drawn, for use inside DOM screens.
export function pipCanvas(size = 72, mood = "idle", color = "#7c5cff") {
    const c = document.createElement("canvas");
    const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
    c.width = size * dpr;
    c.height = size * dpr;
    c.style.width = size + "px";
    c.style.height = size + "px";
    c.setAttribute("aria-hidden", "true");
    const ctx = c.getContext("2d");
    ctx.scale(dpr, dpr);
    drawPip(ctx, size / 2, size / 2 + size * 0.05, size * 0.82, mood, color);
    return c;
}
