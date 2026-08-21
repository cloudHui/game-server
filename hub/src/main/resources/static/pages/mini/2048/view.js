var Cosmic2048View = (function () {
    'use strict';

    var board = document.getElementById('board');
    var message = document.getElementById('message');
    var score = document.getElementById('score');
    var best = document.getElementById('best');
    var undo = document.getElementById('undo');

    function tileLevel(value) {
        return Math.min(12, Math.log(value) / Math.log(2) - 1);
    }

    function render(state) {
        var fragment = document.createDocumentFragment();
        state.grid.forEach(function (row, r) {
            row.forEach(function (value, c) {
                var cell = document.createElement('div');
                cell.className = 'cell';
                if (value) {
                    var tile = document.createElement('div');
                    var key = r + '-' + c;
                    tile.className = 'tile';
                    if (state.merged[key]) tile.classList.add('merged');
                    if (state.spawned === key) tile.classList.add('spawned');
                    tile.dataset.level = tileLevel(value);
                    tile.textContent = value;
                    cell.appendChild(tile);
                }
                fragment.appendChild(cell);
            });
        });
        board.replaceChildren(fragment);
        score.textContent = state.score;
        best.textContent = state.best;
        undo.disabled = !state.canUndo;
    }

    function showMessage(won) {
        message.hidden = false;
        document.getElementById('messageTitle').textContent = won ? '发现新宇宙！' : '星河已满';
        document.getElementById('messageText').textContent = won ?
            '你合成了 2048，仍可继续探索更远星空。' :
            '没有可以合并的星体了，再来一次吧。';
        document.getElementById('continue').hidden = !won;
    }

    function hideMessage() {
        message.hidden = true;
    }

    return {
        board: board,
        hideMessage: hideMessage,
        message: message,
        render: render,
        showMessage: showMessage
    };
})();
