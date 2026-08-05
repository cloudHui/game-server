(function () {
    'use strict';

    MiniGames.requireLogin();
    document.getElementById('back').href = appUrl('/pages/mini/index.html');

    var STATE_KEY = 'cosmic2048-state-v1';
    var BEST_KEY = 'cosmic2048-best-v1';
    var engine = Cosmic2048Engine;
    var view = Cosmic2048View;
    var grid;
    var score;
    var won;
    var history = [];
    var sound = true;
    var merged = {};
    var spawned = null;

    function snapshot() {
        return {grid: engine.copyGrid(grid), score: score, won: won};
    }

    function bestScore() {
        return Math.max(score, Number(localStorage.getItem(BEST_KEY) || 0));
    }

    function save() {
        localStorage.setItem(STATE_KEY, JSON.stringify(snapshot()));
        localStorage.setItem(BEST_KEY, bestScore());
    }

    function render() {
        view.render({
            grid: grid,
            score: score,
            best: bestScore(),
            canUndo: history.length > 0,
            merged: merged,
            spawned: spawned
        });
    }

    function restart() {
        grid = engine.emptyGrid();
        score = 0;
        won = false;
        history = [];
        merged = {};
        spawned = null;
        engine.addRandomTile(grid);
        engine.addRandomTile(grid);
        view.hideMessage();
        save();
        render();
    }

    function load() {
        try {
            var saved = JSON.parse(localStorage.getItem(STATE_KEY));
            if (saved && saved.grid && saved.grid.length === 4) {
                grid = saved.grid;
                score = saved.score || 0;
                won = !!saved.won;
                return;
            }
        } catch (ignore) {
            // 损坏的本地存档直接开始新游戏。
        }
        restart();
    }

    function contains2048() {
        return grid.some(function (row) {
            return row.some(function (value) {
                return value >= 2048;
            });
        });
    }

    function playTone(frequency) {
        if (!sound) return;
        try {
            var AudioContext = window.AudioContext || window.webkitAudioContext;
            var context = new AudioContext();
            var oscillator = context.createOscillator();
            var gain = context.createGain();
            oscillator.frequency.value = frequency;
            gain.gain.setValueAtTime(0.055, context.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, context.currentTime + 0.11);
            oscillator.connect(gain);
            gain.connect(context.destination);
            oscillator.onended = function () { context.close(); };
            oscillator.start();
            oscillator.stop(context.currentTime + 0.11);
        } catch (ignore) {
            // 不支持 Web Audio 时静默游戏。
        }
    }

    function move(direction) {
        if (!view.message.hidden) return;
        var result = engine.move(grid, direction);
        if (!result.changed) return;

        history.push(snapshot());
        if (history.length > 10) history.shift();
        grid = result.grid;
        score += result.score;
        merged = result.merged;
        spawned = engine.addRandomTile(grid);
        save();
        render();
        playTone(result.score ? 520 : 280);

        if (!won && contains2048()) {
            won = true;
            save();
            view.showMessage(true);
        } else if (!engine.movesAvailable(grid)) {
            view.showMessage(false);
        }
    }

    function undo() {
        var previous = history.pop();
        if (!previous) return;
        grid = previous.grid;
        score = previous.score;
        won = previous.won;
        merged = {};
        spawned = null;
        view.hideMessage();
        save();
        render();
    }

    function bindInput() {
        var start;
        view.board.addEventListener('pointerdown', function (event) {
            start = {x: event.clientX, y: event.clientY};
            view.board.setPointerCapture(event.pointerId);
        });
        view.board.addEventListener('pointerup', function (event) {
            if (!start) return;
            var dx = event.clientX - start.x;
            var dy = event.clientY - start.y;
            start = null;
            if (Math.max(Math.abs(dx), Math.abs(dy)) < 24) return;
            move(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0));
        });
        view.board.addEventListener('pointercancel', function () { start = null; });

        document.addEventListener('keydown', function (event) {
            var directions = {ArrowUp: 0, ArrowRight: 1, ArrowDown: 2, ArrowLeft: 3};
            if (directions[event.key] === undefined) return;
            event.preventDefault();
            move(directions[event.key]);
        });
    }

    document.getElementById('undo').onclick = undo;
    document.getElementById('retry').onclick = restart;
    document.getElementById('continue').onclick = view.hideMessage;
    document.getElementById('restart').onclick = function () {
        if (confirm('确定重新开始这一局吗？')) restart();
    };
    document.getElementById('sound').onclick = function () {
        sound = !sound;
        this.textContent = sound ? '♪' : '×';
        this.setAttribute('aria-label', sound ? '关闭声音' : '开启声音');
    };

    bindInput();
    load();
    render();
})();
