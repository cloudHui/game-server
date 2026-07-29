// App controller — owns the renderer and navigates between screens.
import { Renderer } from "./engine/renderer.js";
import { load, get, save, recordPlayDay } from "./progress/save.js";
import { syncSystemPrefs } from "./progress/settings.js";
import { dueSkills } from "./progress/scheduler.js";
import { SKILL_ORDER } from "./skills/index.js";
import { stopMusic, unlock as unlockAudio } from "./engine/audio.js";import { clearOverlays } from "./ui/dom.js";
import { registerServiceWorker } from "./engine/update.js";
import { Runner } from "./game/runner.js";
import { showMenu } from "./game/menu.js";
import { showOnboarding } from "./game/onboarding.js";
import { showMap } from "./game/map.js";
import { showGarage } from "./game/garage.js";
import { showResults } from "./game/results.js";
import { showBalloonPop } from "./game/balloons.js";
import { showParentZone } from "./game/parentzone.js";
import { showGoals } from "./game/goals.js";
import { showTrophies } from "./game/trophies.js";
import { dailySkill, claimDaily } from "./progress/daily.js";
import { applyRunToQuests } from "./progress/quests.js";
import { checkAchievements } from "./progress/achievements.js";
import { evaluateSkillUnlocks, isSkillUnlocked } from "./progress/unlocks.js";
import { showReveal } from "./game/reveal.js";

class App {
  constructor() {
    load();
    syncSystemPrefs();
    this.renderer = new Renderer(document.getElementById("game"));
    this.runner = null;
    this.sessionStart = Date.now();

    this.nav = {
      menu: () => this.menu(),
      map: () => showMap(this.nav),
      garage: () => showGarage(this.nav),
      parent: () => showParentZone(this.nav),
      goals: () => showGoals(this.nav),
      trophies: () => showTrophies(this.nav),
      onboarding: () => this.onboarding(),
      play: (skillId) => this.startRun(skillId),
      again: (skillId) => this.startRun(skillId),
      daily: () => this.startRun(dailySkill(get()), { daily: true }),
    };
  }

  boot() {
    if (!get().onboarded) this.onboarding();
    else this.menu();
  }

  onboarding() {
    if (this.runner) { this.runner.destroy(); this.runner = null; }
    clearOverlays();
    showOnboarding(() => this.menu());
  }

  menu() {
    stopMusic();
    if (this.runner) { this.runner.destroy(); this.runner = null; }
    clearOverlays();
    showMenu(this.nav);
  }

  recommendedSkill() {
    const st = get();
    const due = dueSkills(st);
    if (due.length) return due[0];
    // otherwise the least-developed unlocked skill
    const open = SKILL_ORDER.filter((id) => isSkillUnlocked(st, id));
    return [...open].sort((a, b) => st.skills[a].level - st.skills[b].level)[0];
  }

  startRun(skillId, opts = {}) {
    const focal = skillId || this.recommendedSkill();
    this.currentIsDaily = !!opts.daily;
    const st = get();
    st.lastPlayedSkill = focal; // advance the menu's skill rotation
    save();
    if (this.runner) this.runner.destroy();
    clearOverlays();
    this.runner = new Runner({
      renderer: this.renderer,
      focalSkill: focal,
      onComplete: (summary) => this.finishRun(summary),
      onQuit: () => this.menu(),
    });
    this.runner.start();
  }

  finishRun(summary) {
    if (this.runner) { this.runner.destroy(); this.runner = null; }
    const st = get();
    st.stats.runs += 1;
    st.stats.correct += summary.correct;
    st.stats.seen += summary.total;
    if (summary.missionStars >= 3) st.stats.threeStars += 1;
    // Remember which skills the child has finished a mission in (for badges).
    if (summary.focal && !st.stats.playedSkills.includes(summary.focal)) {
      st.stats.playedSkills.push(summary.focal);
    }

    // Base reward + daily bonus.
    let earned = summary.missionStars + Math.floor(summary.sparks / 5);
    let dailyBonus = 0;
    if (this.currentIsDaily) {
      dailyBonus = claimDaily(st);
      earned += dailyBonus;
    }
    st.stars += earned;

    // Quests (rewards are added to stars inside applyRunToQuests).
    const questsDone = applyRunToQuests(st, summary);
    const questStars = questsDone.reduce((n, q) => n + q.reward, 0);
    st.stats.questsDone += questsDone.length;
    st.stats.starsEarned += earned + questStars;
    // Did this run open a new road on the Adventure Map?
    const newSkills = evaluateSkillUnlocks(st);
    recordPlayDay();
    save();
    this.currentIsDaily = false;

    // Balloon-pop bonus round, then any new-skill reveals, then results.
    // Super Groups missions turn the bonus into skip-count practice.
    const countBy = summary.focal === "groups" ? [2, 5, 10][Math.floor(Math.random() * 3)] : null;
    showBalloonPop({
      countBy,
      onDone: (balloonBonus) => {
        st.stars += balloonBonus;
        st.stats.starsEarned += balloonBonus;
        const achievements = checkAchievements(st); // after all stars are added
        save();
        const limit = st.settings.sessionMinutes;
        const breakDue = limit > 0 && (Date.now() - this.sessionStart) / 60000 >= limit;
        const toResults = () => showResults(summary, earned, this.nav, breakDue, {
          dailyBonus, quests: questsDone, achievements, balloonBonus,
        });
        this._revealSkills(newSkills, toResults);
      },
    });
  }

  // Show a big "new road unlocked" celebration for each freshly-opened skill,
  // one after another, then continue to `done`.
  _revealSkills(metas, done) {
    if (!metas || !metas.length) return done();
    const [m, ...rest] = metas;
    showReveal({
      title: "New road unlocked!",
      name: m.name,
      icon: m.icon,
      swatch: m.color,
      subtitle: m.blurb,
      onClose: () => this._revealSkills(rest, done),
    });
  }
}

const app = new App();
app.boot();

// Resume/unlock audio on the first user interaction (autoplay policy).
const unlockOnce = () => unlockAudio();
window.addEventListener("pointerdown", unlockOnce, { once: true });
window.addEventListener("keydown", unlockOnce, { once: true });

// Register the service worker (offline + self-updating; shows an update toast
// when a new version is deployed).
// 嵌套在站点随机 context-path 下时跳过 SW，避免缓存路径错乱
try { /* registerServiceWorker(); */ } catch (e) {}
