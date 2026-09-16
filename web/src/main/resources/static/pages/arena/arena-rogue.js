(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.ArenaRogue = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    var BOON_POOL = [
        { id: 'wanjian', name: '万剑归宗', icon: '⚔️', quality: '金', desc: '剑气浩荡，普攻 35% 几率触发双重飞剑贯通。', tag: '连击' },
        { id: 'xuanjia', name: '太乙玄甲', icon: '🛡️', quality: '紫', desc: '周身环绕太虚罡气，最终受击减免 20%。', tag: '防御' },
        { id: 'qinglian', name: '青莲化生', icon: '🪷', quality: '红', desc: '引灵入窍，施展功法时回复已损生命 25%。', tag: '续航' },
        { id: 'jiuxiao', name: '九霄神雷', icon: '⚡', quality: '金', desc: '引九天引雷入剑，暴击伤害提升 50% 并概率定身。', tag: '爆发' },
        { id: 'shunying', name: '疾风瞬影', icon: '💨', quality: '蓝', desc: '身化流光，身法速度提升 30%，闪避率 +15%。', tag: '身法' },
        { id: 'niepan', name: '涅槃剑心', icon: '🔥', quality: '红', desc: '绝境逢生，气血低于 30% 时吸血效果翻倍。', tag: '逆风' },
        { id: 'fentian', name: '焚天剑煞', icon: '🌋', quality: '金', desc: '剑锋淬火，每击附加烈焰灼烧真伤。', tag: '真伤' },
        { id: 'jiantai', name: '混沌剑胎', icon: '✨', quality: '紫', desc: '大道胚胎，全局基础攻击力提升 25%。', tag: '属性' },
        { id: 'naling', name: '太虚纳灵', icon: '🔮', quality: '蓝', desc: '聚气如泉，每回合额外获得 20 点大招战意。', tag: '怒气' }
    ];

    function ArenaRogue(options) {
        this.options = options || {};
        this.activeBoons = [];
        this.initModal();
    }

    ArenaRogue.prototype.initModal = function () {
        var self = this;
        var modal = document.createElement('div');
        modal.id = 'rogue-modal';
        modal.className = 'rogue-modal-overlay';
        modal.style.display = 'none';

        modal.innerHTML = [
            '<div class="rogue-modal-content glass-card">',
            '  <div class="rogue-title">',
            '    <h2>✨ 天降机缘 · 仙尊气运传承</h2>',
            '    <p>镇魔除妖感召天地造化，请从以下三卷仙道秘录中挑选一卷：</p>',
            '  </div>',
            '  <div class="rogue-cards-grid" id="rogue-cards"></div>',
            '  <div class="rogue-foot">',
            '    <button class="btn btn-ghost" id="rogue-skip">弃权领悟</button>',
            '  </div>',
            '</div>'
        ].join('');

        document.body.appendChild(modal);
        this.modalEl = modal;

        modal.querySelector('#rogue-skip').addEventListener('click', function () {
            self.close();
        });
    };

    // 随机抽取 3 张不重复的气运卡
    ArenaRogue.prototype.promptChoice = function (callback) {
        var self = this;
        var chosenIndices = [];
        while (chosenIndices.length < 3 && chosenIndices.length < BOON_POOL.length) {
            var rand = Math.floor(Math.random() * BOON_POOL.length);
            if (chosenIndices.indexOf(rand) === -1) chosenIndices.push(rand);
        }

        var cards = chosenIndices.map(function (idx) { return BOON_POOL[idx]; });
        var cardsContainer = this.modalEl.querySelector('#rogue-cards');
        
        cardsContainer.innerHTML = cards.map(function (c) {
            return [
                '<div class="rogue-card rogue-q-' + c.quality + '" data-id="' + c.id + '">',
                '  <div class="rogue-card-head">',
                '    <span class="rogue-card-icon">' + c.icon + '</span>',
                '    <span class="rogue-card-tag">' + c.tag + '</span>',
                '  </div>',
                '  <h3>' + c.name + '</h3>',
                '  <p>' + c.desc + '</p>',
                '  <button class="btn btn-primary btn-sm">参悟继承</button>',
                '</div>'
            ].join('');
        }).join('');

        cardsContainer.querySelectorAll('.rogue-card').forEach(function (el) {
            el.addEventListener('click', function () {
                var boonId = el.getAttribute('data-id');
                var boon = BOON_POOL.find(function (b) { return b.id === boonId; });
                if (boon) {
                    self.activeBoons.push(boon);
                    if (window.ArenaStage && window.ArenaStage.SoundFx) {
                        window.ArenaStage.SoundFx.ding();
                    }
                    if (callback) callback(boon);
                }
                self.close();
            });
        });

        this.modalEl.style.display = 'flex';
    };

    ArenaRogue.prototype.close = function () {
        if (this.modalEl) this.modalEl.style.display = 'none';
    };

    ArenaRogue.prototype.getActiveBoons = function () {
        return this.activeBoons;
    };

    ArenaRogue.prototype.renderBoonsBadge = function (container) {
        if (!container) return;
        if (!this.activeBoons.length) {
            container.innerHTML = '<span class="boon-empty">尚未参悟天机气运</span>';
            return;
        }
        container.innerHTML = this.activeBoons.map(function (b) {
            return '<span class="boon-badge boon-q-' + b.quality + '">' + b.icon + ' ' + b.name + '</span>';
        }).join(' ');
    };

    ArenaRogue.BOON_POOL = BOON_POOL;

    return ArenaRogue;
}));
