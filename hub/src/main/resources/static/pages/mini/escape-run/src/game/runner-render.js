import {
    roundRect,
    shape as drawShape,
    car as drawCar,
    spark as drawSpark,
    cone as drawCone,
    solid as drawSolid,
    partition as drawPartition
} from "../engine/renderer.js";
import {drawPip} from "./mascot.js";

const LANES = 3;
const FONT = '"Segoe UI Rounded","SF Pro Rounded",ui-rounded,"Nunito",system-ui,sans-serif';

class RunnerRendering {
    render() {
        const {ctx, W, H} = this.r;
        this._drawBackground(ctx, W, H);
        this._drawRoad(ctx, W, H);
        for (const p of this.pickups) if (!p.got) this._drawSpark(ctx, p.x, p.y);
        for (const o of this.obstacles) this._drawCone(ctx, o.x, o.y);
        if (this.tiles) this._drawGate(ctx, W, H);
        this._drawPlayer(ctx);
        this.particles.draw(ctx);
        this._drawMascot(ctx, W, H);
        if (this.hitFlash > 0) {
            ctx.fillStyle = `rgba(239,68,68,${this.hitFlash * 0.5})`;
            ctx.fillRect(0, 0, W, H);
        }
    }

    _drawMascot(ctx, W, H) {
        // Companion Pip rides along in the bottom-left, reacting to the child.
        const size = Math.min(58, W * 0.15);
        const y = this.playerY() + 4;
        drawPip(ctx, size * 0.7, y, size, this.mascotMood, this.character.color);
    }

