// The core gameplay scene: a 3-lane driver where challenge "gates" ask a number
// question and you steer into the lane with the right answer. Kind by design —
// no harsh game-over; a mission always finishes so kids feel accomplished.
import {Loop} from "../engine/loop.js";
import {Input} from "../engine/input.js";
import {Particles} from "../engine/particles.js";
import {sfx, startMusic} from "../engine/audio.js";
import {planMission} from "../progress/scheduler.js";
import {get} from "../progress/save.js";
import {WORLDS, DUSK_WORLD, CARS, CHARACTERS} from "./cosmetics.js";
import {installRunnerRendering} from "./runner-render.js";
import {installRunnerGates, GATE_COUNT} from "./runner-gates.js";
import {Hud} from "./hud.js";
import {announce} from "../ui/dom.js";
import {clamp, lerp} from "../util.js";

const LANES = 3;
const DRIVE_TIME = 2.6; // seconds of driving between gates

export class Runner {
    constructor({renderer, focalSkill, onComplete, onQuit}) {
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
        this.events = {leveledUp: [], mastered: []};

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
        const goal = `本段目标：完成六道${this.focal === "comparison" ? "比大小" : "数学"}题。选择正确车道。`;
        announce(goal);
        if (window.MiniFeedback) window.MiniFeedback.readGoal(goal);
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
        if (isObstacle) this.obstacles.push({lane, x: this.laneX(lane), y});
        else this.pickups.push({lane, x: this.laneX(lane), y, got: false});
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

}

installRunnerGates(Runner);
installRunnerRendering(Runner);
