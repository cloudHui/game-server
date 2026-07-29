// The core gameplay scene: a 3-lane driver where challenge "gates" ask a number
// question and you steer into the lane with the right answer. Kind by design —
// no harsh game-over; a mission always finishes so kids feel accomplished.
import { Loop } from "../engine/loop.js";
import { Input } from "../engine/input.js";
import { Particles } from "../engine/particles.js";
import { roundRect, shape as drawShape, car as drawCar, spark as drawSpark, cone as drawCone, solid as drawSolid, partition as drawPartition } from "../engine/renderer.js";
import { sfx, startMusic } from "../engine/audio.js";
import { generateChallenge } from "../skills/index.js";
import { recordResult } from "../progress/mastery.js";
import { planMission, markPracticed } from "../progress/scheduler.js";
import { get, save } from "../progress/save.js";
import { WORLDS, DUSK_WORLD, CARS, CHARACTERS } from "./cosmetics.js";
import { drawPip, pipCanvas } from "./mascot.js";
import { teachContent, hintLine } from "./teach.js";
import { playVoice } from "../engine/voice.js";
import { Hud } from "./hud.js";
import { el, announce } from "../ui/dom.js";
import { clamp, lerp } from "../util.js";

const LANES = 3;
const GATE_COUNT = 6;
const DRIVE_TIME = 2.6; // seconds of driving between gates
const RESOLVE_TIME = 1.0; // feedback pause after a gate

export class Runner {
  constructor({ renderer, focalSkill, onComplete, onQuit }) {
    this.r = renderer;
    this.focal = focalSkill;
    this.onComplete = onComplete;
    this.onQuit = onQuit;
    const st = get();
    // Shape Shadows plays under a twilight "shadow theatre" sky.
    this.world = focalSkill === "shapes" ? DUSK_WORLD : (WORLDS[st.equipped.world] || WORLDS.meadow);
    this.car = CARS[st.equipped.car] || CARS.classic;
    this.character = CHARACTERS[st.equipped.character] || CHARACTERS.pip;
    this.speedScale = clamp(st.settings.speedScale || 1, 0.5, 1.2);
    this.mascotMood = "idle";
    this.mascotTimer = 0;

    this.particles = new Particles();
    this.hud = new Hud(() => this.togglePause());
    this.plan = planMission(st, focalSkill, GATE_COUNT);

    this.state = "driving";
    this.timer = 0;
    this.gateIndex = 0;
    this.speed = 260;
    this.scroll = 0;
    this.paused = false;

    this.lane = 1;
    this.targetLane = 1;
    this.px = 0;
    this.hitFlash = 0;

    this.energy = 40;
    this.sparks = 0;
    this.correct = 0;
    this.total = 0;
    this.skillResults = {}; // skillId -> {seen, correct}
    this.events = { leveledUp: [], mastered: [] };

    this.obstacles = [];
    this.pickups = [];
    this.tiles = null;
    this.challenge = null;
    this.spawnCooldown = 0;
    this.attempt = 1;
    this.gateElapsed = 0;
    this.movedThisGate = false;
    this.hintOffered = false;
    this._leveledThisGate = null;
    this._finished = false;

    this.input = new Input(this.r.canvas, {
      lanes: LANES,
      onAbsolute: (i) => this.setLane(i),
      onRelative: (d) => this.setLane(this.targetLane + d),
      onPause: () => this.togglePause(),
    });

    this.loop = new Loop((dt) => this.step(dt));
  }

  start() {
    this.hud.mount();
    this.hud.setGates(GATE_COUNT, 0);
    this.px = this.laneX(this.lane);
    startMusic("game_calm");
    this.loop.start();
    // Guarded test hook (only when the page is opened with ?e2e) so automated
    // smoke tests can play deterministically. No effect in normal use.
    if (typeof location !== "undefined" && location.search.includes("e2e")) {
      window.__ER = this;
    }
    announce("Mission started. Tap left, middle or right to drive.");
  }

  destroy() {
    this._finished = true;
    this.loop.stop();
    this.input.destroy();
    this.hud.destroy();
    const m = document.getElementById("teach-modal");
    if (m) m.remove();
  }

