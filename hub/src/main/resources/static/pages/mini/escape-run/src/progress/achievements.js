// Achievements — sticky milestone badges shown in the Trophy Room. Once earned,
// always kept (even if stars are later spent). All local, celebratory, no chores.
import {SKILL_ORDER} from "../skills/index.js";

export const ACHIEVEMENTS = [
    {
        id: "first_mission", name: "第一次出发", icon: "🚗", desc: "完成第一次闯关",
        check: (s) => s.stats.runs >= 1
    },
    {
        id: "ten_missions", name: "公路旅行家", icon: "🛣️", desc: "完成 10 次闯关",
        check: (s) => s.stats.runs >= 10
    },
    {
        id: "sharp", name: "百发百中", icon: "🎯", desc: "获得一次三星评价",
        check: (s) => s.stats.threeStars >= 1
    },
    {
        id: "streak_3", name: "连续进步", icon: "🔥", desc: "连续玩 3 天",
        check: (s) => s.streak.count >= 3
    },
    {
        id: "streak_7", name: "一周勇士", icon: "📅", desc: "连续玩 7 天",
        check: (s) => s.streak.count >= 7
    },
    {
        id: "climber", name: "能力升级", icon: "⬆️", desc: "任一能力达到第 2 级",
        check: (s) => SKILL_ORDER.some((id) => s.skills[id].level >= 2)
    },
    {
        id: "trailblazer", name: "开路先锋", icon: "🧭", desc: "任一能力达到第 6 级",
        check: (s) => SKILL_ORDER.some((id) => s.skills[id].level >= 6)
    },
    {
        id: "master_one", name: "能力大师", icon: "🏅", desc: "完全掌握一项能力",
        check: (s) => SKILL_ORDER.some((id) => s.skills[id].mastered)
    },
    {
        id: "master_all", name: "数学全能王", icon: "👑", desc: "掌握全部数学能力",
        check: (s) => SKILL_ORDER.every((id) => s.skills[id].mastered)
    },
    {
        id: "market_master", name: "分组大师", icon: "🛒", desc: "掌握乘除分组",
        check: (s) => s.skills.groups && s.skills.groups.mastered
    },
    {
        id: "shape_scout", name: "图形侦察员", icon: "🔺", desc: "完成一次图形闯关",
        check: (s) => (s.stats.playedSkills || []).includes("shapes")
    },
    {
        id: "fair_friend", name: "公平分享", icon: "🍰", desc: "完成一次分数闯关",
        check: (s) => (s.stats.playedSkills || []).includes("fractions")
    },
    {
        id: "daily_hero", name: "每日之星", icon: "🌟", desc: "完成一次每日挑战",
        check: (s) => s.daily.claimedKey != null
    },
    {
        id: "quester", name: "任务冠军", icon: "📜", desc: "完成 3 个任务",
        check: (s) => s.stats.questsDone >= 3
    },
    {
        id: "collector", name: "收藏家", icon: "🎁", desc: "解锁 3 个伙伴",
        check: (s) => (s.unlocks.characters || []).length >= 3
    },
    {
        id: "saver", name: "攒星达人", icon: "✨", desc: "累计获得 40 颗星",
        check: (s) => s.stats.starsEarned >= 40
    },
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
