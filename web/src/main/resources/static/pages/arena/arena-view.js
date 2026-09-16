/**
 * @file arena-view.js
 * @description 剑气除魔 UI 纯视图渲染与模板组件库。
 * 负责各界面卡片、榜单、通天塔区间及神兽进阶视图的 HTML 构建。
 */
(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.ArenaView = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    var ARCHETYPE_MAP = {
        1: ['HIGH_ATK', '⚡ 狂暴雷霆 (高攻秒人)', 'rgba(244,63,94,0.15)', '#f43f5e'],
        2: ['HIGH_DEF', '🛡️ 金刚铁壁 (极高防御)', 'rgba(251,191,36,0.15)', '#fbbf24'],
        3: ['HIGH_HEAL', '🪷 枯木逢春 (巨额回血)', 'rgba(16,185,129,0.15)', '#10b981'],
        0: ['HIGH_RES', '✨ 太虚魔障 (高抗性)', 'rgba(168,85,247,0.15)', '#c084fc']
    };

    var ENEMY_PREFIXES = ["赤焰", "寒潭", "阴煞", "九尾", "罗刹", "血海", "幽冥", "吞天", "九幽", "天魔", "鸿蒙"];
    var ENEMY_TYPES = ["妖将", "魔帅", "鬼祖", "妖皇", "魔圣", "邪尊", "剑灵", "道主"];
    var BEAST_NAMES = ['碧水金晶兽', '插翅白虎', '烈焰朱雀', '玄甲灵龟', '应龙残魂', '麒麟圣兽', '白泽灵王', '饕餮魔尊', '混沌兽皇', '太古祖龙'];

    var ArenaView = {
        /**
         * 渲染顶端资源胶囊栏。
         * @param {Object} state 玩家修行快照
         * @returns {string} HTML 字符串
         */
        renderResourcesHtml: function (state) {
            if (!state) return '';
            return '<span>💧 灵液 ' + state.liquid + '</span>' +
                   '<span>🪙 灵币 ' + state.coins + '</span>' +
                   '<span>🔮 仙缘 ' + state.fate + '</span>' +
                   '<span>💎 战阵石 ' + state.stones + '</span>' +
                   '<span>🦄 神兽碎片 ' + (state.beastShards || 0) + '</span>';
        },

        /**
         * 渲染仙侣培养卡片列表（包含突破、参悟与升星）。
         * @param {Array} heroes 玩家拥有的仙修列表
         * @param {Function} heroGetter 根据 ID 获取图鉴仙修详情的回调函数
         * @returns {string} HTML 字符串
         */
        renderCultivateHtml: function (heroes, heroGetter) {
            if (!heroes || !heroes.length) return '';
            return heroes.map(function (p) {
                var h = heroGetter(p.id);
                var realm = (window.ArenaClicker && window.ArenaClicker.getRealmByRank)
                    ? window.ArenaClicker.getRealmByRank(p.rank)
                    : { name: '炼气', color: '#38bdf8' };
                var starNeed = p.stars < 5 ? [20, 35, 50, 70][p.stars - 1] : 100;
                var starMax = p.stars >= 5;

                return '<div class="card">' +
                    '<div style="display:flex;justify-content:space-between;align-items:center;">' +
                        '<span class="tag">' + h.quality + ' · ' + h.role + '</span>' +
                        '<span class="tag" style="color:' + realm.color + ';background:rgba(255,255,255,0.06);border:1px solid ' + realm.color + '40;">' + realm.name + '境</span>' +
                    '</div>' +
                    '<h3>' + h.name + '</h3>' +
                    '<b>' + realm.name + ' ' + p.rank + ' 重 · ' + '★'.repeat(p.stars) + '☆'.repeat(Math.max(0, 5 - p.stars)) + '</b>' +
                    '<p>本命功法 ' + p.skill + ' 级 · 碎片 ' + p.shards + '/' + (starMax ? '满星' : starNeed) + '</p>' +
                    '<div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-top:4px;">' +
                        '<button class="btn btn-primary btn-sm" data-rank="' + p.id + '">突破 · ' + (p.rank * 200) + ' 灵液</button>' +
                        '<button class="btn btn-ghost btn-sm" data-skill="' + p.id + '">功法 · ' + (p.skill * 150) + ' 灵币</button>' +
                        '<button class="btn btn-ghost btn-sm" data-star="' + p.id + '" style="grid-column: span 2; border-color:var(--gold); color:var(--gold);"' +
                            (starMax || p.shards < starNeed ? ' disabled' : '') + '>' +
                            (starMax ? '★ 五星圆满' : '升星进阶 · 消耗 ' + starNeed + ' 碎片') +
                        '</button>' +
                    '</div>' +
                '</div>';
            }).join('');
        },

        /**
         * 渲染 999 层通天塔区间卡片与翻页按钮。
         * @param {Object} state 玩家快照
         * @param {number} viewFloor 当前浏览起始层数
         * @returns {string} HTML 字符串
         */
        renderTowerHtml: function (state, viewFloor) {
            if (!state) return '';
            var start = Math.max(1, viewFloor || 1);
            var end = Math.min(999, start + 14);
            var cards = [];

            for (var n = start; n <= end; n++) {
                var isBoss = (n % 10 === 0);
                var archInfo = ARCHETYPE_MAP[n % 4];
                var pReq = n <= 12 ? n * 18000 : Math.round(216000 + (n - 12) * 12000 + Math.pow(n - 12, 1.82) * 150);
                var mName = ENEMY_PREFIXES[(n - 1) % ENEMY_PREFIXES.length] + ENEMY_TYPES[(n - 1) % ENEMY_TYPES.length];

                var pillTier = 0;
                if (n <= 30 && n % 3 === 0) pillTier = 1;
                else if (n > 30 && n <= 90 && (n - 30) % 6 === 0) pillTier = 2;
                else if (n > 90 && n <= 240 && (n - 90) % 10 === 0) pillTier = 3;

                cards.push(
                    '<div class="card" style="' + (isBoss ? 'border-color:rgba(244,63,94,0.4);' : '') + '">' +
                        '<div style="display:flex;justify-content:space-between;align-items:center;">' +
                            '<span class="tag">' + (isBoss ? '👹 领主魔王' : '通天塔') + ' ' + n + ' 层</span>' +
                            '<span class="tag" style="background:' + archInfo[2] + ';color:' + archInfo[3] + ';">' + archInfo[1] + '</span>' +
                        '</div>' +
                        '<h3>' + mName + '</h3>' +
                        '<p style="font-size:11px;color:var(--text-muted);line-height:1.4;">' +
                            '推荐战力 ' + pReq.toLocaleString() + '<br>' +
                            (n <= state.dungeonCleared ? '✨ 重复平定奖励' : '🎁 首通天地机缘') + ': 灵液 ' + ((n <= state.dungeonCleared ? 180 : 600) * n) +
                            (pillTier > 0 ? '<br><b style="color:var(--gold);">🎁 首通赐【' + ['', '洗髓丹', '聚灵丹', '九幽丹'][pillTier] + '】</b>' : '') +
                        '</p>' +
                        '<button class="btn ' + (n <= state.dungeonCleared + 1 ? 'btn-primary' : 'btn-ghost') + '" data-stage="' + n + '"' +
                            (n > state.dungeonCleared + 1 ? ' disabled' : '') + '>' +
                            (n <= state.dungeonCleared ? '扫荡镇魔' : '御剑突破') +
                        '</button>' +
                    '</div>'
                );
            }

            cards.push(
                '<div style="grid-column: 1 / -1; display:flex; justify-content:center; gap:12px; margin-top:8px;">' +
                    '<button class="btn btn-ghost" onclick="jumpTowerFloor(' + (start - 15) + ')"' + (start <= 1 ? ' disabled' : '') + '>⬅️ 上一区间</button>' +
                    '<span style="display:flex;align-items:center;font-size:12px;color:var(--text-muted);">第 ' + start + ' ~ ' + end + ' 层 (共999层)</span>' +
                    '<button class="btn btn-ghost" onclick="jumpTowerFloor(' + (start + 15) + ')"' + (end >= 999 ? ' disabled' : '') + '>下一区间 ➡️</button>' +
                '</div>'
            );

            return cards.join('');
        },

        /**
         * 渲染万妖兽神塔层数卡片列表。
         * @param {Object} state 玩家快照
         * @returns {string} HTML 字符串
         */
        renderBeastTowerHtml: function (state) {
            if (!state) return '';
            var bCleared = state.beastTowerCleared || 0;
            return Array.from({ length: 10 }, function (_, i) { return i + 1; }).map(function (n) {
                var pReq = n * 45000;
                var drop = 5 + n * 2;
                return '<div class="card">' +
                    '<div style="display:flex;justify-content:space-between;align-items:center;">' +
                        '<span class="tag" style="color:var(--purple);background:rgba(168,85,247,0.1);">兽神塔 ' + n + ' 重</span>' +
                        '<small style="color:var(--text-muted);">推荐战力 ' + pReq.toLocaleString() + '</small>' +
                    '</div>' +
                    '<h3>' + BEAST_NAMES[n - 1] + '</h3>' +
                    '<p style="font-size:12px;color:var(--text-muted);">通关掉落：<b style="color:var(--purple);">神兽碎片 ×' + drop + '</b></p>' +
                    '<button class="btn ' + (n <= bCleared + 1 ? 'btn-primary' : 'btn-ghost') + '" data-beast-stage="' + n + '"' +
                        (n > bCleared + 1 ? ' disabled' : '') + '>' +
                        (n <= bCleared ? '扫荡兽神' : '降伏神兽') +
                    '</button>' +
                '</div>';
            }).join('');
        },

        /**
         * 渲染四大神通道法选择药丸徽章。
         * @param {string} equippedSkill 当前装备的神通标识
         * @param {Array} skillsList 神通列表元数据
         * @returns {string} HTML 字符串
         */
        renderSkillsHtml: function (equippedSkill, skillsList) {
            return skillsList.map(function (s) {
                var active = (s.id === equippedSkill);
                return '<div class="skill-pill ' + (active ? 'active' : '') + '" data-skill-id="' + s.id + '">' +
                    '<h4>' + s.tag + ' · ' + s.name + '</h4>' +
                    '<p>' + s.desc + '</p>' +
                '</div>';
            }).join('');
        },

        /**
         * 渲染每日问道任务列表。
         * @param {Object} state 玩家快照
         * @param {Object} taskCfg 任务配置字典
         * @returns {Object} 包含 html 字符串与总活跃度数值
         */
        renderTasksHtml: function (state, taskCfg) {
            if (!state || !state.tasks) return { html: '', activity: 0 };
            var activity = 0;
            var html = Object.keys(taskCfg).map(function (id) {
                var c = taskCfg[id];
                var t = state.tasks[id];
                if (!t) return '';
                if (t.claimed) activity += c[3];
                return '<div class="task">' +
                    '<div>' +
                        '<b>' + c[0] + '</b>' +
                        '<p style="font-size:12px;color:var(--text-muted);">' + c[2] + ' · 活跃 +' + c[3] + '</p>' +
                    '</div>' +
                    '<div class="bar"><i style="width:' + Math.min(100, (t.progress / c[1]) * 100) + '%"></i></div>' +
                    '<button class="btn btn-primary" data-claim="' + id + '"' +
                        (t.claimed || t.progress < c[1] ? ' disabled' : '') + '>' +
                        (t.claimed ? '已领悟' : t.progress + '/' + c[1] + ' 领取') +
                    '</button>' +
                '</div>';
            }).join('');
            return { html: html, activity: activity };
        }
    };

    return ArenaView;
}));