  laneX(i) {
    const roadW = Math.min(this.r.W, 560) * 0.9;
    const left = (this.r.W - roadW) / 2;
    return left + (roadW * (i + 0.5)) / LANES;
  }
  laneWidth() {
    return (Math.min(this.r.W, 560) * 0.9) / LANES;
  }
  playerY() {
    return this.r.H - Math.max(120, this.r.H * 0.18);
  }

  setLane(i) {
    i = clamp(Math.round(i), 0, LANES - 1);
    if (i !== this.targetLane) {
      this.targetLane = i;
      this.movedThisGate = true; // player is engaged; suppress hesitation hint
      sfx.move();
    }
  }

  togglePause() {
    this.paused = !this.paused;
    if (this.paused) {
      this.loop.stop();
      this._showPause();
    } else {
      this._hidePause();
      this.loop.start();
    }
  }

  _showPause() {
    import("./menu.js").then((m) => m.showPause(this));
  }
  _hidePause() {
    const p = document.getElementById("pause-screen");
    if (p) p.remove();
  }

  // ---- main step ----
  step(dt) {
    if (this.paused) return;
    this.speed = (260 + this.gateIndex * 14) * this.speedScale;
    this.scroll += this.speed * dt;
    this.px = lerp(this.px, this.laneX(this.targetLane), Math.min(1, dt * 12));
    this.lane = this.targetLane;
    if (this.hitFlash > 0) this.hitFlash -= dt;
    if (this.mascotTimer > 0) {
      this.mascotTimer -= dt;
      if (this.mascotTimer <= 0) this.mascotMood = "idle";
    }

    if (this.state === "driving") this._driving(dt);
    else if (this.state === "gate") this._gate(dt);
    else if (this.state === "resolve") this._resolve(dt);

    this.particles.update(dt);
    this.render();
  }

  setMood(mood, dur = 1.2) {
    this.mascotMood = mood;
    this.mascotTimer = dur;
  }

  _driving(dt) {
    this.timer += dt;
    this.energy = clamp(this.energy - 1.2 * dt, 0, 100);
    this.hud.setFuel(this.energy);

    // spawn obstacles + sparks with a spacing validator
    this.spawnCooldown -= dt;
    if (this.spawnCooldown <= 0) {
      this.spawnCooldown = 0.7 + Math.random() * 0.5;
      this._spawn();
    }
    this._moveEntities(dt);
    this._collide();

    if (this.timer >= DRIVE_TIME) {
      this.obstacles = []; // clear the road before a gate (fairness)
      this._startGate();
    }
  }

  _spawn() {
    const lane = (Math.random() * LANES) | 0;
    const y = -40;
    // validator: don't stack a pickup and obstacle in the same lane/time
    const isObstacle = Math.random() < 0.55;
    if (isObstacle) this.obstacles.push({ lane, x: this.laneX(lane), y });
    else this.pickups.push({ lane, x: this.laneX(lane), y, got: false });
  }

  _moveEntities(dt) {
    const dy = this.speed * dt;
    for (const o of this.obstacles) o.y += dy;
    for (const p of this.pickups) p.y += dy;
    this.obstacles = this.obstacles.filter((o) => o.y < this.r.H + 60);
    this.pickups = this.pickups.filter((p) => p.y < this.r.H + 60 && !p.got);
  }

  _collide() {
    const py = this.playerY();
    const cw = this.laneWidth() * 0.5;
    for (const o of this.obstacles) {
      if (Math.abs(o.y - py) < 40 && Math.abs(o.x - this.px) < cw && !o.hit) {
        o.hit = true;
        this.hitFlash = 0.35;
        this.energy = clamp(this.energy - 12, 0, 100);
        this.sparks = Math.max(0, this.sparks - 1);
        this.hud.setScore(this.sparks);
        sfx.wrong();
        this.particles.burst(o.x, o.y, ["#f97316", "#ef4444"], 10);
      }
    }
    for (const p of this.pickups) {
      if (Math.abs(p.y - py) < 40 && Math.abs(p.x - this.px) < cw && !p.got) {
        p.got = true;
        this.sparks += 1;
        this.energy = clamp(this.energy + 6, 0, 100);
        this.hud.setScore(this.sparks);
        sfx.collect();
        this.particles.burst(p.x, p.y, ["#ffd23f", "#a3e635"], 8);
      }
    }
  }

