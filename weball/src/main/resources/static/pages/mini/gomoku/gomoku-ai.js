(function (w) {
    'use strict';

    var SIZE = 15;

    function countDir(cells, x, y, dx, dy, color) {
        var n = 0, cx = x + dx, cy = y + dy;
        while (cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE && cells[cy][cx] === color) {
            n++;
            cx += dx;
            cy += dy;
        }
        return n;
    }

    function checkWin(cells, x, y, color) {
        return countDir(cells, x, y, 1, 0, color) + countDir(cells, x, y, -1, 0, color) >= 4
            || countDir(cells, x, y, 0, 1, color) + countDir(cells, x, y, 0, -1, color) >= 4
            || countDir(cells, x, y, 1, 1, color) + countDir(cells, x, y, -1, -1, color) >= 4
            || countDir(cells, x, y, 1, -1, color) + countDir(cells, x, y, -1, 1, color) >= 4;
    }

    function scorePoint(cells, x, y, color) {
        if (cells[y][x]) return -1;
        cells[y][x] = color;
        var s = 0;
        [[1, 0], [0, 1], [1, 1], [1, -1]].forEach(function (d) {
            var n = 1 + countDir(cells, x, y, d[0], d[1], color) + countDir(cells, x, y, -d[0], -d[1], color);
            if (n >= 5) s += 100000;
            else if (n === 4) s += 10000;
            else if (n === 3) s += 1000;
            else if (n === 2) s += 100;
            else s += 10;
        });
        cells[y][x] = 0;
        return s;
    }

    function hasNeighbor(cells, x, y, distance) {
        for (var dy = -distance; dy <= distance; dy++) for (var dx = -distance; dx <= distance; dx++) {
            var nx = x + dx, ny = y + dy;
            if ((dx || dy) && nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE && cells[ny][nx]) return true;
        }
        return false;
    }

    /** 只搜索棋子附近的高价值点，控制移动端的搜索量。 */
    function candidateMoves(cells, aiColor, myColor, limit) {
        var list = [], occupied = cells.some(function (row) {
            return row.some(function (value) {
                return value !== 0;
            });
        });
        for (var y = 0; y < SIZE; y++) for (var x = 0; x < SIZE; x++) {
            if (!cells[y][x] && (!occupied || hasNeighbor(cells, x, y, 2))) {
                list.push({
                    x: x, y: y, score: scorePoint(cells, x, y, aiColor) * 1.1
                        + scorePoint(cells, x, y, myColor) * 1.25
                });
            }
        }
        if (!occupied) return [{x: 7, y: 7, score: 0}];
        list.sort(function (a, b) {
            return b.score - a.score;
        });
        return list.slice(0, limit);
    }

    function evaluate(cells, aiColor) {
        var score = 0;
        for (var y = 0; y < SIZE; y++) for (var x = 0; x < SIZE; x++) {
            if (!cells[y][x]) score += scorePoint(cells, x, y, aiColor) * 0.08;
        }
        return score;
    }

    function search(cells, aiColor, myColor, depth, maximizing, alpha, beta, deadline) {
        if (deadline && Date.now() >= deadline) throw TIMEOUT;
        if (!depth) return evaluate(cells, aiColor);
        var moves = candidateMoves(cells, aiColor, myColor, depth === 1 ? 10 : 7);
        var best = maximizing ? -1e15 : 1e15;
        for (var i = 0; i < moves.length; i++) {
            var move = moves[i], color = maximizing ? aiColor : myColor;
            cells[move.y][move.x] = color;
            var value;
            try {
                value = checkWin(cells, move.x, move.y, color) ? (maximizing ? 1e6 : -1e6)
                    : search(cells, aiColor, myColor, depth - 1, !maximizing, alpha, beta, deadline);
            } finally {
                cells[move.y][move.x] = 0;
            }
            if (maximizing) {
                best = Math.max(best, value);
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, value);
                beta = Math.min(beta, best);
            }
            if (beta <= alpha) break;
        }
        return best;
    }

    var TIMEOUT = {timeout: true};

    function findImmediate(cells, color, aiColor, myColor) {
        var moves = candidateMoves(cells, aiColor, myColor, 30);
        for (var i = 0; i < moves.length; i++) {
            cells[moves[i].y][moves[i].x] = color;
            var wins = checkWin(cells, moves[i].x, moves[i].y, color);
            cells[moves[i].y][moves[i].x] = 0;
            if (wins) return moves[i];
        }
        return null;
    }

    function pickAtDepth(cells, aiColor, myColor, depth, deadline, previous) {
        var win = findImmediate(cells, aiColor, aiColor, myColor);
        if (win) return win;
        var block = findImmediate(cells, myColor, aiColor, myColor);
        if (block) return block;
        var moves = candidateMoves(cells, aiColor, myColor, depth >= 4 ? 12 : 9);
        var best = -1e15, pick = previous || moves[0];
        for (var i = 0; i < moves.length; i++) {
            if (deadline && Date.now() >= deadline) throw TIMEOUT;
            var move = moves[i];
            cells[move.y][move.x] = aiColor;
            var value;
            try {
                value = checkWin(cells, move.x, move.y, aiColor) ? 1e6
                    : search(cells, aiColor, myColor, depth - 1, false, -1e15, 1e15, deadline);
            } finally {
                cells[move.y][move.x] = 0;
            }
            if (value > best) {
                best = value;
                pick = move;
            }
        }
        return pick;
    }

    /** 保持原有三个难度档位，返回当前局面的最佳落点。 */
    function pickMove(cells, aiColor, myColor, level) {
        if (level >= 4) return pickMasterMove(cells, aiColor, myColor, 4800);
        var depth = level === 1 ? 1 : (level === 2 ? 2 : 3);
        return pickAtDepth(cells, aiColor, myColor, depth, 0, null);
    }

    function pickMasterMove(cells, aiColor, myColor, budgetMs) {
        var deadline = Date.now() + Math.max(100, budgetMs || 4800);
        var best = candidateMoves(cells, aiColor, myColor, 1)[0] || null;
        for (var depth = 1; depth <= 8; depth++) {
            try {
                best = pickAtDepth(cells, aiColor, myColor, depth, deadline, best);
            } catch (e) {
                if (e !== TIMEOUT) throw e;
                break;
            }
        }
        return best;
    }

    w.GomokuAi = {pickMove: pickMove, pickMasterMove: pickMasterMove};
})(typeof self !== 'undefined' ? self : window);
