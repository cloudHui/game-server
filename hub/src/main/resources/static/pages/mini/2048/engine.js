var Cosmic2048Engine = (function () {
    'use strict';

    var SIZE = 4;

    function emptyGrid() {
        return Array.from({length: SIZE}, function () {
            return [0, 0, 0, 0];
        });
    }

    function copyGrid(grid) {
        return grid.map(function (row) {
            return row.slice();
        });
    }

    function availableCells(grid) {
        var cells = [];
        grid.forEach(function (row, r) {
            row.forEach(function (value, c) {
                if (!value) cells.push([r, c]);
            });
        });
        return cells;
    }

    function addRandomTile(grid) {
        var cells = availableCells(grid);
        if (!cells.length) return null;
        var cell = cells[Math.floor(Math.random() * cells.length)];
        grid[cell[0]][cell[1]] = Math.random() < 0.9 ? 2 : 4;
        return cell.join('-');
    }

    function coordinates(direction) {
        var all = [];
        for (var i = 0; i < SIZE; i++) {
            var line = [];
            for (var j = 0; j < SIZE; j++) {
                var r = direction === 0 ? j : direction === 2 ? SIZE - 1 - j : i;
                var c = direction === 1 ? SIZE - 1 - j : direction === 3 ? j : i;
                line.push([r, c]);
            }
            all.push(line);
        }
        return all;
    }

    function mergeLine(values) {
        var compact = values.filter(Boolean);
        var result = [];
        var score = 0;
        var mergedIndexes = [];
        for (var i = 0; i < compact.length; i++) {
            if (compact[i] === compact[i + 1]) {
                result.push(compact[i] * 2);
                score += compact[i] * 2;
                mergedIndexes.push(result.length - 1);
                i++;
            } else {
                result.push(compact[i]);
            }
        }
        while (result.length < SIZE) result.push(0);
        return {values: result, score: score, mergedIndexes: mergedIndexes};
    }

    function move(grid, direction) {
        var next = copyGrid(grid);
        var score = 0;
        var changed = false;
        var merged = {};

        coordinates(direction).forEach(function (line) {
            var result = mergeLine(line.map(function (cell) {
                return next[cell[0]][cell[1]];
            }));
            score += result.score;
            line.forEach(function (cell, index) {
                if (next[cell[0]][cell[1]] !== result.values[index]) changed = true;
                next[cell[0]][cell[1]] = result.values[index];
            });
            result.mergedIndexes.forEach(function (index) {
                merged[line[index].join('-')] = true;
            });
        });
        return {grid: next, score: score, changed: changed, merged: merged};
    }

    function movesAvailable(grid) {
        if (availableCells(grid).length) return true;
        for (var r = 0; r < SIZE; r++) {
            for (var c = 0; c < SIZE; c++) {
                if (r < SIZE - 1 && grid[r][c] === grid[r + 1][c]) return true;
                if (c < SIZE - 1 && grid[r][c] === grid[r][c + 1]) return true;
            }
        }
        return false;
    }

    return {
        addRandomTile: addRandomTile,
        copyGrid: copyGrid,
        emptyGrid: emptyGrid,
        move: move,
        movesAvailable: movesAvailable
    };
})();
