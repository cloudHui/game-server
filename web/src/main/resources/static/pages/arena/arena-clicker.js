(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.ArenaClicker = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    // 用户钦定的修仙十二大境界体系
    var REALMS = [
        { name: '炼气', color: '#94a3b8', title: '引气入体 · 灵动初现', mult: 1.0 },
        { name: '筑基', color: '#38bdf8', title: '铸就道基 · 御剑凌空', mult: 1.2 },
        { name: '金丹', color: '#fbbf24', title: '金丹九转 · 气冲斗牛', mult: 1.5 },
        { name: '元婴', color: '#f59e0b', title: '碎丹成婴 · 瞬息千里', mult: 1.8 },
        { name: '化神', color: '#a855f7', title: '神识化形 · 剑意通冥', mult: 2.2 },
        { name: '炼虚', color: '#c084fc', title: '洞虚破妄 · 万法皆空', mult: 2.7 },
        { name: '合体', color: '#f43f5e', title: '身道合一 · 移山填海', mult: 3.3 },
        { name: '大乘', color: '#e11d48', title: '红尘圆满 · 剑斩飞仙', mult: 4.0 },
        { name: '仙',   color: '#34d399', title: '超脱凡胎 · 逍遥天地', mult: 5.0 },
        { name: '仙君', color: '#10b981', title: '统御仙阙 · 万剑俯首', mult: 6.5 },
        { name: '道主', color: '#6366f1', title: '执掌天道 · 弹指纪元', mult: 8.5 },
        { name: '道尊', color: '#ec4899', title: '至高无上 · 一念宇宙', mult: 12.0 }
    ];

    function getRealmByRank(rank) {
        var idx = Math.max(0, Math.min(REALMS.length - 1, (rank || 1) - 1));
        return REALMS[idx];
    }

    function ArenaClicker(container, options) {
        this.container = container;
        this.options = options || {};
        this.onHarvest = options.onHarvest || function () {};
        this.onSlash = options.onSlash || function () {};
        
        this.combo = 0;
        this.comboTimer = null;
        this.autoTimer = null;
        this.rank = 1;
        this.sparks = [];

        this.initDOM();
        this.startAutoSlash();
    }

    ArenaClicker.prototype.setRank = function (rank) {
        this.rank = rank || 1;
        this.updateRealmDisplay();
    };

    ArenaClicker.prototype.initDOM = function () {
        var self = this;
        this.container.innerHTML = [
            '<div class="clicker-wrap">',
            '  <div class="realm-header">',
            '    <div class="realm-badge" id="realm-badge">炼气期</div>',
            '    <div class="realm-desc" id="realm-desc">引气入体 · 灵动初现</div>',
            '  </div>',
            '  <div class="sword-altar" id="sword-altar">',
            '    <div class="altar-rune-ring"></div>',
            '    <div class="altar-target" id="altar-target">',
            '      <div class="target-core">💎</div>',
            '      <div class="target-glow"></div>',
            '    </div>',
            '    <div class="orbiting-sword sword-1">🗡️</div>',
            '    <div class="orbiting-sword sword-2">🗡️</div>',
            '    <div class="orbiting-sword sword-3">🗡️</div>',
            '    <div class="combo-tag" id="clicker-combo" style="display:none;">连击 ×0</div>',
            '  </div>',
            '  <div class="clicker-action-bar">',
            '    <button class="btn btn-primary pulse-btn" id="btn-click-slash">⚡ 点屏御剑 · 聚灵斩魔</button>',
            '    <button class="btn btn-ghost" id="btn-harvest-grotto">🏮 领取洞府灵液</button>',
            '  </div>',
            '</div>'
        ].join('');

        this.targetEl = this.container.querySelector('#altar-target');
        this.altarEl = this.container.querySelector('#sword-altar');
        this.comboEl = this.container.querySelector('#clicker-combo');
        this.badgeEl = this.container.querySelector('#realm-badge');
        this.descEl = this.container.querySelector('#realm-desc');

        // 点击事件
        var slashHandler = function (e) {
            e.preventDefault();
            self.doSlash(e.clientX, e.clientY);
        };

        this.altarEl.addEventListener('click', slashHandler);
        this.container.querySelector('#btn-click-slash').addEventListener('click', slashHandler);
        this.container.querySelector('#btn-harvest-grotto').addEventListener('click', function () {
            self.onHarvest();
            if (window.ArenaStage && window.ArenaStage.SoundFx) {
                window.ArenaStage.SoundFx.ding();
            }
        });

        this.updateRealmDisplay();
    };

    ArenaClicker.prototype.updateRealmDisplay = function () {
        var r = getRealmByRank(this.rank);
        if (this.badgeEl) {
            this.badgeEl.textContent = r.name + '境';
            this.badgeEl.style.borderColor = r.color;
            this.badgeEl.style.color = r.color;
            this.badgeEl.style.boxShadow = '0 0 12px ' + r.color + '40';
        }
        if (this.descEl) {
            this.descEl.textContent = r.title + ' (战力加成 ×' + r.mult + ')';
        }
    };

    ArenaClicker.prototype.doSlash = function (clientX, clientY) {
        var self = this;
        var r = getRealmByRank(this.rank);

        // 连击累加
        this.combo++;
        if (this.comboTimer) clearTimeout(this.comboTimer);
        this.comboTimer = setTimeout(function () {
            self.combo = 0;
            if (self.comboEl) self.comboEl.style.display = 'none';
        }, 1800);

        // 刷新 Combo 显示
        if (this.comboEl) {
            this.comboEl.style.display = 'block';
            this.comboEl.textContent = '连击 ×' + this.combo;
            this.comboEl.classList.remove('pop');
            void this.comboEl.offsetWidth; // 触发重绘动画
            this.comboEl.classList.add('pop');
        }

        // 祭坛动画
        if (this.targetEl) {
            this.targetEl.classList.remove('hit');
            void this.targetEl.offsetWidth;
            this.targetEl.classList.add('hit');
        }

        // 音效
        if (window.ArenaStage && window.ArenaStage.SoundFx) {
            if (this.combo % 5 === 0) {
                window.ArenaStage.SoundFx.thunder();
            } else {
                window.ArenaStage.SoundFx.clash();
            }
        }

        // 产生飘字
        var gain = Math.round(15 * r.mult * (1 + Math.min(2.0, this.combo * 0.05)));
        this.spawnFloatingGain('+' + gain + ' 灵气', clientX, clientY);

        this.onSlash(gain, this.combo);
    };

    ArenaClicker.prototype.spawnFloatingGain = function (text, x, y) {
        var pop = document.createElement('div');
        pop.className = 'altar-gain-text';
        pop.textContent = text;
        
        var rect = this.altarEl.getBoundingClientRect();
        var posX = (x && x > rect.left && x < rect.right) ? (x - rect.left) : (rect.width * 0.5 + (Math.random() - 0.5) * 60);
        var posY = (y && y > rect.top && y < rect.bottom) ? (y - rect.top) : (rect.height * 0.5 + (Math.random() - 0.5) * 40);

        pop.style.left = posX + 'px';
        pop.style.top = posY + 'px';

        this.altarEl.appendChild(pop);
        setTimeout(function () {
            if (pop.parentNode) pop.parentNode.removeChild(pop);
        }, 900);
    };

    ArenaClicker.prototype.startAutoSlash = function () {
        var self = this;
        this.autoTimer = setInterval(function () {
            // 每隔 3 秒自动轻砍一下
            if (document.hidden) return;
            var r = getRealmByRank(self.rank);
            var gain = Math.round(10 * r.mult);
            self.spawnFloatingGain('+' + gain + ' 潜修', null, null);
        }, 3000);
    };

    ArenaClicker.prototype.destroy = function () {
        if (this.comboTimer) clearTimeout(this.comboTimer);
        if (this.autoTimer) clearInterval(this.autoTimer);
    };

    ArenaClicker.REALMS = REALMS;
    ArenaClicker.getRealmByRank = getRealmByRank;

    return ArenaClicker;
}));
