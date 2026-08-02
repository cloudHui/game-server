importScripts('chess-rules.js', 'chess-ai.js');

/** Worker 返回走法而不修改页面棋盘，页面会再次校验并执行。 */
self.onmessage = function (event) {
    var data = event.data;
    var move = data.level >= 4
        ? self.ChessAi.pickMasterMove(data.board, data.budgetMs || 7800)
        : self.ChessAi.pickMove(data.board, data.level);
    self.postMessage({id: data.id, move: move});
};