  // ---- gates ----
  _startGate() {
    const skillId = this.plan[this.gateIndex] || this.focal;
    const level = get().skills[skillId].level;
    this.challenge = generateChallenge(skillId, level);
    this.attempt = 1;
    this._resetGateHesitation();
    this._layoutTiles();
    this.state = "gate";
    this.hud.showBanner(this.challenge.promptText, skillId);
    this.hud.hideHint(); // hint only appears if the child hesitates
    // Pip reacts to the theme: points when a shadow/silhouette appears.
    const ptype = this.challenge.prompt && this.challenge.prompt.type;
    if (ptype === "silhouette" || ptype === "composite") this.setMood("point", 1.6);
    announce(this.challenge.say);
  }

  // Hints are offered sparingly — only when the child seems unsure (hasn't moved
  // partway through the gate), instead of on every question.
  _resetGateHesitation() {
    this.gateElapsed = 0;
    this.movedThisGate = false;
    this.hintOffered = false;
  }

  _maybeOfferHint() {
    if (this.hintOffered || this.movedThisGate || this.gateElapsed < 1.4) return;
    this.hintOffered = true;
    this.hud.showHint(() => this._showHint());
  }

  _layoutTiles() {
    this.tiles = this.challenge.options.map((val, i) => ({
      lane: i,
      x: this.laneX(i),
      y: -90,
      value: val,
      state: "idle",
    }));
  }

  _gate(dt) {
    const py = this.playerY();
    this.gateElapsed += dt;
    this._maybeOfferHint();
    // Reading-heavy challenges can slow the tiles a little (challenge.pace ≤ 1).
    const dy = this.speed * (this.challenge.pace || 1) * dt;
    for (const t of this.tiles) t.y += dy;
    if (this.tiles[0].y >= py) {
      this._evaluate();
    }
  }

  _evaluate() {
    const ch = this.challenge;
    const chosen = this.lane;
    const isCorrect = chosen === ch.correctIndex;

    for (const t of this.tiles) {
      if (t.lane === ch.correctIndex) t.state = "correct";
      else if (t.lane === chosen) t.state = "wrong";
      else t.state = "dim";
    }
    this.hud.hideHint();

    // Second attempt (a learning re-do): don't re-score, just reinforce.
    if (this.attempt === 2) {
      if (isCorrect) {
        this.sparks += 1;
        this.hud.setScore(this.sparks);
        this.setMood("cheer", 1.1);
        sfx.correct();
        this.particles.burst(this.px, this.playerY(), ["#22c55e", "#a3e635"], 14);
        announce("You got it!");
        const st = get(); st.stats.recovered += 1; save();
      } else {
        this.setMood("oops", 1.0);
        announce("That's okay — the answer is shown in green.");
      }
      this._toResolve();
      return;
    }

    // First attempt: this is the graded one.
    this.total += 1;
    const rec = (this.skillResults[ch.skillId] ||= { seen: 0, correct: 0 });
    rec.seen += 1;
    const st = get();
    const evt = recordResult(st.skills[ch.skillId], ch.skillId, isCorrect);
    markPracticed(st, ch.skillId);
    this._leveledThisGate = evt.leveledUp ? ch.skillId : null;
    if (evt.leveledUp) this.events.leveledUp.push(ch.skillId);
    if (evt.mastered) this.events.mastered.push(ch.skillId);

    if (isCorrect) {
      this.correct += 1;
      rec.correct += 1;
      this.sparks += 3;
      this.energy = clamp(this.energy + 22, 0, 100);
      this.hud.setScore(this.sparks);
      this.setMood("cheer", 1.1);
      sfx.correct();
      this.particles.burst(this.px, this.playerY(), ["#22c55e", "#a3e635", "#ffd23f"], 20);
      announce("Correct!");
      save();
      this._toResolve();
    } else {
      this.energy = clamp(this.energy - 8, 0, 100);
      this.hud.setFuel(this.energy);
      this.setMood("oops", 1.1);
      sfx.wrong();
      save();
      this._teachThenRetry(); // corrective feedback + second chance
    }
  }

  _toResolve() {
    this.state = "resolve";
    this.timer = 0;
    this.hud.setFuel(this.energy);
  }

