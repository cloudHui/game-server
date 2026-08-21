import {generateChallenge} from "../skills/index.js";
import {recordResult} from "../progress/mastery.js";
import {markPracticed} from "../progress/scheduler.js";
import {get, save} from "../progress/save.js";
import {teachContent, hintLine} from "./teach.js";
import {playVoice} from "../engine/voice.js";
import {sfx} from "../engine/audio.js";
import {pipCanvas} from "./mascot.js";
import {el, announce} from "../ui/dom.js";
import {clamp} from "../util.js";

export const GATE_COUNT = 6;
const RESOLVE_TIME = 1.0;

class RunnerGates {
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
        if (window.MiniFeedback) window.MiniFeedback.readGoal(this.challenge.say);
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
                announce("答对了！");
                if (window.MiniFeedback) window.MiniFeedback.praise("很棒！");
                const st = get();
                st.stats.recovered += 1;
                save();
            } else {
                this.setMood("oops", 1.0);
                announce("没关系，正确答案已经用绿色标出。");
            }
            this._toResolve();
            return;
        }

        // First attempt: this is the graded one.
        this.total += 1;
        const rec = (this.skillResults[ch.skillId] ||= {seen: 0, correct: 0});
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
            announce("答对了！");
            if (window.MiniFeedback) window.MiniFeedback.praise("很棒！");
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
        const {line, node} = teachContent(this.challenge);
        this._showModal({
            mood: "point",
            title: "我们一起看看",
            line,
            node,
            voice: "teach_intro",
            button: "再试一次",
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
            title: "提示",
            line: hintLine(this.challenge),
            node: null,
            voice: "hint_intro",
            button: "知道了",
            onClose: () => {
            },
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
        const {line, node} = teachContent(sample);
        this._showModal({
            mood: "cheer",
            title: "升级了！看看新方法",
            line,
            node,
            voice: "levelup",
            button: "知道了",
            onClose: () => {
                this.state = "driving";
                this.timer = 0;
                this.spawnCooldown = 0.6;
            },
        });
    }

    // Generic modal that pauses the run, shows Pip + a visual, then resumes.
    _showModal({mood, title, line, node, button, voice, onClose}) {
        this.loop.stop();
        announce(line);
        if (window.MiniFeedback) window.MiniFeedback.readGoal(line);
        else if (voice) playVoice(voice);
        const card = el("div", {class: "card"}, [
            el("div", {class: "onb-hero"}, [
                pipCanvas(76, mood, this.character.color),
                el("div", {class: "bubble", text: line}),
            ]),
            node ? el("div", {class: "teach-visual"}, [node]) : null,
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
        const wrap = el("div", {id: "teach-modal", class: "screen sheet"}, [card]);
        document.getElementById("overlays").appendChild(wrap);
    }


}

export function installRunnerGates(Runner) {
    for (const name of Object.getOwnPropertyNames(RunnerGates.prototype)) {
        if (name !== "constructor") Object.defineProperty(Runner.prototype, name,
            Object.getOwnPropertyDescriptor(RunnerGates.prototype, name));
    }
}
