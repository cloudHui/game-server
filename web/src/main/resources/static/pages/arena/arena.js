/**
 * @file arena.js
 * @description 剑气除魔主流程控制器。
 * 负责路由切换、服务器通信、各玩法状态流转及调用 ArenaView / ArenaStage / ArenaAudio 进行交互驱动。
 */
(() => {
    'use strict';

    var base = location.pathname.split('/pages/arena/')[0] || '';
    var token = localStorage.getItem('token') || '';
    var headers = { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token };
    var $ = function (s) { return document.querySelector(s); };
    var $$ = function (s) { return [].slice.call(document.querySelectorAll(s)); };

    var catalog, state, clicker, arenaStage, rogue;
    var towerViewFloor = 1;
    var battleSpeed = 1;

    /** 每日任务静态配置: [名称, 目标次数, 奖励描述, 活跃点] */
    var TASK_CONFIG = {
        login:   ['登录问道', 1, '灵液 200', 10],
        dungeon: ['挑战通天塔三次', 3, '灵液 500', 20],
        rank:    ['培养仙侣一次', 1, '灵币 300', 15],
        beast:   ['神兽驯养进阶', 1, '战阵石 150', 20],
        recruit: ['招募仙侣一次', 1, '仙缘 1', 15],
        arena:   ['擂台论剑一次', 1, '灵币 400', 20]
    };

    /** 六阶坐骑神兽元数据 */
    var BEAST_CONFIG = [
        { tier: 1, name: '踏云鹿', icon: '🦌', talent: '【踏云步】全队身法闪避率 +10%' },
        { tier: 2, name: '幽冥白虎', icon: '🐯', talent: '【杀伐煞】全队暴击伤害 +35%' },
        { tier: 3, name: '九天朱雀', icon: '🦅', talent: '【涅槃火】阵亡时 100% 涅槃重生' },
        { tier: 4, name: '辟邪玄武', icon: '🐢', talent: '【玄武甲】开局提供 25% 护体灵盾' },
        { tier: 5, name: '太初青龙', icon: '🐲', talent: '【龙威镇】敌方全体攻击削减 20%' },
        { tier: 6, name: '鸿蒙麒麟', icon: '🦄', talent: '【祥瑞辟】全属性 +25%，挂机收益翻倍' }
    ];

    /** 四大神通道法元数据 */
    var SKILLS_LIST = [
        { id: 'silence', name: '太虚封魔咒', tag: '🔕 沉默流', desc: '35% 几率沉默敌人2回合，封禁大招与被动回血，专克高攻秒人与自愈怪' },
        { id: 'pierce',  name: '九天破甲决', tag: '⚔️ 破甲流', desc: '无视 50% 防御，暴击伤害 +60%，刀刀真伤斩破金刚铁壁' },
        { id: 'defense', name: '金刚不坏身', tag: '🛡️ 霸体流', desc: '最终减伤 40%，开局获 30% 生命护盾，受击反震 15% 伤害' },
        { id: 'heal',    name: '青帝长生引', tag: '🪷 吸血流', desc: '造成伤害 40% 转为吸血，回合结束自愈 10% 生命' }
    ];

    /**
     * 弹出轻量仙侠提示条。
     * @param {string} msg 提示内容
     */
    function toast(msg) {
        var el = $('#toast');
        if (!el) return;
        el.textContent = msg;
        el.classList.add('show');
        setTimeout(function () { el.classList.remove('show'); }, 2000);
    }
    window.toast = toast;
    window.refreshArenaState = async function () {
        try {
            state = await json('/api/arena/state', { headers: headers });
            render();
        } catch (e) {
            console.warn(e);
        }
    };

    /**
     * 发送异步 JSON 网络请求。
     * @param {string} url 请求相对路径
     * @param {Object} opt fetch 选项
     * @returns {Promise<Object>} 响应数据
     */
    async function json(url, opt) {
        var r = await fetch(base + url, opt);
        var b = await r.json();
        if (!r.ok) throw new Error(b.error || b.msg || '请求失败');
        return b;
    }

    /**
     * 执行修行行为动作并重新渲染。
     * @param {string} actionName 动作代码
     * @param {string} id 目标ID
     * @param {number} count 数量或层数
     */
    async function action(actionName, id, count) {
        try {
            state = await json('/api/arena/action', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ action: actionName, id: id, count: count })
            });
            render();
            toast('操作成功');

            // 通天塔通关可能偶遇肉鸽机缘感悟
            if (actionName === 'dungeon' && rogue && Math.random() < 0.4) {
                setTimeout(function () {
                    rogue.promptChoice(function (b) {
                        toast('破境感悟机缘：' + b.name);
                        rogue.renderBoonsBadge($('#dungeon-boons'));
                        rogue.renderBoonsBadge($('#arena-boons-badge'));
                    });
                }, 500);
            }
        } catch (e) {
            toast(e.message);
        }
    }

    /**
     * 快速跳转通天塔浏览区间。
     * @param {number} floor 目标起始层数
     */
    window.jumpTowerFloor = function (floor) {
        towerViewFloor = Math.max(1, Math.min(999, floor));
        renderTowerSection();
    };

    /**
     * 导航选项卡切换绑定。
     */
    $$('nav button').forEach(function (b) {
        b.onclick = function () {
            $$('nav button, .page').forEach(function (x) { x.classList.remove('active'); });
            b.classList.add('active');
            var target = $('#' + b.dataset.page);
            if (target) target.classList.add('active');
            if (b.dataset.page === 'arena' && arenaStage) {
                setTimeout(function () { arenaStage.resize(); }, 50);
            }
        };
    });

    /**
     * 获取仙修详情。
     * @param {string} id 仙修标识
     * @returns {Object} 仙修对象
     */
    function hero(id) {
        return (catalog && catalog.heroes && catalog.heroes.find(function (h) { return h.id === id; })) ||
            { id: id, name: id, quality: '紫', role: '修士', hp: 5000, atk: 500, def: 200, skill: '御剑术' };
    }

    /**
     * 全局状态同步与各视图组件全量重绘。
     */
    function render() {
        var r = state;
        if (!r) return;

        // 1. 顶部资源胶囊栏
        $('#resources').innerHTML = ArenaView.renderResourcesHtml(r);
        $('#attempts').textContent = '今日通天塔次数 ' + r.dungeonAttempts + '/5';
        $('#beast-attempts').textContent = '今日兽神塔剩余 ' + (r.beastTowerAttempts || 3) + '/3';
        $('#pity').textContent = '金色保底 ' + r.pity + '/90';
        $('#formation-level').textContent = r.formationLevel + ' 级 · 最终攻击 +' + (r.formationLevel * 2) + '% · 神兽总放大 +' + (r.beastFormationAmp || 5) + '%';

        // 2. 洞府主修仙者与境界
        var mainHero = r.heroes && r.heroes[0];
        var mainRank = mainHero ? mainHero.rank : 1;
        var realmInfo = (window.ArenaClicker && window.ArenaClicker.getRealmByRank)
            ? window.ArenaClicker.getRealmByRank(mainRank)
            : { name: '炼气', title: '引气入体', color: '#38bdf8' };

        if ($('#home-realm-tag')) {
            $('#home-realm-tag').textContent = realmInfo.name + '境';
            $('#home-realm-tag').style.borderColor = realmInfo.color;
            $('#home-realm-tag').style.color = realmInfo.color;
        }
        if (mainHero && $('#home-hero-name')) {
            var h = hero(mainHero.id);
            $('#home-hero-name').textContent = h.name;
            $('#home-hero-desc').textContent = h.quality + '品 · ' + realmInfo.title + ' · ' + h.role;
        }
        if (clicker) clicker.setRank(mainRank);

        // 3. 挂机演武与经验条
        $('#grind-level-badge').textContent = '试炼 Lv.' + (r.grindLevel || 1);
        $('#grind-exp-text').textContent = (r.grindExp || 0) + ' / ' + (r.grindExpNeeded || 800);
        $('#grind-exp-bar').style.width = Math.min(100, ((r.grindExp || 0) / (r.grindExpNeeded || 800)) * 100) + '%';
        if (r.grindRates) {
            $('#grind-rate-info').textContent = '当前挂机效率：经验 +' + r.grindRates.expPerMin + '/分 · 灵液 +' + r.grindRates.liquidPerMin + '/分 · 灵币 +' + r.grindRates.coinsPerMin + '/分';
        }

        // 4. 四阶属性丹
        $('#pills-total-badge').textContent = '已服用 ' + (r.pillsTotal || 0) + '/50 颗';
        $('#pill-cnt-1').textContent = (r.pillsTier1 || 0) + '/10';
        $('#pill-cnt-2').textContent = (r.pillsTier2 || 0) + '/10';
        $('#pill-cnt-3').textContent = (r.pillsTier3 || 0) + '/15';
        $('#pill-cnt-4').textContent = (r.pillsTier4 || 0) + '/15';

        // 5. 仙修突破与升星卡片列表
        $('#cultivate-grid').innerHTML = ArenaView.renderCultivateHtml(r.heroes, hero);

        // 6. 每日镇魔俸禄横幅
        if (r.dungeonSettlement) {
            $('#settle-status-tag').textContent = (r.dungeonSettled === 1) ? '今日已领取' : '待领取';
            $('#settle-desc').textContent = '当前镇魔最高层：' + r.dungeonCleared + ' 层 · 每日可领灵液 ' + r.dungeonSettlement.liquid +
                '、灵币 ' + r.dungeonSettlement.coins + '、战阵石 ' + r.dungeonSettlement.stones +
                (r.dungeonSettlement.fate > 0 ? '、仙缘 ' + r.dungeonSettlement.fate : '');
            $('#btn-dungeon-settle').disabled = (r.dungeonSettled === 1 || r.dungeonCleared === 0);
        }

        // 7. 坐骑神兽展示卡
        var bLv = r.beastLevel || 1;
        var bCfg = BEAST_CONFIG[Math.min(BEAST_CONFIG.length - 1, bLv - 1)];
        $('#beast-tier-badge').textContent = bLv + ' 阶 · ' + bCfg.name;
        $('#beast-icon-box').textContent = bCfg.icon;
        $('#beast-name-text').textContent = bCfg.name;
        $('#beast-talent-text').textContent = bCfg.talent;
        $('#beast-amp-text').textContent = '全战阵威力总增幅 +' + (r.beastFormationAmp || 5) + '%';
        $('#beast-shard-text').textContent = (r.beastShards || 0) + ' / ' + (r.beastShardCost || 30);
        $('#beast-shard-bar').style.width = Math.min(100, ((r.beastShards || 0) / (r.beastShardCost || 30)) * 100) + '%';
        $('#btn-beast-upgrade').disabled = (r.beastShards || 0) < (r.beastShardCost || 30);

        // 8. 通天塔与兽神塔卡片
        renderTowerSection();
        renderBeastTowerSection();

        // 9. 每日问道任务
        var taskRes = ArenaView.renderTasksHtml(r, TASK_CONFIG);
        $('#task-list').innerHTML = taskRes.html;
        $('#activity').textContent = '问道活跃 ' + taskRes.activity + '/100';

        // 10. 神通道法装配
        renderSkillsSection();

        // 11. 神装宝库四大槽位渲染
        renderGearSection();

        // 12. 渡劫破境大境界渲染
        renderTribulationSection();

        // 13. 洞府灵田药园
        renderGardenSection();

        // 14. 虚空秘境肉鸽模式
        renderVoidSection();

        // 15. 重新绑定交互监听
        bindActionEvents();
    }

    /** 渲染渡劫破境卡片 */
    function renderTribulationSection() {
        var r = state;
        if (!r) return;
        var tLv = r.tribulationLevel || 0;
        var tTitles = ['炼气期', '筑基期', '结丹期', '元婴期', '化神期', '大乘期', '飞升真仙'];
        var tName = tTitles[Math.min(tTitles.length - 1, tLv)];
        var nextName = tTitles[Math.min(tTitles.length - 1, tLv + 1)];
        if ($('#tribulation-badge')) $('#tribulation-badge').textContent = tName;
        if ($('#tribulation-desc')) {
            $('#tribulation-desc').textContent = tLv >= 6 ? '功德圆满，已证不朽金仙' :
                '当前大境界：' + tName + ' · 渡劫目标：' + nextName + '（需仙修达到 ' + ((tLv + 1) * 4) + ' 阶，耗丹药护体）';
        }
        if ($('#btn-tribulation')) {
            $('#btn-tribulation').onclick = function () {
                action('tribulation', '', 1);
            };
        }
    }

    /** 渲染神装宝库卡片 */
    function renderGearSection() {
        var r = state;
        var el = $('#gear-grid');
        if (!r || !el || !r.gears) return;

        var gearSlots = [
            { slot: 'weapon', label: '本命神兵', icon: '🗡️' },
            { slot: 'armor', label: '护体仙袍', icon: '🥋' },
            { slot: 'helmet', label: '紫霄宝冠', icon: '👑' },
            { slot: 'talisman', label: '混元玉佩', icon: '📿' }
        ];

        el.innerHTML = gearSlots.map(function (s) {
            var g = r.gears[s.slot] || { name: '初品法器', level: 0, desc: '暂无加成' };
            return '<div class="glass-card" style="padding:18px;">' +
                '<div class="title" style="margin-bottom:8px;">' +
                '<h4 style="font-size:16px; color:var(--gold);">' + s.icon + ' ' + g.name + '</h4>' +
                '<span class="tag" style="color:var(--sky);">' + g.level + ' 品强化</span>' +
                '</div>' +
                '<p style="font-size:12px; color:var(--text); margin-bottom:6px;">【' + s.label + '】</p>' +
                '<div style="font-size:12px; color:var(--jade); margin-bottom:14px;">' + (g.desc || '') + '</div>' +
                '<button class="btn btn-primary" data-refine="' + s.slot + '" style="width:100%; font-size:12px;">' +
                '🔨 精炼打造 · 消耗精炼石' +
                '</button>' +
                '</div>';
        }).join('');

        el.querySelectorAll('[data-refine]').forEach(function (b) {
            b.onclick = function () {
                action('refine_gear', b.dataset.refine, 1);
            };
        });
    }

    /** 渲染通天塔卡片与监听 */
    function renderTowerSection() {
        $('#dungeon-grid').innerHTML = ArenaView.renderTowerHtml(state, towerViewFloor);
        $$('[data-stage]').forEach(function (b) {
            b.onclick = function () { action('dungeon', '', +b.dataset.stage); };
        });
    }

    /** 渲染兽神塔卡片与监听 */
    function renderBeastTowerSection() {
        $('#beast-tower-grid').innerHTML = ArenaView.renderBeastTowerHtml(state);
        $$('[data-beast-stage]').forEach(function (b) {
            b.onclick = function () { action('beast_tower', '', +b.dataset.beastStage); };
        });
    }

    /** 渲染神通道法卡片与监听 */
    function renderSkillsSection() {
        var eq = (state && state.equippedSkill) || 'pierce';
        var curSkill = SKILLS_LIST.find(function (s) { return s.id === eq; }) || SKILLS_LIST[1];
        $('#equipped-skill-name').textContent = curSkill.name;
        $('#skills-picker').innerHTML = ArenaView.renderSkillsHtml(eq, SKILLS_LIST);
        $$('[data-skill-id]').forEach(function (el) {
            el.onclick = function () { action('equip_skill', el.dataset.skillId, 1); };
        });
    }

    /** 绑定通用卡片交互点击事件 */
    function bindActionEvents() {
        $$('[data-rank]').forEach(function (b) { b.onclick = function () { action('rank', b.dataset.rank, 1); }; });
        $$('[data-skill]').forEach(function (b) { b.onclick = function () { action('skill', b.dataset.skill, 1); }; });
        $$('[data-star]').forEach(function (b) { b.onclick = function () { action('star', b.dataset.star, 1); }; });
        $$('[data-claim]').forEach(function (b) { b.onclick = function () { action('claim', b.dataset.claim, 1); }; });
    }

    // 页面动作按钮委派 (战阵升级、离线收获、丹药操作等)
    $$('[data-action]').forEach(function (b) {
        b.onclick = function () { action(b.dataset.action, '', 1); };
    });

    // 仙缘招募抽卡
    $$('[data-draw]').forEach(function (b) {
        b.onclick = async function () {
            try {
                var before = state.heroes.reduce(function (m, h) { m[h.id] = h.shards; return m; }, {});
                var n = +b.dataset.draw;
                state = await json('/api/arena/action', {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify({ action: 'draw', count: n })
                });
                render();
                var audio = window.ArenaAudio || (window.ArenaStage && window.ArenaStage.SoundFx);
                if (audio && audio.ding) audio.ding();

                $('#draw-result').innerHTML = state.heroes.filter(function (h) {
                    return before[h.id] === undefined || before[h.id] !== h.shards;
                }).map(function (h) {
                    return '<span class="draw">' + hero(h.id).name + ' · 碎片 +' + (h.shards - (before[h.id] || 0)) + '</span>';
                }).join('');
                toast('天降仙缘招募完成！');
            } catch (e) {
                toast(e.message);
            }
        };
    });

    // 战报步进控制器初始化
    var battlePlayer = new ArenaBattlePlayer({
        render: function (e) {
            if (!e) return;
            if (arenaStage) arenaStage.handleBattleEvent(e);
            if (e.text) {
                var row = document.createElement('div');
                row.textContent = (e.type === 'ROUND_START' ? '—— ' : '') + e.text + (e.type === 'ROUND_START' ? ' ——' : '');
                $('#battle-log').appendChild(row);
                $('#battle-log').scrollTop = 99999;
            }
        }
    });

    function setBattleControlsEnabled(enabled) {
        ['#battle-toggle', '#battle-speed', '#battle-skip', '#battle-replay'].forEach(function (id) {
            var btn = $(id);
            if (btn) btn.disabled = !enabled;
        });
    }

    $('#battle-toggle').onclick = function () {
        var s = battlePlayer.status();
        if (s.playing) {
            battlePlayer.pause();
            $('#battle-toggle').textContent = '继续';
        } else {
            if (s.finished) {
                $('#battle-log').innerHTML = '';
                battlePlayer.replay();
            } else {
                battlePlayer.play();
            }
            $('#battle-toggle').textContent = '暂停';
        }
    };

    $('#battle-speed').onclick = function () {
        battleSpeed = (battleSpeed === 1) ? 2 : (battleSpeed === 2) ? 4 : 1;
        battlePlayer.setSpeed(battleSpeed);
        $('#battle-speed').textContent = '速度 ' + battleSpeed + '×';
    };

    $('#battle-skip').onclick = function () {
        battlePlayer.skip();
        $('#battle-toggle').textContent = '重播';
    };

    $('#battle-replay').onclick = function () {
        $('#battle-log').innerHTML = '';
        battlePlayer.replay();
        $('#battle-toggle').textContent = '暂停';
    };

    var currentBattleMode = '1v1';

    function switchBattleMode(mode) {
        currentBattleMode = mode;
        if (mode === '1v1') {
            if ($('#btn-mode-1v1')) $('#btn-mode-1v1').className = 'btn btn-primary btn-sm';
            if ($('#btn-mode-3v3')) $('#btn-mode-3v3').className = 'btn btn-ghost btn-sm';
            if ($('#panel-1v1-versus')) $('#panel-1v1-versus').style.display = 'flex';
            if ($('#panel-3v3-versus')) $('#panel-3v3-versus').style.display = 'none';
        } else {
            if ($('#btn-mode-1v1')) $('#btn-mode-1v1').className = 'btn btn-ghost btn-sm';
            if ($('#btn-mode-3v3')) $('#btn-mode-3v3').className = 'btn btn-primary btn-sm';
            if ($('#panel-1v1-versus')) $('#panel-1v1-versus').style.display = 'none';
            if ($('#panel-3v3-versus')) $('#panel-3v3-versus').style.display = 'block';
            updateTeamSynergiesPreview();
        }
    }
    window.switchBattleMode = switchBattleMode;

    function updateTeamSynergiesPreview() {
        if (!catalog || !catalog.heroes) return;
        var a1 = $('#attacker-1') ? $('#attacker-1').value : '';
        var a2 = $('#attacker-2') ? $('#attacker-2').value : '';
        var a3 = $('#attacker-3') ? $('#attacker-3').value : '';
        var teamIds = [a1, a2, a3].filter(Boolean);
        var teamHeroes = teamIds.map(function (id) {
            return catalog.heroes.find(function (h) { return h.id === id; });
        }).filter(Boolean);

        var syns = [];
        var warriorCount = teamHeroes.filter(function (h) { return h.role === '强攻' || h.id.indexOf('jian') !== -1; }).length;
        if (warriorCount >= 2) syns.push('【诛仙剑阵: 全队攻击+15%】');
        var tankCount = teamHeroes.filter(function (h) { return h.role === '坚守' || h.id.indexOf('yao') !== -1 || h.id.indexOf('ming') !== -1; }).length;
        if (tankCount >= 1) syns.push('【回天法相: 全队气血+20%】');
        var spellCount = teamHeroes.filter(function (h) { return h.role === '控制' || h.role === '灵巧' || h.id.indexOf('lei') !== -1; }).length;
        if (spellCount >= 1) syns.push('【九霄天劫: 全队暴击+10%】');

        var box = $('#team-synergies-preview');
        if (box) {
            box.innerHTML = syns.length ? '激活战阵羁绊：' + syns.join(' ') : '阵容羁绊：当前未达成羁绊组合';
        }
    }

    /** 执行回合挑战对决 */
    async function fight() {
        try {
            var skill = (state && state.equippedSkill) || 'pierce';
            var r;
            if (currentBattleMode === '3v3') {
                var a1 = $('#attacker-1') ? $('#attacker-1').value : 'jianhuang';
                var a2 = $('#attacker-2') ? $('#attacker-2').value : 'leizun';
                var a3 = $('#attacker-3') ? $('#attacker-3').value : 'yaohuang';
                var d1 = $('#defender-1') ? $('#defender-1').value : 'mingwang';
                var d2 = $('#defender-2') ? $('#defender-2').value : 'kongming';
                var d3 = $('#defender-3') ? $('#defender-3').value : 'taixu';
                var aTeam = [a1, a2, a3].join(',');
                var dTeam = [d1, d2, d3].join(',');
                r = await json('/api/arena/team-battle?attackers=' + encodeURIComponent(aTeam) +
                    '&defenders=' + encodeURIComponent(dTeam) +
                    '&skill=' + encodeURIComponent(skill) +
                    '&seed=' + Date.now(), { headers: headers });
            } else {
                var a = $('#attacker').value, d = $('#defender').value;
                r = await json('/api/arena/battle?attacker=' + encodeURIComponent(a) +
                    '&defender=' + encodeURIComponent(d) +
                    '&skill=' + encodeURIComponent(skill) +
                    '&seed=' + Date.now(), { headers: headers });
            }
            await action('arena', '', 1);
            $('#battle-log').innerHTML = '';
            if (arenaStage) {
                arenaStage.setFighters(r.attacker, r.defender);
            }
            battlePlayer.load(r.events);
            battlePlayer.setSpeed(battleSpeed);
            setBattleControlsEnabled(true);
            $('#battle-toggle').textContent = '暂停';
            battlePlayer.play();
        } catch (e) {
            toast(e.message);
        }
    }
    $('#fight').onclick = fight;

    /** 渲染洞府灵田 */
    function renderGardenSection() {
        var r = state;
        var el = $('#garden-grid');
        if (!r || !el) return;
        var fields = r.fields || [];
        if (!fields.length) return;

        var seedOpts = '<option value="herb">凝气草 (2分 · 产灵草+灵液)</option>' +
                       '<option value="ore">玄铁藤 (5分 · 产玄铁+灵液)</option>' +
                       '<option value="lotus">九叶金莲 (10分 · 产精炼石+灵丹)</option>';

        el.innerHTML = fields.map(function (f) {
            var fid = f.fieldId;
            var isGrowing = f.seedId && f.remainingSeconds > 0;
            var isMature = f.isMature;
            var isEmpty = !f.seedId;

            var body = '';
            if (isEmpty) {
                body = '<div style="display:flex;gap:6px;align-items:center;">' +
                    '<select id="seed-select-' + fid + '" style="flex:1;font-size:12px;padding:4px 8px;border-radius:6px;background:rgba(0,0,0,0.3);color:var(--text);border:1px solid var(--border);">' + seedOpts + '</select>' +
                    '<button class="btn btn-primary btn-sm" onclick="plantGarden(' + fid + ')">播种</button>' +
                    '</div>';
            } else if (isGrowing) {
                var matureTotal = f.seedId === 'herb' ? 120 : (f.seedId === 'ore' ? 300 : 600);
                var pct = Math.round(((matureTotal - f.remainingSeconds) / matureTotal) * 100);
                body = '<div>' +
                    '<div style="font-size:12px;color:var(--sky);margin-bottom:6px;">正在培元生机 · 剩余 ' + f.remainingSeconds + ' 秒</div>' +
                    '<div style="background:rgba(255,255,255,0.1);height:6px;border-radius:3px;overflow:hidden;">' +
                    '<div style="width:' + pct + '%;height:100%;background:linear-gradient(90deg,var(--sky),var(--jade));"></div>' +
                    '</div></div>';
            } else if (isMature) {
                body = '<button class="btn btn-primary btn-sm" style="width:100%;" onclick="harvestGarden(' + fid + ')">✨ 灵植已成熟 · 一键采摘收获</button>';
            }

            return '<div class="glass-card" style="padding:14px;border-color:rgba(255,255,255,0.1);">' +
                '<div class="title" style="margin-bottom:8px;">' +
                '<h4 style="font-size:14px;color:var(--gold);margin:0;">' + f.name + '</h4>' +
                '<span class="tag" style="color:' + (isMature ? 'var(--jade)' : (isGrowing ? 'var(--sky)' : 'var(--text-muted)')) + ';">' + f.cropName + '</span>' +
                '</div>' + body + '</div>';
        }).join('');
    }

    window.plantGarden = function (fid) {
        var sel = $('#seed-select-' + fid);
        if (sel) action('plant', sel.value, fid);
    };
    window.harvestGarden = function (fid) {
        action('harvest', '', fid);
    };

    /** 渲染虚空秘境肉鸽模式 */
    function renderVoidSection() {
        var r = state;
        if (!r) return;
        var vFloor = r.voidCleared || 0;
        var vBuffs = (r.voidBuffs || '').split(',').filter(Boolean);

        if ($('#void-floor-tag')) $('#void-floor-tag').textContent = '深度 ' + vFloor + ' 层';
        if ($('#void-desc')) {
            $('#void-desc').textContent = '虚空秘境当前深度：第 ' + vFloor + ' 层 · 每一层获天降机缘气运，战胜获得海量灵液与灵石！';
        }

        var listEl = $('#void-buffs-list');
        if (listEl) {
            if (vBuffs.length === 0) {
                listEl.innerHTML = '<span style="font-size:12px;color:var(--text-muted);">暂未获得虚空气运加持，请挑战通关获得</span>';
            } else {
                listEl.innerHTML = vBuffs.map(function (b) {
                    return '<span class="tag" style="color:var(--gold);background:rgba(251,191,36,0.12);">✨ ' + b + '</span>';
                }).join('');
            }
        }

        if ($('#btn-void-climb')) {
            $('#btn-void-climb').onclick = async function () {
                try {
                    await action('void_climb', '', 1);
                    if (window.ArenaRogue && arenaRogue) {
                        arenaRogue.promptChoice(function (chosen) {
                            action('void_buff', chosen.name, 1);
                            toast('领悟机缘：' + chosen.name);
                        });
                    }
                } catch (e) {
                    toast(e.message);
                }
            };
        }

        if ($('#btn-void-reset')) {
            $('#btn-void-reset').onclick = function () {
                if (confirm('确定要重置虚空秘境进度吗？层数与当前气运将重新开始。')) {
                    action('void_reset', '', 1);
                }
            };
        }
    }

    /** 模块入口启动初始化 */
    async function init() {
        if (!token) {
            toast('请先从大厅登录');
            return;
        }
        try {
            var res = await Promise.all([
                json('/api/arena/catalog'),
                json('/api/arena/state', { headers: headers })
            ]);
            catalog = res[0];
            state = res[1];

            // 填充攻防选择器
            if (catalog && catalog.heroes) {
                var opts = catalog.heroes.map(function (h) {
                    return '<option value="' + h.id + '">' + h.name + ' (' + h.quality + '·' + h.role + ')</option>';
                }).join('');
                $('#attacker').innerHTML = opts;
                $('#defender').innerHTML = opts;
                if (catalog.heroes.length > 1) {
                    $('#defender').value = catalog.heroes[1].id;
                }

                // 填充 3v3 小队选择器
                ['attacker-1', 'attacker-2', 'attacker-3', 'defender-1', 'defender-2', 'defender-3'].forEach(function (id, idx) {
                    var sel = $('#' + id);
                    if (sel) {
                        sel.innerHTML = opts;
                        if (catalog.heroes[idx % catalog.heroes.length]) {
                            sel.value = catalog.heroes[idx % catalog.heroes.length].id;
                        }
                        sel.onchange = updateTeamSynergiesPreview;
                    }
                });
            }

            if ($('#btn-mode-1v1')) $('#btn-mode-1v1').onclick = function () { switchBattleMode('1v1'); };
            if ($('#btn-mode-3v3')) $('#btn-mode-3v3').onclick = function () { switchBattleMode('3v3'); };
            if (window.ArenaRogue) window.arenaRogue = new ArenaRogue();

            // 初始化 Canvas 舞台
            var canvas = $('#stage-canvas');
            if (canvas && window.ArenaStage) {
                arenaStage = new ArenaStage(canvas);
            }

            // 飞剑特效选择
            var swordSelect = $('#sword-select');
            if (swordSelect && arenaStage) {
                swordSelect.onchange = function (e) {
                    arenaStage.setSword(e.target.value);
                    var info = ArenaStage.SWORD_THEMES[e.target.value];
                    toast('已佩戴本命飞剑：' + (info ? info.name : '诛仙剑煞'));
                };
            }

            // 音效开关
            var soundBtn = $('#btn-sound-toggle');
            var audio = window.ArenaAudio || (window.ArenaStage && window.ArenaStage.SoundFx);
            if (soundBtn && audio) {
                soundBtn.onclick = function () {
                    var muted = audio.toggleMute();
                    soundBtn.textContent = muted ? '🔇 静音' : '🔊 仙音';
                    toast(muted ? '音效已静音' : '仙音已开启');
                };
            }

            // 洞府解压点击
            var clickerWrap = $('#grotto-clicker-container');
            if (clickerWrap && window.ArenaClicker) {
                clicker = new ArenaClicker(clickerWrap, {
                    onHarvest: function () { action('grotto', '', 1); },
                    onSlash: function (gain, combo) {}
                });
            }

            // 镇魔肉鸽气运
            if (window.ArenaRogue) {
                rogue = new ArenaRogue();
                var btnBoon = $('#btn-try-boon');
                if (btnBoon) {
                    btnBoon.onclick = function () {
                        rogue.promptChoice(function (b) {
                            toast('参悟机缘：' + b.name);
                            rogue.renderBoonsBadge($('#dungeon-boons'));
                            rogue.renderBoonsBadge($('#arena-boons-badge'));
                        });
                    };
                }
                rogue.renderBoonsBadge($('#dungeon-boons'));
                rogue.renderBoonsBadge($('#arena-boons-badge'));
            }

            // 初始全量渲染
            render();
        } catch (e) {
            toast(e.message);
        }
    }

    init();
})();
