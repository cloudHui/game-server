// Achievements — sticky milestone badges shown in the Trophy Room. Once earned,
// always kept (even if stars are later spent). All local, celebratory, no chores.
import { SKILL_ORDER } from "../skills/index.js";

export const ACHIEVEMENTS = [
  { id: "first_mission", name: "First Drive", icon: "🚗", desc: "Finish your first mission",
    check: (s) => s.stats.runs >= 1 },
  { id: "ten_missions", name: "Road Tripper", icon: "🛣️", desc: "Finish 10 missions",
    check: (s) => s.stats.runs >= 10 },
  { id: "sharp", name: "Sharp Shooter", icon: "🎯", desc: "Earn a 3-star mission",
    check: (s) => s.stats.threeStars >= 1 },
  { id: "streak_3", name: "On a Roll", icon: "🔥", desc: "Play 3 days in a row",
    check: (s) => s.streak.count >= 3 },
  { id: "streak_7", name: "Week Warrior", icon: "📅", desc: "Play 7 days in a row",
    check: (s) => s.streak.count >= 7 },
  { id: "climber", name: "Level Up", icon: "⬆️", desc: "Reach level 2 in any skill",
    check: (s) => SKILL_ORDER.some((id) => s.skills[id].level >= 2) },
  { id: "trailblazer", name: "Trailblazer", icon: "🧭", desc: "Reach level 6 in any skill",
    check: (s) => SKILL_ORDER.some((id) => s.skills[id].level >= 6) },
  { id: "master_one", name: "Skill Master", icon: "🏅", desc: "Master a skill",
    check: (s) => SKILL_ORDER.some((id) => s.skills[id].mastered) },
  { id: "master_all", name: "Grand Master", icon: "👑", desc: "Master every skill",
    check: (s) => SKILL_ORDER.every((id) => s.skills[id].mastered) },
  { id: "market_master", name: "Market Master", icon: "🛒", desc: "Master Super Groups",
    check: (s) => s.skills.groups && s.skills.groups.mastered },
  { id: "shape_scout", name: "Shape Scout", icon: "🔺", desc: "Finish a Shape Shadows mission",
    check: (s) => (s.stats.playedSkills || []).includes("shapes") },
  { id: "fair_friend", name: "Fair Friend", icon: "🍰", desc: "Finish a Fair Share mission",
    check: (s) => (s.stats.playedSkills || []).includes("fractions") },
  { id: "daily_hero", name: "Daily Hero", icon: "🌟", desc: "Complete a Daily Challenge",
    check: (s) => s.daily.claimedKey != null },
  { id: "quester", name: "Quest Champ", icon: "📜", desc: "Complete 3 quests",
    check: (s) => s.stats.questsDone >= 3 },
  { id: "collector", name: "Collector", icon: "🎁", desc: "Unlock 3 characters",
    check: (s) => (s.unlocks.characters || []).length >= 3 },
  { id: "saver", name: "Star Saver", icon: "✨", desc: "Earn 40 stars in total",
    check: (s) => s.stats.starsEarned >= 40 },
];

// Unlock any newly-earned achievements; returns the freshly unlocked ones.
export function checkAchievements(state) {
  const have = new Set(state.achievements.unlocked);
  const fresh = [];
  for (const a of ACHIEVEMENTS) {
    if (!have.has(a.id) && a.check(state)) {
      state.achievements.unlocked.push(a.id);
      fresh.push(a);
    }
  }
  return fresh;
}
