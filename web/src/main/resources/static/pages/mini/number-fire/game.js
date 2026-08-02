/**
 * 数字射击入口：关卡流程、输入匹配、计分结算。
 */
(function (w) {
    var STORAGE_KEY = 'number-fire-progress';

    function Game() {
        this.canvas = document.getElementById('gameCanvas');
        this.canvas.width = 800;
        this.canvas.height = 560;
        this.render = new w.NumberFireRender(this.canvas);
        this.render.preparePlane('planeSvg');
        this.ui = new w.NumberFireUi(this);

        this.levels = w.NumberFireLevels || [];
        this.levelIndex = 0;
        this.progress = this.loadProgress();
        this.level = null;

        this.plane = {
            x: 360,
            y: 460,
            width: 80,
            height: 60,
            targetX: 360,
            isMoving: false,
            pending: false,
            target: null,
            rotation: 0
        };
        this.targets = [];
        this.queue = [];
        this.bullets = [];
        this.explosions = [];
        this.inputBuf = '';
        this.lastInputAt = 0;
        this.readingHint = '';

        this.hits = 0;
        this.leaks = 0;
        this.wrong = 0;
        this.remaining = 60;
        this.timer = null;
        this.running = false;
        this.ended = false;
        this.lastSpawn = 0;
        this.lastGen = 0;

        this.bindUi();
        this.ui.showLobby();
    }

    Game.prototype.loadProgress = function () {
        try {
            var data = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
            return {unlocked: data.unlocked || 1, best: data.best || {}};
        } catch (e) {
            return {unlocked: 1, best: {}};
        }
    };

    Game.prototype.saveProgress = function () {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(this.progress));
    };

    Game.prototype.bindUi = function () {
        var self = this;
        document.getElementById('btnRetry').addEventListener('click', function () {
            self.startLevel(self.levelIndex);
        });
        document.getElementById('btnNext').addEventListener('click', function () {
            self.goNextLevel();
        });
        document.getElementById('btnLobby').addEventListener('click', function () {
            self.endToLobby();
        });
        document.getElementById('btnBackLobby').addEventListener('click', function () {
            self.endToLobby();
        });
        document.addEventListener('keydown', function (e) {
            self.onKey(e);
        });
        // 手机虚拟数字键盘：与物理按键共用 onKey
        if (w.MiniFirePad) {
            w.MiniFirePad.mountDigits(document.getElementById('touchPad'), function (key) {
                self.onKey({
                    key: key, preventDefault: function () {
                    }
                });
            });
        }
    };

    Game.prototype.endToLobby = function () {
        this.stopTimer();
        this.running = false;
        this.ended = true;
        this.ui.showLobby();
    };

    Game.prototype.startLevel = function (index) {
        if (index < 0 || index >= this.levels.length) return;
        this.levelIndex = index;
        this.level = this.levels[index];
        this.resetRound();
        this.ui.showPlay();
        this.ui.updateHud();
        this.running = true;
        this.ended = false;
        this.startTimer();
        this.loop();
    };

    Game.prototype.resetRound = function () {
        this.targets = [];
        this.queue = [];
        this.bullets = [];
        this.explosions = [];
        this.inputBuf = '';
        this.readingHint = '';
        this.hits = 0;
        this.leaks = 0;
        this.wrong = 0;
        this.remaining = this.level.timeLimit;
        this.lastSpawn = 0;
        this.lastGen = 0;
        this.plane.x = this.canvas.width / 2 - this.plane.width / 2;
        this.plane.targetX = this.plane.x;
        this.plane.isMoving = false;
        this.plane.pending = false;
        this.plane.target = null;
    };

    Game.prototype.calcScore = function () {
        var lv = this.level;
        if (!lv) return 0;
        var base = Math.round((this.hits / lv.targetHits) * 100);
        return Math.max(0, Math.min(100, base - this.leaks * 4 - this.wrong * 2));
    };

    Game.prototype.startTimer = function () {
        var self = this;
        this.stopTimer();
        this.timer = setInterval(function () {
            if (!self.running) return;
            self.remaining--;
            self.ui.updateHud();
            if (self.remaining <= 0) self.finishLevel();
        }, 1000);
    };

    Game.prototype.stopTimer = function () {
        if (this.timer) {
            clearInterval(this.timer);
            this.timer = null;
        }
    };

    Game.prototype.onKey = function (e) {
        if (!this.running || this.ended) return;
        if (e.key === 'Backspace') {
            this.inputBuf = this.inputBuf.slice(0, -1);
            this.ui.updateStatus(this.inputBuf, this.readingHint);
            return;
        }
        if (e.key === 'Escape') {
            this.inputBuf = '';
            this.ui.updateStatus(this.inputBuf, this.readingHint);
            return;
        }
        if (!/^[0-9]$/.test(e.key)) return;
        e.preventDefault();
        this.inputBuf += e.key;
        this.lastInputAt = Date.now();
        if (this.inputBuf.length > this.level.digits) this.inputBuf = e.key;
        this.ui.updateStatus(this.inputBuf, this.readingHint);
        this.tryMatchInput();
    };

    Game.prototype.tryMatchInput = function () {
        if (this.plane.isMoving || this.plane.pending) return;
        var buf = this.inputBuf;
        if (!buf) return;
        var target = this.targets.find(function (t) {
            return String(t.value) === buf;
        });
        if (!target) {
            var prefix = this.targets.some(function (t) {
                return String(t.value).indexOf(buf) === 0;
            });
            if (!prefix && buf.length >= this.level.digits) {
                this.wrong++;
                this.inputBuf = '';
                this.ui.updateHud();
            }
            return;
        }
        this.inputBuf = '';
        this.ui.updateStatus('', this.readingHint);
        this.plane.targetX = target.x + target.width / 2 - this.plane.width / 2;
        this.plane.isMoving = true;
        this.plane.pending = true;
        this.plane.target = target;
    };

    Game.prototype.shoot = function (target) {
        this.bullets.push({
            x: this.plane.x + this.plane.width / 2,
            y: this.plane.y,
            value: target.value,
            speed: 14,
            target: target
        });
    };

    Game.prototype.onHit = function (target) {
        this.hits++;
        this.readingHint = w.NumberFireSpeech.toChinese(target.value);
        w.NumberFireSpeech.speakNumber(target.value);
        this.ui.updateHud();
        if (this.hits >= this.level.targetHits) this.finishLevel();
    };

    Game.prototype.finishLevel = function () {
        if (this.ended) return;
        this.ended = true;
        this.running = false;
        this.stopTimer();
        var score = this.calcScore();
        var lv = this.level;
        var pass = w.NumberFirePassScore || 80;
        if (score > (this.progress.best[lv.id] || 0)) this.progress.best[lv.id] = score;
        var passed = score >= (lv.passScore || pass);
        if (passed && this.progress.unlocked < lv.id + 1) {
            this.progress.unlocked = Math.min(this.levels.length, lv.id + 1);
        }
        this.saveProgress();
        this.ui.showResult(score, passed);
    };

    Game.prototype.goNextLevel = function () {
        if (this.levelIndex < this.levels.length - 1) this.startLevel(this.levelIndex + 1);
        else this.ui.showLobby();
    };

    Game.prototype.draw = function () {
        this.render.clear();
        this.render.drawPlane(this.plane);
        this.render.drawTargets(this.targets);
        this.render.drawBullets(this.bullets);
        this.render.drawExplosions(this.explosions);
    };

    Game.prototype.loop = function () {
        var self = this;
        w.NumberFireEngine.tick(this);
        this.draw();
        if (this.running && !this.ended) requestAnimationFrame(function () {
            self.loop();
        });
    };

    w.onload = function () {
        new Game();
    };
})(window);
