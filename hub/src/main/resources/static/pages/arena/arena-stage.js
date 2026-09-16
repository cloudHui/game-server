(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.ArenaStage = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    // 引入独立 Web Audio 仙侠音效合成引擎
    var SoundFx = (typeof globalThis !== 'undefined' && globalThis.ArenaAudio) ||
                  (typeof window !== 'undefined' && window.ArenaAudio) ||
                  (typeof require === 'function' ? (function () {
                      try { return require('./arena-audio.js'); } catch (e) { return null; }
                  })() : null) || {
                      whoosh: function () {},
                      clash: function () {},
                      thunder: function () {},
                      ding: function () {},
                      toggleMute: function () { return false; },
                      isMuted: function () { return false; }
                  };

    // 剑型与颜色主题
    var SWORD_THEMES = {
        gold: { name: '诛仙剑煞', color: '#fbbf24', glow: 'rgba(251,191,36,0.8)', tail: 'rgba(245,158,11,0.5)' },
        fire: { name: '赤霄龙渊', color: '#f43f5e', glow: 'rgba(244,63,94,0.8)', tail: 'rgba(225,29,72,0.5)' },
        ice:  { name: '玄冥寒霜', color: '#38bdf8', glow: 'rgba(56,189,248,0.8)', tail: 'rgba(14,165,233,0.5)' },
        jade: { name: '太虚青芒', color: '#34d399', glow: 'rgba(52,211,153,0.8)', tail: 'rgba(16,185,129,0.5)' },
        thunder: { name: '九天紫霄', color: '#a855f7', glow: 'rgba(168,85,247,0.8)', tail: 'rgba(147,51,234,0.5)' }
    };

    function ArenaStage(canvas, options) {
        options = options || {};
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.dpr = window.devicePixelRatio || 1;
        this.swordTheme = SWORD_THEMES.gold;

        this.width = canvas.clientWidth || 800;
        this.height = canvas.clientHeight || 280;
        this.resize();

        // 战斗双雄状态模型
        this.left = {
            id: 'attacker', name: '我方修士', maxHp: 10000, hp: 10000, ghostHp: 10000,
            energy: 0, hitFlash: 0, quality: '金', role: '剑修', x: 0, y: 0
        };
        this.right = {
            id: 'defender', name: '守关魔尊', maxHp: 10000, hp: 10000, ghostHp: 10000,
            energy: 0, hitFlash: 0, quality: '红', role: '魔王', x: 0, y: 0
        };

        // 动态飞行物体与粒子池
        this.flyingSwords = [];
        this.particles = [];
        this.popTexts = [];

        // 震屏参数
        this.shakeTime = 0;
        this.shakeIntensity = 0;

        // 动画主循环
        this.running = false;
        this.lastTime = 0;
        this.bindEvents();
        this.start();
    }

    ArenaStage.prototype.resize = function () {
        var rect = this.canvas.getBoundingClientRect();
        this.width = rect.width || 800;
        this.height = rect.height || 280;
        this.canvas.width = this.width * this.dpr;
        this.canvas.height = this.height * this.dpr;
        this.ctx.scale(this.dpr, this.dpr);
    };

    ArenaStage.prototype.bindEvents = function () {
        var self = this;
        window.addEventListener('resize', function () {
            self.resize();
        });
    };

    ArenaStage.prototype.setSword = function (themeKey) {
        if (SWORD_THEMES[themeKey]) {
            this.swordTheme = SWORD_THEMES[themeKey];
        }
    };

    ArenaStage.prototype.setFighters = function (att, def) {
        if (att) {
            this.left.id = att.id || 'attacker';
            this.left.name = att.name || '出战修士';
            this.left.maxHp = att.hp || 10000;
            this.left.hp = att.hp || 10000;
            this.left.ghostHp = this.left.hp;
            this.left.energy = 0;
            this.left.quality = att.quality || '金';
            this.left.role = att.role || '剑修';
        }
        if (def) {
            this.right.id = def.id || 'defender';
            this.right.name = def.name || '魔道妖王';
            this.right.maxHp = def.hp || 10000;
            this.right.hp = def.hp || 10000;
            this.right.ghostHp = this.right.hp;
            this.right.energy = 0;
            this.right.quality = def.quality || '红';
            this.right.role = def.role || '魔修';
        }
    };

    ArenaStage.prototype.start = function () {
        if (this.running) return;
        this.running = true;
        this.lastTime = performance.now();
        var self = this;
        function loop(now) {
            if (!self.running) return;
            var dt = Math.min(0.1, (now - self.lastTime) / 1000);
            self.lastTime = now;
            self.update(dt);
            self.render();
            requestAnimationFrame(loop);
        }
        requestAnimationFrame(loop);
    };

    ArenaStage.prototype.stop = function () {
        this.running = false;
    };

    // 震屏控制
    ArenaStage.prototype.shake = function (intensity, duration) {
        this.shakeIntensity = intensity || 8;
        this.shakeTime = duration || 0.25;
    };

    // 产生攻击飞剑动作
    ArenaStage.prototype.fireSword = function (isLeftToRight, isCrit, skillName) {
        var startX = isLeftToRight ? this.width * 0.24 : this.width * 0.76;
        var startY = this.height * 0.52;
        var targetX = isLeftToRight ? this.width * 0.76 : this.width * 0.24;
        var targetY = this.height * 0.52;
        var theme = isLeftToRight ? this.swordTheme : SWORD_THEMES.thunder;

        SoundFx.whoosh();

        this.flyingSwords.push({
            startX: startX,
            startY: startY,
            x: startX,
            y: startY,
            targetX: targetX,
            targetY: targetY,
            progress: 0,
            speed: isCrit ? 3.8 : 3.0,
            curveY: (Math.random() - 0.5) * 80,
            theme: theme,
            isCrit: isCrit,
            isLeft: isLeftToRight,
            skill: skillName,
            tail: []
        });
    };

    // 产生受击火花爆炸粒子
    ArenaStage.prototype.spawnSparks = function (x, y, color, count) {
        count = count || 16;
        for (var i = 0; i < count; i++) {
            var angle = Math.random() * Math.PI * 2;
            var spd = Math.random() * 160 + 60;
            this.particles.push({
                x: x,
                y: y,
                vx: Math.cos(angle) * spd,
                vy: Math.sin(angle) * spd,
                size: Math.random() * 3 + 2,
                color: color || '#fbbf24',
                alpha: 1,
                decay: Math.random() * 2.5 + 2.0
            });
        }
    };

    // 飘字系统
    ArenaStage.prototype.addPopText = function (text, x, y, type) {
        var color = '#f8fafc';
        var size = 16;
        var bold = false;

        if (type === 'CRITICAL') {
            color = '#fbbf24';
            size = 26;
            bold = true;
            text = '暴击 ' + text + ' !';
        } else if (type === 'MISS') {
            color = '#94a3b8';
            size = 18;
            text = '闪避';
        } else if (type === 'HEAL') {
            color = '#34d399';
            size = 18;
            text = '+' + text;
        } else if (type === 'SKILL') {
            color = '#c084fc';
            size = 20;
            bold = true;
        }

        this.popTexts.push({
            text: text,
            x: x + (Math.random() - 0.5) * 30,
            y: y,
            vy: -45,
            alpha: 1,
            size: size,
            color: color,
            bold: bold,
            life: 1.0
        });
    };

    // 战报事件调度驱动
    ArenaStage.prototype.handleBattleEvent = function (e) {
        if (!e) return;
        var isLeftActor = (!e.actor || e.actor === this.left.id);
        var actor = isLeftActor ? this.left : this.right;
        var target = isLeftActor ? this.right : this.left;

        if (e.type === 'ATTACK') {
            var isCrit = false;
            this.fireSword(isLeftActor, isCrit, e.text);
            if (e.text && e.text !== '普通攻击') {
                this.addPopText(e.text, actor.x, actor.y - 70, 'SKILL');
            }
        } else if (e.type === 'CRITICAL') {
            this.shake(8, 0.25);
            SoundFx.thunder();
        } else if (e.type === 'DAMAGE') {
            target.hp = Math.max(0, target.hp - (e.value || 0));
            target.hitFlash = 0.2;
            var isCritHit = (this.shakeTime > 0);
            this.spawnSparks(target.x, target.y, isCritHit ? '#f43f5e' : '#fbbf24', isCritHit ? 24 : 12);
            this.addPopText('-' + (e.value || 0), target.x, target.y - 40, isCritHit ? 'CRITICAL' : 'DAMAGE');
            SoundFx.clash();
        } else if (e.type === 'MISS') {
            this.addPopText('闪避', target.x, target.y - 30, 'MISS');
        } else if (e.type === 'HEAL') {
            actor.hp = Math.min(actor.maxHp, actor.hp + (e.value || 0));
            this.addPopText(String(e.value || 0), actor.x, actor.y - 40, 'HEAL');
        } else if (e.type === 'STATUS_APPLY') {
            var txt = e.text || '定身';
            if (txt.indexOf('沉默') !== -1) {
                target.silenced = true;
                this.addPopText('🔕 ' + txt, target.x, target.y - 55, 'SKILL');
                SoundFx.clash();
            } else if (txt.indexOf('灵盾') !== -1) {
                this.addPopText('🛡️ ' + txt, target.x, target.y - 50, 'HEAL');
            } else {
                this.addPopText(txt, target.x, target.y - 50, 'SKILL');
            }
        } else if (e.type === 'DEATH') {
            this.spawnSparks(target.x, target.y, '#e11d48', 40);
            this.shake(12, 0.4);
            SoundFx.thunder();
        }
    };

    ArenaStage.prototype.update = function (dt) {
        var i;
        // 角色位置动态居中布局
        this.left.x = this.width * 0.22;
        this.left.y = this.height * 0.54;
        this.right.x = this.width * 0.78;
        this.right.y = this.height * 0.54;

        // 血条平滑追赶消退（Ghost Bar）
        if (this.left.ghostHp > this.left.hp) {
            this.left.ghostHp = Math.max(this.left.hp, this.left.ghostHp - (this.left.maxHp * dt * 0.8));
        }
        if (this.right.ghostHp > this.right.hp) {
            this.right.ghostHp = Math.max(this.right.hp, this.right.ghostHp - (this.right.maxHp * dt * 0.8));
        }

        // 受击闪白衰减
        if (this.left.hitFlash > 0) this.left.hitFlash -= dt;
        if (this.right.hitFlash > 0) this.right.hitFlash -= dt;

        // 震屏倒计时
        if (this.shakeTime > 0) this.shakeTime -= dt;

        // 飞剑运动更新
        for (i = this.flyingSwords.length - 1; i >= 0; i--) {
            var s = this.flyingSwords[i];
            s.progress += dt * s.speed;

            // 轨迹记录
            s.tail.push({ x: s.x, y: s.y, alpha: 0.8 });
            if (s.tail.length > 10) s.tail.shift();
            for (var t = 0; t < s.tail.length; t++) {
                s.tail[t].alpha -= dt * 2.5;
            }

            if (s.progress >= 1) {
                // 击中目标
                this.flyingSwords.splice(i, 1);
            } else {
                // 贝塞尔弧线
                var p = s.progress;
                var directX = s.startX + (s.targetX - s.startX) * p;
                var directY = s.startY + (s.targetY - s.startY) * p;
                var arch = Math.sin(p * Math.PI) * s.curveY;
                s.x = directX;
                s.y = directY + arch;
            }
        }

        // 粒子更新
        for (i = this.particles.length - 1; i >= 0; i--) {
            var pt = this.particles[i];
            pt.x += pt.vx * dt;
            pt.y += pt.vy * dt;
            pt.alpha -= dt * pt.decay;
            if (pt.alpha <= 0) {
                this.particles.splice(i, 1);
            }
        }

        // 飘字更新
        for (i = this.popTexts.length - 1; i >= 0; i--) {
            var pop = this.popTexts[i];
            pop.y += pop.vy * dt;
            pop.life -= dt * 1.1;
            pop.alpha = Math.max(0, pop.life);
            if (pop.life <= 0) {
                this.popTexts.splice(i, 1);
            }
        }
    };

    ArenaStage.prototype.render = function () {
        var ctx = this.ctx;
        var w = this.width;
        var h = this.height;

        ctx.save();

        // 震屏偏移
        if (this.shakeTime > 0) {
            var ox = (Math.random() - 0.5) * this.shakeIntensity;
            var oy = (Math.random() - 0.5) * this.shakeIntensity;
            ctx.translate(ox, oy);
        }

        // 绘制仙侠星云渐变底色
        var bgGrad = ctx.createLinearGradient(0, 0, 0, h);
        bgGrad.addColorStop(0, '#090d16');
        bgGrad.addColorStop(0.5, '#0e1726');
        bgGrad.addColorStop(1, '#080d1a');
        ctx.fillStyle = bgGrad;
        ctx.fillRect(0, 0, w, h);

        // 绘制背景灵气法阵光晕
        this.renderMagicAura(ctx, w * 0.5, h * 0.55, h * 0.45);

        // 绘制对决双雄（我方 vs 敌方）
        this.renderFighter(ctx, this.left, true);
        this.renderFighter(ctx, this.right, false);

        // 绘制顶端双方动态血条
        this.renderTopHealthBars(ctx);

        // 绘制飞行中的仙剑与拖尾
        this.renderSwords(ctx);

        // 绘制打击火花粒子
        this.renderParticles(ctx);

        // 绘制浮空伤害数字
        this.renderPopTexts(ctx);

        ctx.restore();
    };

    // 绘制中央仙道太极/法阵光晕
    ArenaStage.prototype.renderMagicAura = function (ctx, cx, cy, r) {
        var now = performance.now() * 0.001;
        ctx.save();
        ctx.translate(cx, cy);

        // 地面悬浮云海椭圆
        var glow = ctx.createRadialGradient(0, 0, 10, 0, 0, r);
        glow.addColorStop(0, 'rgba(59, 130, 246, 0.12)');
        glow.addColorStop(0.7, 'rgba(147, 51, 234, 0.05)');
        glow.addColorStop(1, 'transparent');
        ctx.fillStyle = glow;
        ctx.beginPath();
        ctx.ellipse(0, 0, r * 1.6, r * 0.5, 0, 0, Math.PI * 2);
        ctx.fill();

        // 旋转的符文法环
        ctx.rotate(now * 0.2);
        ctx.strokeStyle = 'rgba(251, 191, 36, 0.15)';
        ctx.lineWidth = 1.5;
        ctx.setLineDash([8, 12]);
        ctx.beginPath();
        ctx.arc(0, 0, r * 0.8, 0, Math.PI * 2);
        ctx.stroke();

        ctx.restore();
    };

    // 绘制角色形象与浮空光环
    ArenaStage.prototype.renderFighter = function (ctx, fighter, isLeft) {
        var now = performance.now() * 0.003;
        var floatY = Math.sin(now + (isLeft ? 0 : 2.5)) * 6; // 上下浮空呼吸
        var x = fighter.x;
        var y = fighter.y + floatY;

        ctx.save();
        ctx.translate(x, y);

        // 角色下方灵气托盘
        ctx.beginPath();
        ctx.ellipse(0, 36, 32, 10, 0, 0, Math.PI * 2);
        ctx.fillStyle = isLeft ? 'rgba(52, 211, 153, 0.25)' : 'rgba(239, 68, 68, 0.25)';
        ctx.fill();

        // 受击白闪
        if (fighter.hitFlash > 0) {
            ctx.shadowColor = '#ffffff';
            ctx.shadowBlur = 24;
        }

        // 角色身躯光环圆卡
        var auraGrad = ctx.createRadialGradient(0, 0, 5, 0, 0, 34);
        if (isLeft) {
            auraGrad.addColorStop(0, '#10b981');
            auraGrad.addColorStop(0.7, '#047857');
            auraGrad.addColorStop(1, '#064e3b');
        } else {
            auraGrad.addColorStop(0, '#ef4444');
            auraGrad.addColorStop(0.7, '#b91c1c');
            auraGrad.addColorStop(1, '#7f1d1d');
        }

        ctx.beginPath();
        ctx.arc(0, 0, 28, 0, Math.PI * 2);
        ctx.fillStyle = fighter.hitFlash > 0 ? '#ffffff' : auraGrad;
        ctx.fill();
        ctx.lineWidth = 2.5;
        ctx.strokeStyle = isLeft ? '#6ee7b7' : '#fca5a5';
        ctx.stroke();

        // 角色中心仙道标志 / 剪影图标
        ctx.fillStyle = fighter.hitFlash > 0 ? '#000000' : '#ffffff';
        ctx.font = 'bold 22px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(isLeft ? '⚔️' : '👹', 0, 1);

        // 角色脚下名字标牌
        ctx.font = 'bold 12px sans-serif';
        ctx.fillStyle = '#e2e8f0';
        ctx.fillText(fighter.name, 0, 48);

        ctx.restore();
    };

    // 绘制顶端血条
    ArenaStage.prototype.renderTopHealthBars = function (ctx) {
        var w = this.width;
        var barW = Math.min(220, w * 0.35);
        var barH = 10;
        var topY = 24;

        // 左方血条
        var leftHpRate = Math.max(0, this.left.hp / this.left.maxHp);
        var leftGhostRate = Math.max(0, this.left.ghostHp / this.left.maxHp);
        var leftX = w * 0.08;

        // 名字与当前血量
        ctx.font = '11px sans-serif';
        ctx.fillStyle = '#94a3b8';
        ctx.textAlign = 'left';
        ctx.fillText(this.left.name + ' (' + Math.round(this.left.hp) + '/' + this.left.maxHp + ')', leftX, topY - 6);

        // 血条底槽
        ctx.fillStyle = 'rgba(15, 23, 42, 0.8)';
        ctx.fillRect(leftX, topY, barW, barH);

        // Ghost Bar
        ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
        ctx.fillRect(leftX, topY, barW * leftGhostRate, barH);

        // 当前血量绿色/青玉
        var hpGrad1 = ctx.createLinearGradient(leftX, 0, leftX + barW, 0);
        hpGrad1.addColorStop(0, '#059669');
        hpGrad1.addColorStop(1, '#10b981');
        ctx.fillStyle = hpGrad1;
        ctx.fillRect(leftX, topY, barW * leftHpRate, barH);

        ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
        ctx.strokeRect(leftX, topY, barW, barH);

        // 右方血条
        var rightHpRate = Math.max(0, this.right.hp / this.right.maxHp);
        var rightGhostRate = Math.max(0, this.right.ghostHp / this.right.maxHp);
        var rightX = w * 0.92 - barW;

        ctx.textAlign = 'right';
        ctx.fillText(this.right.name + ' (' + Math.round(this.right.hp) + '/' + this.right.maxHp + ')', rightX + barW, topY - 6);

        ctx.fillStyle = 'rgba(15, 23, 42, 0.8)';
        ctx.fillRect(rightX, topY, barW, barH);

        ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
        ctx.fillRect(rightX + barW * (1 - rightGhostRate), topY, barW * rightGhostRate, barH);

        var hpGrad2 = ctx.createLinearGradient(rightX, 0, rightX + barW, 0);
        hpGrad2.addColorStop(0, '#f43f5e');
        hpGrad2.addColorStop(1, '#e11d48');
        ctx.fillStyle = hpGrad2;
        ctx.fillRect(rightX + barW * (1 - rightHpRate), topY, barW * rightHpRate, barH);

        ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
        ctx.strokeRect(rightX, topY, barW, barH);
    };

    // 绘制飞剑与流光拖尾
    ArenaStage.prototype.renderSwords = function (ctx) {
        for (var i = 0; i < this.flyingSwords.length; i++) {
            var s = this.flyingSwords[i];
            ctx.save();

            // 拖尾光带
            for (var t = 0; t < s.tail.length; t++) {
                var node = s.tail[t];
                if (node.alpha <= 0) continue;
                ctx.beginPath();
                ctx.arc(node.x, node.y, (t + 1) * 0.8, 0, Math.PI * 2);
                ctx.fillStyle = s.theme.tail;
                ctx.globalAlpha = Math.max(0, node.alpha);
                ctx.fill();
            }

            // 飞剑本体
            ctx.globalAlpha = 1;
            ctx.translate(s.x, s.y);
            var angle = Math.atan2(s.targetY - s.startY, s.targetX - s.startX);
            ctx.rotate(angle);

            // 剑气光晕
            ctx.shadowColor = s.theme.glow;
            ctx.shadowBlur = s.isCrit ? 20 : 12;

            // 剑刃轮廓
            ctx.fillStyle = s.theme.color;
            ctx.beginPath();
            ctx.moveTo(16, 0);
            ctx.lineTo(-10, -4);
            ctx.lineTo(-6, 0);
            ctx.lineTo(-10, 4);
            ctx.closePath();
            ctx.fill();

            // 剑心白光
            ctx.fillStyle = '#ffffff';
            ctx.beginPath();
            ctx.moveTo(12, 0);
            ctx.lineTo(-4, -1.5);
            ctx.lineTo(-4, 1.5);
            ctx.closePath();
            ctx.fill();

            ctx.restore();
        }
    };

    // 绘制火花粒子
    ArenaStage.prototype.renderParticles = function (ctx) {
        for (var i = 0; i < this.particles.length; i++) {
            var pt = this.particles[i];
            ctx.save();
            ctx.globalAlpha = Math.max(0, pt.alpha);
            ctx.fillStyle = pt.color;
            ctx.beginPath();
            ctx.arc(pt.x, pt.y, pt.size, 0, Math.PI * 2);
            ctx.fill();
            ctx.restore();
        }
    };

    // 绘制浮空伤害数字
    ArenaStage.prototype.renderPopTexts = function (ctx) {
        for (var i = 0; i < this.popTexts.length; i++) {
            var pop = this.popTexts[i];
            ctx.save();
            ctx.globalAlpha = Math.max(0, pop.alpha);
            ctx.font = (pop.bold ? 'bold ' : '') + pop.size + 'px sans-serif';
            ctx.fillStyle = pop.color;
            ctx.textAlign = 'center';
            ctx.shadowColor = 'rgba(0, 0, 0, 0.8)';
            ctx.shadowBlur = 4;
            ctx.fillText(pop.text, pop.x, pop.y);
            ctx.restore();
        }
    };

    ArenaStage.SoundFx = SoundFx;
    ArenaStage.SWORD_THEMES = SWORD_THEMES;

    return ArenaStage;
}));
