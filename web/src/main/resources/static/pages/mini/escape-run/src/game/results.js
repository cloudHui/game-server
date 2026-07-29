// Results screen — celebrate the mission, show skills practised, and reward.
import { el, showScreen, starsRow, announce } from "../ui/dom.js";
import { skillMeta } from "../skills/index.js";
import { sfx } from "../engine/audio.js";
import { playVoice } from "../engine/voice.js";

export function showResults(summary, earned, nav, breakDue = false, extras = {}) {
  const { dailyBonus = 0, quests = [], achievements = [], balloonBonus = 0 } = extras;
  const praise =
    summary.missionStars === 3
      ? "AMAZING! ⭐⭐⭐"
      : summary.missionStars === 2
      ? "GREAT JOB!"
      : summary.missionStars === 1
      ? "NICE WORK!"
      : "GOOD TRY — YOU’VE GOT THIS!";

  announce(`Mission complete. ${praise} ${summary.correct} of ${summary.total} correct.`);
  playVoice("win");

  const skillRows = Object.entries(summary.skillResults).map(([id, r]) => {
    const m = skillMeta(id);
    return el("div", { class: "stat" }, [
      el("div", { class: "row", style: "gap:8px;align-items:center" }, [
        el("span", { style: `color:${m.color};font-size:20px`, text: m.icon }),
        el("b", { text: m.name }),
      ]),
      el("span", { class: "chip", text: `${r.correct}/${r.seen}` }),
    ]);
  });

  const celebrations = [];
  if (balloonBonus > 0) {
    celebrations.push(el("div", { class: "chip", style: "background:#fce7f3;color:#9d174d" }, [
      el("span", { text: "🎈" }), `Balloon bonus! +${balloonBonus} ★`,
    ]));
  }
  if (dailyBonus > 0) {
    celebrations.push(el("div", { class: "chip", style: "background:#fef9c3;color:#7c3a00" }, [
      el("span", { text: "🌟" }), `Daily done! +${dailyBonus} ★`,
    ]));
  }
  for (const q of quests) {
    celebrations.push(el("div", { class: "chip", style: "background:#dcfce7;color:#166534" }, [
      el("span", { text: "📜" }), `Quest: ${q.text} (+${q.reward}★)`,
    ]));
  }
  for (const a of achievements) {
    celebrations.push(el("div", { class: "chip", style: "background:#ede9fe;color:#5b21b6" }, [
      el("span", { text: a.icon }), `Trophy: ${a.name}!`,
    ]));
  }
  for (const id of summary.events.leveledUp) {
    celebrations.push(el("div", { class: "chip", style: "background:#e0e7ff;color:#3730a3" }, [
      el("span", { text: "⬆" }), `Level up: ${skillMeta(id).name}!`,
    ]));
  }
  for (const id of summary.events.mastered) {
    celebrations.push(el("div", { class: "chip", style: "background:#fef9c3;color:#854d0e" }, [
      el("span", { text: "🏅" }), `Mastered: ${skillMeta(id).name}!`,
    ]));
  }

  const card = el("div", { class: "card sheet-scroll" }, [
    el("h2", { text: "Mission Complete!" }),
    breakDue
      ? el("div", { class: "chip", style: "background:#dcfce7;color:#166534" }, [
          el("span", { text: "🌿" }), "Great session — time for a break!",
        ])
      : null,
    starsRow(summary.missionStars),
    el("p", { class: "muted", text: praise }),
    el("div", { class: "row" }, [
      el("div", { class: "chip" }, [el("span", { text: "✦" }), `${summary.sparks} sparks`]),
      el("div", { class: "chip" }, [el("span", { text: "★" }), `+${earned} stars`]),
    ]),
    celebrations.length ? el("div", { class: "chips" }, celebrations) : null,
    el("h3", { text: "Skills practised", style: "align-self:flex-start;margin:6px 0 0" }),
    ...skillRows,
    el("button", { class: "btn big good", onclick: () => { sfx.click(); nav.again(summary.focal); } }, "▶  Play Again"),
    el("div", { class: "row" }, [
      el("button", { class: "btn secondary", onclick: () => { sfx.click(); nav.map(); } }, "🗺 Map"),
      el("button", { class: "btn secondary", onclick: () => { sfx.click(); nav.menu(); } }, "🏠 Menu"),
    ]),
  ]);

  showScreen(el("div", { class: "screen sheet" }, [card]));
}
