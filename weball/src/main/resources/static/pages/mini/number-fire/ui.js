/**
 * 关卡大厅与结算面板 UI。
 */
(function (w) {
    var PASS_SCORE = 80;

    function NumberFireUi(game) {
        this.game = game;
        this.levelPage = 1;
        this.levelPageSize = 12;
    }

    NumberFireUi.prototype.showLobby = function () {
        document.getElementById('lobby').style.display = 'block';
        document.getElementById('playPanel').style.display = 'none';
        document.getElementById('resultPanel').style.display = 'none';
        this.renderLevelGrid();
    };

    NumberFireUi.prototype.showPlay = function () {
        document.getElementById('lobby').style.display = 'none';
        document.getElementById('resultPanel').style.display = 'none';
        document.getElementById('playPanel').style.display = 'block';
        this.updateStatus('', '');
    };

    NumberFireUi.prototype.renderLevelGrid = function () {
        var game = this.game;
        var grid = document.getElementById('levelGrid');
        grid.innerHTML = '';
        var pageCount = Math.max(1, Math.ceil(game.levels.length / this.levelPageSize));
        this.levelPage = Math.min(Math.max(this.levelPage, 1), pageCount);
        var start = (this.levelPage - 1) * this.levelPageSize;
        game.levels.slice(start, start + this.levelPageSize).forEach(function (lv, localIdx) {
            var idx = start + localIdx;
            var locked = lv.id > game.progress.unlocked;
            var best = game.progress.best[lv.id];
            var btn = document.createElement('button');
            btn.className = 'level-btn' + (locked ? ' locked' : '') + (best >= PASS_SCORE ? ' cleared' : '');
            btn.disabled = locked;
            btn.innerHTML = '<span class="lv-id">' + lv.id + '</span><span class="lv-meta">' + lv.digits + '位</span>'
                + (best != null ? '<span class="lv-best">' + best + '分</span>' : '');
            btn.title = lv.name + (locked ? '（未解锁）' : '');
            btn.addEventListener('click', function () {
                if (!locked) game.startLevel(idx);
            });
            grid.appendChild(btn);
        });
        var pager = document.getElementById('levelPager');
        if (!pager) {
            pager = document.createElement('div');
            pager.id = 'levelPager';
            pager.className = 'level-pager';
            grid.parentNode.appendChild(pager);
        }
        pager.innerHTML = '<button type="button" data-page="prev">上一页</button>'
            + '<span>' + this.levelPage + ' / ' + pageCount + '</span>'
            + '<button type="button" data-page="next">下一页</button>';
        pager.querySelector('[data-page="prev"]').disabled = this.levelPage === 1;
        pager.querySelector('[data-page="next"]').disabled = this.levelPage === pageCount;
        pager.querySelector('[data-page="prev"]').onclick = function () {
            game.ui.levelPage--;
            game.ui.renderLevelGrid();
        };
        pager.querySelector('[data-page="next"]').onclick = function () {
            game.ui.levelPage++;
            game.ui.renderLevelGrid();
        };
        document.getElementById('unlockHint').textContent =
            '已解锁 ' + Math.min(game.progress.unlocked, game.levels.length) + ' / ' + game.levels.length
            + ' 关 · 得分≥80 解锁下一关';
    };

    NumberFireUi.prototype.updateHud = function () {
        var game = this.game;
        var lv = game.level;
        document.getElementById('levelLabel').textContent = lv.name;
        document.getElementById('score').textContent = String(game.calcScore());
        document.getElementById('hits').textContent = game.hits + '/' + lv.targetHits;
        document.getElementById('time').textContent = String(game.remaining);
        this.updateStatus(game.inputBuf, game.readingHint);
    };

    /** 画布外状态条：输入缓冲与中文读音 */
    NumberFireUi.prototype.updateStatus = function (inputBuf, reading) {
        var inputEl = document.getElementById('inputShow');
        var readingEl = document.getElementById('readingShow');
        if (inputEl) inputEl.textContent = inputBuf || '—';
        if (readingEl) readingEl.textContent = reading || '—';
    };

    NumberFireUi.prototype.showResult = function (score, passed) {
        var game = this.game;
        var title = passed ? (score >= 95 ? '太厉害了！' : '你太棒了！') : '再接再厉！';
        if (!passed && w.MiniCelebrate) {
            w.MiniCelebrate.play({tone: 'encourage', title: title, note: '加油，再试一次就会更棒！', icon: '🌱'});
        }
        w.MiniResult.show({
            title: title,
            pattern: game.level.name + ' · 得分 ' + score,
            elapsed: Math.max(0, game.level.timeLimit - game.remaining) + '秒',
            celebrate: passed,
            tone: score >= 95 ? 'milestone' : 'success',
            onNext: function () {
                passed ? game.goNextLevel() : game.startLevel(game.levelIndex);
            }
        });
    };

    w.NumberFireUi = NumberFireUi;
    w.NumberFirePassScore = PASS_SCORE;
})(window);