  // Show a friendly explanation, then let the child try the same question again.
  _teachThenRetry() {
    const { line, node } = teachContent(this.challenge);
    this._showModal({
      mood: "point",
      title: "Let's look together",
      line,
      node,
      voice: "teach_intro",
      button: "Try again",
      onClose: () => {
        this.attempt = 2;
        this._resetGateHesitation();
        this._layoutTiles();
        this.hud.hideHint();
        this.state = "gate";
      },
    });
  }

  _showHint() {
    if (this.state !== "gate") return;
    const st = get();
    st.stats.hints += 1;
    save();
    this._showModal({
      mood: "point",
      title: "Hint",
      line: hintLine(this.challenge),
      node: null,
      voice: "hint_intro",
      button: "Got it",
      onClose: () => {},
    });
  }

  _resolve(dt) {
    this.timer += dt;
    for (const t of this.tiles) t.y += this.speed * 0.2 * dt;
    if (this.timer >= RESOLVE_TIME) {
      const leveled = this._leveledThisGate;
      this._leveledThisGate = null;
      this.gateIndex += 1;
      this.hud.setGates(GATE_COUNT, this.gateIndex);
      this.hud.hideBanner();
      this.hud.hideHint();
      this.tiles = null;
      this.challenge = null;
      if (this.gateIndex >= GATE_COUNT) {
        this._finish();
      } else if (leveled) {
        this._microLesson(leveled); // just-in-time "I do" before harder gates
      } else {
        this.state = "driving";
        this.timer = 0;
        this.spawnCooldown = 0.6;
      }
    }
  }

  // A quick modeled example when a skill levels up.
  _microLesson(skillId) {
    const level = get().skills[skillId].level;
    const sample = generateChallenge(skillId, level);
    const { line, node } = teachContent(sample);
    this._showModal({
      mood: "cheer",
      title: "Level up! Here's the idea",
      line,
      node,
      voice: "levelup",
      button: "Got it",
      onClose: () => {
        this.state = "driving";
        this.timer = 0;
        this.spawnCooldown = 0.6;
      },
    });
  }

  // Generic modal that pauses the run, shows Pip + a visual, then resumes.
  _showModal({ mood, title, line, node, button, voice, onClose }) {
    this.loop.stop();
    announce(line);
    if (voice) playVoice(voice);
    const card = el("div", { class: "card" }, [
      el("div", { class: "onb-hero" }, [
        pipCanvas(76, mood, this.character.color),
        el("div", { class: "bubble", text: line }),
      ]),
      node ? el("div", { class: "teach-visual" }, [node]) : null,
      el(
        "button",
        {
          class: "btn big good",
          onclick: () => {
            wrap.remove();
            onClose && onClose();
            if (!this._finished) this.loop.start();
          },
        },
        button
      ),
    ]);
    const wrap = el("div", { id: "teach-modal", class: "screen sheet" }, [card]);
    document.getElementById("overlays").appendChild(wrap);
  }

  _finish() {
    this._finished = true;
    this.loop.stop();
    const ratio = this.total ? this.correct / this.total : 0;
    const missionStars = ratio >= 0.85 ? 3 : ratio >= 0.6 ? 2 : ratio >= 0.3 ? 1 : 0;
    const summary = {
      focal: this.focal,
      missionStars,
      correct: this.correct,
      total: this.total,
      sparks: this.sparks,
      skillResults: this.skillResults,
      events: this.events,
    };
    sfx.win();
    setTimeout(() => this.onComplete(summary), 400);
  }

  // ---- rendering ----
  render() {
    const { ctx, W, H } = this.r;
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
    ctx.moveTo(left, 0); ctx.lineTo(left, H);
    ctx.moveTo(left + roadW, 0); ctx.lineTo(left + roadW, H);
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
          t.value.shaded, { cuts: t.value.cuts, color: t.value.color });
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
    const cuts = Array.from({ length: denom }, () => 1 / denom);
    const shaded = Array.from({ length: denom }, (_, i) => i < on);
    drawPartition(ctx, style, cx, cy, r, denom, shaded, { cuts, color: "#f97316" });
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

const FONT =
  '"Segoe UI Rounded","SF Pro Rounded",ui-rounded,"Nunito",system-ui,sans-serif';
