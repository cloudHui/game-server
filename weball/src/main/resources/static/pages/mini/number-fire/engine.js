/**
 * 数字射击运行时：目标生成、子弹碰撞、爆炸帧更新。
 */
(function (w) {
    /** 收集屏幕与队列中已占用数字，保证同屏不重复 */
    function collectUsed(game) {
        var used = {};
        game.targets.forEach(function (t) {
            used[t.value] = true;
        });
        game.queue.forEach(function (t) {
            used[t.value] = true;
        });
        return used;
    }

    /**
     * 从未占用数字中随机取值；一位数等小范围走完整候选池，大范围随机探测。
     * 无可用数字时返回 null，避免回退成重复值。
     */
    function randomValue(game) {
        var lv = game.level;
        var used = collectUsed(game);
        var span = lv.max - lv.min + 1;

        if (span <= 120) {
            var pool = [];
            for (var n = lv.min; n <= lv.max; n++) {
                if (!used[n]) pool.push(n);
            }
            if (!pool.length) return null;
            return pool[Math.floor(Math.random() * pool.length)];
        }

        for (var i = 0; i < 80; i++) {
            var v = lv.min + Math.floor(Math.random() * span);
            if (!used[v]) return v;
        }
        return null;
    }

    function createTarget(game) {
        var value = randomValue(game);
        if (value == null) return null;

        var minDist = 56;
        var x = 20;
        var tries = 0;
        do {
            x = 20 + Math.random() * (game.canvas.width - 80);
            tries++;
        } while (tries < 40 && game.targets.some(function (t) {
            return Math.abs(t.x - x) < minDist;
        }));
        var text = String(value);
        return {x: x, y: -36, value: value, width: Math.max(36, text.length * 18), height: 36};
    }

    function enqueue(game) {
        var lv = game.level;
        if (game.hits >= lv.targetHits) return;
        if (game.targets.length + game.queue.length >= lv.maxOnScreen) return;
        var next = createTarget(game);
        if (!next) return;
        game.queue.push(next);
    }

    function createExplosion(game, x, y, value) {
        var colors = ['#FF6B6B', '#4ECDC4', '#FFE66D', '#95E1D3'];
        var particles = [];
        for (var i = 0; i < 14; i++) {
            particles.push({
                x: 0, y: 0,
                angle: Math.random() * Math.PI * 2,
                speed: 0.6 + Math.random() * 1.6,
                size: 3 + Math.random() * 4,
                color: colors[i % colors.length]
            });
        }
        game.explosions.push({x: x, y: y, value: value, particles: particles, alpha: 1, duration: 28, frame: 0});
    }

    function updatePlane(game) {
        if (!game.plane.isMoving) {
            game.plane.rotation = Math.sin(Date.now() * 0.01) * 0.05;
            return;
        }
        var dx = game.plane.targetX - game.plane.x;
        if (Math.abs(dx) < 2) {
            game.plane.x = game.plane.targetX;
            game.plane.isMoving = false;
            if (game.plane.pending && game.plane.target) {
                game.shoot(game.plane.target);
                game.plane.pending = false;
                game.plane.target = null;
            }
        } else {
            game.plane.x += dx * 0.35;
            game.plane.rotation = Math.sin(Date.now() * 0.01) * 0.1 + (dx > 0 ? 0.1 : -0.1);
        }
    }

    function updateTargets(game) {
        for (var i = game.targets.length - 1; i >= 0; i--) {
            var t = game.targets[i];
            t.y += game.level.fallSpeed * (1 + (Math.random() * 0.12 - 0.06));
            if (t.y > game.canvas.height) {
                game.targets.splice(i, 1);
                game.leaks++;
                game.ui.updateHud();
            }
        }
    }

    function updateBullets(game) {
        for (var i = game.bullets.length - 1; i >= 0; i--) {
            var b = game.bullets[i];
            b.y -= b.speed;
            if (b.target && b.y <= b.target.y + b.target.height) {
                var idx = game.targets.indexOf(b.target);
                if (idx !== -1) {
                    createExplosion(game, b.target.x + b.target.width / 2, b.target.y + b.target.height / 2, b.target.value);
                    game.targets.splice(idx, 1);
                    game.bullets.splice(i, 1);
                    game.onHit(b.target);
                    continue;
                }
            }
            if (b.y < 0) game.bullets.splice(i, 1);
        }
    }

    function updateExplosions(game) {
        for (var i = game.explosions.length - 1; i >= 0; i--) {
            var ex = game.explosions[i];
            ex.frame++;
            ex.alpha = 1 - ex.frame / ex.duration;
            if (ex.frame >= ex.duration) game.explosions.splice(i, 1);
        }
    }

    function tick(game) {
        if (!game.running || game.ended) return;
        if (game.inputBuf && Date.now() - game.lastInputAt > 1800) {
            game.inputBuf = '';
            if (game.ui) game.ui.updateStatus('', game.readingHint);
        }
        updatePlane(game);
        var now = Date.now();
        var lv = game.level;
        if (now - game.lastSpawn > lv.spawnRate) {
            enqueue(game);
            game.lastSpawn = now;
        }
        if (now - game.lastGen > lv.genDelay && game.queue.length && game.targets.length < lv.maxOnScreen) {
            game.targets.push(game.queue.shift());
            game.lastGen = now;
        }
        updateTargets(game);
        updateBullets(game);
        updateExplosions(game);
    }

    w.NumberFireEngine = {tick: tick};
})(window);