    _drawBackground(ctx, W, H) {
        const g = ctx.createLinearGradient(0, 0, 0, H);
        g.addColorStop(0, this.world.sky[0]);
        g.addColorStop(1, this.world.sky[1]);
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, W, H);
    }

    _drawRoad(ctx, W, H) {
        const roadW = Math.min(W, 560) * 0.9;
        const left = (W - roadW) / 2;
        ctx.fillStyle = this.world.ground;
        ctx.fillRect(0, 0, W, H);
        ctx.fillStyle = this.world.road;
        ctx.fillRect(left, 0, roadW, H);
        // dashed lane lines, scrolling
        ctx.strokeStyle = this.world.line;
        ctx.lineWidth = 4;
        ctx.setLineDash([26, 26]);
        ctx.lineDashOffset = -(this.scroll % 52);
        for (let i = 1; i < LANES; i++) {
            const x = left + (roadW * i) / LANES;
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, H);
            ctx.stroke();
        }
        ctx.setLineDash([]);
        // road edges
        ctx.strokeStyle = "rgba(255,255,255,.5)";
        ctx.lineWidth = 6;
        ctx.beginPath();
        ctx.moveTo(left, 0);
        ctx.lineTo(left, H);
        ctx.moveTo(left + roadW, 0);
        ctx.lineTo(left + roadW, H);
        ctx.stroke();
    }

    _drawPlayer(ctx) {
        const lw = this.laneWidth();
        drawCar(ctx, this.px, this.playerY(), lw * 0.52, lw * 0.7, this.car.color);
    }

    _drawCone(ctx, x, y) {
        drawCone(ctx, x, y, 1);
    }

    _drawSpark(ctx, x, y) {
        drawSpark(ctx, x, y, 13);
    }

    _drawGate(ctx, W, H) {
        const lw = this.laneWidth();
        const size = Math.min(lw * 0.72, 92);
        for (const t of this.tiles) {
            const colors = {
                idle: ["#ffffff", "#0f172a"],
                correct: ["#22c55e", "#ffffff"],
                wrong: ["#ef4444", "#ffffff"],
                dim: ["#cbd5e1", "#64748b"],
            }[t.state];
            ctx.save();
            ctx.shadowColor = "rgba(0,0,0,.25)";
            ctx.shadowBlur = 10;
            ctx.shadowOffsetY = 4;
            ctx.fillStyle = colors[0];
            roundRect(ctx, t.x - size / 2, t.y - size / 2, size, size, 18);
            ctx.fill();
            ctx.restore();
            if (this.challenge.optionKind === "shape") {
                drawShape(ctx, t.value.shape, t.x, t.y, size * 0.3, t.value.color, t.value.rot || 0);
            } else if (this.challenge.optionKind === "solid") {
                drawSolid(ctx, t.value.solid, t.x, t.y, size * 0.26, "#5aa9ff");
            } else if (this.challenge.optionKind === "part") {
                drawPartition(ctx, t.value.style, t.x, t.y, size * 0.32, t.value.cuts.length,
                    t.value.shaded, {cuts: t.value.cuts, color: t.value.color});
            } else if (this.challenge.optionKind === "pair") {
                drawShape(ctx, t.value.pair[0], t.x - size * 0.2, t.y, size * 0.2, "#64748b");
                drawShape(ctx, t.value.pair[1], t.x + size * 0.2, t.y, size * 0.2, "#94a3b8");
            } else if (this.challenge.optionKind === "frac") {
                ctx.fillStyle = colors[1];
                ctx.font = `900 ${Math.round(size * 0.5)}px ${FONT}`;
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText(t.value.label, t.x, t.y + 2);
            } else {
                ctx.fillStyle = colors[1];
                ctx.font = `900 ${Math.round(size * 0.44)}px ${FONT}`;
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText(String(t.value), t.x, t.y + 2);
            }
            // Non-colour cue for correctness (helps colour-blind players).
            if (t.state === "correct" || t.state === "wrong") {
                ctx.fillStyle = "#ffffff";
                ctx.font = `900 ${Math.round(size * 0.3)}px ${FONT}`;
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText(t.state === "correct" ? "✓" : "✗", t.x, t.y - size * 0.34);
            }
        }
        // Draw the question on top so it's always readable, even as tiles pass by.
        this._drawPrompt(ctx, W);
    }

    _drawPrompt(ctx, W) {
        const ch = this.challenge;
        const p = ch.prompt;
        if (!p) return;
        const cx = W / 2;
        const top = Math.max(132, this.r.H * 0.15);
        if (p.type === "dots") {
            this._drawDots(ctx, cx, top, p.count, p.grouped);
        } else if (p.type === "expr") {
            this._panelText(ctx, cx, top, p.text);
        } else if (p.type === "bond") {
            this._panelText(ctx, cx, top, `${p.given} + ? = ${p.target}`);
        } else if (p.type === "double" || p.type === "missing") {
            this._panelText(ctx, cx, top, p.text);
        } else if (p.type === "gtext") {
            this._panelText(ctx, cx, top, p.text);
        } else if (p.type === "groups") {
            this._drawGroups(ctx, cx, top, p);
        } else if (p.type === "share") {
            this._drawShare(ctx, cx, top, p);
        } else if (p.type === "showshape") {
            this._drawBigShape(ctx, cx, top, p.shape, "#f43f5e");
        } else if (p.type === "silhouette") {
            this._drawBigShape(ctx, cx, top, p.shape, "#0b1026", p.rot);
        } else if (p.type === "composite") {
            this._drawComposite(ctx, cx, top, p.parts, "#0b1026");
        } else if (p.type === "partof") {
            this._drawPartOf(ctx, cx, top, p.total, p.denom);
        } else if (p.type === "partshow") {
            this._drawBigFraction(ctx, cx, top, p.style, p.denom, p.on);
        } else if (p.type === "pattern") {
            this._drawPatternPrompt(ctx, cx, top, p);
        }
    }

    _panelBox(ctx, cx, cy, w, h) {
        ctx.save();
        ctx.fillStyle = "rgba(11,16,38,.72)";
        roundRect(ctx, cx - w / 2, cy - h / 2, w, h, 16);
        ctx.fill();
        ctx.restore();
    }

    _panelText(ctx, cx, cy, text) {
        ctx.font = `900 40px ${FONT}`;
        const w = Math.max(220, Math.ceil(ctx.measureText(text).width) + 44);
        this._panelBox(ctx, cx, cy, w, 74);
        ctx.fillStyle = "#fff";
        ctx.font = `900 40px ${FONT}`;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(text, cx, cy + 2);
    }

    _drawDots(ctx, cx, cy, count, grouped) {
        // Grouped mode lays dots in rows of ten (with a mid-row breath after five)
        // so children can count "tens, then extras" instead of one by one.
        const cols = grouped ? Math.min(10, count) : Math.min(5, Math.ceil(Math.sqrt(count)));
        const rows = Math.ceil(count / cols);
        const gap = grouped ? 24 : 26;
        const midGap = grouped && cols > 5 ? 10 : 0;
        const radius = grouped ? 8 : 9;
        const w = cols * gap + midGap + 30;
        const h = rows * gap + 30;
        this._panelBox(ctx, cx, cy, w, h);
        let n = 0;
        const startX = cx - ((cols - 1) * gap + midGap) / 2;
        const startY = cy - ((rows - 1) * gap) / 2;
        ctx.fillStyle = "#ffd23f";
        for (let r = 0; r < rows; r++) {
            for (let c = 0; c < cols && n < count; c++, n++) {
                const x = startX + c * gap + (c >= 5 ? midGap : 0);
                ctx.beginPath();
                ctx.arc(x, startY + r * gap, radius, 0, Math.PI * 2);
                ctx.fill();
            }
        }
    }

    _drawGroups(ctx, cx, cy, p) {
        // Bags of dots side by side (equal groups). Optional caption below.
        const bags = p.bags, per = p.per;
        const perCols = per <= 3 ? 1 : 2;
        const perRows = Math.ceil(per / perCols);
        const dGap = 15;
        const bagW = perCols * dGap + 16;
        const bagH = perRows * dGap + 16;
        const bagGap = 12;
        const capH = p.text ? 30 : 0;
        const w = bags * bagW + (bags - 1) * bagGap + 24;
        const h = bagH + capH + 24;
        this._panelBox(ctx, cx, cy, w, h);
        const startX = cx - (bags * bagW + (bags - 1) * bagGap) / 2;
        const bagY = cy - h / 2 + 12;
        for (let b = 0; b < bags; b++) {
            const bx = startX + b * (bagW + bagGap);
            ctx.fillStyle = "rgba(20,184,166,.25)";
            ctx.strokeStyle = "#2dd4bf";
            ctx.lineWidth = 2;
            roundRect(ctx, bx, bagY, bagW, bagH, 10);
            ctx.fill();
            ctx.stroke();
            let n = 0;
            const dx0 = bx + bagW / 2 - ((perCols - 1) * dGap) / 2;
            const dy0 = bagY + bagH / 2 - ((perRows - 1) * dGap) / 2;
            ctx.fillStyle = "#ffd23f";
            for (let r = 0; r < perRows; r++)
                for (let c = 0; c < perCols && n < per; c++, n++) {
                    ctx.beginPath();
                    ctx.arc(dx0 + c * dGap, dy0 + r * dGap, 5.5, 0, Math.PI * 2);
                    ctx.fill();
                }
        }
        if (p.text) {
            ctx.fillStyle = "#fff";
            ctx.font = `900 22px ${FONT}`;
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillText(p.text, cx, bagY + bagH + 16);
        }
    }

    _drawShare(ctx, cx, cy, p) {
        // A pile of items above, plates below, plus the division caption.
        const total = p.total, plates = p.plates;
        const cols = Math.min(6, total);
        const rows = Math.ceil(total / cols);
        const dGap = 15, pGap = 10;
        const plateW = 34;
        const w = Math.max(cols * dGap + 24, plates * (plateW + pGap) + 24);
        const h = rows * dGap + 24 + 30 + 30;
        this._panelBox(ctx, cx, cy, w, h);
        // pile
        let n = 0;
        const dx0 = cx - ((cols - 1) * dGap) / 2;
        const dy0 = cy - h / 2 + 16;
        ctx.fillStyle = "#ffd23f";
        for (let r = 0; r < rows; r++)
            for (let c = 0; c < cols && n < total; c++, n++) {
                ctx.beginPath();
                ctx.arc(dx0 + c * dGap, dy0 + r * dGap, 5.5, 0, Math.PI * 2);
                ctx.fill();
            }
        // plates
        const plateY = dy0 + rows * dGap + 14;
        const px0 = cx - (plates * (plateW + pGap) - pGap) / 2;
        for (let i = 0; i < plates; i++) {
            ctx.fillStyle = "#e2e8f0";
            ctx.strokeStyle = "#94a3b8";
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.ellipse(px0 + i * (plateW + pGap) + plateW / 2, plateY, plateW / 2, plateW / 4, 0, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();
        }
        ctx.fillStyle = "#fff";
        ctx.font = `900 22px ${FONT}`;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(p.text, cx, plateY + 22);
    }

    _drawBigShape(ctx, cx, cy, kind, color, rot = 0) {
        const r = 42;
        this._panelBox(ctx, cx, cy, r * 3, r * 2.7);
        drawShape(ctx, kind, cx, cy, r, color, rot);
    }

    _drawComposite(ctx, cx, cy, parts, color) {
        // Stack the two shapes to make a little object (house, rocket…), as a shadow.
        const r = 34;
        this._panelBox(ctx, cx, cy, r * 3, r * 3.4);
        drawShape(ctx, parts[1], cx, cy - r * 0.95, r * 0.85, color);
        drawShape(ctx, parts[0], cx, cy + r * 0.55, r, color);
    }

    _drawPartOf(ctx, cx, cy, total, denom) {
        // The whole group split into `denom` equal shares (dashed dividers) so the
        // "fair share" is visible: count one share.
        const per = total / denom;
        const perCols = per <= 3 ? per : Math.ceil(per / 2);
        const perRows = Math.ceil(per / perCols);
        const dGap = 15;
        const shareW = perCols * dGap + 14;
        const shareGap = 10;
        const shareH = perRows * dGap + 14;
        const w = denom * shareW + (denom - 1) * shareGap + 24;
        const h = shareH + 24;
        this._panelBox(ctx, cx, cy, w, h);
        const startX = cx - (denom * shareW + (denom - 1) * shareGap) / 2;
        const y0 = cy - shareH / 2;
        for (let s = 0; s < denom; s++) {
            const sx = startX + s * (shareW + shareGap);
            ctx.strokeStyle = "rgba(255,255,255,.35)";
            ctx.setLineDash([4, 4]);
            ctx.lineWidth = 1.5;
            roundRect(ctx, sx, y0, shareW, shareH, 8);
            ctx.stroke();
            ctx.setLineDash([]);
            let n = 0;
            const dx0 = sx + shareW / 2 - ((perCols - 1) * dGap) / 2;
            const dy0 = y0 + shareH / 2 - ((perRows - 1) * dGap) / 2;
            ctx.fillStyle = "#ffd23f";
            for (let r = 0; r < perRows; r++)
                for (let c = 0; c < perCols && n < per; c++, n++) {
                    ctx.beginPath();
                    ctx.arc(dx0 + c * dGap, dy0 + r * dGap, 5.5, 0, Math.PI * 2);
                    ctx.fill();
                }
        }
    }

    _drawBigFraction(ctx, cx, cy, style, denom, on) {
        const r = 42;
        this._panelBox(ctx, cx, cy, r * 3.2, r * 2.7);
        const cuts = Array.from({length: denom}, () => 1 / denom);
        const shaded = Array.from({length: denom}, (_, i) => i < on);
        drawPartition(ctx, style, cx, cy, r, denom, shaded, {cuts, color: "#f97316"});
    }

    _drawPatternPrompt(ctx, cx, cy, p) {
        const seq = p.sequence;
        // Shrink the gap when a long sequence wouldn't fit the screen width.
        const gap = Math.min(46, (this.r.W - 28) / seq.length);
        const w = seq.length * gap + 20;
        this._panelBox(ctx, cx, cy, w, 64);
        const startX = cx - ((seq.length - 1) * gap) / 2;
        for (let i = 0; i < seq.length; i++) {
            const x = startX + i * gap;
            const item = seq[i];
            if (item == null) {
                ctx.fillStyle = "#fff";
                ctx.font = `900 34px ${FONT}`;
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText("?", x, cy + 2);
            } else if (p.kind === "shape") {
                drawShape(ctx, item.shape, x, cy, Math.min(16, gap * 0.36), item.color);
            } else {
                ctx.fillStyle = "#fff";
                ctx.font = `900 26px ${FONT}`;
                ctx.textAlign = "center";
                ctx.textBaseline = "middle";
                ctx.fillText(String(item), x, cy + 2);
            }
        }
    }
}

export function installRunnerRendering(Runner) {
    for (const name of Object.getOwnPropertyNames(RunnerRendering.prototype)) {
        if (name !== "constructor") Object.defineProperty(Runner.prototype, name,
            Object.getOwnPropertyDescriptor(RunnerRendering.prototype, name));
    }
}
