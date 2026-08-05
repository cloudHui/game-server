#!/usr/bin/env node
'use strict';

var fs = require('fs');
var vm = require('vm');
var source = fs.readFileSync(
    'web/src/main/resources/static/pages/mini/2048/engine.js', 'utf8');
var context = {};
vm.createContext(context);
vm.runInContext(source, context);
var engine = context.Cosmic2048Engine;

function expect(actual, expected, message) {
    var normalized = JSON.stringify(actual);
    if (normalized !== JSON.stringify(expected)) {
        throw new Error(message + ': ' + normalized);
    }
}

expect(engine.move([[2, 2, 2, 2], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]], 3).grid[0],
    [4, 4, 0, 0], '同一行应分别合并一次');
expect(engine.move([[4, 4, 8, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]], 1).grid[0],
    [0, 0, 8, 8], '向右移动应保持正确顺序');
expect(engine.movesAvailable([[2, 4, 2, 4], [4, 2, 4, 2], [2, 4, 2, 4], [4, 2, 4, 2]]),
    false, '满盘且无相邻同值时应结束');

process.stdout.write('2048 核心规则校验通过。\n');
