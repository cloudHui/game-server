// Title menu + pause overlay.
import { el, showScreen } from "../ui/dom.js";
import { get } from "../progress/save.js";
import { isDailyDone } from "../progress/daily.js";
import { ensureQuests } from "../progress/quests.js";
import { nextSkill } from "../progress/scheduler.js";
import { skillMeta } from "../skills/index.js";
import { sfx, unlock as unlockAudio, startMusic } from "../engine/audio.js";

export function showMenu(nav) {
  const st = get();
  const streak = st.streak.count || 0;
  const dailyOpen = !isDailyDone(st);
  const questsLeft = ensureQuests(st).filter((q) => !q.done).length;
  const next = nextSkill(st);
  const nm = skillMeta(next);

  const card = el("div", { class: "card sheet-scroll" }, [
    el("h1", { class: "logo", text: "ESCAPE\nRUN" }),
    el("p", { class: "tagline", text: "A math adventure for ages 6–8" }),
    el("div", { class: "spacer" }),
    el(
      "button",
      {
        class: "btn big good",
        style: "flex-direction:column;gap:2px;line-height:1.1",
        onclick: () => {
          unlockAudio();
          sfx.click();
          nav.play(next);
        },
      },
      [
        el("span", { text: "▶  Play" }),
        el("span", { style: "font-size:14px;font-weight:800;opacity:.9", text: `Next: ${nm.icon} ${nm.name}` }),
      ]
    ),
    el("div", { class: "row" }, [
      el("button", { class: "btn secondary", onclick: () => { sfx.click(); nav.map(); } }, "🗺 Map"),
      el("button", { class: "btn secondary", onclick: () => { sfx.click(); nav.garage(); } }, "🚗 Garage"),
    ]),
    el("div", { class: "row" }, [
      menuButton("🌟 Goals", dailyOpen || questsLeft ? "•" : "", () => { sfx.click(); nav.goals(); }),
      el("button", { class: "btn secondary", onclick: () => { sfx.click(); nav.trophies(); } }, "🏆 Trophies"),
    ]),
    el("div", { class: "row", style: "margin-top:4px" }, [
      el("div", { class: "chip" }, [el("span", { text: "🔥" }), `${streak}-day streak`]),
      el("div", { class: "chip" }, [el("span", { text: "★" }), `${st.stars} stars`]),
    ]),
    el(
      "button",
      { class: "btn ghost", style: "margin-top:6px", onclick: () => { sfx.click(); nav.parent(); } },
      "👪 Grown-ups"
    ),
    el(
      "button",
      { class: "btn ghost", onclick: () => { sfx.click(); nav.onboarding(); } },
      "❔ How to play"
    ),
    el("p", { class: "muted small", text: "No ads · No sign-in · Works offline · Your data stays on this device" }),
  ]);

  showScreen(el("div", { class: "screen sheet" }, [card]));
  startMusic("menu_calm");
}

// A secondary button with an optional attention dot badge.
function menuButton(label, badge, onclick) {
  const btn = el("button", { class: "btn secondary", style: "position:relative", onclick }, label);
  if (badge) {
    btn.appendChild(
      el("span", {
        style:
          "position:absolute;top:-4px;right:-4px;width:16px;height:16px;border-radius:50%;background:#ef4444;border:2px solid #fff",
      })
    );
  }
  return btn;
}

export function showPause(runner) {
  const wrap = el("div", { id: "pause-screen", class: "screen sheet" }, [
    el("div", { class: "card" }, [
      el("h2", { text: "Paused" }),
      el("button", { class: "btn big good", onclick: () => runner.togglePause() }, "▶  Resume"),
      el(
        "button",
        { class: "btn secondary", onclick: () => { runner.destroy(); runner.onQuit(); } },
        "Quit to Menu"
      ),
    ]),
  ]);
  document.getElementById("overlays").appendChild(wrap);
}
