#!/usr/bin/env node
'use strict';

var fs = require('fs');
var vm = require('vm');
var path = require('path');
var mini = path.join(__dirname, '../web/src/main/resources/static/pages/mini');
var context = { self: {}, Date: Date };
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(mini, 'chess-rules.js'), 'utf8'), context);
context.self.ChessRules = context.self.ChessRules;

function emptyBoard() {
    return Array.from({ length: 10 }, function () { return Array(9).fill('.'); });
}

function assert(condition, message) {
    if (!condition) throw new Error(message);
}

var board = emptyBoard();
board[9][4] = 'K';
board[0][3] = 'k';
board[5][4] = 'R';
board[0][4] = 'r';
var legalTargets = context.self.ChessRules.legalFrom(board, 5, 4);
assert(!legalTargets.some(function (target) { return target[1] !== 4; }),
    '不得移动挡将棋子后暴露己方将帅');
assert(!context.self.ChessRules.isMoveLegal(board, 0, 4, 9, 4),
    '不能以直接吃帅代替将死判定');

board = emptyBoard();
board[9][4] = 'K';
board[0][4] = 'k';
board[5][4] = 'R';
assert(context.self.ChessRules.faceToFace(board) === false, '中间有棋子时不应将帅照面');

board = emptyBoard();
board[9][4] = 'K';
board[0][4] = 'k';
board[1][3] = 'R';
board[1][5] = 'R';
board[2][4] = 'R';
assert(context.self.ChessRules.isCheckmate(board, false), '无解将走法时应判黑方被将死');
assert(!context.self.ChessRules.isMoveLegal(board, 2, 4, 0, 4), '将死不能替换为吃将');

console.log('棋类规则回归检查通过');
